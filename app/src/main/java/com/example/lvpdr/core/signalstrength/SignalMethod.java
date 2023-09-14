package com.example.lvpdr.core.signalstrength;

import android.telephony.CellSignalStrengthLte;

public abstract class SignalMethod {

    private String name;
    Boolean isExcluded;

    public SignalMethod(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract double getLevel(CellSignalStrengthLte signalStrength) throws Exception;

    public void setExcluded(boolean isExcluded) {
        this.isExcluded = isExcluded;
    }

    public boolean isExcluded() {
        return isExcluded != null && isExcluded;
    }

}
