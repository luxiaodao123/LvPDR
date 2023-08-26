package com.example.lvpdr;
import android.net.ipsec.ike.exceptions.IkeIOException;
import android.nfc.Tag;
import android.renderscript.ScriptGroup;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;


public class Sender extends Thread{
    private static final String TAG = "Sender";
    private static Sender singleton = null;
    private Socket mSocket;
    private SocketAddress mSocketAddress;
    private String mHost;
    private int mPort;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
//    private ReadThread mReadThread;
    private boolean _isConnected = false;
    private boolean syncDataFlag = false;

    private boolean mRun = false;


    public Sender(String host, int port){
        if(singleton != null)
            return;
        this.mHost = host;
        this.mPort = port;
        singleton = this;
    }

    public void connect(){
        try {
            this.mSocket = new Socket();
            this.mSocket.setKeepAlive(true);
            this.mSocketAddress = new InetSocketAddress(mHost, mPort);
            this.mSocket.connect(mSocketAddress, 10000);
            this.mOutputStream = mSocket.getOutputStream();
            this.mInputStream = mSocket.getInputStream();
            this._isConnected = true;

        } catch (IOException e){
            Log.e(TAG, "connect: " + e);
            Log.d(TAG, "connect: fail");
        }
    }

    public static Sender getInstance(){
        return singleton;
    }

    public boolean isConnected() {
        return this._isConnected;
    }

    public void send(String data){
        try {
            byte[] msg = data.getBytes();
            byte[] len = intToByteArray(msg.length);
            byte[] messageBody = new byte[len.length + msg.length];
            System.arraycopy(len, 0, messageBody, 0, len.length);
            System.arraycopy(msg, 0, messageBody, len.length, msg.length);
            Thread thread = new Thread(){
                public void run(){
                    try{
                        Sender.getInstance().mOutputStream.write(messageBody);
                    }catch (Exception e){
                        Log.e(TAG, e.toString());
                    }
                }
            };
            thread.start();
        }catch (Exception e){
            Log.e(TAG, e.toString());
        }
    }

    public void send(byte[] data){
        try {

            Thread thread = new Thread(){
                public void run(){
                    try{
                        Sender.getInstance().mOutputStream.write(data);
                    }catch (Exception e){
                        Log.e(TAG, e.toString());
                    }
                }
            };
            thread.start();
        }catch (Exception e){
            Log.e(TAG, e.toString());
        }
    }

    private byte[] intToByteArray(int i){
        byte[] result = new byte[4];

        result[0] = (byte) ((i >> 24) & 0xFF);
        result[1] = (byte) ((i >> 16) & 0xFF);
        result[2] = (byte) ((i >> 8) & 0xFF);
        result[3] = (byte) (i  & 0xFF);
        return result;
    }

    @Override
    public void run(){
        try{
            connect();
        }catch (Exception e){

        }
    }

}
