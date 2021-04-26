package com.cs528.covidtracker;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.core.app.ActivityCompat;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.mapbox.mapboxsdk.Mapbox;

public class App extends android.app.Application {

    private RequestQueue requestQueue;
    public static App instance;
    public static FusedLocationProviderClient fusedLocationClient;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        requestQueue = Volley.newRequestQueue(this);
        Mapbox.getInstance(this, getResources().getString(R.string.mapbox_access_token));
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        AlarmHandler alarmHandler = new AlarmHandler(this);
        alarmHandler.cancelAlarmManager();
        alarmHandler.setAlarmManager();
    }

    public static RequestQueue getRequestQueue() {
        return instance.requestQueue;
    }

    public static SharedPreferences getPrefs() {
        SharedPreferences prefs = instance.getSharedPreferences("com.cs528.covidtracker.prefs", Context.MODE_PRIVATE);
        return prefs;
    }

}
