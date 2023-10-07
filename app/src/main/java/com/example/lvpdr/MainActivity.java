package com.example.lvpdr;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.Toast;

import com.example.lvpdr.core.LocationTrack;
import com.example.lvpdr.ui.chart.ChartFragment;
import com.example.lvpdr.ui.home.HomeFragment;
import com.example.lvpdr.ui.map.MapFragment;
import com.example.lvpdr.ui.setting.SettingFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.lvpdr.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

//    private ActivityMainBinding binding;

    private int locationRequestCode = 1000;

    public NavigationView navigationView;
    public Fragment mContent;
    BottomNavigationView navView;
    SettingFragment settingFragment = new SettingFragment();
    HomeFragment homeFragment = new HomeFragment();
    ChartFragment chartFragment = new ChartFragment();
    MapFragment mapFragment = new MapFragment();

    LocationTrack locationTrack;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        locationTrack = new LocationTrack(MainActivity.this);
        setContentView(R.layout.activity_main);
        navView = findViewById(R.id.nav_view);
        getSupportFragmentManager().beginTransaction().replace(R.id.nav_host_fragment_activity_main, settingFragment).commit();
        mContent = settingFragment;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, locationRequestCode);
        } else {
            Toast T = Toast.makeText(getApplicationContext(), "Location & file access Permission Granted", Toast.LENGTH_SHORT);
            T.show();
        }

        navView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if(SettingFragment.isBaseInfoLoaded() == false || SettingFragment.isUserNameSeted() == false) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                    // 设置消息文本
                    builder.setMessage("请先完成绑定");

                    // 设置一个默认的“确定”按钮
                    builder.setPositiveButton("确定", null);

                    // 创建AlertDialog对象
                    AlertDialog dialog = builder.create();

                    // 显示消息框
                    dialog.show();
                    return false;
                }

                if(item.getItemId() == R.id.navigation_settings){
                    //getSupportFragmentManager().beginTransaction().replace(R.id.nav_host_fragment_activity_main, homeFragment).commit();
                    switchFragment(mContent, settingFragment);
                    return true;
                }
//                if(item.getItemId() == R.id.navigation_home){
//                    //getSupportFragmentManager().beginTransaction().replace(R.id.nav_host_fragment_activity_main, homeFragment).commit();
//                    switchFragment(mContent, homeFragment);
//                    return true;
//                }
                if(item.getItemId() == R.id.navigation_map){
                    //getSupportFragmentManager().beginTransaction().replace(R.id.nav_host_fragment_activity_main, mapFragment).commit();
                    switchFragment(mContent, mapFragment);
                    return true;
                }
//                if(item.getItemId() == R.id.navigation_chart){
//                    //getSupportFragmentManager().beginTransaction().replace(R.id.nav_host_fragment_activity_main, chartFragment).commit();
//                    switchFragment(mContent, chartFragment);
//                    return true;
//                }
                return false;
            }
        });

    }


    public void switchFragment(Fragment from, Fragment to) {

        if (mContent != to) {
            mContent = to;
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            if (!to.isAdded()) {
                // 先判断是否被add过
                if (from != null) {
                    transaction.hide(from);
                }
                if (to != null) {
                    transaction.add(R.id.nav_host_fragment_activity_main, to).commit();
                }
            } else {
                if (from != null) {
                    transaction.hide(from);
                }
                if (to != null) {
                    transaction.show(to).commit();
                }
            }
        }
    }


    LocationTrack mBackgroundService;
    boolean mServiceBound = false;

    private ServiceConnection mServiceConnection = new ServiceConnection() {


        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationTrack.LocalBinder myBinder = (LocationTrack.LocalBinder) service;
            mBackgroundService = myBinder.getService();
            mServiceBound = true;
        }
    };
    //Start the service when the activity starts
    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, LocationTrack.class);
        startService(intent);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
    }

}