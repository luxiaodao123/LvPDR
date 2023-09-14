package com.example.lvpdr.core.model;

public class LatLng {
    public static final int LOCATE_TYPE_UNKNOWN = -1;
    public static final int LOCATE_TYPE_GPS = 0;
    public static final int LOCATE_TYPE_NET = 1;

    private int locateType = LOCATE_TYPE_UNKNOWN;
    private double latitude;
    private double longitude;
    private float accuracy;
    private float speed;
    private float bearing;
    private int satelliteCount = -1;
    private double barometer;

    public static LatLng buildDefault(int locateType) {
        LatLng latLng = new LatLng();
        latLng.locateType = locateType;
        return latLng;
    }

	/*public static boolean valid(LatLng data) {
		return !GeoSysConversion.outOfChina(data.latitude, data.longitude);
	}*/

	/*public static boolean valid(double lat, double lng) {
		return (lat != 0&&lat !=4.9E-324
				&& lng != 0&&lng!=4.9E-324);
	}*/

    public LatLng() {}

    public LatLng(double latitude, double longitude){
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void clear() {
        latitude = 0;
        longitude = 0;
        satelliteCount = -1;
    }

    public boolean valid() {
        return (this.latitude != 0&&this.latitude !=4.9E-324
                && this.longitude != 0&&this.longitude!=4.9E-324);
    }

    public void update(LatLng data) {
        locateType = data.locateType;
        latitude = data.latitude;
        longitude = data.longitude;
        accuracy = data.accuracy;
        speed = data.speed;
        bearing = data.bearing;
        satelliteCount = data.satelliteCount;
        barometer = data.barometer;
    }

    public void update(double lattitude, double longitude) {
        this.latitude = lattitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getBearing() {
        return bearing;
    }

    public void setBearing(float bearing) {
        this.bearing = bearing;
    }

    public int getLocateType() {
        return locateType;
    }

    public void setLocateType(int locateType) {
        this.locateType = locateType;
    }

    public int getSatelliteCount() {
        return satelliteCount;
    }

    public void setSatelliteCount(int satelliteCount) {
        this.satelliteCount = satelliteCount;
    }

    public double getBarometer() {
        return barometer;
    }

    public void setBarometer(double barometer) {
        this.barometer = barometer;
    }
}
