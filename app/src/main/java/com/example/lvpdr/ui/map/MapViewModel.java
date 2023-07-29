package com.example.lvpdr.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.annotation.DrawableRes;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.MultiPoint;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.MapboxMapOptions;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.Source;
import com.mapbox.mapboxsdk.style.sources.VectorSource;

import java.util.ArrayList;
import java.util.List;


public class MapViewModel extends ViewModel {

    private MapView mapView = null;
    private MapboxMap mapboxMap = null;
    private static MapViewModel singleton = null;

    public MapViewModel() {
        if (singleton == null)
            singleton = this;
    }

    public static MapViewModel getInstance() {
        if (singleton == null)
            singleton = new MapViewModel();
        return singleton;
    }

    public void setMapView(MapView mapView, Context context) {
        if (singleton == null) return;
        singleton.mapView = mapView;
        singleton.mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                singleton.mapboxMap = mapboxMap;
                mapboxMap.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        style.addSource(
                                new VectorSource("museums_source", "mapbox://mapbox.2opop9hr")
                        );
                        _initMap();
                    }
                });

            }
        });
    }

    public MapView getMapView() {
        return singleton.mapView;
    }

    private void _initMap() {

        singleton.mapboxMap.setCameraPosition(new CameraPosition.Builder()
                .target(new LatLng(31, 119))
                .zoom(8.0)
                .build());
        singleton.mapboxMap.getUiSettings().setZoomGesturesEnabled(true);

        GeoJsonSource fusionSource = new GeoJsonSource("fusion-location",
                "{\n" +
                        "\"type\": \"FeatureCollection\",\n" +
                        "\"crs\": { \"type\": \"name\", \"properties\": { \"name\": \"urn:ogc:def:crs:OGC:1.3:CRS84\" } },\n" +
                        "\"features\": [\n" +
                        "]\n" +
                        "}");
        singleton.mapboxMap.getStyle().addSource(fusionSource);

        GeoJsonSource originalSource = new GeoJsonSource("original-location",
                "{\n" +
                        "\"type\": \"FeatureCollection\",\n" +
                        "\"crs\": { \"type\": \"name\", \"properties\": { \"name\": \"urn:ogc:def:crs:OGC:1.3:CRS84\" } },\n" +
                        "\"features\": [\n" +
                        "]\n" +
                        "}");
        singleton.mapboxMap.getStyle().addSource(originalSource);

        CircleLayer fusionLayer = new CircleLayer("fusion-location", "fusion-location");
        fusionLayer.setProperties(
                circleRadius(10f),
                circleColor(Color.parseColor("#FF0000"))
        );
        CircleLayer originalLayer = new CircleLayer("original-location", "original-location");
        originalLayer.setProperties(
                circleRadius(10f),
                circleColor(Color.parseColor("#0000FF"))
        );

        singleton.mapboxMap.getStyle().addLayer(fusionLayer);
        singleton.mapboxMap.getStyle().addLayer(originalLayer);
        //        GeoJsonSource source  = singleton.mapboxMap.getStyle().getSourceAs("source-id");
//        List routeCoordinates = new ArrayList<Point>();
//        routeCoordinates.add(Point.fromLngLat(119, 33.397676));
//        routeCoordinates.add(Point.fromLngLat(119, 33.371142));
//        Feature multiPointFeature = Feature.fromGeometry(MultiPoint.fromLngLats(routeCoordinates));
//        FeatureCollection featureCollectionFromSingleFeature = FeatureCollection.fromFeature(multiPointFeature);
//        source.setGeoJson(featureCollectionFromSingleFeature);
    }

    public void addPointInMapSource(String id, ArrayList<Point> points){
        Feature multiPointFeature = Feature.fromGeometry(MultiPoint.fromLngLats(points));
        updateMapSourceById(id, FeatureCollection.fromFeature(multiPointFeature));
    }

    public void updateMapSourceById(String id, FeatureCollection featureCollection){
        GeoJsonSource source  = singleton.mapboxMap.getStyle().getSourceAs(id);
        source.setGeoJson(featureCollection);
    }




}

