package com.example.lvpdr.ui.setting;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.lvpdr.CoreAlgorithm;
import com.example.lvpdr.HTTPRequest;
import com.example.lvpdr.LocateWakeLock;
import com.example.lvpdr.R;
import com.example.lvpdr.Sender;
import com.example.lvpdr.core.LocationTrack;
import com.example.lvpdr.core.SensorService;
import com.example.lvpdr.data.LocationData;
import com.example.lvpdr.data.cache.RedisCache;
import com.example.lvpdr.data.cache.RedisClient;
import com.example.lvpdr.ui.map.MapViewModel;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class SettingFragment extends Fragment {
    private static final String TAG = "SettingFragment";
    private static FragmentActivity mActivity;
    private static View mView;
    private SettingViewModel mViewModel;
    public static String mAndroidId = "";
    private static boolean _isBinding = false;
    private static boolean _isLocationStart = false;
    private static boolean _isBaseInfoLoaded = false;
    private static boolean _isUserNameSeted = false;
    private static String userName = "";
    private static String vehicleLicense = "";
    private static ArrayList<String> vehicleLicenseArr = new ArrayList<>();
    private ArrayList<Point> originalCoords = new ArrayList<Point>();

    private MapViewModel mapViewModel;
    private CoreAlgorithm coreAlgorithm;
    private SensorService mSensorService;
    private RedisClient mRedisClient;
    private Timer timer = null;
    private Sender mSender;
    private LocationTrack locationTrack;
    private Point currentCoord;
    private LocateWakeLock mLocationWakeLock;


    private static long GNSS_UPDATE_PERIOD = 1500 * 1;
    private static final long GNSS_UPDATE_DELAY = 0;

    private int seqNum = 1;
    private long lastUpdate = 0;


    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setting, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(SettingViewModel.class);
        mActivity = getActivity();
        mView = getView();
        locationTrack = new LocationTrack(getContext(), mActivity);
        mAndroidId = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        mLocationWakeLock = new LocateWakeLock(getContext(), "GPS");
        coreAlgorithm = new CoreAlgorithm(20, 1.7, 0.4f);

        try {
            mSender = new Sender("47.117.168.13", 31337, false);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        mRedisClient = new RedisClient();
        mSensorService = new SensorService(getActivity());

        if(isIgnoringBatteryOptimizations(getContext()) == false) {
            requestIgnoreBatteryOptimizations(getContext());
        }
        //获取基础信息
        _getBaseInfo();

        //初始化界面信息
        _initWidget();

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLocationWakeLock.release();
    }

    private void _getBaseInfo ()  {
        new HTTPRequest().execute("http://47.117.168.13:31322/v1/EmployeeInfo", "GET");
        new HTTPRequest().execute("http://47.117.168.13:31322/v1/VehicleInfo", "GET");
    }

    private void _initWidget() {

        Button modeSwtButton = (Button) getView().findViewById(R.id.modeSwtButton);
        modeSwtButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle("请选择车辆");

                // Set up the spinner
                Spinner spinner = new Spinner(mActivity);
                ArrayAdapter<String> adapter = null;
                adapter = new ArrayAdapter<String>(mActivity, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,  vehicleLicenseArr);
                spinner.setAdapter(adapter);
                builder.setView(spinner);

                // Set up the buttons
                builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//                        input.getText().toString();
                        String license = (String) spinner.getSelectedItem();
                        setVehicleLicense(license);
                        new HTTPRequest().execute("http://47.117.168.13:31322/v1/VehicleBinding", "POST", "{ \"cid\" : \"" + mAndroidId + "\",\"license\":\"" + license + "\"}");
                    }
                });
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            }
        });

        Button bindingButton = (Button) getView().findViewById(R.id.bindingButton);
        bindingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle("请输入姓名:");

                // Set up the input
                final EditText input = new EditText(mActivity);
                // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                builder.setView(input);

                // Set up the buttons
                builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        userName = input.getText().toString();
                        setUsrName(userName);

                        new HTTPRequest().execute("http://47.117.168.13:31322/v1/EmployeeBinding", "POST", "{ \"cid\" : \"" + mAndroidId + "\",\"name\":\"" + userName + "\"}");
                    }
                });
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
            }
        });


        Button startEndButton = (Button) getView().findViewById(R.id.startEndButton);
        startEndButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(_isLocationStart == false) {
                    _triggerStart();
                } else {
                    _triggerEnd();
                }
            }
        });

        Button frequencButton = (Button) getView().findViewById(R.id.frequencButton);
        frequencButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle("请选择上报频率(s):");

                // Set up the spinner
                Spinner spinner = new Spinner(mActivity);
                ArrayAdapter<String> adapter = null;
                adapter = new ArrayAdapter<String>(mActivity, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, new String[]{"5", "10", "30", "60", "300"});
                spinner.setAdapter(adapter);
                builder.setView(spinner);

                // Set up the buttons
                builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String frequency = (String) spinner.getSelectedItem();
                        GNSS_UPDATE_PERIOD = Integer.parseInt(frequency) * 1000;
                    }
                });
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            }
        });


        /**
         * 显示设备编号
         */
        TextView text = (TextView) getView().findViewById(R.id.deviceStrVal);
        text.setText(mAndroidId);

    }

    private void _triggerEnd() {
        Button startEndButton = (Button) getView().findViewById(R.id.startEndButton);
        startEndButton.setText("开始定位");
        startEndButton.setBackgroundColor(getResources().getColor(R.color.purple_700));
        _isLocationStart = false;
        stopLocation();
        //结束定位
    }

    private void _triggerStart(){
        Button startEndButton = (Button) getView().findViewById(R.id.startEndButton);
        startEndButton.setText("结束定位");
        _isLocationStart = true;
        startEndButton.setBackgroundColor(getResources().getColor(R.color.red));
        startLocation();
        //开始定位
    }

    /**
     * 是否绑定人员
     * @return
     */
    static public Boolean isBinding(){
        return _isBinding;
    }

    static public void setBinding(boolean status) {
        _isBinding = status;
    }

    /**
     * 是否定位开始
     * @return
     */
    static public boolean isLocationStart() {
        return _isLocationStart;
    }

    static public void setLocationStart(boolean status) {
        _isLocationStart = status;
    }

    static public boolean isBaseInfoLoaded() {
        return _isBaseInfoLoaded;
    }

    static public void setBaseInfoLoaded(boolean status) {
        _isBaseInfoLoaded = status;
    }

    static public boolean isUserNameSeted() {
        return _isUserNameSeted;
    }

    static public void setUserNameSeted(boolean status) {
        _isUserNameSeted = status;
    }

    static public void setUsrName(String name) {
        setUserNameSeted(true);
        userName = name;
        TextView text = (TextView) mView.findViewById(R.id.employeeStrVal);
        text.setText(name);
    }

    static public void setVehicleLicense(String license) {
        vehicleLicense = license;
        TextView text = (TextView) mView.findViewById(R.id.vehicleStrVal);
        text.setText(license);
    }

    static public void setVehicleLicenseArr(ArrayList<String> arr) {
        vehicleLicenseArr = arr;
    }

    public void startLocation() {
        if(mapViewModel == null) {
            mapViewModel = MapViewModel.getInstance();
        }
        super.onResume();
        //  mSensorManager.startLocation();
        if (_isLocationStart == true) {
            mLocationWakeLock.acquire();
            timer = new Timer();
            timer.schedule (new TimerTask() {
                @Override
                public void run () {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            locationTrack = LocationTrack.getInstance();

                            if (locationTrack.canGetLocation()) {
                                Location loc = locationTrack.getLocation();
                                if(loc == null) return;

                                double longitude = loc.getLongitude();
                                double latitude = loc.getLatitude();

                                //Todo:给出区域坐标
//                                if(TurfJoins.inside(Point.fromLngLat(longitude, latitude), Polygon.fromLngLats(null)) == false) {
//                                    //定位区域外
//                                    _triggerEnd();
//                                    return;
//                                }

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
                                            .setBatteryLevel(mSensorService.getBattery())
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
                                 mapViewModel.addPointInMapSource("original-location", originalCoords);

                            } else {
                                locationTrack.showSettingsAlert();
                            }
                        }
                    });
                }
            }, GNSS_UPDATE_DELAY, GNSS_UPDATE_PERIOD);
        }
    }

    public void stopLocation() {
        super.onPause();
        timer.cancel();
        mSensorService.stopLocation();
        locationTrack.stopListener();
        mLocationWakeLock.release();
    }

    public boolean isIgnoringBatteryOptimizations(Context context){
        boolean isIgnoring = false;
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        if (powerManager != null)
            isIgnoring = powerManager.isIgnoringBatteryOptimizations("com.example.lvpdr");

        return isIgnoring;
    }

    public void requestIgnoreBatteryOptimizations(Context context){
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + "com.example.lvpdr"));
            context.startActivity(intent);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 跳转到指定应用的首页
     */
    private void showActivity(@NonNull String packageName) {
        Intent intent = mActivity.getPackageManager().getLaunchIntentForPackage(packageName);
        startActivity(intent);
    }

    /**
     * 跳转到指定应用的指定页面
     */
    private void showActivity(@NonNull String packageName, @NonNull String activityDir) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, activityDir));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

}