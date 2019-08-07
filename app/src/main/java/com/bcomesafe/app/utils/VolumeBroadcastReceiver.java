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
import android.media.AudioManager;
import android.util.Log;

import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.activities.AlarmActivity;
import com.bcomesafe.app.activities.SplashActivity;

public class VolumeBroadcastReceiver extends BroadcastReceiver {

    // Constants
    public static final long TRESHOLD_BETWEEN_PRESSES = 1000L; // 1 second

    // Debugging
    private static final boolean D = false;
    private static final String TAG = VolumeBroadcastReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null && intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION")) {

            int newVolume = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", 0);
            int oldVolume = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", 0);

            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);

            String cmd = null;
            if (newVolume == 0) {
                cmd = "d";
            } else if (newVolume == maxVolume) {
                cmd = "u";
            } else if (newVolume > oldVolume) {
                cmd = "u";
            } else if (newVolume < oldVolume) {
                cmd = "d";
            }

            log("onReceive() oldVolume=" + oldVolume + "; newVolume=" + newVolume + ";" + "cmd=" + cmd);

            if (cmd != null) {
                AppUser.get().addAlarmCmd(cmd);
                if (AppUser.get().getAlarmCmd().endsWith("ddudd") || AppUser.get().getAlarmCmd().endsWith("dddduudddd")) {
                    log("onReceive() alarm cmd received!");
                    AppUser.get().clearAlarmCmd();

                    Intent splashIntent = new Intent(context.getApplicationContext(), SplashActivity.class);
                    splashIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    splashIntent.putExtra(Constants.EXTRA_AUTO_ALARM, true);
                    context.startActivity(splashIntent);
                }
            }
        }
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
