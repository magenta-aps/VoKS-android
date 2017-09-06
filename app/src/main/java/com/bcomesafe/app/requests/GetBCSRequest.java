/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.requests;

import android.util.Log;

import com.bcomesafe.app.utils.RemoteLogUtils;

public class GetBCSRequest extends Request {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = "GetBCSRequest";

    public GetBCSRequest() {
        super("/list");
        log("Creating GetBCSRequest");
        setMethod(REQUEST_METHOD_GET);
        setShouldOverrideBaseURL(false);
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
