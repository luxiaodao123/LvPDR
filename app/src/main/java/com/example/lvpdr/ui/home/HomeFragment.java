package com.example.lvpdr.ui.home;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.lvpdr.CoreAlgorithm;
import com.example.lvpdr.R;
import com.example.lvpdr.Sender;
import com.example.lvpdr.core.LocationTrack;
import com.example.lvpdr.core.signalstrength.SignalMethod;
import com.example.lvpdr.core.signalstrength.SignalStrengths;
import com.example.lvpdr.data.LocationData;
import com.example.lvpdr.data.cache.RedisCache;
import com.example.lvpdr.data.cache.RedisClient;
import com.example.lvpdr.ui.chart.ChartFragment;
import com.example.lvpdr.ui.map.MapViewModel;
import com.example.lvpdr.ui.setting.SettingFragment;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;

import java.net.SocketException;
import java.security.cert.PolicyNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;


public class HomeFragment extends Fragment implements SensorEventListener {
    private static final String TAG = "HomeFragment";
    private static FragmentActivity mActivity;
    private String m_Text = "";

    private SensorManager mSensorManager;
    private WindowManager mWindowManager;
    Sensor mAccelerometer;
    Sensor mMagneticField;
    Sensor mRotationVector;

    private long lastUpdate = 0;

    private long lastUpdate_coord = 0;
    private long lastUpdate_gyro = 0;
    private long lastUpdate_maget = 0;
    private long lastUpdate_rotvect = 0;
    private long lastUpdate_step = 0;
    private double lastButterWorthMag = 0;
    private double lastPostionMag = 0;
    private int stepCount = 0;
    private double stepLength = 0;

    private static final long GNSS_UPDATE_PERIOD = 1000 * 1;
    private static final long GNSS_UPDATE_DELAY = 0;

    HomeViewModel homeViewModel;
    LocationTrack locationTrack;
    ChartFragment chartFragment;
    MapViewModel mapViewModel;
    CoreAlgorithm coreAlgorithm;
    Sender mSender;
    RedisClient mRedisClient;
    Timer timer = null;

    Boolean lastMagnetometerSet = false;
    Boolean lastAccelerometerSet = false;

    float[] lastAccelerometer = new float[3];
    float[] lastMagnetometer = new float[3];
    float[] mRotationMatrix = new float[9];
    float[] mOrientationAngles = new float[3];
    float[] mRotationMatrixFromVector = new float[16];
    float I[] = new float[9];
    private ArrayList<Double> lastButterWorthMagArr = new ArrayList<>();
    private ArrayList<float[]> accelerationData = new ArrayList<float[]>();
    private ArrayList<Point> originalCoords = new ArrayList<Point>();
    private ArrayList<Point> fusionCoords = new ArrayList<Point>();
    private ArrayList<Point> indoorCoords = new ArrayList<Point>();
    double sumSinAngles = 0;
    double sumCosAngles = 0;

    double currentAngle = 0;

    Point currentCoord;

    static final float ALPHA = 0.25f;

    public int i = 0;

    private float coordCoefficient = 0.3f;

    private boolean _isIndoor = false;
    private boolean _isCollect = false;

    private ArrayList<String> magneticCollected = new ArrayList<>();

