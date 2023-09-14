package com.example.lvpdr.core.locate.gps;

public class GpsLocConfig {
    private boolean shouldStart;
    private long interval;

    public static GpsLocConfig buildStart(long interval) {
        GpsLocConfig config = new GpsLocConfig();
        config.shouldStart = true;
        config.interval = interval;
        return config;
    }

    public static GpsLocConfig buildStop() {
        GpsLocConfig config = new GpsLocConfig();
        config.shouldStart = false;
        config.interval = -1;
        return config;
    }

    public void update(GpsLocConfig config) {
        shouldStart = config.shouldStart;
        interval = config.interval;
    }

    public boolean isShouldStart() {
        return shouldStart;
    }

    public long getInterval() {
        return interval;
    }
}
