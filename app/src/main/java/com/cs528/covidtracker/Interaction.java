package com.cs528.covidtracker;

import com.google.gson.JsonObject;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class Interaction {
    public Date date;
    public double lat, lng;
    public String BT_ID;
    public double BT_strength;

    public Interaction(JsonObject obj) {
        date = new Date(obj.get("time").getAsLong());
        lat = obj.get("lat").getAsDouble();
        lng = obj.get("lng").getAsDouble();
        BT_ID = obj.get("bt_id").getAsString();
        BT_strength = obj.get("bt_strength").getAsDouble();
    }

    public String dayStr() {
        return new SimpleDateFormat("MM/dd/yyyy").format(date);
    }

    public String getFeatureString() {
        return "{ \"type\": \"Feature\", \"properties\": { \"mag\":" + BT_strength + "}, \"geometry\": { \"type\": \"Point\", \"coordinates\": [ " + lng + ", "  + lat + ", 0.0 ] } }";
    }

    public double getScore(ArrayList<CountyData> countyData) {
        int sum = 0;
        double totalDistance = 0;

        for (CountyData cd : countyData) {
            double distance = new LatLng(lat, lng).distanceTo(new LatLng(cd.lat, cd.lng));
            sum += distance * cd.cases;
            totalDistance += distance;
        }

        return BT_strength * (sum / totalDistance) * 100;
    }
}
