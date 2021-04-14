package com.cs528.covidtracker;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;

public class CovidUtils {

    private static final String jhuCountyURL = "https://disease.sh/v3/covid-19/jhucsse/counties";

    public static void getCovidData(Runnable completed) {
        RequestQueue queue = App.getRequestQueue();

        StringRequest stringRequest = new StringRequest(Request.Method.GET, jhuCountyURL, response -> {
            // Save json string to shared preferences
            SharedPreferences prefs = App.getPrefs();
            prefs.edit().putString(Params.CovidDataKey, response).commit();

            completed.run();
        }, error -> System.out.println("Covid data request failed"));

        queue.add(stringRequest);
    }

    public static ArrayList<CountyData> parseCovidJson(String str) {
        JsonArray respArr = JsonParser.parseString(str).getAsJsonArray();

        ArrayList<CountyData> data = new ArrayList<>();

        for (JsonElement elem : respArr) {
            try {
                data.add(new CountyData(elem.getAsJsonObject()));
            } catch (NumberFormatException e) { }
        }

        return data;
    }

}
