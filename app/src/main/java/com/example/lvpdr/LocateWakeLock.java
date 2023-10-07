package com.example.lvpdr;

import android.content.Context;
import android.os.PowerManager;

public class LocateWakeLock {
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    public LocateWakeLock(Context context, String tag) {
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, tag);
    }

    public synchronized void acquire() {
        if (!mWakeLock.isHeld()) mWakeLock.acquire();
    }

    public synchronized void release() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }
}
