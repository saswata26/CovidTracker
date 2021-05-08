package com.cs528.covidtracker;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

import static com.cs528.covidtracker.App.fusedLocationClient;
import static com.cs528.covidtracker.App.instance;


public class BluetoothService extends BroadcastReceiver {

    private BluetoothAdapter mbtAdapter;//for bluetooth
    private Location loc;//store location
    private JsonArray interactionsArray;

    public void onReceive(Context context, Intent intent) {
        mbtAdapter = BluetoothAdapter.getDefaultAdapter();

        //get location:
        if (ActivityCompat.checkSelfPermission(instance, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(instance, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //IMP STORE WITHOUT LOCATION INFO TODO
            //Log.d("MapDemoActivity", "Error trying to get last GPS location");

            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    // Log.d("success", location.toString() + "\n" + currentTime.toString());
                    loc = location;
                }
            }


        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                //IMP STORE WITHOUT LOCATION INFO TODO
                // Log.d("MapDemoActivity", "Error trying to get last GPS location");
                e.printStackTrace();
            }
        });


        //wait 15 seconds for location. since we run this process repeatedly we don't want to wait minutes for the location since that might cause overlapping services
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                if(loc!=null) {//if location is not found then skip
                    mbtAdapter.startDiscovery();
                    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                    instance.registerReceiver(mReceiver, filter);//to call method for when bluetooth device is found

                    Handler handler2 = new Handler();//wait 30 seconds before canceling the bluetooth search
                    handler2.postDelayed(new Runnable() {
                        public void run() {
                            mbtAdapter.cancelDiscovery();
                            instance.unregisterReceiver(mReceiver);
                            App.getPrefs(context).edit().putLong(Params.LastBTScanKey, System.currentTimeMillis()).apply();
                        }
                    }, 30000);   //30 seconds



                }
            }
        }, 15000);   //15 seconds
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                SharedPreferences prefs = App.getPrefs(context);

                if (interactionsArray == null)
                    interactionsArray = JsonParser.parseString(prefs.getString(Params.BluetoothDataKey, "[]")).getAsJsonArray();

                // Check if we've seen this device within the last scan
                long lastScanTime = prefs.getLong(Params.LastBTScanKey, 0);
                long lastTimeChecked = lastTimeChecked(interactionsArray, device.getAddress());

                if (lastTimeChecked > (lastScanTime - AlarmHandler.interval * 2)) {
                    updateLastTimeChecked(prefs, interactionsArray, device.getAddress());
                    return;
                }

                JsonObject sto = new JsonObject();
                sto.addProperty("lastTimeChecked", System.currentTimeMillis() + "");//consider Calendar.getInstance().getTime();
                sto.addProperty("time", System.currentTimeMillis() + "");//consider Calendar.getInstance().getTime();
                sto.addProperty("lat", loc.getLatitude() + "");//the + "" is used in all fields just incase value is null
                sto.addProperty("lng", loc.getLongitude() + "");
                sto.addProperty("bt_id", device.getAddress() + "");//bluetooth address
                sto.addProperty("bt_strength", ((double)(rssi + 128) / 255) + "");//convert from -128 to 127 into 0-1
                Log.d("info", sto.toString());

                interactionsArray.add(sto);

                prefs.edit().putString(Params.BluetoothDataKey, interactionsArray.toString()).apply();
            }
        }
    };

    private void updateLastTimeChecked(SharedPreferences prefs, JsonArray interactions, String id) {
        for (int i = 0; i < interactions.size(); i++) {
            String device_id = interactions.get(i).getAsJsonObject().get("bt_id").getAsString();

            if (device_id.equals(id)) {
                interactions.get(i).getAsJsonObject().addProperty("lastTimeChecked", System.currentTimeMillis() + "");
                prefs.edit().putString(Params.BluetoothDataKey, interactions.toString()).apply();

                break;
            }
        }
    }

    private long lastTimeChecked(JsonArray interactions, String id) {
        long lastTime = -999999;

        for (JsonElement interaction : interactions) {
            JsonObject obj = interaction.getAsJsonObject();

            if (obj.get("bt_id").getAsString().equals(id)) {
                try {
                    lastTime = Math.max(Long.parseLong(obj.get("lastTimeChecked").getAsString()), lastTime);
                } catch (Exception e) {}
            }
        }

        return lastTime;
    }
}
