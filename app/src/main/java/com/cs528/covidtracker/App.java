package com.cs528.covidtracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.mapbox.mapboxsdk.Mapbox;

public class App extends android.app.Application {

    private RequestQueue requestQueue;
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        requestQueue = Volley.newRequestQueue(this);
        Mapbox.getInstance(this, getResources().getString(R.string.mapbox_access_token));
        AlarmHandler alarmHandler= new AlarmHandler(this);
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
