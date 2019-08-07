/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app;

import com.bcomesafe.app.activities.AlarmActivity;
import com.bcomesafe.app.activities.MainActivity;
import com.bcomesafe.app.requests.HttpsTrustManager;
import com.bcomesafe.app.requests.RequestManager;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import android.util.Log;

/**
 * Application subclass
 */
public class AppContext extends MultiDexApplication implements Application.ActivityLifecycleCallbacks {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = AppContext.class.getSimpleName();

    // Application context
    private static AppContext mAppContext = null;

    // Request manager
    private RequestManager mRequestManager = null;
    private boolean mMainActivityIsVisible = false;
    private boolean mAlarmActivityIsVisible = false;

    @Override
    public void onCreate() {
        log("onCreate()");
        super.onCreate();
        mAppContext = this;
        mMainActivityIsVisible = false;
        mAlarmActivityIsVisible = false;
        registerActivityLifecycleCallbacks(this);
        if ((D || DefaultParameters.ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV) && DefaultParameters.SHOULD_USE_SSL) {
            HttpsTrustManager.allowAllSSL();
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static AppContext get() {
        return mAppContext;
    }

    /**
     * Returns instance of RequestManager
     *
     * @return RequestManager instance
     */
    public RequestManager getRequestManager() {
        if (mRequestManager == null) {
            mRequestManager = new RequestManager();
        }
        return mRequestManager;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        log("onActivityCreated()");
    }

    @Override
    public void onActivityStarted(Activity activity) {
        log("onActivityStarted()");
    }

    @Override
    public void onActivityResumed(Activity activity) {
        log("onActivityResumed()");
        if (activity instanceof MainActivity) {
            mMainActivityIsVisible = true;
        } else if (activity instanceof AlarmActivity) {
            mAlarmActivityIsVisible = true;
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        log("onActivityPaused()");
    }

    @Override
    public void onActivityStopped(Activity activity) {
        log("onActivityStopped()");
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        log("onActivitySaveInstanceState()");
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        log("onActivityDestroyed()");
        if (activity instanceof MainActivity) {
            mMainActivityIsVisible = false;
        } else if (activity instanceof AlarmActivity) {
            mAlarmActivityIsVisible = false;
        }
    }

    public boolean getMainActivityIsVisible() {
        return this.mMainActivityIsVisible;
    }

    public boolean getAlarmActivityIsVisible() {
        return this.mAlarmActivityIsVisible;
    }

    /**
     * Logs msg
     *
     * @param msg String
     */
    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
        }
    }
}