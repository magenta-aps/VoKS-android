/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.gcm;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import com.bcomesafe.app.AppContext;
import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.DefaultParameters;
import com.bcomesafe.app.R;
import com.bcomesafe.app.objects.GCMMessageObject;
import com.bcomesafe.app.utils.RemoteLogUtils;

public class GCMIntentService extends IntentService {

    // Debugging
    private static final boolean D = true;
    private static final String TAG = GCMIntentService.class.getSimpleName();

    public GCMIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        log("onHandleIntent()");
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);

        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {
            switch (messageType) {
                case GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR:
                    // Not needed at the moment
                    break;
                case GoogleCloudMessaging.MESSAGE_TYPE_DELETED:
                    // Not needed at the moment
                    break;
                case GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE:  // If it's a regular GCM message
                    // Post notification of received message
                    if (extras.containsKey(Constants.GCM_MESSAGE_OBJECT)) {
                        JSONObject gcmMessageJSON = null;
                        try {
                            gcmMessageJSON = new JSONObject(extras.getString(Constants.GCM_MESSAGE_OBJECT));
                            log("gcmMessageJSON=" + gcmMessageJSON);
                        } catch (JSONException e) {
                            log("Unable to get " + Constants.GCM_MESSAGE_OBJECT + " message JSON object");
                        }

                        if (gcmMessageJSON != null) {
                            sendNotification(gcmMessageJSON);
                        }
                    } else {
                        log("GCM message does not contain " + Constants.GCM_MESSAGE_OBJECT + " object");
                    }
                    break;
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GCMBroadcastReceiver.completeWakefulIntent(intent);
    }

    /**
     * Put the message into a notification and post it.
     *
     * @param gcmMessageJSON message JSONObject
     */
    private void sendNotification(JSONObject gcmMessageJSON) {
        GCMMessageObject gcmMessage = new GCMMessageObject();
        gcmMessage.fromJSON(gcmMessageJSON);

        // Check if messages timestamp does not exceed threshold in milliseconds
        long currentTimestamp = System.currentTimeMillis();
        if (gcmMessage.getTimestamp() == Constants.INVALID_LONG_ID) {
            gcmMessage.setTimestamp(currentTimestamp);
        }
        if (gcmMessage.getTimestamp() >= (currentTimestamp - DefaultParameters.GCM_MESSAGE_THRESHOLD_MS) && gcmMessage.getTimestamp() <= (currentTimestamp + DefaultParameters.GCM_MESSAGE_THRESHOLD_MS)) {
            // If message is not empty
            if (!gcmMessage.getTitle().equals(Constants.INVALID_STRING_ID) && !gcmMessage.getContent().equals(Constants.INVALID_STRING_ID)
                    && gcmMessage.getId() != Constants.INVALID_LONG_ID) {
                // If MainActivity is visible - show message as system message in chat
                // If MainActivity is not visible and alarm activity is visible:
                // queue message and later show in chat if main activity will be visible or show notifications
                // Otherwise show notification
                if (AppContext.get().getMainActivityIsVisible()) { // Show in chat
                    log("MainActivity is running, showing notification in chat");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Constants.ACTION_SHOW_GCM_MESSAGE_IN_CHAT).putExtra(Constants.GCM_MESSAGE_ID, gcmMessage.getId()).putExtra(Constants.GCM_MESSAGE_CONTENT, gcmMessage.getContent()));
                } else {
                    if (AppContext.get().getAlarmActivityIsVisible()) {
                        log("Adding GCM message to queue");
                        AppUser.get().addGCMMessageToQueue(gcmMessage.getId(), gcmMessage.getTitle(), gcmMessage.getContent(), gcmMessage.getTimestamp());
                        log("Messages in queue=" + AppUser.get().getGCMMessagesQueueSize());
                    } else { // Show notification
                        // Set notification ID
                        int notificationId = gcmMessage.getId();
                        log("Notification id=" + notificationId);

                        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

                        // Got it intent
                        Intent intentGotIt = new Intent(this, GotItService.class);
                        intentGotIt.setAction(Constants.ACTION_GOT_IT);
                        intentGotIt.putExtra(Constants.REQUEST_PARAM_NOTIFICATION_ID, notificationId);
                        PendingIntent pendingIntentGotIt = PendingIntent.getService(getBaseContext(), notificationId, intentGotIt, 0);

                        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                                .setPriority(NotificationCompat.PRIORITY_MAX)
                                .setSmallIcon(getNotificationIcon())
                                .setAutoCancel(true)
                                .setContentTitle(gcmMessage.getTitle())
                                .setContentText(gcmMessage.getContent());

                        mBuilder.setContentIntent(pendingIntentGotIt);

                        Notification notification;

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN /*&& !D*/) {
                            notification = mBuilder.build();
                            // Set custom layout
                            RemoteViews notificationView = new RemoteViews(getPackageName(), R.layout.gcm_message);
                            notificationView.setTextViewText(R.id.tv_gcm_message_title, gcmMessage.getTitle());
                            notificationView.setTextViewText(R.id.tv_gcm_message_content, gcmMessage.getContent());
                            // Got it click
                            notificationView.setOnClickPendingIntent(R.id.b_gcm_message_got_it, pendingIntentGotIt);
                            notification.contentView = notificationView;
                            notification.bigContentView = notificationView;
                        } else {
                            mBuilder.setStyle(new NotificationCompat.BigTextStyle().setBigContentTitle(gcmMessage.getTitle()).bigText(gcmMessage.getContent()));
                            notification = mBuilder.build();
                        }
                        // Auto cancel
                        notification.flags |= Notification.FLAG_AUTO_CANCEL;
                        // Notify
                        mNotificationManager.notify(notificationId, notification);
                    }
                }
            } else {
                log("gcmMessage id, title or content is empty, ignoring message");
            }
        } else {
            log("gcmMessage outdated! timestamp=" + gcmMessage.getTimestamp() + " currentTimestamp=" + currentTimestamp + " thresholdMs=" + DefaultParameters.GCM_MESSAGE_THRESHOLD_MS + ", ignoring message");
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

    private int getNotificationIcon() {
        boolean whiteIcon = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        return whiteIcon ? R.drawable.ic_launcher_white : R.drawable.ic_launcher;
    }
}
