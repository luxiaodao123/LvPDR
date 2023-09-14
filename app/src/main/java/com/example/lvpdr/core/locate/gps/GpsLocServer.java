package com.example.lvpdr.core.locate.gps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;

import com.example.lvpdr.core.locate.AbstractLocServer;
import com.example.lvpdr.core.model.LatLng;
import com.example.lvpdr.data.HttpConstant;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GpsLocServer extends AbstractLocServer {
    private static final String TAG = "GpsLocServer";
    private static final long LOCATE_TIME_OUT = 60 * 1000;
    private static final int MIN_SATELLITE_COUNT = 6;
    private static final int MAX_SATELLITE_COUNT = 8;

    private static final int MIN_DISTANCE = 5;

    private Context mContext;
    private LocationManager mLocationClient = null;
    //private Handler mHandler;

    private GnssStatus.Callback mStatusCallback;
    private GnssMeasurementsEvent.Callback mMeasurementCallback;
    private GnssNavigationMessage.Callback mNavigationCallback;

    private Map<Integer, SatelliteInfo> mSatelliteInfoMap = new HashMap<>(MAX_SATELLITE_COUNT);
    private byte[] mGnssLocType = new byte[]{0x00, 0x00};

    private LatLng mRecentLocation = LatLng.buildDefault(LatLng.LOCATE_TYPE_GPS);

    private boolean mLocating;

    private boolean mCanGetDataFromGps = true;

    private long mInterval;
    private boolean mShouldStart;

    private LatLng mLstLocation = LatLng.buildDefault(LatLng.LOCATE_TYPE_GPS);

    private Set<Observer<LatLng>> mLocationObserverSet = new HashSet<>();
    private Set<Observer<Void>> mOutZoneObserverSet = new HashSet<>();

    public void observeOutZone(Observer<Void> observer, boolean register) {
        if (register) {
            mOutZoneObserverSet.add(observer);
        } else {
            mOutZoneObserverSet.remove(observer);
        }
    }

    public void observeLocation(Observer<LatLng> observer, boolean register) {
        if (register) {
            mLocationObserverSet.add(observer);
        } else {
            mLocationObserverSet.remove(observer);
        }
    }

    public LatLng getLstLocation() {
        return mLstLocation;
    }

    public void make4Next(LatLng latLng) {
        mLstLocation.update(latLng);
    }

    public boolean dataValid(@NonNull LatLng locationNow) {
        if (!mShouldStart) {
            return false;
        }
        if (!mLstLocation.valid()) {
            return locationNow.valid() && locationNow.getSatelliteCount() > MIN_SATELLITE_COUNT - 1;
        }
        return (locationNow.getSatelliteCount() > MIN_SATELLITE_COUNT - 1) &&
                locationNow.valid() && !HttpConstant.POWER_CONSUMPTION_OPTIMIZATION;
//                (!HttpConstant.POWER_CONSUMPTION_OPTIMIZATION || LatLngUtil.calculateLineDistance(mLstLocation.getLatitude(), mLstLocation.getLongitude(),
//                        locationNow.getLatitude(), locationNow.getLongitude()) >= MIN_DISTANCE);
    }

    public boolean localDataValid() {
        return mRecentLocation.valid() && mRecentLocation.getSatelliteCount() > (MIN_SATELLITE_COUNT -1);
    }

    public boolean valid() {
        if (!gpsValid(mContext)) {
            Log.d(TAG, "check permission fail: gps invalid");
            return false;
        }
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "check permission fail: no permission");
            return false;
        }
        return true;
    }

    public byte[] getGnssLocType() {
        // byte0->bit0:GPS,bit1:BDS,bit2:GLONASS,bit3:BDS-2,bit4:BDS-3,bit5:GLONASS,bit6:Galileo,bit7:QZSS
        // byte1->bit0:SBAS,bit1:L-band,bit2...bit7:reserved
        Arrays.fill(mGnssLocType, (byte) 0x00);

        for (Iterator<Map.Entry<Integer, SatelliteInfo>> iterator = mSatelliteInfoMap.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Integer, SatelliteInfo> next = iterator.next();
            int type = next.getValue().getType();
            switch (type) {
                case GnssStatus.CONSTELLATION_GPS:
                    mGnssLocType[0] += 1;
                    break;
                case GnssStatus.CONSTELLATION_SBAS:
                    mGnssLocType[1] += 1;
                    break;
                case GnssStatus.CONSTELLATION_GLONASS:
                    mGnssLocType[0] += (1 << 5);
                    break;
                case GnssStatus.CONSTELLATION_QZSS:
                    mGnssLocType[0] += (1 << 7);
                    break;
                case GnssStatus.CONSTELLATION_BEIDOU:
                    mGnssLocType[0] += (1 << 3);
                    break;
                case GnssStatus.CONSTELLATION_GALILEO:
                    mGnssLocType[0] += (1 << 6);
                    break;
                case GnssStatus.CONSTELLATION_IRNSS:
                    mGnssLocType[0] += (1 << 2);
                    break;
            }
        }
        return mGnssLocType;
    }

    public int getSatelliteCount() {
        return mRecentLocation.getSatelliteCount();
    }

    private void clearData() {
        mRecentLocation.clear();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void init(Context ctx) {
        mContext = ctx;
        //mWakeLock = new SipWakeLock((PowerManager) mContext.getSystemService(Context.POWER_SERVICE));
        try {
            Log.d(TAG, "init");
            mLocationClient = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            //mHandler = new Handler();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mStatusCallback = new GnssStatus.Callback() {
                    @Override
                    public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                        super.onSatelliteStatusChanged(status);

                        int count = 0;
                        int maxCount = status.getSatelliteCount();
                        for (int i = 0;i < maxCount;i++) {
                            float cn0dbHz = status.getCn0DbHz(i);
                            if (cn0dbHz > 0) {
                                updateSatelliteInfo(status.getSvid(i), cn0dbHz, status.getConstellationType(i));
                                count++;
                                //Timber.d("item: %s", cn0dbHz);
                            }
                        }
                        onSatelliteCountChanged(count);

                        //Log.d(TAG, "satellite count: " + mSatelliteCount);
						/*mCanGetDataFromGps = (status.getSatelliteCount() > 33);
						if (!mCanGetDataFromGps) {
							stopLBS();
						}*/
                    }
                };
				/*mMeasurementCallback = new GnssMeasurementsEvent.Callback() {
					@Override
					public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
						super.onGnssMeasurementsReceived(eventArgs);
					}
				};
				mNavigationCallback = new GnssNavigationMessage.Callback() {
					@Override
					public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
						super.onGnssNavigationMessageReceived(event);
					}
				};*/
                mLocationClient.registerGnssStatusCallback(mStatusCallback);
				/*mLocationClient.registerGnssMeasurementsCallback(mMeasurementCallback);
				mLocationClient.registerGnssNavigationMessageCallback(mNavigationCallback);*/
				/*mLocationClient.addNmeaListener(new OnNmeaMessageListener() {
					@Override
					public void onNmeaMessage(String message, long timestamp) {
						Timber.d("nmea: %s", message);
						if (message != null && message.startsWith("$")) {
							String[] tokenArray = message.split(",");
							String type = tokenArray[0];
							if (type.startsWith("$GPGGA") && tokenArray.length > 9 && !tokenArray[9].isEmpty()) {
								//mLastMslAltitude = Double.parseDouble(tokenArray[9]);
							}
						}
					}
				});*/
            } else {
                //mCanGetDataFromGps = true;

                mLocationClient.addGpsStatusListener(mGpsStatusListener);
            }

            registerGpsReceiver(true);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "init fail: " + (e == null ? "null" : e.getLocalizedMessage()));
        }
    }

    private void updateSatelliteInfo(int id, float cn0dbHz, int type) {
        if (mSatelliteInfoMap.containsKey(id)) {
            mSatelliteInfoMap.get(id).setCn0dbHz(cn0dbHz);
        } else {
            if (mSatelliteInfoMap.size() < MAX_SATELLITE_COUNT) {
                SatelliteInfo info = new SatelliteInfo(type, cn0dbHz);
                mSatelliteInfoMap.put(id, info);
            } else {
                int minId = -1;
                float minCn0dbHz = Integer.MAX_VALUE;
                for (Iterator<Map.Entry<Integer, SatelliteInfo>> iterator = mSatelliteInfoMap.entrySet().iterator(); iterator.hasNext();) {
                    Map.Entry<Integer, SatelliteInfo> next = iterator.next();
                    SatelliteInfo item = next.getValue();
                    if (item.getCn0dbHz() < minCn0dbHz) {
                        minCn0dbHz = item.getCn0dbHz();
                        minId = next.getKey();
                    }
                }
                if (minId != -1) {
                    mSatelliteInfoMap.remove(minId);
                }
                SatelliteInfo info = new SatelliteInfo(type, cn0dbHz);
                mSatelliteInfoMap.put(id, info);
            }
        }
    }

    private void onSatelliteCountChanged(int count) {
//        Timber.d("satellite changed: %s", count);
        mRecentLocation.setSatelliteCount(count);
    }

    private GpsStatus.Listener mGpsStatusListener = event -> {
        @SuppressLint("MissingPermission") GpsStatus status = mLocationClient.getGpsStatus(null);
        if (status == null) {
            //Log.d(TAG, "satellite num: 0");
            onSatelliteCountChanged(0);
        } else if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            int maxSatellites = status.getMaxSatellites();
//            Timber.d("max satellite count: %s", maxSatellites);
            Iterator<GpsSatellite> it = status.getSatellites().iterator();
            int count = 0;
            while (it.hasNext() && count <= maxSatellites) {
                GpsSatellite s = it.next();
                float snr = s.getSnr();
                if (snr > 0) {
                    count++;
                    //Timber.d("item1: %s", snr);
                }
            }
            onSatelliteCountChanged(count);
            //Log.d(TAG, "satellite num: " + count);
        }
    };

    @Override
    public void release() {
        registerGpsReceiver(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mLocationClient.unregisterGnssStatusCallback(mStatusCallback);
			/*mLocationClient.unregisterGnssMeasurementsCallback(mMeasurementCallback);
			mLocationClient.unregisterGnssNavigationMessageCallback(mNavigationCallback);*/
        } else {
            mLocationClient.removeGpsStatusListener(mGpsStatusListener);
        }
        stopLocate();
        //mHandler = null;
        if (mLocationClient != null) {
            mLocationClient.removeUpdates(mLocationListener);
            mLocationClient = null;
        }
        //mCanGetDataFromGps = false;
    }

    //private SipWakeLock mWakeLock;

    @SuppressLint("MissingPermission")
    public void startLocate(long interval) {
        Log.d(TAG, String.format("start lbs: %s", interval));
        mShouldStart = true;
        mInterval = interval;

        if (!mCanGetDataFromGps) {
            Log.d(TAG, "start lbs fail: no enough satellite");
            return;
        }
        if (mLocating) {
            Log.d(TAG, "start lbs fail: already start");
            notifySuccess();
            return;
        }
        if (!valid()) {
            return;
        }
        mLocating = true;

		/*if (mWakeLock != null) {
			mWakeLock.acquire(this);
		}*/

        //mHandler.postDelayed(mTimeoutRunnable, LOCATE_TIME_OUT);
        Log.d(TAG, "start lbs suc");
        mLocationClient.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0, mLocationListener, Looper.getMainLooper());
    }

	/*private Runnable mTimeoutRunnable = () -> {
		String error = "not enough stallite";
		Log.e(TAG, error);
		notifyError(100004, error);
		stopLBS();

		mLocating = false;
	};*/

    public void stopLocate() {
        stopLocate(true);
    }

    private void stopLocate(boolean byHand) {
        Log.d(TAG, "stop lbs");
        if (byHand) {
            mShouldStart = false;
        }
		/*if (mHandler != null) {
			mHandler.removeCallbacksAndMessages(null);
		}*/
        if (mLocationClient != null) {
            mLocationClient.removeUpdates(mLocationListener);
        }

        mLocating = false;

        clearData();
    }

    private boolean mReceiverRegistered;

    private void registerGpsReceiver(boolean register) {
        if (mContext == null) {
            return;
        }
        if (register) {
            if (!mReceiverRegistered) {
                mReceiverRegistered = true;
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(LocationManager.MODE_CHANGED_ACTION);
                intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
                mContext.registerReceiver(mGpsReceiver, intentFilter);
            }
        } else {
            if (mReceiverRegistered) {
                mContext.unregisterReceiver(mGpsReceiver);
            }
        }
    }

    private Boolean mLstGpsOpen = null;

    private BroadcastReceiver mGpsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean gpsValid = gpsValid(context);
            if (mLstGpsOpen == null || mLstGpsOpen != gpsValid) {
                mLstGpsOpen = gpsValid;
             //   Timber.d("gps valid change: %s", gpsValid);

                if (mLstGpsOpen) {
                    if (mShouldStart) {
                        startLocate(mInterval);
                    }
                } else {
                    stopLocate(false);
                }
            }
        }
    };

    private boolean gpsValid(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // 通过GPS卫星定位，定位级别可以精确到街（通过24颗卫星定位，在室外和空旷的地方定位准确、速度快）
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 通过WLAN或移动网络(3G/2G)确定的位置（也称作AGPS，辅助GPS定位。主要用于在室内或遮盖物（建筑群或茂密的深林等）密集的地方定位）
        //boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gps;
    }

    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
			/*if (mHandler != null) {
				mHandler.removeCallbacksAndMessages(null);
			}*/

            Bundle extras = location.getExtras();
            if (extras != null) {
                int satelliteUsed = extras.getInt("satellites", -1);
          //      Timber.d("satellite used: %s", satelliteUsed);
            }
            String provider = location.getProvider();
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            double altitude = location.getAltitude();
            if (location.hasAltitude()) {
                // P=P0×（1-H/44300)^5.256
                // H=44300*(1-(P/P0)^(1/5.256))
                // P0=大气压（0℃，101.325kPa）
                double sPv = 1013.25 * Math.pow(1.0 - altitude / 44300.0, 5.256);
                mRecentLocation.setBarometer(sPv);
            } else {
                mRecentLocation.setBarometer(-1.0);
            }
            Log.d(TAG, String.format("lo: %s, %s, %s", provider, lat, lng));
            //AppToast.show("al g: " + altitude + ":" + sPv);
            //double[] result = GeoSysConversion.wgs84toGCJ02(lat, lng);

            if (location.hasAccuracy()) {
                float accuracy = location.getAccuracy();
                mRecentLocation.setAccuracy(accuracy);
            } else {
                mRecentLocation.setAccuracy(-1.0F);
            }
            if (location.hasSpeed()) {
                float speed = toKmh(location.getSpeed());
                mRecentLocation.setSpeed(speed);
            } else {
                mRecentLocation.setSpeed(-1.0F);
            }
            if (location.hasBearing()) {
                float bearing = location.getBearing();
                mRecentLocation.setBearing(bearing);
            } else {
                mRecentLocation.setBearing(-1.0F);
            }
            mRecentLocation.update(lat, lng);

