/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.requests;

import android.util.Log;

import com.bcomesafe.app.Constants;
import com.bcomesafe.app.utils.RemoteLogUtils;

public class DeletePhoneRequest extends Request {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = DeletePhoneRequest.class.getSimpleName();

    public DeletePhoneRequest(String device_id) {
        super("/update-device");
        log("Creating DeletePhoneRequest");
        log("device_id=" + device_id);
        addParameter(Constants.REQUEST_PARAM_DEVICE_ID, device_id);
        addParameter(Constants.REQUEST_PARAM_USER_PHONE, "");
        setMethod(REQUEST_METHOD_POST);
        setPostParametersType(POST_PARAMETERS_URL);
        setShouldOverrideBaseURL(true);
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
