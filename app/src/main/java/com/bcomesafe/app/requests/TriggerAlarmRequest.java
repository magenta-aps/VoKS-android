/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.requests;

import android.util.Log;

import com.bcomesafe.app.Constants;

public class TriggerAlarmRequest extends Request {

    // Debugging
    private static final boolean D = true;
    private static final String TAG = TriggerAlarmRequest.class.getSimpleName();

    public TriggerAlarmRequest(String deviceId, int callPolice) {
        super("/trigger-alarm");
        if (D) {
            Log.e(TAG, "Creating TriggerAlarmRequest");
        }
        addParameter(Constants.REQUEST_PARAM_DEVICE_ID, deviceId);
        addParameter(Constants.REQUEST_PARAM_CALL_POLICE, Integer.toString(callPolice));
        setMethod(REQUEST_METHOD_POST);
        setPostParametersType(POST_PARAMETERS_URL);
        setShouldOverrideBaseURL(true);
    }
}
