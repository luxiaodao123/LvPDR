package com.example.lvpdr.core.locate;

import android.content.Context;

import com.example.lvpdr.core.model.LatLng;

public abstract class AbstractLocServer {
    protected AbstractLocServer() {}
    public abstract void init(Context context);
    public abstract void release();
    public abstract void startLocate(long interval);
    public abstract void stopLocate();
    public abstract LatLng getRecentLocation();
}
