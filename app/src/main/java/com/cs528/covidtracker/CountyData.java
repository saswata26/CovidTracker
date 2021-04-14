package com.cs528.covidtracker;

import com.google.gson.JsonObject;

import java.util.Date;

public class CountyData {
    private String jsonStr;
    public String countyName;
    public double lat, lng;
    public int cases, deaths;

    public CountyData(JsonObject json) {
        jsonStr = json.toString();
        countyName = json.get("county").getAsString();

        JsonObject coords = json.get("coordinates").getAsJsonObject();
        lat = coords.get("latitude").getAsDouble();
        lng = coords.get("longitude").getAsDouble();

        JsonObject stats = json.get("stats").getAsJsonObject();
        cases = stats.get("confirmed").getAsInt();
        deaths = stats.get("deaths").getAsInt();

    }
}
