package com.cs528.covidtracker.ui.map;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.cs528.covidtracker.App;
import com.cs528.covidtracker.CountyData;
import com.cs528.covidtracker.CovidUtils;
import com.cs528.covidtracker.Params;
import com.cs528.covidtracker.R;
import com.google.gson.JsonArray;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.HeatmapLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.heatmapDensity;
import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.linear;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.expressions.Expression.rgba;
import static com.mapbox.mapboxsdk.style.expressions.Expression.rgb;
import static com.mapbox.mapboxsdk.style.expressions.Expression.stop;
import static com.mapbox.mapboxsdk.style.expressions.Expression.zoom;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapIntensity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapRadius;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapWeight;

public class MapFragment extends Fragment implements PermissionsListener, LocationListener {

    private PermissionsManager permissionsManager;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private Style loadedStyle;
    private LocationManager locationManager;
    private ArrayList<CountyData> countyData;

    private boolean trackingCurrPos = false;

    private Source geoJsonSource;
    private static final String EARTHQUAKE_SOURCE_ID = "earthquakes";
    private static final String HEATMAP_LAYER_ID = "earthquakes-heat";
    private static final String HEATMAP_LAYER_SOURCE = "earthquakes";

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map, container, false);

        String covidData = App.getPrefs().getString(Params.CovidDataKey, "[]");
        countyData = CovidUtils.parseCovidJson(covidData);

        // Get updated data from api
        CovidUtils.getCovidData(() -> {
            String updated = App.getPrefs().getString(Params.CovidDataKey, "[]");
            countyData = CovidUtils.parseCovidJson(updated);

            if (loadedStyle != null)
                addCovidSource(loadedStyle);
        });

        mapView = root.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(mapboxMap -> mapboxMap.setStyle(Style.DARK, style -> {
            MapFragment.this.mapboxMap = mapboxMap;
            MapFragment.this.loadedStyle = style;

            addCovidSource(style);
            addHeatmapLayer(style);

            enableLocationComponent(style);
        }));

        return root;
    }

    private void addCovidSource(@NonNull Style loadedMapStyle) {
        ArrayList<Feature> features = new ArrayList<>();
        for (CountyData cd : countyData) {
            features.add(Feature.fromJson(cd.getFeatureString()));
        }

        FeatureCollection featureCollection = FeatureCollection.fromFeatures(features);

        try {
            geoJsonSource = new GeoJsonSource(HEATMAP_LAYER_SOURCE, featureCollection);
            loadedMapStyle.addSource(geoJsonSource);
        } catch (Exception e) { }
    }

    private void addHeatmapLayer(@NonNull Style loadedMapStyle) {
        HeatmapLayer layer = new HeatmapLayer(HEATMAP_LAYER_ID, EARTHQUAKE_SOURCE_ID);
        layer.setMaxZoom(30);
        layer.setSourceLayer(HEATMAP_LAYER_SOURCE);
        layer.setProperties(
            // Color ramp for heatmap.  Domain is 0 (low) to 1 (high).
            // Begin color ramp at 0-stop with a 0-transparency color
            // to create a blur-like effect.
                heatmapColor(
                        interpolate(
                                linear(), heatmapDensity(),
                                literal(0), rgba(33, 102, 172, 0.4),
                                literal(0.2), rgb(103, 169, 207),
                                literal(0.4), rgb(209, 229, 240),
                                literal(0.6), rgb(253, 219, 199),
                                literal(0.8), rgb(239, 138, 98),
                                literal(1), rgb(178, 24, 43)
                        )
                ),

                // Increase the heatmap weight based on frequency and property magnitude
                heatmapWeight(
                        interpolate(
                                linear(), get("mag"),
                                stop(0, 0),
                                stop(1, 0.1),
                                stop(getHighestCases(), 1)
                        )
                ),

                // Increase the heatmap color weight weight by zoom level
                // heatmap-intensity is a multiplier on top of heatmap-weight
                heatmapIntensity(
                        interpolate(
                                linear(), zoom(),
                                stop(0, 1),
                                stop(9, 3)
                        )
                ),

                // Adjust the heatmap radius by zoom level
                heatmapRadius(
                        interpolate(
                                linear(), zoom(),
                                stop(0, 2),
                                stop(9, 150)
                        )
                )
        );

        loadedMapStyle.addLayerAbove(layer, "waterway-label");
    }

    @SuppressWarnings( {"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
        if (PermissionsManager.areLocationPermissionsGranted(getActivity())) {
            locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

            LocationComponent locationComponent = mapboxMap.getLocationComponent();
            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(getActivity(), loadedMapStyle).build());
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setRenderMode(RenderMode.COMPASS);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(getActivity());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(getActivity(), "We need your location to show where you are on the map", Toast.LENGTH_LONG).show();
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
        if (trackingCurrPos) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                    .zoom(10)
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
}