package com.example.lvpdr.ui.map;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.lvpdr.R;
import com.example.lvpdr.databinding.FragmentMapBinding;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;


public class MapFragment extends Fragment {
    private static FragmentActivity mActivity;
    MapViewModel mapViewModel;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        Mapbox.getInstance(getContext(), getString(R.string.mapbox_access_token));
        mapViewModel = new ViewModelProvider(this).get(MapViewModel.class);
        View view = inflater.inflate(R.layout.fragment_map, container, false);
        MapView mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mapViewModel.getMapView().onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        mapViewModel.getMapView().onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapViewModel.getMapView().onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapViewModel.getMapView().onLowMemory();
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View view = getView();
        if (view != null) {
            mActivity = getActivity();
            mapViewModel.getInstance().setMapView(mActivity.findViewById(R.id.mapView), getContext());
        }
    }

}