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

public class UpdateDeviceRequest extends Request {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = UpdateDeviceRequest.class.getSimpleName();

    public UpdateDeviceRequest(String device_id,
                               String userPhone,
                               String acceptedTac,
                               String skipPhone,
                               String phoneToken) {
        super("/update-device");
        log("Creating UpdateDeviceRequest");
        log("device_id=" + device_id);
        log("userPhone=" + userPhone);
        log("acceptedTac=" + acceptedTac);
        log("skipPhone=" + skipPhone);
        log("phoneToken=" + phoneToken);
        addParameter(Constants.REQUEST_PARAM_DEVICE_ID, device_id);
        if (userPhone != null && userPhone.length() > 0) {
            addParameter(Constants.REQUEST_PARAM_USER_PHONE, userPhone);
        }
        if (acceptedTac != null && acceptedTac.length() > 0) {
            addParameter(Constants.REQUEST_PARAM_ACCEPTED_TAC, acceptedTac);
        }
        if (skipPhone != null && skipPhone.length() > 0) {
            addParameter(Constants.REQUEST_PARAM_SKIP_PHONE, skipPhone);
        }
        if (phoneToken != null && phoneToken.length() > 0) {
            addParameter(Constants.REQUEST_PARAM_PHONE_TOKEN, phoneToken);
        }
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
