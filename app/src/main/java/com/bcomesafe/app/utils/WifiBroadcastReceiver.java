/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.utils;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.bcomesafe.app.DefaultParameters;

import java.util.ArrayList;
import java.util.List;

public class WifiBroadcastReceiver extends BroadcastReceiver {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = WifiBroadcastReceiver.class.getSimpleName();

    // States
    private static final int STATE_CONNECTED = 1;
    private static final int STATE_DISCONNECTED = 2;

    // Listeners
    private List<WifiStateReceiverListener> mListeners;
    // WiFi network state
    private int mState = STATE_DISCONNECTED;
    private final boolean mNotifyInitial;


    public interface WifiStateReceiverListener {
        void onNetworkAvailable();

        void onNetworkUnavailable();
    }

    public WifiBroadcastReceiver(boolean notifyInitial) {
        mListeners = new ArrayList<>();
        mState = STATE_DISCONNECTED;
        mNotifyInitial = notifyInitial;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        log("onReceive() action=" + intent.getAction());
        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            try {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI
                        || (DefaultParameters.WIFI_REQUIREMENT_DISABLED && networkInfo.getType() == ConnectivityManager.TYPE_MOBILE)) {
                    log("WIFI state=" + networkInfo.getState());
                    if (networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                        log("WIFI connected");
                        notifyStateToAll(STATE_CONNECTED);
                    } else {
                        log("WIFI other state");
                        notifyStateToAll(STATE_DISCONNECTED);
                    }
                } else {
                    log("Not WIFI ACTION");
                }
            } catch (Exception e) {
                // Nothing to do
            }
        } else if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            int extraWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            switch (extraWifiState) {
                case WifiManager.WIFI_STATE_DISABLED:
                    log("WIFI STATE DISABLED");
                    notifyStateToAll(STATE_DISCONNECTED);
                    break;
                case WifiManager.WIFI_STATE_DISABLING:
                    log("WIFI STATE DISABLING");
                    notifyStateToAll(STATE_DISCONNECTED);
                    break;
                case WifiManager.WIFI_STATE_ENABLED:
                    log("WIFI STATE ENABLED");
                    if (Utils.isWiFiConnected(context)) {
                     // NOTE this was needed because some devices does not trigger
                     // WiFi connected action on initial start with WiFi enabled and connected
                        log("WIFI connected from changed action state enabled");
                        notifyStateToAll(STATE_CONNECTED);
                    }
                    break;
                case WifiManager.WIFI_STATE_ENABLING:
                    log("WIFI STATE ENABLING");
                    break;
                case WifiManager.WIFI_STATE_UNKNOWN:
                    log("WIFI STATE UNKNOWN");
                    break;
            }
        }
    }

    /**
     * Notifies state to all listeners
     *
     * @param state int
     */
    private void notifyStateToAll(int state) {
        if (mState != state) {
            mState = state;
            for (WifiStateReceiverListener listener : mListeners) {
                notifyState(listener, state);
            }
        }
    }

    /**
     * Notifies state to listener
     *
     * @param listener WifiStateReceiverListener
     * @param state    int
     */
    private void notifyState(WifiStateReceiverListener listener, int state) {
        if (listener == null) {
            return;
        }

        if (state == STATE_CONNECTED) {
            listener.onNetworkAvailable();
        } else {
            listener.onNetworkUnavailable();
        }
    }

    /**
     * Adds listener
     *
     * @param listener WifiStateReceiverListener
     */
    public void addListener(WifiStateReceiverListener listener) {
        mListeners.add(listener);
        if (mNotifyInitial) {
            notifyState(listener, mState);
        }
    }

    /**
     * Removes listener
     *
     * @param listener WifiStateReceiverListener
     */
    public void removeListener(WifiStateReceiverListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Closes and cleans up
     */
    public void close() {
        if (mListeners != null) {
            mListeners.clear();
            mListeners = null;
        }
        mState = STATE_DISCONNECTED;
    }

    /**
     * Logs msg
     *
     * @param msg - String
     */
    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
