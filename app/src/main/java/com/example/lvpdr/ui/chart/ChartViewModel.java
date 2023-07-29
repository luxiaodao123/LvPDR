package com.example.lvpdr.ui.chart;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.lvpdr.ui.home.HomeViewModel;

public class ChartViewModel extends ViewModel {

    private static ChartViewModel singleton = null;

    public ChartViewModel() {
        if(singleton == null)
            singleton = this;
    }

    public static ChartViewModel getInstance(){
        if(singleton == null)
            singleton = new ChartViewModel();
        return singleton;
    }
}