    private String mAndroidId;
    private BatteryManager mBatteryManager;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        return inflater.inflate(R.layout.fragment_home, container, false);

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mAndroidId = SettingFragment.mAndroidId;
        coreAlgorithm = new CoreAlgorithm(20, 1.7, 0.4f);
        try {
            mSender = new Sender("47.117.168.13", 31337, false);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
//        mSender.start();
        mRedisClient = new RedisClient();
//        mRedisClient.

        View view = getView();
        if (view != null) {
            mActivity = getActivity();
            mBatteryManager = (BatteryManager) mActivity.getSystemService(Context.BATTERY_SERVICE);
            mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
            mWindowManager = mActivity.getWindow().getWindowManager();
            assert mSensorManager != null;
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            //senGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            //mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
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

            Switch toggle1 = (Switch) view.findViewById(R.id.indoorSw);
            toggle1.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    _isIndoor = true;
                    TextView textView = view.findViewById(R.id.gpsCord);
                    textView.setText("室内坐标：");
                    TextView text = (TextView) getView().findViewById(R.id.gpsx);
                    text.setText("0.000");

                    text = (TextView) getView().findViewById(R.id.gpsy);
                    text.setText("0.000");
                } else {
                    _isIndoor = false;
                    TextView textView = view.findViewById(R.id.gpsCord);
                    textView.setText("卫星坐标：");
                    TextView text = (TextView) getView().findViewById(R.id.gpsx);
                    text.setText("0.000");

                    text = (TextView) getView().findViewById(R.id.gpsy);
                    text.setText("0.000");
                }
            });

            Button button = (Button) view.findViewById(R.id.dataClearSw);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (_isCollect) {
                        button.setText("定位模式");
                        button.setBackgroundColor(getResources().getColor(R.color.purple_700));
                        _isCollect = false;
                        if (magneticCollected.size() == 0) return;
                        Map<String, String> magInfo = new HashMap<>();
                        String listString = "";
                        for (String s : magneticCollected) {
                            listString += s + "\t";
                        }
                        magInfo.put("Zone_0001", listString);
                        mRedisClient.hset("zoneInfo", magInfo);
                        magneticCollected.clear();
                    } else
                    {
                        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                        builder.setTitle("采集区域编号");

                        // Set up the input
                        final EditText input = new EditText(mActivity);
                        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        builder.setView(input);

                        // Set up the buttons
                        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                m_Text = input.getText().toString();
                            }
                        });
                        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });

                        builder.show();
                        button.setText("采集模式");
                        button.setBackgroundColor(getResources().getColor(R.color.teal_200));
                        _isCollect = true;
                        String res = null;
                        try {
                            res = mRedisClient.hget("zoneInfo", m_Text);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        if (res == null || res.equals("[]")) return;
                        ArrayList<String> magneticCollected = new ArrayList<String>(Arrays.asList(res.split("\t")));
                        try {
                            RedisCache.setCache(magneticCollected);
                        } catch (InvalidProtocolBufferException e) {
                            throw new RuntimeException(e);
                        }

                    }
