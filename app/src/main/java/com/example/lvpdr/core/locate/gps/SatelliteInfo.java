package com.example.lvpdr.core.locate.gps;

public class SatelliteInfo {
    private int type;
    private float cn0dbHz;

    public SatelliteInfo() {}

    public SatelliteInfo(int type, float cn0dbHz) {
        this.type = type;
        this.cn0dbHz = cn0dbHz;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public float getCn0dbHz() {
        return cn0dbHz;
    }

    public void setCn0dbHz(float cn0dbHz) {
        this.cn0dbHz = cn0dbHz;
    }
}
