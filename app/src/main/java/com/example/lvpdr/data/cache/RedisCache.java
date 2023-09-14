package com.example.lvpdr.data.cache;

import android.util.Log;

import com.example.lvpdr.data.LocationData;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.turf.TurfMeasurement;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class RedisCache {
    private static final String TAG = "RedisClient";
    private static ArrayList<String> strCache = new ArrayList<>();
    private static ArrayList<LocationData.MagneticData> objCache = new ArrayList<>();
    private static List<Point> ptList= new ArrayList<>();
    private static List<Double> magList = new ArrayList<>(); // 升序
    private static double[] magArr; // 升序
    private static final double magRange = 0.5;// 磁场强度误差范围，需要根据实际情况去决定
    private static final double slopeRange = 0.5;// 磁场变化斜率误差范围，需要根据实际情况去决定
    private static List<LineString> mEntranceArea = new ArrayList<>();//入口区域

    private static void _merge(double arr[], int l, int m, int r, List<Double> mag, List<Point> point) {
        int n1 = m - l + 1;
        int n2 = r - m;

        double L[] = new double[n1];
        double R[] = new double[n2];
        List<Point> LP = new ArrayList<>(point.subList(l, l + n1));
        List<Double> LM = new ArrayList<>(mag.subList(l, l + n1));
        List<Point> RP = new ArrayList<>(point.subList(m + 1, m + 1+ n2));
        List<Double> RM = new ArrayList<>(mag.subList(m + 1, m + 1 + n2));;


        for (int i = 0; i < n1; ++i)
            L[i] = arr[l + i];
        for (int j = 0; j < n2; ++j)
            R[j] = arr[m + 1 + j];

        int i = 0, j = 0;

        int k = l;
        while (i < n1 && j < n2) {
            if (L[i] <= R[j]) {
                arr[k] = L[i];
                mag.set(k, LM.get(i));
                point.set(k, LP.get(i));
                i++;
            }
            else {
                arr[k] = R[j];
                mag.set(k, RM.get(j));
                point.set(k, RP.get(j));
                j++;
            }
            k++;
        }

        while (i < n1) {
            arr[k] = L[i];
            mag.set(k, LM.get(i));
            point.set(k, LP.get(i));
            i++;
            k++;
        }

        while (j < n2) {
            arr[k] = R[j];
            mag.set(k, RM.get(j));
            point.set(k, RP.get(j));
            j++;
            k++;
        }
    }

    private static void _sort(double arr[], int l, int r, List<Double> l1, List<Point> l2) {
        if (l < r) {
            int m = (l + r) / 2;

            _sort(arr, l, m, l1, l2);
            _sort(arr, m + 1, r, l1, l2);

            _merge(arr, l, m, r, l1, l2);
        }
    }

    public static void _sort(){
        _sort(magArr, 0, magArr.length -1, magList, ptList);
    }

    static public void setCache(ArrayList<String> data) throws InvalidProtocolBufferException {
        strCache = data;
        magArr = new double[data.size()];
        int i = 0;
        for(String s: data){
            LocationData.MagneticData obj = LocationData.MagneticData.parseFrom(Base64.getDecoder().decode(s));
            double mag = obj.getMagnitude();
            Point pt = Point.fromLngLat(obj.getLongitude(), obj.getLatitude());
            magArr[i++] = mag;
            magList.add(mag);
            ptList.add(pt);
            objCache.add(obj);
        }
        _sort();
    }

    static public void setCache(String data){
        //Todo:
    }

    static public ArrayList<String> getStrCache(){
        return strCache;
    }

    static public ArrayList<LocationData.MagneticData> getObjCache() {
        return objCache;
    }

    static public List<Point> getPtList() {
        return ptList;
    }

    //Todo: add zoneID
    static public List<Point> getPtListByRange(double mag){
        if(magList.size() == 0){
            Log.w(TAG, "地磁数据为空");
            return null;
        }

        if((mag > magList.get(magList.size() - 1) + magRange) ||
                (mag < magList.get(0) - magRange))
            return null;

        int start = 0;
        int end = magList.size();

        if(mag < magList.get(magList.size() - 1) + magRange && mag > magList.get(magList.size() - 1)){
            double thred = mag - magRange;
            for(int i = magList.size() - 1; i > 0; i -= 2){
                if(magList.get(i) < thred){
                    if(i == magList.size() - 1) return ptList.subList(i, end);
                    else if (magList.get(i + 1) < thred) {
                        return ptList.subList(i + 2, end);
                    } else if (magList.get(i + 1) >= thred) {
                        return ptList.subList(i + 1, end);
                    }
                    else continue;
                }
            }
            //warning: 阈值设置过大
            Log.w(TAG, "阈值设置过大");
            return ptList.subList(start, end);
        }

        if(mag > magList.get(0) - magRange && mag < magList.get(0)){
            double thred = mag + magRange;
            for(int i = 0; i < magList.size() - 1; i += 2){
                if(magList.get(i) > thred){
                    if(i == 0) return ptList.subList(start, 1);
                    else if (magList.get(i - 1) > thred) {
                        return ptList.subList(start, i - 1);
                    } else if (magList.get(i - 1) <= thred) {
                        return ptList.subList(start, i);
                    }
                    else continue;
                }
            }

            //warning: 阈值设置过大
            Log.w(TAG, "阈值设置过大");
            return ptList.subList(start, end);
        }


        for(int i = 0; i < magList.size() - 1; i++){
            double topThred = mag + magRange;
            double bottomThred = mag - magRange;
            if(magList.get(i)  >= bottomThred){
                start = i;
                for(int j = i + 1; j < magList.size() - 1; j++){
                    if(magList.get(j) > topThred){
                        end = j;
                        return ptList.subList(start, end);
                    }
                }
                return ptList.subList(start, end);
            }
        }
        return ptList.subList(start, end);
    }

    //Todo: add zoneID
    //slope: d(mag)/ d(len)
    static public List<Point> getPtListBySlope(double slope){
        if(objCache.size() == 0){
            Log.w(TAG, "地磁数据为空");
            return null;
        }
        List<Point> res = new ArrayList<>();
        for(int i = 0; i < objCache.size() - 1; i++){
            double dl = TurfMeasurement.distance(
                    Point.fromLngLat(objCache.get(i + 1).getLongitude(), objCache.get(i + 1).getLatitude()),
                    Point.fromLngLat(objCache.get(i).getLongitude(), objCache.get(i).getLatitude())
            ) * 1000;
            double dm = objCache.get(i + 1).getMagnitude()  - objCache.get(i).getMagnitude();
            if(Math.abs(dm / dl - slope) < slopeRange && dm * slope > 0){
                res.add(Point.fromLngLat(objCache.get(i + 1).getLongitude(), objCache.get(i + 1).getLatitude()));
            }
        }
        return res;
    }

    static public List<LineString> getEntranceArea(){
        return mEntranceArea;
    }
}
