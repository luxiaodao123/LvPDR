package com.example.lvpdr.core.signalstrength;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import androidx.core.app.ActivityCompat;


import java.util.List;

public class LTESignal {
    private Context mContext = null;
    private TelephonyManager mTelephonyManager;
    private static LTESignal singleton = null;

    public LTESignal(Context context) {
        if (singleton != null) return;
        this.mContext = context;
        TelephonyManager mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        singleton = this;
//        List<CellInfo> cellInfoList = mTelephonyManager.getAllCellInfo();
//        for (CellInfo cellInfo : cellInfoList) {
//            if (cellInfo instanceof CellInfoLte) {
//                CellSignalStrengthLte cellSignalStrengthLte = ((CellInfoLte) cellInfo).getCellSignalStrength();
//                Log.i("666", "DBm\t" + cellSignalStrengthLte.getDbm());
//                Log.i("666", "level\t" + cellSignalStrengthLte.getLevel());
//                Log.i("666", "Cqi\t" + cellSignalStrengthLte.getCqi());
//            }
//        }
    }

    public static LTESignal getInstance() {
        return singleton;
    }

    public List<CellInfo> getAllCellInfo() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return null;
        }
        return mTelephonyManager.getAllCellInfo();
    }

    public int getLteDbm(CellSignalStrengthLte cellSignalStrengthLte){
        return cellSignalStrengthLte.getDbm();
    }
}
