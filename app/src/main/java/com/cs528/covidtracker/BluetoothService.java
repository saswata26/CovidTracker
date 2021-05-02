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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;

import static com.cs528.covidtracker.App.fusedLocationClient;
import static com.cs528.covidtracker.App.instance;


public class BluetoothService extends BroadcastReceiver {

    private BluetoothAdapter mbtAdapter;//for bluetooth
    private HashSet<String> addedDevices;//to prevent adding duplicate devices
    private Location loc;//store location

    public void onReceive(Context context, Intent intent) {
        addedDevices = new HashSet<String>();
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
                if (!addedDevices.contains(device.getAddress())) {//check if this is a duplicate
                    addedDevices.add(device.getAddress());//add to catch future duplicates
                    // Log.d("info", "success" + device.toString() + "huh\n" + device.getName() + "dev\n" + device.getType() + "class\n" + device.getBluetoothClass() + "alias\n" + device.getBondState() + "addr\n" + rssi+"/\n LOC:"+loc.toString());

                    JSONObject sto = new JSONObject();
                    try {
                        sto.put("time", System.currentTimeMillis() + "");//consider Calendar.getInstance().getTime();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    try {
                        sto.put("lat", loc.getLatitude() + "");//the + "" is used in all fields just incase value is null
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    try {
                        sto.put("lng", loc.getLongitude() + "");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    try {
                        sto.put("bt_id", device.getAddress() + "");//bluetooth address
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    try {
                        sto.put("bt_strength", ((double)(rssi + 128) / 255) + "");//convert from -128 to 127 into 0-1
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.d("info", sto.toString());
                    SharedPreferences prefs = App.getPrefs();
                    prefs.edit().putString(Params.BluetoothDataKey, sto.toString());
                } else {
                    //Log.d("info","dupe");
                }
            }
        }
    };


}
