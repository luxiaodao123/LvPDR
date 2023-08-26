package com.example.lvpdr.ui.map;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.annotation.DrawableRes;

import com.example.lvpdr.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
import com.mapbox.mapboxsdk.storage.Resource;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor;

import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.Source;
import com.mapbox.mapboxsdk.style.sources.VectorSource;

import java.util.ArrayList;
import java.util.List;

import static com.mapbox.mapboxsdk.style.layers.Property.NONE;
import static com.mapbox.mapboxsdk.style.layers.Property.VISIBLE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;

public class MapViewModel extends ViewModel {

    private static final String DROPPED_MARKER_LAYER_ID = "DROPPED_MARKER_LAYER_ID";

    private MapView mapView = null;
    private MapboxMap mapboxMap = null;
    private static MapViewModel singleton = null;
    private Layer droppedMarkerLayer;
    private ImageView hoveringMarker;
    private boolean isAdding = false;

    public MapViewModel() {
        if (singleton == null)
            singleton = this;
    }

    public static MapViewModel getInstance() {
        if (singleton == null)
            singleton = new MapViewModel();
        return singleton;
    }

    public void setMapView(FragmentActivity activity, Context context) {
        if (singleton == null) return;
        singleton.mapView = activity.findViewById(R.id.mapView);
        singleton.mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                singleton.mapboxMap = mapboxMap;
                mapboxMap.setStyle("https://www.spidersens.cn/mapstyle/basic.json", new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        style.addSource(
                                new VectorSource("museums_source", "mapbox://mapbox.2opop9hr")
                        );
                        _initMap();
                        initDroppedMarker(style, activity);

                        hoveringMarker = new ImageView(activity);
                        hoveringMarker.setImageResource(R.drawable.ic_location_red);
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
                        hoveringMarker.setLayoutParams(params);
                        mapView.addView(hoveringMarker);

                        activity.findViewById(R.id.addPoint).setOnClickListener(new View.OnClickListener(){
                            @Override
                            public void onClick(View view){
                                FloatingActionButton button = activity.findViewById(R.id.addPoint);
                                if(isAdding){
                                    button.setImageResource(R.drawable.ic_button_new);
                                    hoveringMarker.setVisibility(View.INVISIBLE);
                                    final LatLng mapTargetLatLng = mapboxMap.getCameraPosition().target;
                                    if (style.getLayer(DROPPED_MARKER_LAYER_ID) != null) {
                                        GeoJsonSource source = style.getSourceAs("dropped-marker-source-id");
                                        if (source != null) {
                                            source.setGeoJson(Point.fromLngLat(mapTargetLatLng.getLongitude(), mapTargetLatLng.getLatitude()));
                                        }
                                        droppedMarkerLayer = style.getLayer(DROPPED_MARKER_LAYER_ID);
                                        if (droppedMarkerLayer != null) {
                                            droppedMarkerLayer.setProperties(visibility(VISIBLE));
                                        }
                                    }

                                    double longitude = mapTargetLatLng.getLongitude();
                                    double latitude = mapTargetLatLng.getLatitude();

                                    String sLon = String.format("%.6f", longitude);
                                    TextView text = (TextView) activity.findViewById(R.id.gpsx);
                                    text.setText(sLon);


                                    String sLat = String.format("%.6f", latitude);
                                    text = (TextView) activity.findViewById(R.id.gpsy);
                                    text.setText(sLat);

                                    isAdding = false;
                                }else{
                                    button.setImageResource(R.drawable.ic_button_check);
                                    hoveringMarker.setVisibility(View.VISIBLE);
                                    droppedMarkerLayer = style.getLayer(DROPPED_MARKER_LAYER_ID);
                                    if (droppedMarkerLayer != null) {
                                        droppedMarkerLayer.setProperties(visibility(NONE));
                                    }
                                    isAdding = true;
                                }

                            }
                        });
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
                .target(new LatLng(31.84944, 119.9701341))
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
//        GeoJsonSource source  = singleton.mapboxMap.getStyle().getSourceAs("original-location");
//        List routeCoordinates = new ArrayList<Point>();
//        routeCoordinates.add(Point.fromLngLat(119.9701341, 31.84944));
//        routeCoordinates.add(Point.fromLngLat(119.9701341, 31.84944));
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


    private void initDroppedMarker(@NonNull Style loadedMapStyle, FragmentActivity activity) {

        Resources resource = activity.getResources();

        loadedMapStyle.addImage("dropped-icon-image", BitmapFactory.decodeResource(
                resource, R.drawable.ic_location_blue));
        loadedMapStyle.addSource(new GeoJsonSource("dropped-marker-source-id"));
        loadedMapStyle.addLayer(new SymbolLayer(DROPPED_MARKER_LAYER_ID,
                "dropped-marker-source-id").withProperties(
                iconImage("dropped-icon-image"),
                visibility(NONE),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
        ));
    }

}

