package com.cs528.covidtracker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executor;

import static com.cs528.covidtracker.App.fusedLocationClient;
import static com.cs528.covidtracker.App.instance;


public class BluetoothService extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {


        Date currentTime = Calendar.getInstance().getTime();
    Location loc;
        if (ActivityCompat.checkSelfPermission(instance, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(instance, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            //IMP STORE WITHOUT LOCATION INFO TODO
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    //IMP STORE WITH LOCATION INFO TODO
                    Toast.makeText(context, currentTime.toString(), Toast.LENGTH_SHORT).show();
                    Log.d("success", location.toString()+"\n"+currentTime.toString());

                }
            }


        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                //IMP STORE WITHOUT LOCATION INFO TODO
                Log.d("MapDemoActivity", "Error trying to get last GPS location");
                e.printStackTrace();
            }
        });



//        Toast.makeText(context, currentTime.toString(),Toast.LENGTH_SHORT).show();
       // Toast.makeText(context, currentTime.toString(), Toast.LENGTH_SHORT).show();

    }

}
