/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.activities;

import android.annotation.SuppressLint;
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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.bcomesafe.app.BuildConfig;
import com.bcomesafe.app.adapters.BCSListAdapter;
import com.bcomesafe.app.objects.BCSObject;
import com.bcomesafe.app.requests.GetBCSRequest;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * AlarmActivity - responsible for checking device UID, registration to BCS, GCM registration,
 * alarm starting
 */
public class AlarmActivity extends Activity implements OnClickListener, WifiBroadcastReceiver.WifiStateReceiverListener, BCSListAdapter.BCSListAdapterClient, TextWatcher {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = AlarmActivity.class.getSimpleName();

    // View members
    private RelativeLayout rlAlarm;
    private SquareButton sbStartAlarm;
    private RelativeLayout rlCancelAlarm;
    private RelativeLayout rlMessage;
    private TextView tvMessage, tvCancelAlarm;
    private ImageView ivSettings;
    private RelativeLayout rlSettings;
    // Logs
    private ImageView ivLogs;
    private RelativeLayout rlLogs;
    private EditText etLogs;
    private TextView tvLogs;
    // Settings
    private TextView tvSelectBCS, tvRegister, tvLoading;
    private EditText etFilter, etName, etEmail;
    private RelativeLayout rlRegister;
    private RecyclerView rvBCS;

    // Request ids
    private long mGetBCSRequestId = Constants.INVALID_LONG_ID;
    private long mRegisterMobRequestId = Constants.INVALID_LONG_ID;

    // Variables
    private GoogleCloudMessaging mGCM;
    private boolean mActivityStarted = false;
    // BCS list
    private String mPreviousSelectedBCSId = null;
    private List<BCSObject> mBCSList = null;
    private List<BCSObject> mBCSListToShow = null;
    private BCSListAdapter mBCSListAdapter;

    // WiFi state broadcast receiver
    private WifiBroadcastReceiver mWifiStateBroadcastReceiver;

    // Registration checkers
    private boolean mGCMRegistrationNeeded = false;
    private boolean mBCSRegistrationNeeded = false;
    private boolean mBCSRegistrationOK = false;
    // WorkerAsyncTask
    private WorkerAsyncTask mWorkerAsyncTask = null;
    private Handler mWorkerAsyncTaskHandler;

    // Signal for finished actions, needed for tests
    private final CountDownLatch mSignal = new CountDownLatch(1);
    private String mSignalMessage;