//            if (localDataValid()) {
//                // 判断是否离开定位区域
//                LocationZone locationZone = AppPreference.getLocationZone();
//                if (locationZone != null) {
//                    int count = locationZone.getCount();
//                    if (locationZone != null && count > 0) {
//                        LatLng[] locationZoneArray = new LatLng[count];
//                        List<Double> latList = locationZone.getLatList();
//                        List<Double> lngList = locationZone.getLngList();
//                        for (int i = 0;i < count;i++) {
//                            locationZoneArray[i] = new LatLng(latList.get(i), lngList.get(i));
//                        }
//                        boolean inZone = GraphUtil.isPointInPolygon(mRecentLocation, locationZoneArray) ||
//                                GraphUtil.isPointInRectangle(mRecentLocation, locationZoneArray);
//                        if (!inZone) {
//                            for (Iterator<Observer<Void>> iterator = mOutZoneObserverSet.iterator();iterator.hasNext();) {
//                                iterator.next().onChanged(null);
//                            }
//                        }
//                    }
//                }
//            }

            notifySuccess();
		/*if (mWakeLock != null) {
			mWakeLock.release(this);
		}*/
            //mLocating = false;
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {}

        @Override
        public void onProviderDisabled(@NonNull String provider) {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    public LatLng getRecentLocation() {
        return mRecentLocation;
    }

    private float toKmh(float speed) {
        return speed * 3.6F;
    }

    private void notifySuccess() {
		/*if (mCallback != null) {
			mCallback.onGetData(mRecentLocation);
		}*/
        Iterator<Observer<LatLng>> iterator = mLocationObserverSet.iterator();
        while (iterator.hasNext()) {
            iterator.next().onChanged(mRecentLocation);
        }
    }

    private void notifyError(int code, String error) {
		/*if (mCallback != null) {
			mCallback.onError(code, error);
		}*/
    }
}
