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
import com.bcomesafe.app.utils.RemoteLogUtils;

public class GotItRequest extends Request {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = GotItRequest.class.getSimpleName();

    public GotItRequest(int notificationId) {
        super("/got-it");
        log("Creating GotItRequest");
        addParameter(Constants.REQUEST_PARAM_DEVICE_ID, AppUser.get().getDeviceUID());
        addParameter(Constants.REQUEST_PARAM_NOTIFICATION_ID, Integer.toString(notificationId));
        setMethod(REQUEST_METHOD_POST);
        setPostParametersType(POST_PARAMETERS_URL);
        setShouldOverrideBaseURL(true);
    }

    private void log(@SuppressWarnings("SameParameterValue") String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}