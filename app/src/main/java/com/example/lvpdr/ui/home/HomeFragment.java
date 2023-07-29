package com.example.lvpdr.ui.home;


import static android.hardware.Sensor.TYPE_ROTATION_VECTOR;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.lvpdr.CoreAlgorithm;
import com.example.lvpdr.R;
import com.example.lvpdr.core.LocationTrack;
import com.example.lvpdr.databinding.FragmentHomeBinding;
import com.example.lvpdr.databinding.FragmentMapBinding;
import com.example.lvpdr.ui.chart.ChartFragment;
import com.example.lvpdr.ui.map.MapViewModel;
import com.mapbox.geojson.Point;

import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class HomeFragment extends Fragment implements SensorEventListener {
    private static FragmentActivity mActivity;

    private SensorManager mSensorManager;
    Sensor mAccelerometer;
    Sensor mMagneticField;
    Sensor mRotationVector;

    private long lastUpdate = 0;
    private long lastUpdate_gyro = 0;
    private long lastUpdate_maget = 0;
    private int stepCount = 0;
    private double stepLength = 0;

    private static final long GNSS_UPDATE_PERIOD = 1000 * 1;
    private static final long GNSS_UPDATE_DELAY = 0;

    HomeViewModel homeViewModel;
    LocationTrack locationTrack;
    ChartFragment chartFragment;
    MapViewModel mapViewModel;
    CoreAlgorithm coreAlgorithm;
    Timer timer = null;

    Boolean lastMagnetometerSet = false;
    Boolean lastAccelerometerSet = false;

    float [] lastAccelerometer = new float[3];
    float [] lastMagnetometer = new float[3];
    float [] mRotationMatrix = new float[9];
    float [] mOrientationAngles = new float[3];
    float [] mRotationMatrixFromVector = new float[16];
    ArrayList<float[]> accelerationData = new ArrayList<float[]>();
    private  ArrayList<Point> coords = new ArrayList<Point>();


    static final float ALPHA = 0.25f;

    public int i = 0;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        return inflater.inflate(R.layout.fragment_home, container, false);

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        coreAlgorithm = new CoreAlgorithm(20, 1.7, 0.4f);
        View view = getView();
        if (view != null) {
            mActivity = getActivity();
            mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
            assert mSensorManager != null;
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            //senGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            mRotationVector = mSensorManager.getDefaultSensor(TYPE_ROTATION_VECTOR);
            Switch toggle = (Switch) view.findViewById(R.id.sensorSw);
            toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    i = 1;
                    startLocation();
                } else {
                    i = 0;
                    stopLocation();
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mSensorManager.unregisterListener(this);
    }

    public void stopLocation() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        if(timer != null)
            timer.cancel ();

    }

    public void startLocation() {
        if(mapViewModel== null) mapViewModel = MapViewModel.getInstance();
        super.onResume();
        if (i == 1) {
            timer = new Timer();
            timer.schedule (new TimerTask() {
                @Override
                public void run () {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // This code will always run on the UI thread, therefore is safe to modify UI elements.
                            locationTrack = LocationTrack.getInstance();
                            if (locationTrack.canGetLocation()) {
                                double longitude = locationTrack.getLongitude();
                                double latitude = locationTrack.getLatitude();

                                String sLon = String.format("%.2f", longitude);
                                TextView text = (TextView) getView().findViewById(R.id.gpsx);
                                text.setText(sLon);


                                String sLat = String.format("%.2f", latitude);
                                text = (TextView) getView().findViewById(R.id.gpsy);
                                text.setText(sLat);

                                coords.add(Point.fromLngLat(latitude, longitude));
                                mapViewModel.addPointInMapSource("original-location", coords);
                            } else {
                                locationTrack.showSettingsAlert();
                            }
                        }
                    });


                }
            }, GNSS_UPDATE_DELAY, GNSS_UPDATE_PERIOD);
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
//            mSensorManager.registerListener(this, senGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, mRotationVector, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor sensor = sensorEvent.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            lastAccelerometerSet = true;

            float x = sensorEvent.values[0];
            float y = sensorEvent.values[1];
            float z = sensorEvent.values[2];

            long currentTime = System.currentTimeMillis();

            if ((currentTime - lastUpdate) > 0) {
                long timeDiff = currentTime - lastUpdate;
                lastUpdate = currentTime;
                {
                    String sX = Float.toString(x);
                    TextView text = (TextView) getView().findViewById(R.id.ax);
                    text.setText(sX);

                    String sY = Float.toString(y);
                    text = (TextView) getView().findViewById(R.id.ay);
                    text.setText(sY);

                    String sZ = Float.toString(z);
                    text = (TextView) getView().findViewById(R.id.az);
                    text.setText(sZ);

                }
            }

            System.arraycopy(sensorEvent.values, 0, lastAccelerometer, 0, sensorEvent.values.length);
            if(chartFragment == null) chartFragment = ChartFragment.getInstance();
            if(chartFragment == null) return;
            double accData = Math.sqrt(Math.pow(lastAccelerometer[0], 2) +
                    Math.pow(lastAccelerometer[1], 2) +
                    Math.pow(lastAccelerometer[2], 2)
            );
            chartFragment.onUdata((float) accData);
            if((accData < 11 && accData > 9.1) || accData > 13) return;
            double newStepLength = coreAlgorithm.calculateStepLength(lastAccelerometer);
            stepLength += newStepLength;
            if(newStepLength > 0.1)
                stepCount += 1;
            accelerationData.clear();
            TextView text = (TextView) getView().findViewById(R.id.stepLength);
            text.setText(String.format("%.2f", stepLength));

            text = (TextView) getView().findViewById(R.id.stepNum);
            text.setText(Integer.toString(stepCount));

        }
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            float mx = sensorEvent.values[0];
            float my = sensorEvent.values[1];
            float mz = sensorEvent.values[2];

            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastUpdate_maget > 300)) {

                lastUpdate_maget = currentTime;

                {

                    String sX = Float.toString(mx);
                    TextView text = (TextView) getView().findViewById(R.id.mx);
                    text.setText(sX);

                    String sY = Float.toString(my);
                    text = (TextView) getView().findViewById(R.id.my);
                    text.setText(sY);

                    String sZ = Float.toString(mz);
                    text = (TextView) getView().findViewById(R.id.mz);
                    text.setText(sZ);

                }
            }
            //calculate
            lastMagnetometerSet = true;

            System.arraycopy(sensorEvent.values, 0, lastMagnetometer, 0, sensorEvent.values.length);

            lastMagnetometer = lowPass(sensorEvent.values.clone(), lastMagnetometer);

            if (lastAccelerometerSet && lastMagnetometerSet)
            {
                mSensorManager.getRotationMatrix(mRotationMatrix, null, lastAccelerometer, lastMagnetometer);
                mSensorManager.getOrientation(mRotationMatrix, mOrientationAngles);
                float azimuthInRadians = mOrientationAngles[0];

                int azimuthInDegress = ((int)(azimuthInRadians * 180/(float) Math.PI) + 360) % 360;
//                System.out.println(azimuthInDegress);
            }

        }
        if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR){
            SensorManager.getRotationMatrixFromVector(mRotationMatrixFromVector, sensorEvent.values);
            SensorManager.getOrientation(mRotationMatrixFromVector, mOrientationAngles);
        }
        if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float gx = sensorEvent.values[0];
            float gy = sensorEvent.values[1];
            float gz = sensorEvent.values[2];

            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastUpdate_gyro > 300)) {

                lastUpdate_gyro = currentTime;

                {

                    String sX = Float.toString(gx);
                    TextView text = (TextView) getView().findViewById(R.id.gx);
                    text.setText(sX);

                    String sY = Float.toString(gy);
                    text = (TextView) getView().findViewById(R.id.gy);
                    text.setText(sY);

                    String sZ = Float.toString(gz);
                    text = (TextView) getView().findViewById(R.id.gz);
                    text.setText(sZ);

                }
            }
        }

    }

    protected float[] lowPass( float[] input, float[] output ) {
        if ( output == null ) return input;

        for ( int i=0; i<input.length; i++ ) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

}