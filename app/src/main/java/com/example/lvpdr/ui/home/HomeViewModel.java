package com.example.lvpdr.ui.home;

import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.lvpdr.core.LocationTrack;

public class HomeViewModel extends ViewModel {

    private static HomeViewModel singleton = null;
    private Location location;

    public HomeViewModel() {
        if(singleton == null)
            singleton = this;
    }

    public static HomeViewModel getInstance(){
        if(singleton == null)
                singleton = new HomeViewModel();
        return singleton;
    }

    public  void setLocation(Location loc){
        location = loc;
    }

    public Location getLocation(){
        return location;
    }
}