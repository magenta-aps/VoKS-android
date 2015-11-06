/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.activities;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import com.bcomesafe.app.AppContext;
import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.DefaultParameters;
import com.bcomesafe.app.R;
import com.bcomesafe.app.gcm.GotItService;
import com.bcomesafe.app.objects.GCMMessageObject;
import com.bcomesafe.app.requests.RegisterMobRequest;
import com.bcomesafe.app.utils.FontsUtils;
import com.bcomesafe.app.utils.RandomString;
import com.bcomesafe.app.utils.RemoteLogUtils;
import com.bcomesafe.app.utils.Utils;
import com.bcomesafe.app.utils.WifiBroadcastReceiver;
import com.bcomesafe.app.widgets.SquareButton;

/**
 * AlarmActivity - responsible for checking device UID, registration to shelter, GCM registration,
 * alarm starting
 */
public class AlarmActivity extends Activity implements OnClickListener, WifiBroadcastReceiver.WifiStateReceiverListener {

    // Debugging
    private static final boolean D = true;
    private static final String TAG = AlarmActivity.class.getSimpleName();

    // Constants
    private static final boolean SHELTER_REGISTRATION_REQUIRED_EVERYTIME = true;
    private static final long WORKER_ASYNC_TASK_LAUNCH_INTERVAL = 3000L;

    // View members
    private LinearLayout llAlarm;
    private SquareButton sbStartAlarm;
    private RelativeLayout rlCancelAlarm;
    private RelativeLayout rlMessage;
    private TextView tvMessage, tvCancelAlarm;

    // Request ids
    private long mRegisterMobRequestId = Constants.INVALID_LONG_ID;

    // Variables
    private GoogleCloudMessaging mGCM;
    private boolean mActivityStarted = false;

    // WiFi state broadcast receiver
    private WifiBroadcastReceiver mWifiStateBroadcastReceiver;

    // Registration checkers
    private boolean mGCMRegistrationNeeded = false;
    private boolean mShelterRegistrationNeeded = false;
    private boolean mShelterRegistrationOK = false;
    // WorkerAsyncTask
    private WorkerAsyncTask mWorkerAsyncTask = null;
    private Handler mWorkerAsyncTaskHandler;

    // Signal for finished actions, needed for tests
    private final CountDownLatch mSignal = new CountDownLatch(1);
    private String mSignalMessage;

    /**
     * Broadcast receiver for request completed actions
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("onReceive()");
            if (intent.getAction().equals(Constants.REQUEST_COMPLETED_ACTION)) {
                log("onReceive() request completed action");
                if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mRegisterMobRequestId) {
                    handleRegisterMobRequestResult(mRegisterMobRequestId);
                }
            }
        }
    };

    /**
     * Broadcast receiver for volume changes
     */
    private final BroadcastReceiver mVolumeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("onReceive() mVolumeBroadcastReceiver");
            unregisterReceiver(mVolumeBroadcastReceiver);
            Utils.muteSounds(context, true);
            registerReceiver(mVolumeBroadcastReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RemoteLogUtils.getInstance().initializeLogging();

        log("onCreate()");
        log("Device UID=" + AppUser.get().getDeviceUID());
        log("MAC=" + AppUser.get().getDeviceMacAddress());
        log("GCM=" + AppUser.get().getGCMId());
        log("Registered app version=" + AppUser.get().getGCMRegisteredAppVersion());
        log("Registered os version=" + AppUser.get().getGCMRegisteredOSVersion());
        Bundle gotExtras = getIntent().getExtras();
        if (gotExtras == null || !gotExtras.containsKey(Constants.EXTRA_SKIP_INITIAL_MUTE)) {
            // Initial mute sounds
            Utils.muteSounds(this, false);
        }

        // Clear GCM messages queue if needed
        AppUser.get().clearGCMMessagesQueue();
        log("QueuedGCMMessages size=" + AppUser.get().getGCMMessagesQueueSize());

        setContentView(R.layout.activity_alarm);

        // Initialize views, set fonts and OnClickListeners
        initializeViews();
        setFonts();
        setOnClickListeners();

        // Check device UID
        checkDeviceUID();
        // Check device MAC address
        checkMACAddress();
        // Check device GCM registration ID
        checkGCMRegistrationId();

        // Register volume change broadcast receiver
        registerReceiver(mVolumeBroadcastReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));

