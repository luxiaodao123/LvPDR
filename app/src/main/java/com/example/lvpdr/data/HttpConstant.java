package com.example.lvpdr.data;

public class HttpConstant {
    public static final boolean POWER_CONSUMPTION_OPTIMIZATION = false;

    public static final String URL = "http://192.168.31.175:9900";
    public static final String CONTROL_SERVER_HOST = "118.89.111.14";
    public static final int CONTROL_SERVER_PORT = 31337;

    public static final byte DEVICE_BINDING_MASTER_TYPE_EMPLOYEE = 0x01;
    public static final byte DEVICE_BINDING_REQ_TYPE_BIND = 0x01;
    public static final byte DEVICE_BINDING_REQ_TYPE_QUERY = 0x02;
    public static final byte DEVICE_BINDING_STATUS_UNBIND = 0x00;
    public static final byte DEVICE_BINDING_STATUS_UNDER_APPROVAL = 0x01;
    public static final byte DEVICE_BINDING_STATUS_BOND = 0x02;
    public static final byte DEVICE_BINDING_STATUS_RETRY = (byte) 0xFF;

    public static final byte[] LOCATE_REPORT_MAC = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    public static final int EPO_LAST_STORED_TIMESTAMP = 0;
    public static final int EPO_LAST_USED_TIMESTAMP = 0;
    public static final int DEVICE_INFO_HW_VER = 0;
    public static final int DEVICE_INFO_SD_VER = 0;
    public static final int DEVICE_INFO_BOOT_VER = 0;
    public static final int DEVICE_INFO_AGNSS_TIMESLOT = 0;
}