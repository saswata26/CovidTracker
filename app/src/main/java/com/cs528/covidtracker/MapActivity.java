package com.cs528.covidtracker;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.DatePicker;
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

import java.text.DecimalFormat;
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

public class MapActivity extends AppCompatActivity implements PermissionsListener,
        LocationListener, MapboxMap.OnMoveListener, MapboxMap.OnScaleListener, MapboxMap.OnMapClickListener {

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
    private int interactionChange = 0;

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

        dateText.setOnClickListener(view -> {
            Calendar cal = Calendar.getInstance();
            cal.setTime(getDate(currDateStr));
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePicker = new DatePickerDialog(MapActivity.this, (datePicker1, year1, month1, day1) -> {
                Calendar cal1 = Calendar.getInstance();
                cal1.set(Calendar.YEAR, year1);
                cal1.set(Calendar.MONTH, month1);
                cal1.set(Calendar.DAY_OF_MONTH, day1);

                currDateStr = new SimpleDateFormat("MM/dd/yyyy").format(cal1.getTime());
                setupScoreCard();

                if (loadedStyle != null) {
                    loadedStyle.removeLayer(INTERACTION_LAYER_ID + (interactionChange - 1));
                    loadedStyle.removeLayer(INTERACTION_LAYER_SOURCE + (interactionChange - 1));

                    addInteractionSource(loadedStyle, INTERACTION_LAYER_SOURCE + interactionChange);
                    addInteractionsLayer(loadedStyle, INTERACTION_LAYER_SOURCE + interactionChange, INTERACTION_LAYER_ID + interactionChange);
                    interactionChange += 1;
                }
            }, year, month, day);

            datePicker.show();
        });

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
                    .zoom(14)
                    .build();

            mapboxMap.setCameraPosition(position);
            currLoc.setVisibility(View.GONE);
        });

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(mapboxMap -> mapboxMap.setStyle(Style.DARK, style -> {
            MapActivity.this.mapboxMap = mapboxMap;
            MapActivity.this.loadedStyle = style;

            float lastKnownLat = App.getPrefs().getFloat(Params.LastKnownLatKey, -1);
            float lastKnownLng = App.getPrefs().getFloat(Params.LastKnownLngKey, -1);

            if (lastKnownLat > 0) {
                CameraPosition position = new CameraPosition.Builder()
                        .target(new LatLng(lastKnownLat, lastKnownLng))
                        .zoom(14)
                        .build();

                mapboxMap.setCameraPosition(position);
            }

            mapboxMap.getUiSettings().setAttributionEnabled(false);
            mapboxMap.getUiSettings().setLogoEnabled(false);

            mapboxMap.addOnMoveListener(MapActivity.this);
            mapboxMap.addOnScaleListener(MapActivity.this);
            mapboxMap.addOnMapClickListener(MapActivity.this);

            addCovidSource(style);
            addHeatmapLayer(style);

            addInteractionSource(style, INTERACTION_LAYER_SOURCE + interactionChange);
            addInteractionsLayer(style, INTERACTION_LAYER_SOURCE + interactionChange, INTERACTION_LAYER_ID + interactionChange);
            interactionChange += 1;

            enableLocationComponent(style);
        }));
    }

    private void setupScoreCard() {
        Date currDate = getDate(currDateStr);

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

    private Date getDate(String str) {
        try {
            return new SimpleDateFormat("MM/dd/yyyy").parse(currDateStr);
        } catch (Exception e) {
            return new Date();
        }
    }

    private double calculateScore(String dateString) {
        double score = 0;

        for (Interaction interaction : interactionsByDay.get(dateString)) {
            score += interaction.getScore(countyData);
        }

        return score;
    }

    private void loadInteractions() {
        String testInteractions = "[{ \"time\": 1619715597000, \"lat\": 42.323300, \"lng\": -71.371660, \"bt_id\": \"bt_id\", \"bt_strength\": 0.0}," +
                "{ \"time\": 1619715598000, \"lat\": 42.326301, \"lng\": -71.372667, \"bt_id\": \"bt_id\", \"bt_strength\": 0.1}," +
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

    private void addInteractionSource(@NonNull Style loadedMapStyle, String SOURCE_ID) {
        ArrayList<Interaction> interactions = interactionsByDay.containsKey(currDateStr) ? interactionsByDay.get(currDateStr) : new ArrayList<>();

        ArrayList<Feature> features = new ArrayList<>();
        for (Interaction i : interactions) {
            features.add(Feature.fromJson(i.getFeatureString()));
        }

        FeatureCollection featureCollection = FeatureCollection.fromFeatures(features);

        try {
            interactionsSource = new GeoJsonSource(SOURCE_ID, featureCollection);
            loadedMapStyle.addSource(interactionsSource);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void addInteractionsLayer(@NonNull Style loadedMapStyle, String SOURCE_ID, String LAYER_ID) {
        CircleLayer circleLayer = new CircleLayer(LAYER_ID, SOURCE_ID);
        circleLayer.setProperties(

            // Size circle radius by earthquake magnitude and zoom level
                circleRadius(
                        interpolate(
                                linear(), get("mag"),
                                stop(0, 8),
                                stop(1, 8)
                        )
                ),

                // Color circle by earthquake magnitude
                circleColor(
                        interpolate(
                                linear(), get("mag"),
                                literal(0), rgba(173,255,47, 0),
                                literal(1), rgb(178, 24, 43)
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
        layer.setMaxZoom(22);
        layer.setSourceLayer(HEATMAP_LAYER_SOURCE);
        layer.setProperties(
            // Color ramp for heatmap.  Domain is 0 (low) to 1 (high).
            // Begin color ramp at 0-stop with a 0-transparency color
            // to create a blur-like effect.
                heatmapColor(
                        interpolate(
                                linear(), heatmapDensity(),
                                literal(0), rgba(255, 255, 178, 0.6),
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
                                stop(3, 25),
                                stop(22, 300)
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

            if (mapboxMap != null) {
                LocationComponent locationComponent = mapboxMap.getLocationComponent();
                locationComponent.activateLocationComponent(
                        LocationComponentActivationOptions.builder(this, loadedMapStyle).build());

                try {
                    locationComponent.setLocationComponentEnabled(true);
                } catch (Exception e) {
                    System.out.println("Current location unavailable");
                }

                locationComponent.setRenderMode(RenderMode.COMPASS);
            }
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
            App.getPrefs().edit().putFloat(Params.LastKnownLatKey, (float)location.getLatitude())
                    .putFloat(Params.LastKnownLngKey, (float)location.getLongitude()).apply();

            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                    .zoom(14)
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

    @Override
    public boolean onMapClick(@NonNull LatLng point) {
        for (Interaction interaction : interactionsByDay.get(currDateStr)) {
            if (new LatLng(interaction.lat, interaction.lng).distanceTo(point) < 30) {
                String dateStr = new SimpleDateFormat("h:mm aa").format(interaction.date);
                String scoreText = new DecimalFormat("#.#").format(interaction.getScore(countyData));
                Toast.makeText(this, "Interaction at " + dateStr + " contributed " + scoreText + " to your score", Toast.LENGTH_SHORT).show();
                break;
            }
        }

        return true;
    }
}