package com.example.lvpdr.data.cache;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisClient {

    private static RedisClient singleton = null;
    private JedisPool pool = null;

    public RedisClient(){
        if(singleton== null){
            pool = new JedisPool("47.117.168.13", 16379);
            singleton = this;
        }
    }

    public static RedisClient getInstance(){
        return singleton;
    }

    public Jedis getPool(){
        try {
            return pool.getResource();
        }catch (Exception e){
            return null;
        }
    }

}