//                    originalCoords.clear();
//                    fusionCoords.clear();
//                    lastUpdate = 0;
//                    lastUpdate_coord = 0;
//                    lastUpdate_gyro = 0;
//                    lastUpdate_maget = 0;
//                    lastUpdate_step = 0;
//                    stepCount = 0;
//                    stepLength = 0;
//                    sumSinAngles = 0;
//                    sumCosAngles = 0;
//                    currentCoord= null;
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
        locationTrack.stopListener();
        if(timer != null)
            timer.cancel ();

    }

    public void startLocation() {
        if(mapViewModel == null) mapViewModel = MapViewModel.getInstance();
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
 //                           if(_isIndoor){
  //                              if(currentCoord == null){
  //                                  TextView text = (TextView) getView().findViewById(R.id.gpsx);
  //                                  double longitude = new Double(text.getText().toString());
  //                                  if(longitude < 1.0) return;
  //                                  text = (TextView) getView().findViewById(R.id.gpsy);
  //                                  double latitude = new Double(text.getText().toString());
  //                                  currentCoord = Point.fromLngLat(longitude, latitude);
  //                                  indoorCoords.add(currentCoord);
  //                             }
  //                              return;
  //                          }

                            if (locationTrack.canGetLocation()) {
                                Location loc = locationTrack.getLocation();
                                if(loc == null) return;
                                double longitude = loc.getLongitude();
                                double latitude = loc.getLatitude();

                                //Todo:给出区域坐标
//                                if(!TurfJoins.inside(Point.fromLngLat(longitude, latitude), Polygon.fromLngLats(null))) {
//                                    //定位区域外
//                                    stopLocation();
//                                    return;
//                                }

                                String sLon = String.format("%.5f", longitude);
                                TextView text = (TextView) getView().findViewById(R.id.gpsx);
                                text.setText(sLon);


                                String sLat = String.format("%.5f", latitude);
                                text = (TextView) getView().findViewById(R.id.gpsy);
                                text.setText(sLat);

                                originalCoords.add(Point.fromLngLat(longitude, latitude));
                                if(currentCoord == null){
                                    currentCoord = Point.fromLngLat(longitude, latitude);
                                }


                                if(mSender.isConnected()){
                                    // 发送点位数据
                                    LocationData.locationData locationData =  LocationData.locationData.newBuilder()
                                            .setId(mAndroidId)
                                            .setSatelliteNum(0)
                                            .setHdop(0)
                                            .setBatteryLevel(mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                                            .setGnssSpeed(loc.getSpeed())
                                            .setBarometer(0.0f)
                                            .setUpTime(0)
                                            .setLatitude((int)(latitude * 10000000))
                                            .setLongitude((int)(longitude * 10000000))
                                            .setTimestamp((int)(System.currentTimeMillis() / 1000))
                                            .build();
                                    mSender.send(locationData.toByteArray() ,false);
                                }


                                // Todo:第一版室外先用纯卫星定位


//                                currentCoord = coreAlgorithm.fusionLocation(currentCoord, Point.fromLngLat(longitude, latitude), coordCoefficient);
//
//                                String sFusionLon = String.format("%.5f", currentCoord.longitude());
//                                text = (TextView) getView().findViewById(R.id.fusionx);
//                                text.setText(sFusionLon);
//
//                                String sFusionLat = String.format("%.5f", currentCoord.latitude());
//                                text = (TextView) getView().findViewById(R.id.fusiony);
//                                text.setText(sFusionLat);
//
//                                fusionCoords.add(currentCoord);
                                mapViewModel.addPointInMapSource("original-location", originalCoords);
//                                mapViewModel.addPointInMapSource("fusion-location", fusionCoords);

                            } else {
                                locationTrack.showSettingsAlert();
                            }
                        }
                    });


                }
            }, GNSS_UPDATE_DELAY, GNSS_UPDATE_PERIOD);
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
//            mSensorManager.registerListener(this, senGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_GAME);
//            mSensorManager.registerListener(this, mRotationVector, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor sensor = sensorEvent.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //只在步行的时候才有效
            //Todo: 考虑车辆定位，目前的想到的思路（还未实现）：室外的时候用GNSS, 室内的时候以地磁数据为主（可能准确率不是很高，就要考虑其他方案）
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
            if((accData < 11 && accData > 9.1) || accData > 13) return;
            //chartFragment.onUdata((float) accData);
            double newStepLength = coreAlgorithm.calculateStepLength(lastAccelerometer);
            if(currentTime - lastUpdate_step > 3000){
                //长时间静止
                coordCoefficient = 1.0f;
            }else
                coordCoefficient = 0.3f;
            if(newStepLength > 0.1){
                if(lastUpdate_step == 0 || currentTime - lastUpdate_step > 200) {
                    stepCount += 1;
                    lastUpdate_step = currentTime;
                } else
                    return;
            }else return;
            stepLength += newStepLength;
            if(currentCoord != null){
                Point coord3857 = coreAlgorithm.EPSG4326To3857(currentCoord);
                double currentLon =  Math.sin(currentAngle) * newStepLength + coord3857.longitude();
                double currentLat =  Math.cos(currentAngle) * newStepLength + coord3857.latitude();
                currentCoord = coreAlgorithm.EPSG3857To4326(Point.fromLngLat(currentLon, currentLat));
                if(false) {
                    double lastDeltaMag = 0;
                    if(lastPostionMag > 0) lastDeltaMag = lastButterWorthMag - lastPostionMag;
                    lastPostionMag = lastButterWorthMag;
                    //coreAlgorithm.indoorFusionLocation(currentCoord, lastButterWorthMag, newStepLength, lastDeltaMag);
                    indoorCoords.add(currentCoord);
                    mapViewModel.addPointInMapSource("indoor-location", indoorCoords);
                    if(mSender.isConnected()){
                        // 发送点位数据
                        mSender.send(currentCoord.coordinates().toString());
                    }
                }
                if(_isCollect == true && lastButterWorthMagArr.size() > 0){
                    double avgMag = 0.0;
                    for (int i = 0; i < lastButterWorthMagArr.size(); i++){
                        avgMag += lastButterWorthMagArr.get(i) / lastButterWorthMagArr.size();
                    }
                    lastButterWorthMagArr.clear();
//                    if(mSender.isConnected()){
//                        // 发送点位数据
//                        LocationData.MagneticData locationData =  LocationData.MagneticData.newBuilder()
//                                .setZoneId(1)
//                                .setMagnitude(avgMag)
//                                .setLatitude((int)(currentLat * 10000000))
//                                .setLongitude((int)(currentLon * 10000000))
//                                .setTimestamp((int)(currentTime / 1000))
//                                .build();
////                        mSender.send(locationData.toByteArray());
//                        magneticCollected.add(Base64.getEncoder().encodeToString(locationData.toByteArray()));
//                    }
                }
            }
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
            if ((currentTime - lastUpdate_maget > 0)) {
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

            double magnetometerData = Math.sqrt(Math.pow(lastMagnetometer[0], 2) +
                    Math.pow(lastMagnetometer[1], 2) +
                    Math.pow(lastMagnetometer[2], 2)
            );
            lastButterWorthMag = coreAlgorithm.ButterWorth_lowPass(magnetometerData);
            if(chartFragment == null) chartFragment = ChartFragment.getInstance();
            if(chartFragment == null) return;
            chartFragment.onUdata((float) magnetometerData, (float)lastButterWorthMag);
            lastButterWorthMagArr.add(lastButterWorthMag);

            if (lastAccelerometerSet && lastMagnetometerSet)
            {

                mSensorManager.getRotationMatrix(mRotationMatrix, I, lastAccelerometer, sensorEvent.values);
                mSensorManager.getOrientation(mRotationMatrix, mOrientationAngles);

                currentAngle = mOrientationAngles[0];
//                sumCosAngles += Math.cos(mOrientationAngles[0]);
//                sumSinAngles += Math.sin(mOrientationAngles[0]);
            }

//            if(mSender.isConnected()){
//                // 发送点位数据
//                mSender.send(String.valueOf(magnetometerData));
//            }

        }
        if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR){
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastUpdate_maget > 1000)){
                lastUpdate_maget = currentTime;
                //SensorManager.getRotationMatrixFromVector(mRotationMatrix, sensorEvent.values);
                //SensorManager.getOrientation(mRotationMatrixFromVector, mOrientationAngles);

                final int worldAxisForDeviceAxisX;
                final int worldAxisForDeviceAxisY;

                switch (mWindowManager.getDefaultDisplay().getRotation()) {
                    case Surface.ROTATION_0:
                    default:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                        break;
                    case Surface.ROTATION_90:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                        break;
                    case Surface.ROTATION_180:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                        break;
                    case Surface.ROTATION_270:
                        worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                        worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                        break;
                }
//            TextView text = (TextView) getView().findViewById(R.id.stepNum);
//            text.setText(Integer.toString(mWindowManager.getDefaultDisplay().getRotation()));
                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(mRotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                // Transform rotation matrix into azimuth/pitch/roll
                float[] orientation = new float[3];
                //SensorManager.getOrientation(adjustedRotationMatrix, orientation);
               // if
                sumCosAngles = Math.cos(orientation[0]);
                sumSinAngles = Math.sin(orientation[0]);

            }

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