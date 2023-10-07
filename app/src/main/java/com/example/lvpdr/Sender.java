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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

import javax.security.auth.callback.Callback;


public class Sender extends Thread{
    private static final String TAG = "Sender";
    private static Sender singleton = null;
    private Socket mSocket;
    private DatagramSocket mUdpSocket;
    private SocketAddress mSocketAddress;
    private String mHost;
    private int mPort;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
//    private ReadThread mReadThread;
    private boolean _isConnected = false;
    private boolean syncDataFlag = false;

    private boolean mRun = false;


    public Sender(String host, int port, boolean isTcp) throws SocketException {
        if(singleton != null)
            return;
        this.mHost = host;
        this.mPort = port;
        if(isTcp == false) {
            mUdpSocket = new DatagramSocket();
            _isConnected = true;
        }

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
            byte[] messageBody = new byte[msg.length];
//            System.arraycopy(len, 0, messageBody, 0, len.length);
            System.arraycopy(msg, 0, messageBody, 0, msg.length);
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

    public void send(byte[] data, boolean isTcp){
        try {
            Thread thread = new Thread(){
                public void run(){
                    if(isTcp == true) {
                        try {
                            Sender.getInstance().mOutputStream.write(data);
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    } else {
                        try {
                            DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName(mHost), mPort);
                            mUdpSocket.send(sendPacket);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
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

    private byte[] hdlcEncode(byte[] msg){
        int msgLen = msg.length;
        byte[] encodedFrame = new byte[msgLen * 2 + 2];
        encodedFrame[0] = 0x7e;
        int frameEnd = 1;
        int index = 0;

        for(byte bt: msg){
            if(bt == 0x7e){
                encodedFrame[frameEnd] = 0x7d;
                frameEnd += 1;
                encodedFrame[frameEnd] = 0x5e;
            }else if(bt == 0x7d){
                encodedFrame[frameEnd] = 0x7d;
                frameEnd += 1;
                encodedFrame[frameEnd] = 0x5d;
            }else {
                encodedFrame[frameEnd] = msg[index];
                frameEnd += 1;
            }
            index++;
        }

        encodedFrame[frameEnd] = 0x7e;
        frameEnd += 1;
        return Arrays.copyOfRange(encodedFrame, 0, frameEnd);
    }

    private void hdlcDecode(byte[] msg, Callback callback){
        int state = 0;
        int frameEnd = 0;
        int msgLen = msg.length;
        byte[] frame = new byte[msgLen];
        for( int srcIdx = 0; srcIdx < msgLen; srcIdx++){
            if(msg[srcIdx] == 0x7e){
                if(state == 0) {
                    state = 1;
                    continue;
                } else if (state == 1) {
                    try{
                        //callback.
                    }catch (Exception e){
                        Log.e("Sender", e.toString());
                    }
                    frame = new byte[msgLen];
                    state = 0;
                    frameEnd = 0;
                }
            } else if (msg[srcIdx] == 0x7d && srcIdx + 1 < msgLen) {
                if (msg[srcIdx + 1] == 0x5e){
                    frame[frameEnd] = 0x7e;
                    ++frameEnd;
                    ++srcIdx;
                } else if (msg[srcIdx + 1] == 0x5d) {
                    frame[frameEnd] = 0x7d;
                    ++frameEnd;
                    ++srcIdx;
                } else {
                    frame[frameEnd] = msg[srcIdx];
                    ++frameEnd;
                }
            }
        }
    }

    @Override
    public void run(){
        try{
            connect();
        }catch (Exception e){

        }
    }

}
