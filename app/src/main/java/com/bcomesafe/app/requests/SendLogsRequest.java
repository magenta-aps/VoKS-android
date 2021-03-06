/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.requests;

import android.util.Log;

import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.Constants;

public class SendLogsRequest extends Request {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = SendLogsRequest.class.getSimpleName();

    public SendLogsRequest(String logJSONObject) {
        super("/logger");
        log("Creating SendLogsRequest");
        addParameter(Constants.REQUEST_PARAM_DEVICE_ID, AppUser.get().getDeviceUID());
        addParameter(Constants.REQUEST_PARAM_DEVICE_TYPE, Constants.REQUEST_PARAM_ANDROID);
        addParameter(Constants.REQUEST_PARAM_REMOTE_LOG, logJSONObject);
        setMethod(REQUEST_METHOD_POST);
        setPostParametersType(POST_PARAMETERS_BODY);
        setShouldOverrideBaseURL(true);
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
        }
    }
}
