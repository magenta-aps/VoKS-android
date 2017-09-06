/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.gcm;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.bcomesafe.app.AppContext;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.requests.GotItRequest;
import com.bcomesafe.app.utils.RemoteLogUtils;

public class GotItService extends IntentService {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = GotItService.class.getSimpleName();

    public GotItService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        log("onHandleIntent()");
        if (intent.getAction().equals(Constants.ACTION_GOT_IT)) {
            log("Action is GotIt, doing work");
            Bundle extras = intent.getExtras();
            if (extras != null && extras.containsKey("notification_id")) {
                log("Got notification id=" + extras.getInt("notification_id", Constants.INVALID_INT_ID));
                int notificationId = extras.getInt(Constants.REQUEST_PARAM_NOTIFICATION_ID, Constants.INVALID_INT_ID);
                if (notificationId != Constants.INVALID_INT_ID) {
                    log("Canceling notification and making request");
                    // Cancel notification
                    NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancel(notificationId);
                    // Make got it request
                    AppContext.get().getRequestManager().makeRequest(new GotItRequest(notificationId));
                }
            } else {
                log("Extras does not contain notification id");
            }
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
