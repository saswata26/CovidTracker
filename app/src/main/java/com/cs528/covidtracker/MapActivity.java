package com.cs528.covidtracker;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.android.gestures.MoveGestureDetector;
import com.mapbox.android.gestures.StandardScaleGestureDetector;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import com.mapbox.mapboxsdk.style.layers.HeatmapLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.Source;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.heatmapDensity;
import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.linear;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.expressions.Expression.rgba;
import static com.mapbox.mapboxsdk.style.expressions.Expression.rgb;
import static com.mapbox.mapboxsdk.style.expressions.Expression.stop;
import static com.mapbox.mapboxsdk.style.expressions.Expression.zoom;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeWidth;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapIntensity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapRadius;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapWeight;

public class MapActivity extends AppCompatActivity implements PermissionsListener, LocationListener, MapboxMap.OnMoveListener, MapboxMap.OnScaleListener {

    private TextView dateText, scoreText, interactionsText;
    private HashMap<String, ArrayList<Interaction>> interactionsByDay;
    private String currDateStr;
    private View currLoc;
    private PermissionsManager permissionsManager;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private Style loadedStyle;
    private LocationManager locationManager;
    private ArrayList<CountyData> countyData;

    private boolean trackingCurrPos = true;
    private Location lastLoc;

    private Source covidSource, interactionsSource;
    private static final String EARTHQUAKE_SOURCE_ID = "earthquakes";
    private static final String HEATMAP_LAYER_ID = "earthquakes-heat";
    private static final String HEATMAP_LAYER_SOURCE = "earthquakes";
    private static final String INTERACTION_LAYER_SOURCE = "interactions";
    private static final String INTERACTION_LAYER_ID = "interactions-layer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        String covidData = App.getPrefs().getString(Params.CovidDataKey, "[]");
        countyData = CovidUtils.parseCovidJson(covidData);

        // Get updated data from api
        CovidUtils.getCovidData(() -> {
            String updated = App.getPrefs().getString(Params.CovidDataKey, "[]");
            countyData = CovidUtils.parseCovidJson(updated);
            setupScoreCard();

            if (loadedStyle != null)
                addCovidSource(loadedStyle);
        });

        dateText = findViewById(R.id.dateText);
        scoreText = findViewById(R.id.scoreText);
        interactionsText = findViewById(R.id.interactionsText);

        // Initially set to today's date
        currDateStr = new SimpleDateFormat("MM/dd/yyyy").format(new Date());
        loadInteractions();
        setupScoreCard();

