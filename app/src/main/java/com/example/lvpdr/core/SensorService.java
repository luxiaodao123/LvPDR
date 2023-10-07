package com.example.lvpdr.core;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.example.lvpdr.CoreAlgorithm;
import com.example.lvpdr.R;
import com.example.lvpdr.Sender;
import com.example.lvpdr.data.cache.RedisClient;
import com.example.lvpdr.ui.chart.ChartFragment;
import com.example.lvpdr.ui.home.HomeViewModel;
import com.example.lvpdr.ui.map.MapViewModel;
import com.mapbox.geojson.Point;

import java.util.ArrayList;
import java.util.Timer;

public class SensorService extends Service implements SensorEventListener {
    private static final String TAG = "SeneorManager";
    private static FragmentActivity mActivity;
    private static SensorService singleton = null;
    private static SensorManager mSensorManager;
    private WindowManager mWindowManager;
    private BatteryManager mBatteryManager;

    private Sensor mAccelerometer;
    private Sensor mMagneticField;
    private Sensor mRotationVector;
    private Sensor mGyroscopde;

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

    private Boolean lastMagnetometerSet = false;
    private Boolean lastAccelerometerSet = false;

    private ChartFragment chartFragment;
    private MapViewModel mapViewModel;
    private CoreAlgorithm coreAlgorithm;
    private Sender mSender;

    private float coordCoefficient = 0.3f;

    private Point currentCoord;
    private double currentAngle = 0;

    private boolean _isIndoor = false;
    private boolean _isCollect = false;

    public SensorService(FragmentActivity activity) {
        if (singleton != null) return;
        singleton = this;
        mActivity = activity;
        mBatteryManager = (BatteryManager) mActivity.getSystemService(Context.BATTERY_SERVICE);
        mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
        mWindowManager = mActivity.getWindow().getWindowManager();
        assert mSensorManager != null;
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscopde = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public static SensorService getInstance() {
        return singleton;
    }

    public void stopLocation() {
        mSensorManager.unregisterListener(this);
    }

    public void startLocation() {
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mGyroscopde, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mRotationVector, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor sensor = sensorEvent.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            //只在步行的时候才有效
            //Todo: 考虑车辆定位，目前的想到的思路（还未实现）：室外的时候用GNSS, 室内的时候以地磁数据为主（可能准确率不是很高，就要考虑其他方案）
            lastAccelerometerSet = true;

            long currentTime = System.currentTimeMillis();

            if ((currentTime - lastUpdate) > 0) lastUpdate = currentTime;

            System.arraycopy(sensorEvent.values, 0, lastAccelerometer, 0, sensorEvent.values.length);
            double accData = Math.sqrt(Math.pow(lastAccelerometer[0], 2) +
                    Math.pow(lastAccelerometer[1], 2) +
                    Math.pow(lastAccelerometer[2], 2)
            );
            if ((accData < 11 && accData > 9.1) || accData > 13) return;

            double newStepLength = coreAlgorithm.calculateStepLength(lastAccelerometer);
            if (currentTime - lastUpdate_step > 3000) {
                //长时间静止
                coordCoefficient = 1.0f;
            } else
                coordCoefficient = 0.3f;
            if (newStepLength > 0.1) {
                if (lastUpdate_step == 0 || currentTime - lastUpdate_step > 200) {
                    stepCount += 1;
                    lastUpdate_step = currentTime;
                } else
                    return;
            } else return;
            stepLength += newStepLength;
            if (currentCoord != null) {
                Point coord3857 = coreAlgorithm.EPSG4326To3857(currentCoord);
                double currentLon = Math.sin(currentAngle) * newStepLength + coord3857.longitude();
                double currentLat = Math.cos(currentAngle) * newStepLength + coord3857.latitude();
                currentCoord = coreAlgorithm.EPSG3857To4326(Point.fromLngLat(currentLon, currentLat));
                if (false) {
                    double lastDeltaMag = 0;
                    if (lastPostionMag > 0) lastDeltaMag = lastButterWorthMag - lastPostionMag;
                    lastPostionMag = lastButterWorthMag;
                    //coreAlgorithm.indoorFusionLocation(currentCoord, lastButterWorthMag, newStepLength, lastDeltaMag);
                    indoorCoords.add(currentCoord);
                    mapViewModel.addPointInMapSource("indoor-location", indoorCoords);
                    if (mSender.isConnected()) {
                        // 发送点位数据
                        mSender.send(currentCoord.coordinates().toString());
                    }
                }
                if (_isCollect == true && lastButterWorthMagArr.size() > 0) {
                    double avgMag = 0.0;
                    for (int i = 0; i < lastButterWorthMagArr.size(); i++) {
                        avgMag += lastButterWorthMagArr.get(i) / lastButterWorthMagArr.size();
                    }
                    lastButterWorthMagArr.clear();
                }
            }

        }
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {

            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastUpdate_maget > 0)) {
                lastUpdate_maget = currentTime;
            }
            //calculate
            lastMagnetometerSet = true;

            System.arraycopy(sensorEvent.values, 0, lastMagnetometer, 0, sensorEvent.values.length);

            lastMagnetometer = coreAlgorithm.getInstance().lowPass(sensorEvent.values.clone(), lastMagnetometer);

            double magnetometerData = Math.sqrt(Math.pow(lastMagnetometer[0], 2) +
                    Math.pow(lastMagnetometer[1], 2) +
                    Math.pow(lastMagnetometer[2], 2)
            );
            lastButterWorthMag = coreAlgorithm.ButterWorth_lowPass(magnetometerData);
            if (chartFragment == null) chartFragment = ChartFragment.getInstance();
            if (chartFragment == null) return;
            chartFragment.onUdata((float) magnetometerData, (float) lastButterWorthMag);
            lastButterWorthMagArr.add(lastButterWorthMag);

            if (lastAccelerometerSet && lastMagnetometerSet) {

                mSensorManager.getRotationMatrix(mRotationMatrix, I, lastAccelerometer, sensorEvent.values);
                mSensorManager.getOrientation(mRotationMatrix, mOrientationAngles);

                currentAngle = mOrientationAngles[0];
//                sumCosAngles += Math.cos(mOrientationAngles[0]);
//                sumSinAngles += Math.sin(mOrientationAngles[0]);
            }
        }
        if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastUpdate_maget > 1000)) {
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

                float[] adjustedRotationMatrix = new float[9];
                SensorManager.remapCoordinateSystem(mRotationMatrix, worldAxisForDeviceAxisX,
                        worldAxisForDeviceAxisY, adjustedRotationMatrix);

                float[] orientation = new float[3];

                sumCosAngles = Math.cos(orientation[0]);
                sumSinAngles = Math.sin(orientation[0]);

            }

        }
        if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            long currentTime = System.currentTimeMillis();
            if ((currentTime - lastUpdate_gyro > 300)) {
                lastUpdate_gyro = currentTime;
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    public int getBattery() {
        return mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
