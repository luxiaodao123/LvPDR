package com.example.lvpdr;

import android.os.AsyncTask;
import android.util.Log;

import com.example.lvpdr.ui.setting.SettingFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

public class HTTPRequest extends AsyncTask<String, Void, String> {
    private static final String TAG = "HTTPRequest";
    private static JSONArray EmployeeInfo = null;
    private static JSONArray VehicleInfo = null;

    @Override
    protected String doInBackground(String... params) {
        try {
            String urlStr = params[0];
            String httpType = params[1];

            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            if(httpType == "GET"){
                // Log the server response code
                int responseCode = connection.getResponseCode();
                Log.i(TAG, "Server responded with: " + responseCode);

                // And if the code was HTTP_OK then parse the contents
                if (responseCode == HttpURLConnection.HTTP_OK) {

                    // Convert request content to string
                    InputStream is = connection.getInputStream();
                    String content = convertInputStream(is, "UTF-8");
                    is.close();

                    return content;
                }
            } else if (httpType == "POST"){
                String jsonParam = params[2];

                connection.setRequestMethod(httpType);
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                connection.setRequestProperty("Accept", "application/json;charset=utf-8");
                OutputStream out = connection.getOutputStream();
                out.write(jsonParam.getBytes(StandardCharsets.UTF_8));
                out.flush();
                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.i(TAG, "Server responded with: " + responseCode);

                // And if the code was HTTP_OK then parse the contents
                if (responseCode == HttpURLConnection.HTTP_OK) {

                    // Convert request content to string
                    InputStream is = connection.getInputStream();
                    String content = convertInputStream(is, "UTF-8");
                    is.close();

                    return content;
                }
            }else {}




        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String convertInputStream(InputStream is, String encoding) {
        Scanner scanner = new Scanner(is, encoding).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        //Do anything with response..
        try {
            if(result == null) return;
            JSONObject json = new JSONObject(result);
            if(json.has("EmployeeInfo") == true) {
                EmployeeInfo = (JSONArray) json.get("EmployeeInfo");
                SettingFragment.setBaseInfoLoaded(true);
                if (EmployeeInfo.length() != 0)  {
                    for (int i = 0; i < EmployeeInfo.length(); i++) {
                        JSONObject employee = (JSONObject) EmployeeInfo.get(i);
                        if(employee.get("cid").equals(SettingFragment.mAndroidId)) {
                            SettingFragment.setUsrName((String) employee.get("name"));
                            SettingFragment.setBinding(true);
                            break;
                        }
                    }
                }
//
            } else if (json.has("VehicleInfo") == true) {
                VehicleInfo = (JSONArray) json.get("VehicleInfo");
                ArrayList<String> list = new ArrayList<>();
                if (VehicleInfo.length() != 0)  {
                    for (int i = 0; i < VehicleInfo.length(); i++) {
                        JSONObject vehicle = (JSONObject) VehicleInfo.get(i);
                        list.add((String) vehicle.get("license"));
                        if(vehicle.get("cid").equals(SettingFragment.mAndroidId)) {
                            SettingFragment.setVehicleLicense((String) vehicle.get("license"));
                            SettingFragment.setBinding(true);
                            break;
                        }
                    }
                    SettingFragment.setVehicleLicenseArr(list);
                }
            } else {

            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    static public JSONArray getEmployeeInfo() {
        return EmployeeInfo;
    }

    static public JSONArray getVehicleInfo() {
        return VehicleInfo;
    }
}