        currLoc = findViewById(R.id.currLocation);
        currLoc.setOnClickListener(view -> {
            if (mapboxMap == null || lastLoc == null)
                return;

            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(lastLoc.getLatitude(), lastLoc.getLongitude()))
                    .zoom(9)
                    .build();

            mapboxMap.setCameraPosition(position);
            currLoc.setVisibility(View.GONE);
        });

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(mapboxMap -> mapboxMap.setStyle(Style.DARK, style -> {
            MapActivity.this.mapboxMap = mapboxMap;
            MapActivity.this.loadedStyle = style;

            mapboxMap.getUiSettings().setAttributionEnabled(false);
            mapboxMap.getUiSettings().setLogoEnabled(false);

            mapboxMap.addOnMoveListener(MapActivity.this);
            mapboxMap.addOnScaleListener(MapActivity.this);

            addCovidSource(style);
            addHeatmapLayer(style);

            addInteractionSource(style);
            addInteractionsLayer(style);

            enableLocationComponent(style);
        }));
    }

    private void setupScoreCard() {
        Date currDate;

        try {
            currDate = new SimpleDateFormat("MM/dd/yyyy").parse(currDateStr);
        } catch (Exception e) {
            currDate = new Date();
        }

        String dayBefore = getDayBeforeString(currDate);
        int score = 0, interactions = 0;
        String scoreChange = "", interactionsChange = "";

        if (interactionsByDay.containsKey(currDateStr)) {
            score = (int) calculateScore(currDateStr);
            interactions = interactionsByDay.get(currDateStr).size();
        }

        if (interactionsByDay.containsKey(dayBefore)) {
            int lastScore = (int) calculateScore(dayBefore);
            int lastInteractions = interactionsByDay.get(dayBefore).size();

            if (lastScore < score)
                scoreChange = "⇡";
            else if (lastScore > score)
                scoreChange = "⇣";

            if (lastInteractions < interactions)
                interactionsChange = "⇡";
            else if (lastInteractions > interactions)
                interactionsChange = "⇣";
        }

        dateText.setText(new SimpleDateFormat("EE, MMMM d").format(currDate));

        scoreText.setText(score + scoreChange);
        interactionsText.setText(interactions + interactionsChange);

    }

    private String getDayBeforeString(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, -1);
        Date yesterday = calendar.getTime();
        return new SimpleDateFormat("MM/dd/yyyy").format(yesterday);
    }

    private double calculateScore(String dateString) {
        double score = 0;

        for (Interaction interaction : interactionsByDay.get(dateString)) {
            score += interaction.getScore(countyData);
        }

        return score;
    }

    private void loadInteractions() {
        String testInteractions = "[{ \"time\": 1619715597000, \"lat\": 42.326300, \"lng\": -71.370660, \"bt_id\": \"bt_id\", \"bt_strength\": 0.5}," +
                "{ \"time\": 1619715598000, \"lat\": 42.326301, \"lng\": -71.371667, \"bt_id\": \"bt_id\", \"bt_strength\": 0.3}," +
                "{ \"time\": 1619715599000, \"lat\": 42.326107, \"lng\": -71.370662, \"bt_id\": \"bt_id\", \"bt_strength\": 0.5}," +
                "{ \"time\": 1619715597000, \"lat\": 42.326505, \"lng\": -71.373669, \"bt_id\": \"bt_id\", \"bt_strength\": 0.7}," +
                "{ \"time\": 1619715597000, \"lat\": 42.336302, \"lng\": -71.370663, \"bt_id\": \"bt_id\", \"bt_strength\": 1}," +
                "{ \"time\": 1619649912000, \"lat\": 42.336302, \"lng\": -71.370663, \"bt_id\": \"bt_id\", \"bt_strength\": 100000}]";
        String interStr = App.getPrefs().getString(Params.BluetoothDataKey, testInteractions);

        JsonArray interactionsArray = JsonParser.parseString(interStr).getAsJsonArray();
        ArrayList<Interaction> interactions = new ArrayList<>();

        for (JsonElement elem : interactionsArray) {
            interactions.add(new Interaction(elem.getAsJsonObject()));
        }

        interactionsByDay = new HashMap<>();
        for (Interaction interaction : interactions) {
            String dayStr = interaction.dayStr();

            if (!interactionsByDay.containsKey(dayStr))
                interactionsByDay.put(dayStr, new ArrayList<>());
            interactionsByDay.get(dayStr).add(interaction);
        }
    }

    private void addInteractionSource(@NonNull Style loadedMapStyle) {
        ArrayList<Interaction> interactions = interactionsByDay.get(currDateStr);

        ArrayList<Feature> features = new ArrayList<>();
        for (Interaction i : interactions) {
            features.add(Feature.fromJson(i.getFeatureString()));
        }

        FeatureCollection featureCollection = FeatureCollection.fromFeatures(features);

        try {
            interactionsSource = new GeoJsonSource(INTERACTION_LAYER_SOURCE, featureCollection);
            loadedMapStyle.addSource(interactionsSource);
        } catch (Exception e) { }
    }

    private void addInteractionsLayer(@NonNull Style loadedMapStyle) {
        CircleLayer circleLayer = new CircleLayer(INTERACTION_LAYER_ID, INTERACTION_LAYER_SOURCE);
        circleLayer.setProperties(

            // Size circle radius by earthquake magnitude and zoom level
                circleRadius(
                        interpolate(
                                linear(), zoom(),
                                literal(7), interpolate(
                                        linear(), get("mag"),
                                        stop(1, 1),
                                        stop(6, 4)
                                ),
                                literal(16), interpolate(
                                        linear(), get("mag"),
                                        stop(1, 5),
                                        stop(6, 50)
                                )
                        )
                ),

                // Color circle by earthquake magnitude
                circleColor(
                        interpolate(
                                linear(), get("mag"),
                                literal(1), rgba(33, 102, 172, 0),
                                literal(2), rgb(103, 169, 207),
                                literal(3), rgb(209, 229, 240),
                                literal(4), rgb(253, 219, 199),
                                literal(5), rgb(239, 138, 98),
                                literal(6), rgb(178, 24, 43)
                        )
                ),

            // Transition from heatmap to circle layer by zoom level
                circleOpacity(
                        interpolate(
                                linear(), zoom(),
                                stop(7, 0),
                                stop(8, 1)
                        )
                ),
                circleStrokeColor("white"),
                circleStrokeWidth(1.0f)
        );

        loadedMapStyle.addLayerAbove(circleLayer, HEATMAP_LAYER_ID);
    }

    private void addCovidSource(@NonNull Style loadedMapStyle) {
        ArrayList<Feature> features = new ArrayList<>();
        for (CountyData cd : countyData) {
            features.add(Feature.fromJson(cd.getFeatureString()));
        }

        FeatureCollection featureCollection = FeatureCollection.fromFeatures(features);

        try {
            covidSource = new GeoJsonSource(HEATMAP_LAYER_SOURCE, featureCollection);
            loadedMapStyle.addSource(covidSource);
        } catch (Exception e) { }
    }

    private void addHeatmapLayer(@NonNull Style loadedMapStyle) {
        HeatmapLayer layer = new HeatmapLayer(HEATMAP_LAYER_ID, EARTHQUAKE_SOURCE_ID);
        layer.setMaxZoom(15);
        layer.setSourceLayer(HEATMAP_LAYER_SOURCE);
        layer.setProperties(
            // Color ramp for heatmap.  Domain is 0 (low) to 1 (high).
            // Begin color ramp at 0-stop with a 0-transparency color
            // to create a blur-like effect.
                heatmapColor(
                        interpolate(
                                linear(), heatmapDensity(),
                                literal(0), rgba(255, 255, 178, 0.2),
                                literal(0.1), rgb(255, 255, 178),
                                literal(0.3), rgb(254, 178, 76),
                                literal(0.5), rgb(253, 141, 60),
                                literal(0.7), rgb(252, 78, 42),
                                literal(1), rgb(227,26,28)
                        )
                ),
                // Increase the heatmap weight based on frequency and property magnitude
                heatmapWeight(
                        interpolate(
                                linear(), get("mag"),
                                stop(0, 0),
                                stop(10, 0.1),
                                stop(getHighestCases(), 1)
                        )
                ),

                // Increase the heatmap color weight weight by zoom level
                // heatmap-intensity is a multiplier on top of heatmap-weight
//                heatmapIntensity(
//                        interpolate(
//                                linear(), zoom(),
//                                stop(0, 1),
//                                stop(15, 2)
//                        )
//                ),

                // Adjust the heatmap radius by zoom level
                heatmapRadius(
                        interpolate(
                                linear(), zoom(),
                                stop(0, 1),
                                stop(3, 10),
                                stop(15, 250)
                        )
                )
        );

        loadedMapStyle.addLayerAbove(layer, "waterway-label");
    }

    @SuppressWarnings( {"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

            LocationComponent locationComponent = mapboxMap.getLocationComponent();
            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, loadedMapStyle).build());

            try {
                locationComponent.setLocationComponentEnabled(true);
            } catch (Exception e) {
                System.out.println("Current location unavailable");
            }

            locationComponent.setRenderMode(RenderMode.COMPASS);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, "We need your location to show where you are on the map", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(style -> enableLocationComponent(style));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLoc = location;

        if (trackingCurrPos) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                    .zoom(9)
                    .build();

            mapboxMap.setCameraPosition(position);
        }
    }

    private int getHighestCases() {
        int highest = 0;

        for (CountyData cd : countyData) {
            if (cd.cases > highest)
                highest = cd.cases;
        }

        return highest;
    }

    private String getMagnitudes() {
        JsonArray arr = new JsonArray();

        for (CountyData cd : countyData) {
            arr.add(cd.cases);
//            arr.add(10000);
//            break;
        }

        return "{\"type\": \"Feature\", \"properties\": { \"mag\": " + arr.toString() + "}}";
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) { }

    @Override
    public void onProviderEnabled(String s) { }

    @Override
    public void onProviderDisabled(String s) { }

    @Override
    public void onMoveBegin(@NonNull MoveGestureDetector detector) {
        trackingCurrPos = false;
        currLoc.setVisibility(View.VISIBLE);
    }

    @Override
    public void onMove(@NonNull MoveGestureDetector detector) { }

    @Override
    public void onMoveEnd(@NonNull MoveGestureDetector detector) { }

    @Override
    public void onScaleBegin(@NonNull StandardScaleGestureDetector detector) {
        trackingCurrPos = false;
        currLoc.setVisibility(View.VISIBLE);
    }

    @Override
    public void onScale(@NonNull StandardScaleGestureDetector detector) { }

    @Override
    public void onScaleEnd(@NonNull StandardScaleGestureDetector detector) { }
}