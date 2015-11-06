/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.utils;

import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import com.bcomesafe.app.AppContext;
import com.bcomesafe.app.DefaultParameters;
import com.bcomesafe.app.objects.RemoteLogObject;
import com.bcomesafe.app.requests.SendLogsRequest;

/**
 * Remote log utils
 */
public class RemoteLogUtils {

    private static final boolean D = true;
    private static final String TAG = RemoteLogUtils.class.getSimpleName();

    private static final int LOGS_SIZE_TO_SEND = 100;

    private static RemoteLogUtils mInstance = null;

    private ArrayList<RemoteLogObject> mLogs;

    // It can not be local
    @SuppressWarnings("FieldCanBeLocal")
    private boolean mUsing = false;

    private RemoteLogUtils() {
        String msg = "Android SDK: " + Build.VERSION.SDK_INT + "; "
                + "Release: " + Build.VERSION.RELEASE + "; "
                + "Brand: " + Build.BRAND + "; "
                + "Device: " + Build.DEVICE + "; "
                + "Id: " + Build.ID + "; "
                + "Hardware: " + Build.HARDWARE + "; "
                + "Manufacturer: " + Build.MANUFACTURER + "; "
                + "Model: " + Build.MODEL + "; "
                + "Product: " + Build.PRODUCT;
        put(TAG, msg, System.currentTimeMillis());
    }

    public static RemoteLogUtils getInstance() {
        if (mInstance == null) {
            mInstance = new RemoteLogUtils();
        }
        return mInstance;
    }

    public void initializeLogging() {
        if (mLogs == null) {
            mLogs = new ArrayList<>();
        }
    }

    public void put(String tag, String msg, long timestamp) {
        if (DefaultParameters.REMOTE_LOGS_ENABLED) {
            if (!mUsing && mLogs != null) {
                mLogs.add(new RemoteLogObject(tag, msg, timestamp));
                checkSize();
            }
        }
    }

    private void checkSize() {
        log("checkSize() size=" + mLogs.size());
        if (mLogs.size() == LOGS_SIZE_TO_SEND) {
            sendLogs(false);
        }
    }

    public void finalizeAndCleanUp() {
        log("finalize()");
        if (mLogs != null && mLogs.size() > 0) {
            sendLogs(true);
        }
        mInstance = null;
    }

    private void sendLogs(boolean finalize) {
        log("sendLogs()");
        ArrayList<RemoteLogObject> logs = new ArrayList<>();
        mUsing = true;
        logs.addAll(mLogs);
        mLogs.clear();
        if (finalize) {
            mLogs = null;
        }
        mUsing = false;
        JSONArray logsJSONArray = new JSONArray();
        for (RemoteLogObject rlo : logs) {
            try {
                JSONObject logJSONObject = new JSONObject();
                logJSONObject.put("tag", rlo.getTag());
                logJSONObject.put("timestamp", rlo.getTimestamp());
                logJSONObject.put("message", rlo.getMessage());
                logsJSONArray.put(logJSONObject);
            } catch (Exception e) {
                log("Unable to put log object");
            }
        }
        AppContext.get().getRequestManager().makeRequest(new SendLogsRequest(logsJSONArray.toString()));
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
        }
    }
}