    // Auto alarm
    private boolean mAutoAlarm = false;

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
                } else if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mGetBCSRequestId) {
                    handleGetBCSRequestResult(mGetBCSRequestId);
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

    private final BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals(RemoteLogUtils.ACTION_LOG)
                    && intent.getExtras().containsKey(RemoteLogUtils.EXTRA_LOG) && etLogs != null) {
                etLogs.setText(etLogs.getText() + "\n" + intent.getExtras().getString(RemoteLogUtils.EXTRA_LOG));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize views
        setContentView(R.layout.activity_alarm);
        initializeViews();
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalBroadcastReceiver, new IntentFilter(RemoteLogUtils.ACTION_LOG));

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
        if (gotExtras != null && gotExtras.containsKey(Constants.EXTRA_AUTO_ALARM)) {
            mAutoAlarm = gotExtras.getBoolean(Constants.EXTRA_AUTO_ALARM, false);
            if (mAutoAlarm) {
                // Register broadcast receiver for completed requests
                LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.REQUEST_COMPLETED_ACTION));
            }
        }

        // Clear GCM messages queue if needed
        AppUser.get().clearGCMMessagesQueue();
        log("QueuedGCMMessages size=" + AppUser.get().getGCMMessagesQueueSize());

        // Set fonts and OnClickListeners
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
        rlAlarm.setVisibility(View.GONE);
        rlMessage.setVisibility(View.VISIBLE);
        tvMessage.setText(msg);
        rlSettings.setVisibility(View.GONE);
    }

    /**
     * Shows retry views
     */
    private void showRetryViews(String msg) {
        log("showRetryViews()");
        sbStartAlarm.setEnabled(false);
        rlAlarm.setVisibility(View.GONE);
        tvMessage.setText(msg);
        rlMessage.setVisibility(View.VISIBLE);
        rlSettings.setVisibility(View.GONE);
    }

    /**
     * Shows alarm views
     */
    private void showAlarmViews() {
        log("showAlarmViews()");
        sbStartAlarm.setEnabled(true);
        rlAlarm.setVisibility(View.VISIBLE);
        tvMessage.setText(getString(R.string.none));
        rlSettings.setVisibility(View.GONE);
        if (mAutoAlarm) {
            startMainActivity();
        }
    }

    /**
     * Shows registration form views
     */
    private void showRegistrationFormViews() {
        log("showRegistrationFormViews()");
        etName.setText(AppUser.get().getUserName());
        etEmail.setText(AppUser.get().getUserEmail());
        mPreviousSelectedBCSId = AppUser.get().getBCSId();

        if (mBCSList == null || mBCSList.size() == 0) {
            tvLoading.setVisibility(View.VISIBLE);
            rvBCS.setAdapter(null);
            getBCS();
        } else {
            rvBCS.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
            mBCSListToShow = new ArrayList<>();
            mBCSListAdapter = new BCSListAdapter(mBCSListToShow, this);
            rvBCS.setAdapter(mBCSListAdapter);
            tvLoading.setVisibility(View.GONE);
        }

        etFilter.setText("");
        rlSettings.setVisibility(View.VISIBLE);
        rlSettings.requestFocus();
    }

    private void showLogsViews() {
        log("showLogsViews()");
        rlLogs.setVisibility(View.VISIBLE);
        rlLogs.requestFocus();
    }

    private void sendLogs() {
        log("sendLogs()");
        try {
            Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
            emailIntent.setType("plain/text");
            emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Logs");
            emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, etLogs.getText().toString());
            startActivity(Intent.createChooser(emailIntent, "Send logs"));
        } catch (Exception ignored) {
            Toast.makeText(this, "Unable to create send logs intent", Toast.LENGTH_SHORT).show();
        }
    }

    private void invalidateBCSDebug() {
        log("invalidateBCSDebug()");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (AppUser.get().getBCSDebug()) {
                    ivLogs.setVisibility(View.VISIBLE);
                } else {
                    ivLogs.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        log("onResume()");
        super.onResume();
        // Check existing requests results if needed
        if (mGetBCSRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mGetBCSRequestId)) {
            handleGetBCSRequestResult(mGetBCSRequestId);
        }
        if (mRegisterMobRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mRegisterMobRequestId)) {
            handleRegisterMobRequestResult(mRegisterMobRequestId);
        }
        // Register broadcast receiver for completed requests
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.REQUEST_COMPLETED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!mAutoAlarm) {
            // Unregister broadcast receiver for completed requests
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
            } catch (Exception e) {
                // Nothing to do
            }
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
        if (mAutoAlarm) {
            // Unregister broadcast receiver for completed requests
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
            } catch (Exception e) {
                // Nothing to do
            }
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalBroadcastReceiver);
    }

    /**
     * Initializes views
     */
    private void initializeViews() {
        rlAlarm = (RelativeLayout) findViewById(R.id.rl_start_alarm);
        sbStartAlarm = (SquareButton) findViewById(R.id.sb_start_alarm);
        rlCancelAlarm = (RelativeLayout) findViewById(R.id.rl_cancel_alarm);
        tvCancelAlarm = (TextView) findViewById(R.id.tv_cancel_alarm);
        rlMessage = (RelativeLayout) findViewById(R.id.rl_message);
        tvMessage = (TextView) findViewById(R.id.tv_message);
        ivSettings = (ImageView) findViewById(R.id.iv_settings);
        rlSettings = (RelativeLayout) findViewById(R.id.rl_settings);

        tvSelectBCS = (TextView) findViewById(R.id.tv_settings_select_bcs);
        tvRegister = (TextView) findViewById(R.id.tv_settings_register);
        tvLoading = (TextView) findViewById(R.id.tv_settings_loading);
        etFilter = (EditText) findViewById(R.id.et_settings_filter);
        etFilter.addTextChangedListener(this);
        etName = (EditText) findViewById(R.id.et_settings_name);
        etEmail = (EditText) findViewById(R.id.et_settings_email);
        rlRegister = (RelativeLayout) findViewById(R.id.rl_settings_register);
        rvBCS = (RecyclerView) findViewById(R.id.rv_settings_bcs);

        //noinspection ConstantConditions
        ivSettings.setVisibility(DefaultParameters.BCS_SETTINGS_FUNCTIONALITY_ENABLED ? View.VISIBLE : View.GONE);

        ivLogs = (ImageView) findViewById(R.id.iv_logs);
        rlLogs = (RelativeLayout) findViewById(R.id.rl_logs);
        etLogs = (EditText) findViewById(R.id.et_logs);
        tvLogs = (TextView) findViewById(R.id.tv_logs_send);
    }

    /**
     * Sets fonts
     */
    private void setFonts() {
        sbStartAlarm.setTypeface(FontsUtils.getInstance().getTfBold());
        tvCancelAlarm.setTypeface(FontsUtils.getInstance().getTfBold());
        tvMessage.setTypeface(FontsUtils.getInstance().getTfBold());

        tvSelectBCS.setTypeface(FontsUtils.getInstance().getTfBold());
        tvRegister.setTypeface(FontsUtils.getInstance().getTfBold());
        tvLoading.setTypeface(FontsUtils.getInstance().getTfBold());
        etFilter.setTypeface(FontsUtils.getInstance().getTfLight());
        etName.setTypeface(FontsUtils.getInstance().getTfLight());
        etEmail.setTypeface(FontsUtils.getInstance().getTfLight());

        etLogs.setTypeface(FontsUtils.getInstance().getTfLight());
        tvLogs.setTypeface(FontsUtils.getInstance().getTfBold());
    }

    /**
     * Sets OnClickListeners
     */
    private void setOnClickListeners() {
        sbStartAlarm.setOnClickListener(this);
        rlCancelAlarm.setOnClickListener(this);
        ivSettings.setOnClickListener(this);

        rlRegister.setOnClickListener(this);

        ivLogs.setOnClickListener(this);
        tvLogs.setOnClickListener(this);
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
            case R.id.iv_settings: // Settings button
                showRegistrationFormViews();
                break;
            case R.id.rl_settings_register: // Register button
                onClickButtonRegister();
                break;
            case R.id.iv_logs: // Logs button
                showLogsViews();
                break;
            case R.id.tv_logs_send: // Logs send button
                sendLogs();
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (DefaultParameters.BCS_SETTINGS_FUNCTIONALITY_ENABLED) {
            if (rlSettings.getVisibility() == View.VISIBLE) {
                String currentlySelectedBCSId = AppUser.get().getBCSId();
                String currentlySelectedBCSName = AppUser.get().getBCSName();
                if (currentlySelectedBCSId != null && currentlySelectedBCSId.length() > 0 &&
                        currentlySelectedBCSName != null && currentlySelectedBCSName.length() > 0) {
                    rlSettings.setVisibility(View.GONE);
                    if (!mBCSRegistrationOK ||
                            (mPreviousSelectedBCSId != null && mPreviousSelectedBCSId.length() > 0
                                    && !mPreviousSelectedBCSId.equals(currentlySelectedBCSId))) {
                        startWorkerAsyncTaskLauncher();
                    }
                    return;
                }
            }
        }
        if (rlLogs.getVisibility() == View.VISIBLE) {
            rlLogs.setVisibility(View.GONE);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onBCSSelected(int position) {
        if (mBCSListToShow != null && position < mBCSListToShow.size()) {
            log("onBCSSelected() id=" + mBCSListToShow.get(position).getBCSId());
            AppUser.get().setBCSDebug(mBCSListToShow.get(position).getDebug());
            invalidateBCSDebug();
            AppUser.get().setBCSId(mBCSListToShow.get(position).getBCSId());
            AppUser.get().setBCSName(mBCSListToShow.get(position).getBCSName());
            String bcsUrl = mBCSListToShow.get(position).getBCSUrl();
            if (bcsUrl != null && !bcsUrl.toLowerCase(Locale.getDefault()).contains(DefaultParameters.DEFAULT_API_URL_SUFFIX)) {
                bcsUrl += DefaultParameters.DEFAULT_API_URL_SUFFIX;
            }
            AppUser.get().setAPIURL(bcsUrl);
            AppUser.get().setPoliceNumber(mBCSListToShow.get(position).getPoliceNumber());
            if (mBCSListAdapter != null) {
                mBCSListAdapter.notifyDataSetChanged();
            }
        } else {
            log("onBCSSelected() error");
        }
    }

    private void onClickButtonRegister() {
        if (rlSettings.getVisibility() == View.VISIBLE) {
            String currentlySelectedBCSId = AppUser.get().getBCSId();
            String currentlySelectedBCSName = AppUser.get().getBCSName();
            if (currentlySelectedBCSId == null || currentlySelectedBCSId.length() == 0 &&
                    currentlySelectedBCSName == null || currentlySelectedBCSName.length() == 0) {
                Toast.makeText(this, getString(R.string.error_shelter_not_selected), Toast.LENGTH_SHORT).show();
                return;
            }

            String enteredName = etName.getText().toString().trim();
            if (enteredName.length() == 0) {
                Toast.makeText(this, getString(R.string.error_name_empty), Toast.LENGTH_SHORT).show();
                return;
            }

            String enteredEmail = etEmail.getText().toString().trim();
            if (enteredEmail.length() == 0) {
                Toast.makeText(this, getString(R.string.error_email_empty), Toast.LENGTH_SHORT).show();
                return;
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(enteredEmail).matches()) {
                Toast.makeText(this, getString(R.string.error_email_not_valid), Toast.LENGTH_SHORT).show();
                return;
            }

            View view = this.getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }

            AppUser.get().setUserName(enteredName);
            AppUser.get().setUserEmail(enteredEmail);

            rlSettings.setVisibility(View.GONE);
            mBCSRegistrationNeeded = true;
            mBCSRegistrationOK = false;
            startWorkerAsyncTaskLauncher();
        }
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        if (editable != null) {
            filterBCSToShow(editable.toString().trim());
        }
    }

    private void filterBCSToShow(String filter) {
        if (mBCSListToShow != null && mBCSList != null) {
            if (filter == null || filter.length() == 0) {
                mBCSListToShow.clear();
                mBCSListToShow.addAll(mBCSList);
                mBCSListAdapter.notifyDataSetChanged();
            } else {
                mBCSListToShow.clear();
                for (BCSObject bcsObject : mBCSList) {
                    if (bcsObject.getBCSName() != null
                            && bcsObject.getBCSName().toLowerCase(Locale.getDefault())
                            .contains(filter.toLowerCase(Locale.getDefault()))) {
                        mBCSListToShow.add(bcsObject);
                    }
                }
                mBCSListAdapter.notifyDataSetChanged();
            }
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
                mBCSRegistrationNeeded = true;
            } else {
                log("GCM ID - OK");
                if (AppUser.get().getIsRegisteredOnBCS() && !mBCSRegistrationNeeded) {
                    log("Device registered to BCS");
                    // Everything is ok
                } else {
                    mBCSRegistrationNeeded = true;
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
        if (mWorkerAsyncTask == null && mGetBCSRequestId == Constants.INVALID_LONG_ID && mRegisterMobRequestId == Constants.INVALID_LONG_ID) {
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
     * Gets BCS
     */
    private void getBCS() {
        log("getBCS()");
        mGetBCSRequestId = AppContext.get().getRequestManager().makeRequest(new GetBCSRequest());
    }

    /**
     * Registers to BCS
     */
    private void registerToBCS() {
        log("registerToBCS()");
        mRegisterMobRequestId = AppContext.get().getRequestManager().makeRequest(
                new RegisterMobRequest(AppUser.get().getDeviceUID(),
                        AppUser.get().getGCMId(),
                        AppUser.get().getDeviceMacAddress(),
                        Utils.getCurrentLanguageCode(this),
                        AppUser.get().getBCSId(),
                        AppUser.get().getUserName(),
                        AppUser.get().getUserEmail()
                )
        );
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
    @SuppressWarnings("deprecation")
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
            mBCSRegistrationNeeded = true;
        } else {
            log("DeviceUID exists:" + AppUser.get().getDeviceUID());
        }
    }

    /**
     * Generates device UID
     *
     * @return string - device UID
     */
    @SuppressLint("HardwareIds")
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
    @SuppressLint("HardwareIds")
    private void checkMACAddress() {
        log("checkMACAddress()");
        // Get device MAC address
        String currentMacAddress = null;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface nif : all) {
                    if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                    byte[] macBytes = nif.getHardwareAddress();
                    if (macBytes == null) {
                        break;
                    }

                    StringBuilder macStringBuilder = new StringBuilder();
                    for (byte b : macBytes) {
                        macStringBuilder.append(String.format("%02X:", b));
                    }

                    if (macStringBuilder.length() > 0) {
                        macStringBuilder.deleteCharAt(macStringBuilder.length() - 1);
                    }
                    currentMacAddress = macStringBuilder.toString();

                }
            } catch (Exception ignored) {

            }
        } else {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wInfo = wifiManager.getConnectionInfo();
            currentMacAddress = wInfo.getMacAddress();
        }

        if (currentMacAddress == null || currentMacAddress.equals(Constants.INVALID_STRING_ID)) {
            log("Couldn't get device MAC address");
        } else {
            // If current device mac address is not equal to stored one
            if (!AppUser.get().getDeviceMacAddress().equals(currentMacAddress)) {
                log("Storing new device MAC address:" + currentMacAddress);
                AppUser.get().setDeviceMacAddress(currentMacAddress);
                // Force BCS registration
                mBCSRegistrationNeeded = true;
            } else {
                log("Device MAC address already stored:" + AppUser.get().getDeviceMacAddress());
            }
        }
    }

    /**
     * Handles get BCS request result
     */
    private void handleGetBCSRequestResult(long requestId) {
        log("handleGetBCSRequestResult()");

        // Check request HTTP status code
        int httpStatusCode = AppContext.get().getRequestManager().getRequestStatusCode(requestId);

        log("handleGetBCSRequestResult() status code:" + httpStatusCode);

        switch (httpStatusCode) {
            case Constants.HTTP_STATUS_INTERNAL_APP_ERROR:
            case Constants.HTTP_STATUS_BAD_REQUEST:
            case Constants.HTTP_STATUS_CODE_INTERNAL_SERVER_ERROR:
            case Constants.HTTP_STATUS_CODE_NOT_AVAILABLE:
            case Constants.HTTP_STATUS_CODE_NOT_FOUND:
                log("handleGetBCSRequestResult() got request error");
                showRetryViews(getString(R.string.error_no_shelters));
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
            log("handleGetBCSRequestResult() got response:" + response);

            JSONArray responseJsonArray = null;
            try {
                responseJsonArray = new JSONArray(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (responseJsonArray != null && responseJsonArray.length() > 0) {
                // Parse BCS
                try {
                    mBCSList = new Gson().fromJson(responseJsonArray.toString(), new TypeToken<List<BCSObject>>() {
                    }.getType());
                } catch (Exception ignored) {

                }

                if (mBCSList != null && mBCSList.size() > 0) {
                    log("handleGetBCSRequestResult() BCS size=" + mBCSList.size());
                    if (DefaultParameters.BCS_SETTINGS_FUNCTIONALITY_ENABLED) {
                        if (mBCSList.size() == 1) {
                            AppUser.get().setBCSId(mBCSList.get(0).getBCSId());
                            AppUser.get().setBCSDebug(mBCSList.get(0).getDebug());
                            invalidateBCSDebug();
                            AppUser.get().setBCSName(mBCSList.get(0).getBCSName());
                            String bcsUrl = mBCSList.get(0).getBCSUrl();
                            if (bcsUrl != null && !bcsUrl.toLowerCase(Locale.getDefault()).contains(DefaultParameters.DEFAULT_API_URL_SUFFIX)) {
                                bcsUrl += DefaultParameters.DEFAULT_API_URL_SUFFIX;
                            }
                            AppUser.get().setAPIURL(bcsUrl);
                            AppUser.get().setPoliceNumber(mBCSList.get(0).getPoliceNumber());

                            if (mBCSRegistrationNeeded || DefaultParameters.BCS_REGISTRATION_REQUIRED_EVERY_TIME) {
                                // Register to BCS
                                registerToBCS();
                            } else {
                                mBCSRegistrationOK = true;

                                showAlarmViews();
                                stopWorkerAsyncTaskLauncher();
                            }
                        } else {
                            String currentlySelectedBCSId = AppUser.get().getBCSId();
                            String currentlySelectedBCSName = AppUser.get().getBCSName();
                            if (currentlySelectedBCSId != null && currentlySelectedBCSId.length() > 0 &&
                                    currentlySelectedBCSName != null && currentlySelectedBCSName.length() > 0) {
                                boolean selectedBCSExists = false;
                                for (BCSObject bcsObject : mBCSList) {
                                    if (bcsObject.getBCSId() != null && bcsObject.getBCSId().equals(currentlySelectedBCSId)) {
                                        selectedBCSExists = true;
                                        break;
                                    }
                                }

                                if (!selectedBCSExists) {
                                    AppUser.get().setBCSId(null);
                                    AppUser.get().setBCSDebug(false);
                                    invalidateBCSDebug();
                                    AppUser.get().setBCSName(null);
                                    AppUser.get().setPoliceNumber(null);
                                    AppUser.get().setAPIURL(Constants.INVALID_STRING_ID);
                                }
                            } else {
                                AppUser.get().setBCSId(null);
                                AppUser.get().setBCSDebug(false);
                                invalidateBCSDebug();
                                AppUser.get().setBCSName(null);
                                AppUser.get().setPoliceNumber(null);
                                AppUser.get().setAPIURL(Constants.INVALID_STRING_ID);
                            }
                            showRegistrationFormViews();
                            stopWorkerAsyncTaskLauncher();
                        }
                    } else {
                        // BCS will be checked in worker async task
                        AppUser.get().setBCSId(null);
                        AppUser.get().setBCSDebug(false);
                        invalidateBCSDebug();
                        AppUser.get().setBCSName(null);
                        AppUser.get().setPoliceNumber(null);
                        AppUser.get().setAPIURL(Constants.INVALID_STRING_ID);
                    }
                } else {
                    log("handleGetBCSsRequestResult() bad response json array");
                    AppUser.get().setBCSId(null);
                    AppUser.get().setBCSDebug(false);
                    invalidateBCSDebug();
                    AppUser.get().setBCSName(null);
                    AppUser.get().setPoliceNumber(null);
                    AppUser.get().setAPIURL(Constants.INVALID_STRING_ID);
                    showRetryViews(getString(R.string.error_no_shelters));
                }
            } else {
                log("handleGetBCSsRequestResult() bad response json array");
                AppUser.get().setBCSId(null);
                AppUser.get().setBCSDebug(false);
                invalidateBCSDebug();
                AppUser.get().setBCSName(null);
                AppUser.get().setPoliceNumber(null);
                AppUser.get().setAPIURL(Constants.INVALID_STRING_ID);
                showRetryViews(getString(R.string.error_no_shelters));
            }
        }

        mGetBCSRequestId = Constants.INVALID_LONG_ID;
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

                    String BCSId = Constants.INVALID_STRING_ID;
                    Boolean BCSDebug = null;
                    String wsURL = Constants.INVALID_STRING_ID;
                    String apiURL = Constants.INVALID_STRING_ID;
                    boolean renew = false;
                    boolean useGps = false;

                    if (success) {
                        // shelter_id
                        try {
                            BCSId = responseJson.getString(Constants.REQUEST_RESPONSE_PARAM_SHELTER_ID).trim();
                        } catch (JSONException je) {
                            log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_SHELTER_ID);
                        }
                        // debug
                        try {
                            BCSDebug = responseJson.getBoolean(Constants.REQUEST_PARAM_DEBUG);
                        } catch (JSONException je) {
                            log("Response does not have " + Constants.REQUEST_PARAM_DEBUG);
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
                        // renew
                        try {
                            renew = responseJson.getBoolean(Constants.REQUEST_RESPONSE_PARAM_RENEW);
                        } catch (JSONException je) {
                            log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_RENEW);
                        }
                        // use_gps
                        try {
                            useGps = responseJson.getBoolean(Constants.REQUEST_RESPONSE_PARAM_USE_GPS);
                        } catch (JSONException je) {
                            log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_USE_GPS);
                        }
                        if (renew) {
                            AppUser.get().setBCSId(null);
                            AppUser.get().setBCSDebug(false);
                            invalidateBCSDebug();
                            AppUser.get().setBCSName(null);
                            AppUser.get().setPoliceNumber(null);
                            AppUser.get().setAPIURL(Constants.INVALID_STRING_ID);
                            AppUser.get().setBCSUseGPS(false);
                            mBCSRegistrationNeeded = true;
                            mBCSList.clear();
                            mBCSListToShow.clear();
                            mBCSListAdapter.notifyDataSetChanged();
                            showRetryViews(getString(R.string.loading));
                        } else {
                            if (!BCSId.equals(Constants.INVALID_STRING_ID) && !wsURL.equals(Constants.INVALID_STRING_ID) && !apiURL.equals(Constants.INVALID_STRING_ID)) {
                                AppUser.get().setBCSId(BCSId);
                                AppUser.get().setBCSDebug(BCSDebug);
                                invalidateBCSDebug();
                                AppUser.get().setWsURL(wsURL);
                                AppUser.get().setAPIURL(apiURL);
                                AppUser.get().setBCSUseGPS(useGps);
                                // Ok
                                AppUser.get().setIsRegisteredOnBCS(true);
                                mBCSRegistrationOK = true;
                                showAlarmViews();
                                stopWorkerAsyncTaskLauncher();
                            } else {
                                showRetryViews(getString(R.string.check_internet_connection));
                                // It's done
                                mSignalMessage = "Register mob request parse error: no shelter_id (BCSId), wsURL or apiURL";
                            }
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
     * WorkerAsyncTask responsible for GCM registration, getting BCS and registration to BCS
     * if only one BCS got
     */
    private class WorkerAsyncTask extends AsyncTask<Void, Void, Boolean> {

        private String GCMMsg = Constants.INVALID_STRING_ID;
        private String GCMId = Constants.INVALID_STRING_ID;
        private boolean mBCSIsReachable = false;

        @Override
        protected void onPreExecute() {
            log("WorkerAsyncTask onPreExecute()");
            // Show loading views
            showLoading(getString(R.string.loading));
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            log("WorkerAsyncTask doInBackground()");
            //noinspection ConstantConditions
            mBCSIsReachable = DefaultParameters.WIFI_REQUIREMENT_DISABLED || Utils.isURLReachable(getApplicationContext(), DefaultParameters.getDefaultCheckURL(), 3);
            //noinspection ConstantConditions
            log("mBCSIsReachable=" + mBCSIsReachable);

            if (mBCSIsReachable) {
                if (mGCMRegistrationNeeded) {
                    try {
                        if (mGCM == null) {
                            mGCM = GoogleCloudMessaging.getInstance(getApplicationContext());
                        }

                        //noinspection deprecation
                        GCMId = mGCM.register(DefaultParameters.GCM_SENDER_ID);

                        GCMMsg = "Device registered, GCM registration ID=" + GCMId;

                        // Store GCM registration ID
                        if (!isCancelled()) {
                            storeRegistrationId(GCMId);
                        }
                    } catch (IOException ex) {
                        if (BuildConfig.DEBUG_MODE) {
                            ex.printStackTrace();
                        }
                        GCMMsg = "Error :" + ex.getMessage();
                    }
                    log("GCMMsg=" + GCMMsg);
                } else {
                    log("GCM registration is not needed");
                }

                String currentlySelectedBCSId = AppUser.get().getBCSId();
                String currentlySelectedBCSName = AppUser.get().getBCSName();
                String currentlySelectedBCSUrl = AppUser.get().getAPIURL();
                if (TextUtils.isEmpty(currentlySelectedBCSId) || TextUtils.isEmpty(currentlySelectedBCSName)
                        || TextUtils.isEmpty(currentlySelectedBCSUrl)) {
                    log("do not have BCS selected");
                    if (mBCSList == null || mBCSList.size() == 0) {
                        log("do not have BCS list, call to get BCSs");
                        getBCS();
                        return true;
                    }
                } else {
                    log("already have BCS selected, do not call to get BCS");
                }

                if (DefaultParameters.BCS_SETTINGS_FUNCTIONALITY_ENABLED) {
                    //noinspection ConstantConditions,PointlessBooleanExpression
                    if (mBCSRegistrationNeeded || DefaultParameters.BCS_REGISTRATION_REQUIRED_EVERY_TIME) {
                        // Register to BCS
                        if (!isCancelled()) {
                            registerToBCS();
                            return true;
                        }
                    } else {
                        if (!isCancelled()) {
                            mBCSRegistrationOK = true;
                            return true;
                        }
                    }
                } else {
                    // Check BCS
                    boolean foundReachableBCS = false;
                    if (mBCSList != null && mBCSList.size() > 0) {
                        for (BCSObject bcsObject : mBCSList) {
                            if (!TextUtils.isEmpty(bcsObject.getBCSId())
                                    && !TextUtils.isEmpty(bcsObject.getBCSName())
                                    && !TextUtils.isEmpty(bcsObject.getBCSUrl())) {
                                String BCSUrl = Utils.createCheckUrl(bcsObject.getBCSUrl());
                                log("checking BCS url=" + BCSUrl);
                                if (Utils.isURLReachable(getApplicationContext(),
                                        BCSUrl,
                                        3)) {
                                    log("BCS id=" + bcsObject.getBCSId() + " is reachable");
                                    AppUser.get().setBCSName(bcsObject.getBCSName());
                                    AppUser.get().setBCSId(bcsObject.getBCSId());
                                    AppUser.get().setBCSDebug(bcsObject.getDebug());
                                    String bcsUrl = bcsObject.getBCSUrl();
                                    if (bcsUrl != null && !bcsUrl.toLowerCase(Locale.getDefault()).contains(DefaultParameters.DEFAULT_API_URL_SUFFIX)) {
                                        bcsUrl += DefaultParameters.DEFAULT_API_URL_SUFFIX;
                                    }
                                    AppUser.get().setAPIURL(bcsUrl);
                                    AppUser.get().setPoliceNumber(bcsObject.getPoliceNumber());
                                    foundReachableBCS = true;
                                    invalidateBCSDebug();
                                    break;
                                } else {
                                    log("BCS id=" + bcsObject.getBCSId() + " is not reachable");
                                }
                            }
                        }
                    } else {
                        // Check current BCS
                        foundReachableBCS = Utils.isURLReachable(getApplicationContext(), Utils.createCheckUrl(AppUser.get().getAPIURL()), 3);
                        invalidateBCSDebug();
                        log("Saved BCS is reachable - use it");
                    }

                    if (foundReachableBCS) {
                        log("Found reachable BCS");
                        // Register to BCS
                        if (!isCancelled()) {
                            registerToBCS();
                            return true;
                        }
                    } else {
                        log("Didnt find reachable BCS");
                        // Get BCSs
                        getBCS();
                        return false;
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
                    if (mBCSList == null || mBCSList.size() == 0) {
                        log("BCS registration is not OK, waiting for get BCS to finish");
                        // Wait for get BCS to finish
                    } else {
                        if (mBCSRegistrationOK) {
                            log("BCS registration is OK, showing alarm views");
                            showAlarmViews();
                            stopWorkerAsyncTaskLauncher();

                            // It's done
                            mSignal.countDown();
                        } else {
                            log("BCS registration is NOT OK, waiting for BCS registration to finish");
                            // Wait for BCS registration to finish
                        }
                    }
                } else {
                    showRetryViews(getString(R.string.check_internet_connection));

                    // It's done
                    mSignalMessage = "BCS is not reachable";
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
            mWorkerAsyncTaskHandler.postDelayed(mWorkerAsyncTaskLauncher, DefaultParameters.WORKER_ASYNC_TASK_LAUNCH_INTERVAL);
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