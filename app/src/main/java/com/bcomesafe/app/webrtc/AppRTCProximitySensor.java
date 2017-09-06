package com.bcomesafe.app.webrtc;

/*
 * libjingle
 * Copyright 2014 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

import com.bcomesafe.app.utils.RemoteLogUtils;
import com.bcomesafe.app.webrtcutils.AppRTCUtils;
import com.bcomesafe.app.webrtcutils.AppRTCUtils.NonThreadSafe;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

/**
 * AppRTCProximitySensor manages functions related to the proximity sensor.
 * On most device, the proximity sensor is implemented as a boolean-sensor.
 * It returns just two values "NEAR" or "FAR". Thresholding is done on the LUX
 * value i.e. the LUX value of the light sensor is compared with a threshold.
 * A LUX-value more than the threshold means the proximity sensor returns "FAR".
 * Anything less than the threshold value and the sensor  returns "NEAR".
 */
public class AppRTCProximitySensor implements SensorEventListener {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = AppRTCProximitySensor.class.getSimpleName();

    // This class should be created, started and stopped on one thread
    // (e.g. the main thread). We use |nonThreadSafe| to ensure that this is
    // the case. Only active when |DEBUG| is set to true.
    private final NonThreadSafe nonThreadSafe = new AppRTCUtils.NonThreadSafe();

    private final Runnable mOnSensorStateListener;
    private final SensorManager mSensorManager;
    private Sensor mProximitySensor = null;
    private boolean mLastStateReportIsNear = false;

    /**
     * Construction
     */
    public static AppRTCProximitySensor create(Context context, Runnable sensorStateListener) {
        return new AppRTCProximitySensor(context, sensorStateListener);
    }

    private AppRTCProximitySensor(Context context, Runnable sensorStateListener) {
        log("AppRTCProximitySensor" + AppRTCUtils.getThreadInfo());
        mOnSensorStateListener = sensorStateListener;
        mSensorManager = ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
    }

    /**
     * Activate the proximity sensor. Also do initialization if called for the
     * first time.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean start() {
        checkIfCalledOnValidThread();
        log("start" + AppRTCUtils.getThreadInfo());
        if (!initDefaultSensor()) {
            // Proximity sensor is not supported on this device.
            return false;
        }
        mSensorManager.registerListener(this, mProximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        return true;
    }

    /**
     * Deactivate the proximity sensor.
     */
    public void stop() {
        checkIfCalledOnValidThread();
        log("stop" + AppRTCUtils.getThreadInfo());
        if (mProximitySensor == null) {
            return;
        }
        mSensorManager.unregisterListener(this, mProximitySensor);
    }

    /**
     * Getter for last reported state. Set to true if "near" is reported.
     */
    public boolean sensorReportsNearState() {
        checkIfCalledOnValidThread();
        return mLastStateReportIsNear;
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        checkIfCalledOnValidThread();
        AppRTCUtils.assertIsTrue(sensor.getType() == Sensor.TYPE_PROXIMITY);
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            log("The values returned by this sensor cannot be trusted");
        }
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        checkIfCalledOnValidThread();
        AppRTCUtils.assertIsTrue(event.sensor.getType() == Sensor.TYPE_PROXIMITY);
        // As a best practice; do as little as possible within this method and
        // avoid blocking.
        float distanceInCentimeters = event.values[0];
        if (distanceInCentimeters < mProximitySensor.getMaximumRange()) {
            log("Proximity sensor => NEAR state");
            mLastStateReportIsNear = true;
        } else {
            log("Proximity sensor => FAR state");
            mLastStateReportIsNear = false;
        }

        // Report about new state to listening client. Client can then call
        // sensorReportsNearState() to query the current state (NEAR or FAR).
        if (mOnSensorStateListener != null) {
            mOnSensorStateListener.run();
        }

        log("onSensorChanged" + AppRTCUtils.getThreadInfo() + ": " + "accuracy=" + event.accuracy
                + ", timestamp=" + event.timestamp + ", distance=" + event.values[0]);
    }

    /**
     * Get default proximity sensor if it exists. Tablet devices (e.g. Nexus 7)
     * does not support this type of sensor and false will be returned in such
     * cases.
     */
    private boolean initDefaultSensor() {
        if (mProximitySensor != null) {
            return true;
        }
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (mProximitySensor == null) {
            return false;
        }
        logProximitySensorInfo();
        return true;
    }

    /**
     * Helper method for logging information about the proximity sensor.
     */
    private void logProximitySensorInfo() {
        if (mProximitySensor == null) {
            return;
        }
        StringBuilder info = new StringBuilder("Proximity sensor: ");
        info.append("name=");
        info.append(mProximitySensor.getName());
        info.append(", vendor: ");
        info.append(mProximitySensor.getVendor());
        info.append(", power: ");
        info.append(mProximitySensor.getPower());
        info.append(", resolution: ");
        info.append(mProximitySensor.getResolution());
        info.append(", max range: ");
        info.append(mProximitySensor.getMaximumRange());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            // Added in API level 9.
            info.append(", min delay: ");
            info.append(mProximitySensor.getMinDelay());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            // Added in API level 20.
            info.append(", type: ");
            info.append(mProximitySensor.getStringType());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Added in API level 21.
            info.append(", max delay: ");
            info.append(mProximitySensor.getMaxDelay());
            info.append(", reporting mode: ");
            info.append(mProximitySensor.getReportingMode());
            info.append(", isWakeUpSensor: ");
            info.append(mProximitySensor.isWakeUpSensor());
        }
        log(info.toString());
    }

    /**
     * Helper method for debugging purposes. Ensures that method is
     * called on same thread as this object was created on.
     */
    private void checkIfCalledOnValidThread() {
        if (!nonThreadSafe.calledOnValidThread()) {
            throw new IllegalStateException("Method is not called on valid thread");
        }
    }

    /**
     * Logs msg
     *
     * @param msg String
     */
    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
