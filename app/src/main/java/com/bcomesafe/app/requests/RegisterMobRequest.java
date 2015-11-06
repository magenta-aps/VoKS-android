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

public class RegisterMobRequest extends Request {

    // Debugging
    private static final boolean D = true;
    private static final String TAG = RegisterMobRequest.class.getSimpleName();

    public RegisterMobRequest(String device_id, String gcm_id, String mac_address, String language) {
        super("/register-device");
        log("Creating RegisterMobRequest");
        log("device_id=" + device_id);
        log("gcm_id=" + gcm_id);
        log("mac_address=" + mac_address);
        log("language=" + language);
        addParameter(Constants.REQUEST_PARAM_DEVICE_TYPE, Constants.REQUEST_PARAM_ANDROID);
        addParameter(Constants.REQUEST_PARAM_DEVICE_ID, device_id);
        addParameter(Constants.REQUEST_PARAM_GCM_ID, gcm_id);
        addParameter(Constants.REQUEST_PARAM_DEVICE_MAC, mac_address);
        addParameter(Constants.REQUEST_PARAM_LANGUAGE, language);
        setMethod(REQUEST_METHOD_POST);
        setPostParametersType(POST_PARAMETERS_URL);
        setShouldOverrideBaseURL(false);
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
