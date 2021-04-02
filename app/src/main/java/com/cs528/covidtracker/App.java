package com.cs528.covidtracker;

import com.mapbox.mapboxsdk.Mapbox;

public class App extends android.app.Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Mapbox.getInstance(this, getResources().getString(R.string.mapbox_access_token));
    }
}