        // Register WIFI state change broadcast receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mWifiStateBroadcastReceiver = new WifiBroadcastReceiver(true);
        mWifiStateBroadcastReceiver.addListener(this);
        registerReceiver(mWifiStateBroadcastReceiver, intentFilter);
    }

    /**
     * Shows loading views with message
     *
     * @param msg string
     */
    private void showLoading(String msg) {
        log("showLoading()");
        sbStartAlarm.setEnabled(false);
        llAlarm.setVisibility(View.GONE);
        rlMessage.setVisibility(View.VISIBLE);
        tvMessage.setText(msg);
    }

    /**
     * Shows retry views
     */
    private void showRetryViews(String msg) {
        log("showRetryViews()");
        sbStartAlarm.setEnabled(false);
        llAlarm.setVisibility(View.GONE);
        tvMessage.setText(msg);
        rlMessage.setVisibility(View.VISIBLE);
    }

    /**
     * Shows alarm views
     */
    private void showAlarmViews() {
        log("showAlarmViews()");
        sbStartAlarm.setEnabled(true);
        llAlarm.setVisibility(View.VISIBLE);
        rlMessage.setVisibility(View.GONE);
        tvMessage.setText(getString(R.string.none));
    }

    @Override
    protected void onResume() {
        log("onResume()");
        super.onResume();
        // Check existing requests results if needed
        if (mRegisterMobRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mRegisterMobRequestId)) {
            handleRegisterMobRequestResult(mRegisterMobRequestId);
        }
        // Register broadcast receiver for completed requests
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.REQUEST_COMPLETED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister broadcast receiver for completed requests
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            // Nothing to do
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel any works
        stopWorkerAsyncTaskLauncher();
        if (mWorkerAsyncTask != null) {
            mWorkerAsyncTask.cancel(true);
            mWorkerAsyncTask = null;
        }
        // Check GCM queue and if needed display notifications
        checkGCMMessagesQueue();
        // Unregister volume change broadcast receiver
        unregisterReceiver(mVolumeBroadcastReceiver);
        // Restore user volume settings
        restoreUserVolumeSettings();
        // Unregister wifi state broadcast receiver
        unregisterWiFiStateBroadcastReceiver();
        // Clear request store
        AppContext.get().getRequestManager().clearRequestStore();
        // Finalize remote logs
        if (!mActivityStarted) {
            RemoteLogUtils.getInstance().finalizeAndCleanUp();
        }
    }

    /**
     * Initializes views
     */
    private void initializeViews() {
        llAlarm = (LinearLayout) findViewById(R.id.ll_start_alarm);
        sbStartAlarm = (SquareButton) findViewById(R.id.sb_start_alarm);
        rlCancelAlarm = (RelativeLayout) findViewById(R.id.rl_cancel_alarm);
        tvCancelAlarm = (TextView) findViewById(R.id.tv_cancel_alarm);
        rlMessage = (RelativeLayout) findViewById(R.id.rl_message);
        tvMessage = (TextView) findViewById(R.id.tv_message);
    }

    /**
     * Sets fonts
     */
    private void setFonts() {
        sbStartAlarm.setTypeface(FontsUtils.getInstance().getTfBold());
        tvCancelAlarm.setTypeface(FontsUtils.getInstance().getTfBold());
        tvMessage.setTypeface(FontsUtils.getInstance().getTfBold());
    }

    /**
     * Sets OnClickListeners
     */
    private void setOnClickListeners() {
        sbStartAlarm.setOnClickListener(this);
        rlCancelAlarm.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sb_start_alarm: // Start alarm button
                startMainActivity();
                break;
            case R.id.rl_cancel_alarm: // Cancel button
                finish();
                break;
        }
    }

    /**
     * Checks GCM registration ID
     */
    private void checkGCMRegistrationId() {
        log("checkGCMRegistrationId()");
        if (checkPlayServices()) {
            mGCM = GoogleCloudMessaging.getInstance(this);
            if (getRegistrationId(getApplicationContext()).equals(Constants.INVALID_STRING_ID)) {
                mGCMRegistrationNeeded = true;
                mShelterRegistrationNeeded = true;
            } else {
                log("GCM ID - OK");
                if (AppUser.get().getIsRegisteredOnShelter() && !mShelterRegistrationNeeded) {
                    log("Device registered to shelter");
                    // Everything is ok
                } else {
                    mShelterRegistrationNeeded = true;
                }
            }
        } else {
            log("No valid Google Play Services APK found.");
            mGCMRegistrationNeeded = false;
        }
    }

    /**
     * Starts main activity
     */
    private void startMainActivity() {
        mActivityStarted = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /**
     * Starts worker async task
     */
    private void startWorkerAsyncTask() {
        if (mWorkerAsyncTask == null && mRegisterMobRequestId == Constants.INVALID_LONG_ID) {
            log("Starting worker async task");
            mWorkerAsyncTask = new WorkerAsyncTask();
            mWorkerAsyncTask.execute();
        } else {
            log("Skipping starting worker async task, its already running");
        }
    }

    /**
     * Gets the current registration ID for application on GCM service.
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     * registration ID.
     */
    private String getRegistrationId(Context context) {
        String registrationId = AppUser.get().getGCMId();
        if (registrationId.equals(Constants.INVALID_STRING_ID)) {
            log("GCM registration ID not found.");
            return Constants.INVALID_STRING_ID;
        }
        // Check if app or os was updated; if so, it must clear the registration ID
        // since the existing registration ID is not guaranteed to work with
        // the new app or os version.
        int registeredAppVersion = AppUser.get().getGCMRegisteredAppVersion();
        int registeredOSVersion = AppUser.get().getGCMRegisteredOSVersion();
        int currentVersion = Utils.getCurrentAppVersion(context);
        int currentOSVersion = Utils.getCurrentOsVersion();
        if (registeredAppVersion != currentVersion || registeredOSVersion != currentOSVersion) {
            log("App version or OS version changed, its needed to register GCM id");
            return Constants.INVALID_STRING_ID;
        }
        return registrationId;
    }

    /**
     * Registers to shelter
     */
    private void registerToShelter() {
        log("registerToShelter()");
        mRegisterMobRequestId = AppContext.get().getRequestManager().makeRequest(new RegisterMobRequest(AppUser.get().getDeviceUID(), AppUser.get().getGCMId(), AppUser.get().getDeviceMacAddress(), Utils.getCurrentLanguageCode(this)));
    }

    /**
     * Stores the registration ID and app versionCode in the AppUser shared preferences
     *
     * @param gcmId GCM registration ID
     */
    private void storeRegistrationId(String gcmId) {
        AppUser.get().setGCMId(gcmId);
        AppUser.get().setGCMRegisteredAppVersion(Utils.getCurrentAppVersion(getApplicationContext()));
        AppUser.get().setGCMRegisteredOSVersion(Utils.getCurrentOsVersion());
    }

    /**
     * Check the device to make sure it has the Google Play Services APK.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        return resultCode == ConnectionResult.SUCCESS;
    }

    /**
     * Checks device UID
     */
    private void checkDeviceUID() {
        log("checkDeviceUID()");
        // If device UID does not exist
        if (AppUser.get().getDeviceUID().contentEquals(Constants.INVALID_STRING_ID)) {
            log("DeviceUID does not exist, generating new");
            // Generate device UID
            String deviceUID = generateDeviceUID();
            log("New deviceUID stored:" + deviceUID);
            AppUser.get().setDeviceUID(deviceUID);
            mShelterRegistrationNeeded = true;
        } else {
            log("DeviceUID exists:" + AppUser.get().getDeviceUID());
        }
    }

    /**
     * Generates device UID
     *
     * @return string - device UID
     */
    private String generateDeviceUID() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String deviceUID = telephonyManager.getDeviceId();
        log("DeviceUID:" + deviceUID);
        if (deviceUID == null) { // If we could not get device UID from telephony manager
            deviceUID = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            if (deviceUID == null || deviceUID.contentEquals(Constants.INVALID_STRING_ID)) {
                return System.currentTimeMillis() + new RandomString(16).nextString();
            } else {
                return deviceUID;
            }
        } else if (deviceUID.contentEquals(Constants.INVALID_STRING_ID)) {
            return System.currentTimeMillis() + new RandomString(16).nextString();
        } else {
            return deviceUID;
        }
    }

    /**
     * Checks device MAC address
     */
    private void checkMACAddress() {
        log("checkMACAddress()");
        // Get device MAC address
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wInfo = wifiManager.getConnectionInfo();
        String currentMacAddress = wInfo.getMacAddress();
        if (currentMacAddress == null || currentMacAddress.equals(Constants.INVALID_STRING_ID)) {
            log("Couldn't get device MAC address");
        } else {
            // If current device mac address is not equal to stored one
            if (!AppUser.get().getDeviceMacAddress().equals(currentMacAddress)) {
                log("Storing new device MAC address:" + currentMacAddress);
                AppUser.get().setDeviceMacAddress(currentMacAddress);
                // Force shelter registration
                mShelterRegistrationNeeded = true;
            } else {
                log("Device MAC address already stored:" + AppUser.get().getDeviceMacAddress());
            }
        }
    }

    /**
     * Handles register mob request result
     */
    private void handleRegisterMobRequestResult(long requestId) {
        log("handleRegisterMobRequestResult()");

        // Check request HTTP status code
        int httpStatusCode = AppContext.get().getRequestManager().getRequestStatusCode(requestId);
        log("HTTP status code:" + httpStatusCode);

        switch (httpStatusCode) {
            case Constants.HTTP_STATUS_INTERNAL_APP_ERROR:
            case Constants.HTTP_STATUS_BAD_REQUEST:
            case Constants.HTTP_STATUS_CODE_INTERNAL_SERVER_ERROR:
            case Constants.HTTP_STATUS_CODE_NOT_AVAILABLE:
            case Constants.HTTP_STATUS_CODE_NOT_FOUND:
                log("Got request error");
                showRetryViews(getString(R.string.check_internet_connection));

                // It's done
                mSignalMessage = "Register mob request error";
                break;
            case Constants.HTTP_STATUS_CODE_OK:
            case Constants.HTTP_STATUS_CODE_REDIRECT_1:
            case Constants.HTTP_STATUS_CODE_REDIRECT_2:
                // Continue
                break;
        }

        // If HTTP status is OK
        if (httpStatusCode == Constants.HTTP_STATUS_CODE_OK) {
            // Parse result
            String response = AppContext.get().getRequestManager().getRequestResult(requestId);
            AppContext.get().getRequestManager().clearRequestData(requestId);
            log("Got response:" + response);

            JSONObject responseJson = null;
            try {
                responseJson = new JSONObject(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (responseJson != null) {
                if (!responseJson.isNull(Constants.REQUEST_RESPONSE_PARAM_SUCCESS)) {
                    boolean success = false;
                    try {
                        success = responseJson.getBoolean(Constants.REQUEST_RESPONSE_PARAM_SUCCESS);
                    } catch (JSONException e) {
                        showRetryViews(getString(R.string.check_internet_connection));
                        // It's done
                        mSignalMessage = "Register mob request parse error, unable to parse param " + Constants.REQUEST_RESPONSE_PARAM_SUCCESS;
                    }

                    String shelterId = Constants.INVALID_STRING_ID;
                    String wsURL = Constants.INVALID_STRING_ID;
                    String apiURL = Constants.INVALID_STRING_ID;

                    if (success) {
                        // shelter_id
                        try {
                            shelterId = responseJson.getString(Constants.REQUEST_RESPONSE_PARAM_SHELTER_ID).trim();
                        } catch (JSONException je) {
                            log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_SHELTER_ID);
                        }
                        // ws_url
                        try {
                            wsURL = responseJson.getString(Constants.REQUEST_RESPONSE_PARAM_WS_URL).trim();
                        } catch (JSONException je) {
                            log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_WS_URL);
                        }
                        // api_url
                        try {
                            apiURL = responseJson.getString(Constants.REQUEST_RESPONSE_PARAM_API_URL).trim();
                        } catch (JSONException je) {
                            log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_API_URL);
                        }
                        // dev_mode
                        try {
                            boolean devMode = responseJson.getBoolean(Constants.REQUEST_RESPONSE_PARAM_DEV_MODE);
                            AppUser.get().setDevMode(devMode);
                        } catch (JSONException je) {
                            log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_DEV_MODE);
                        }
                        if (!shelterId.equals(Constants.INVALID_STRING_ID) && !wsURL.equals(Constants.INVALID_STRING_ID) && !apiURL.equals(Constants.INVALID_STRING_ID)) {
                            AppUser.get().setShelterID(shelterId);
                            AppUser.get().setWsURL(wsURL);
                            AppUser.get().setAPIURL(apiURL);
                            // Ok
                            AppUser.get().setIsRegisteredOnShelter(true);
                            mShelterRegistrationOK = true;
                            showAlarmViews();
                            stopWorkerAsyncTaskLauncher();
                        } else {
                            showRetryViews(getString(R.string.check_internet_connection));
                            // It's done
                            mSignalMessage = "Register mob request parse error: no shelterId, wsURL or apiURL";
                        }
                    } else {
                        // message
                        String message = null;
                        try {
                            message = responseJson.getString(Constants.REQUEST_RESPONSE_PARAM_MESSAGE).trim();
                        } catch (JSONException je) {
                            log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_MESSAGE);
                        }
                        if (message != null && !message.equals(Constants.INVALID_STRING_ID)) {
                            showRetryViews(message);
                        } else {
                            showRetryViews(getString(R.string.check_internet_connection));
                        }
                        // It's done
                        mSignalMessage = "Register mob request success == false";
                    }
                } else {
                    log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_SUCCESS);
                    showRetryViews(getString(R.string.check_internet_connection));
                    // It's done
                    mSignalMessage = "Register mob request parse error: no param " + Constants.REQUEST_RESPONSE_PARAM_SUCCESS;
                }
            } else {
                log("Unable to parse response JSONObject");
                showRetryViews(getString(R.string.check_internet_connection));
                // It's done
                mSignalMessage = "Register mob request parse error, unable to parse response JSONObject";
            }
        }

        mRegisterMobRequestId = Constants.INVALID_LONG_ID;

        // It's done
        mSignal.countDown();
    }

    /**
     * WorkerAsyncTask responsible for GCM registration and registration to shelter
     */
    private class WorkerAsyncTask extends AsyncTask<Void, Void, Boolean> {

        private String GCMMsg = Constants.INVALID_STRING_ID;
        private String GCMId = Constants.INVALID_STRING_ID;
        private boolean mShelterIsReachable = false;

        @Override
        protected void onPreExecute() {
            log("WorkerAsyncTask onPreExecute()");
            // Show loading views
            showLoading(getString(R.string.loading));
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            log("WorkerAsyncTask doInBackground()");
            mShelterIsReachable = Utils.isURLReachable(getApplicationContext(), DefaultParameters.getDefaultCheckURL(), 3);
            log("mShelterIsReachable=" + mShelterIsReachable);

            if (mShelterIsReachable) {
                if (mGCMRegistrationNeeded) {
                    try {
                        if (mGCM == null) {
                            mGCM = GoogleCloudMessaging.getInstance(getApplicationContext());
                        }

                        GCMId = mGCM.register(DefaultParameters.GCM_SENDER_ID);

                        GCMMsg = "Device registered, GCM registration ID=" + GCMId;

                        // Store GCM registration ID
                        if (!isCancelled()) {
                            storeRegistrationId(GCMId);
                        }
                    } catch (IOException ex) {
                        GCMMsg = "Error :" + ex.getMessage();
                    }
                    log("GCMMsg=" + GCMMsg);
                } else {
                    log("GCM registration is not needed");
                }
                //noinspection ConstantConditions,PointlessBooleanExpression
                if (mShelterRegistrationNeeded || SHELTER_REGISTRATION_REQUIRED_EVERYTIME) {
                    // Register to shelter
                    if (!isCancelled()) {
                        registerToShelter();
                        return true;
                    }
                } else {
                    if (!isCancelled()) {
                        mShelterRegistrationOK = true;
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            log("WorkerAsyncTask onPostExecute() result=" + result);
            if (!isCancelled()) {
                if (result) {
                    if (mShelterRegistrationOK) {
                        log("Shelter registration is OK, showing alarm views");
                        showAlarmViews();
                        stopWorkerAsyncTaskLauncher();

                        // It's done
                        mSignal.countDown();
                    } else {
                        log("Shelter registration is NOT OK, waiting for shelter registration to finish");
                        // Wait for shelter registration to finish
                    }
                } else {
                    showRetryViews(getString(R.string.check_internet_connection));

                    // It's done
                    mSignalMessage = "Shelter is not reachable";
                    mSignal.countDown();
                }
            } else {
                log("WorkerAsyncTask is cancelled");
            }

            log("onPostExecute() end");

            if (mWorkerAsyncTask != null) {
                mWorkerAsyncTask = null;
            }
        }
    }

    /**
     * Displays GCM notification
     *
     * @param gcmMessageObject GCMMessageObject
     */
    private void displayNotification(GCMMessageObject gcmMessageObject) {
        // Check if messages timestamp does not exceed threshold in milliseconds
        long currentTimestamp = System.currentTimeMillis();
        if (gcmMessageObject.getTimestamp() == Constants.INVALID_LONG_ID) {
            gcmMessageObject.setTimestamp(currentTimestamp);
        }
        if (gcmMessageObject.getTimestamp() >= (currentTimestamp - DefaultParameters.GCM_MESSAGE_THRESHOLD_MS) && gcmMessageObject.getTimestamp() <= (currentTimestamp + DefaultParameters.GCM_MESSAGE_THRESHOLD_MS)) {
            // If message is not empty
            if (!gcmMessageObject.getTitle().equals(Constants.INVALID_STRING_ID) && !gcmMessageObject.getContent().equals(Constants.INVALID_STRING_ID)
                    && gcmMessageObject.getId() != Constants.INVALID_LONG_ID) {
                // Show notification
                // Set notification ID
                int notificationId = gcmMessageObject.getId();
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
                        .setContentTitle(gcmMessageObject.getTitle())
                        .setContentText(gcmMessageObject.getContent());

                mBuilder.setContentIntent(pendingIntentGotIt);

                Notification notification;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    notification = mBuilder.build();
                    // Set custom layout
                    RemoteViews notificationView = new RemoteViews(getPackageName(), R.layout.gcm_message);
                    notificationView.setTextViewText(R.id.tv_gcm_message_title, gcmMessageObject.getTitle());
                    notificationView.setTextViewText(R.id.tv_gcm_message_content, gcmMessageObject.getContent());
                    // Got it click
                    notificationView.setOnClickPendingIntent(R.id.b_gcm_message_got_it, pendingIntentGotIt);
                    notification.contentView = notificationView;
                    notification.bigContentView = notificationView;
                } else {
                    mBuilder.setStyle(new NotificationCompat.BigTextStyle().setBigContentTitle(gcmMessageObject.getTitle()).bigText(gcmMessageObject.getContent()));
                    notification = mBuilder.build();
                }
                // Auto cancel
                notification.flags |= Notification.FLAG_AUTO_CANCEL;
                // Notify
                mNotificationManager.notify(notificationId, notification);
            } else {
                log("gcmMessage id, title or content is empty, ignoring message");
            }
        } else {
            log("gcmMessage outdated! timestamp=" + gcmMessageObject.getTimestamp() + " currentTimestamp=" + currentTimestamp + " thresholdMs=" + DefaultParameters.GCM_MESSAGE_THRESHOLD_MS + ", ignoring message");
        }
    }

    /**
     * Gets notification icon
     *
     * @return int resource id
     */
    private int getNotificationIcon() {
        boolean whiteIcon = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        return whiteIcon ? R.drawable.ic_launcher_white : R.drawable.ic_launcher;
    }

    /**
     * WifiBroadcastReceiver callback called when WiFi network is available
     */
    @Override
    public void onNetworkAvailable() {
        log("onNetworkAvailable()");
        startWorkerAsyncTaskLauncher();
    }

    /**
     * WifiBroadcastReceiver callback called when WiFi network is unavailable
     */
    @Override
    public void onNetworkUnavailable() {
        log("onNetworkUnavailable()");
        stopWorkerAsyncTaskLauncher();
        showRetryViews(getString(R.string.check_internet_connection));
    }

    /**
     * Logs out message
     *
     * @param msg String
     */
    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }

    /**
     * Checks GCM messages queue
     */
    private void checkGCMMessagesQueue() {
        int gcmQueuedMessagesCount = AppUser.get().getGCMMessagesQueueSize();
        if (gcmQueuedMessagesCount > 0) {
            for (int i = 0; i < gcmQueuedMessagesCount; i++) {
                displayNotification(AppUser.get().getGCMMessageFromQueue(i));
            }
        }
    }

    /**
     * Restores user volume settings if needed e.g. if MainActivity not started
     */
    private void restoreUserVolumeSettings() {
        if (!mActivityStarted) {
            Utils.unmuteSounds(this);
        }
    }

    /**
     * Unregister WiFi state broadcast receiver
     */
    private void unregisterWiFiStateBroadcastReceiver() {
        unregisterReceiver(mWifiStateBroadcastReceiver);
        mWifiStateBroadcastReceiver.removeListener(this);
        mWifiStateBroadcastReceiver.close();
        mWifiStateBroadcastReceiver = null;
    }

    /**
     * Starts WorkerAsyncTask launcher
     */
    private void startWorkerAsyncTaskLauncher() {
        log("startWorkerAsyncTaskLauncher()");
        if (mWorkerAsyncTaskHandler != null) {
            mWorkerAsyncTaskHandler.removeCallbacks(mWorkerAsyncTaskLauncher);
        }
        mWorkerAsyncTaskHandler = new Handler();
        mWorkerAsyncTaskLauncher.run();
    }

    /**
     * Stops WorkerAsyncTask launcher
     */
    private void stopWorkerAsyncTaskLauncher() {
        log("stopWorkerAsyncTaskLauncher()");
        if (mWorkerAsyncTaskHandler != null) {
            mWorkerAsyncTaskHandler.removeCallbacks(mWorkerAsyncTaskLauncher);
        }
        mWorkerAsyncTaskHandler = null;
    }

    // Worker async task launcher
    private final Runnable mWorkerAsyncTaskLauncher = new Runnable() {
        @Override
        public void run() {
            log("mWorkerAsyncTaskLauncher run()");
            startWorkerAsyncTask();
            mWorkerAsyncTaskHandler.postDelayed(mWorkerAsyncTaskLauncher, WORKER_ASYNC_TASK_LAUNCH_INTERVAL);
        }
    };

    /**
     * Gets signal
     *
     * @return CountDownLatch
     */
    public CountDownLatch getSignal() {
        return mSignal;
    }

    /**
     * Gets signal message
     *
     * @return String signalMessage
     */
    public String getSignalMessage() {
        return mSignalMessage;
    }
}