package com.example.lvpdr.data.cache;
import android.util.Log;

import java.util.Map;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntryID;


public class RedisClient {

    private static final String TAG = "RedisClient";
    private static RedisClient singleton = null;
    private Jedis jedis = null;

    public RedisClient(){
        if(singleton== null){
            jedis = new Jedis("luxiaodao.cn", 16379);
            singleton = this;
        }
    }

    public static RedisClient getInstance(){
        return singleton;
    }

    public Jedis getPool(){
        try {
            return jedis;
        }catch (Exception e){
            return null;
        }
    }

    public String ping(){
        return jedis.ping();
    }

    public void set(String key, String value){
        try {
            Thread thread = new Thread(){
                public void run(){
                    try{
                        jedis.set(key, value);
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

    public void xadd(String key, String ID, Map<String, String> map){
        try {
            Thread thread = new Thread(){
                public void run(){
                    try{
                        jedis.xadd(key, new StreamEntryID(ID), map);
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

    public void hset(String key, Map<String, String> hash){
        try {
            Thread thread = new Thread(){
                public void run(){
                    try{
                        jedis.hset(key,  hash);
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


    public String hget(String key, String field) throws InterruptedException {
        GHet hget = new GHet();
        Thread thread = new Thread(hget);
        hget.setParam(key, field);
        thread.start();
        thread.join();
        return hget.getValue();
    }

    public class GHet implements Runnable {
        private volatile String value;
        private String mKey;
        private String mField;

        public void setParam(String key, String field){
            mKey = key;
            mField = field;
        }

        @Override
        public void run() {
            value = jedis.hget(mKey,  mField);
        }

        public String getValue() {
            return value;
        }
    }

}
