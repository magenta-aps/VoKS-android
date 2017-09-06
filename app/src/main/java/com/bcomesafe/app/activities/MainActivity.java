/*
 * libjingle
 * Copyright 2013 Google Inc.
 *
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.activities;


import com.bcomesafe.app.AppContext;
import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.DefaultParameters;
import com.bcomesafe.app.adapters.RecyclerViewAdapter;
import com.bcomesafe.app.objects.ChatMessageObject;
import com.bcomesafe.app.objects.GCMMessageObject;
import com.bcomesafe.app.requests.GotItRequest;
import com.bcomesafe.app.requests.TriggerAlarmRequest;
import com.bcomesafe.app.utils.FontsUtils;
import com.bcomesafe.app.utils.RemoteLogUtils;
import com.bcomesafe.app.utils.Utils;
import com.bcomesafe.app.utils.WifiBroadcastReceiver;
import com.bcomesafe.app.webrtc.AppRTCAudioManager;
import com.bcomesafe.app.webrtc.AppRTCClient;
import com.bcomesafe.app.webrtc.AppRTCClient.SignalingParameters;
import com.bcomesafe.app.webrtc.AppRTCProximitySensor;
import com.bcomesafe.app.webrtc.PeerConnectionClient;
import com.bcomesafe.app.R;
import com.bcomesafe.app.webrtc.UnhandledExceptionHandler;
import com.bcomesafe.app.webrtc.WebSocketRTCClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.RendererCommon.ScalingType;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * MainActivity - interoperability between the Android/Java implementation of PeerConnection and the server
 */
public class MainActivity extends Activity implements AppRTCClient.SignalingEvents,
        PeerConnectionClient.PeerConnectionEvents, View.OnClickListener,
        RecyclerViewAdapter.RecyclerViewAdapterClient, TextView.OnEditorActionListener,
        WifiBroadcastReceiver.WifiStateReceiverListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = MainActivity.class.getSimpleName();

    // WebSocket connection reconnect interval in ms
    private static final int WS_RECONNECTION_INTERVAL = 3000;
    // Camera and microphone in use checker interval in ms
    private static final int CM_CHECKING_INTERVAL = 3000;
    // Auto fade after milliseconds
    private static final long AUTO_FADE_AFTER_MS = 5000L;

    // View members
    private RelativeLayout rlRoot;
    private GLSurfaceView glsvVideoView;
    private RelativeLayout rlOverlay;
    private TextView tvHideChat;
    private RelativeLayout rlCallPolice;
    private TextView tvCallPolice;
    private Button bHideChat;
    // Call police overlay
    private LinearLayout llCPO;
    private RelativeLayout rlCPOChat, rlCPOHide, rlCPOCallPolice;
    private TextView tvCPOAlarmActive, tvCPOExplanation, tvCPOCallPolice, tvCPOTapToChat, tvCPOTapToHide;
    // Chat views
    private RecyclerView rv;
    private Button bSendMessage;
    private Button bCallCC;
    private EditText etMessage;

    // MAC textview
    private TextView tvMac;

    // Fade animations
    private ObjectAnimator mFadeInOverlay, mFadeOutOverlay, mFadeOutChatText;

    // WebRTC variables
    private PeerConnectionClient mPCC = null;
    private AppRTCClient mAppRtcClient;
    private SignalingParameters mSignalingParameters;
    private AppRTCAudioManager mAudioManager = null;
    private VideoRenderer.Callbacks mLocalRender;
    private PeerConnectionClient.PeerConnectionParameters mPeerConnectionParameters;
    // States variables
    // Peer connection state from shelter
    private int mShelterPeerConnectionState;
    // Video state from shelter
    private int mShelterVideoState = Constants.SHELTER_NOT_WATCHING;
    // Is Ice connected
    private boolean mIceConnected;
    // Was WebSocket connection connected
    private boolean mWasWSConnected = false;
    // Is peer connection close pending
    private boolean mPendingPCClose;
    // Is IceConnection closed
    private boolean mIceConnectionClosed = true;
    // Is PeerConnection creating
    private boolean mPeerConnectionCreating = false;
    // Shelter status
    private int mShelterStatus = Constants.SHELTER_STATUS_NOT_KNOWN;
    // Crisis center call status
    private int mCCCallStatus = Constants.CC_CALL_NOT_AVAILABLE;
    // Is app being killed
    private boolean mKillingApp;
    // Is activity running e.g. not paused
    private boolean mActivityRunning = false;

    // Chat variables
    private LinearLayoutManager mLayoutManager;
    private RecyclerViewAdapter mAdapter;
    private ArrayList<ChatMessageObject> mData;
    private boolean mIsChatOverlayFading = false;
    // Chat auto fade async task
    private AutoFadeAsyncTask mAutoFadeAsyncTask = null;

    // Requests ids
    private long mTriggerAlarmRequestId = Constants.INVALID_LONG_ID;
    private long mTriggerAlarmInitialRequestId = Constants.INVALID_LONG_ID;

    // Trigger alarm error counter
    private int mTriggerAlarmInitialRequestCount = 0;

    // WebSocket reconnection handler
    private Handler mWSReconnectionHandler;

    // Chat messages queue if no peer connection available
    private final List<ChatMessageObject> mChatMessagesQueue = new ArrayList<>();

    // Proximity sensor object. It measures the proximity of an object in cm
    // relative to the view screen of a device and can therefore be used to
    // assist locking the screen to prevent from "ear clicking" call police button
    private AppRTCProximitySensor mProximitySensor = null;
    private boolean mScreenLocked = false;
    private boolean mPendingShowChatOverlay = false;

    // Camera and microphone in use checker
    private Handler mCMInUseHandler;
    private boolean mCallIsNotAvailable = false;
    private boolean mCameraIsAvailable, mMicrophoneIsAvailable;
    private boolean mCanCreatePeerConnectionChecking = false;

    // Battery level
    private int mPrevBatteryLevel = Constants.INVALID_INT_ID;
    private int mPendingBatteryLevel = Constants.INVALID_INT_ID;

    // WiFi state broadcast receiver
    private WifiBroadcastReceiver mWifiStateBroadcastReceiver;
    private boolean mWifiStateInitial = true;

    // Client id
    private String mClientId;

    // Location
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    private Location mPrevLocation = null;
    private Location mPendingLocation = null;

    // Broadcast receiver for battery level change
    private final BroadcastReceiver mBatteryBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get battery level %
            int currentBatteryLevel = intent.getIntExtra("level", 0);
            log("Received battery level=" + currentBatteryLevel + " prevBatteryLevel=" + mPrevBatteryLevel);
            if (currentBatteryLevel != mPrevBatteryLevel) {
                sendBatteryLevel(currentBatteryLevel);
            }
        }
    };

    /**
     * Broadcast receiver for request completed actions
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("onReceive()");
            if (intent.getAction().equals(Constants.REQUEST_COMPLETED_ACTION)) {
                log("onReceive() request completed action");
                if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mTriggerAlarmRequestId) {
                    handleTriggerAlarmRequestResult(mTriggerAlarmRequestId, false);
                } else if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mTriggerAlarmInitialRequestId) {
                    handleTriggerAlarmRequestResult(mTriggerAlarmInitialRequestId, true);
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

    /**
     * Broadcast receiver for GPS provider changes
     */
    private final BroadcastReceiver mGPSBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("onReceive() mGPSBroadcastReceiver");
            if (intent.getAction().equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                checkGPSStatus();
            }
        }
    };

    /**
     * Local broadcast receiver
     */
    private final BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.ACTION_SHOW_GCM_MESSAGE_IN_CHAT)) {
                // NOTE atm no need to get message title
                if (intent.getExtras() != null /*&& intent.getExtras().containsKey(Constants.GCM_MESSAGE_TITLE)*/
                        && intent.getExtras().containsKey(Constants.GCM_MESSAGE_ID)
                        && intent.getExtras().containsKey(Constants.GCM_MESSAGE_CONTENT)
                        /*&& !intent.getStringExtra(Constants.GCM_MESSAGE_TITLE).equals(Constants.INVALID_STRING_ID)*/
                        && intent.getIntExtra(Constants.GCM_MESSAGE_ID, Constants.INVALID_INT_ID) != Constants.INVALID_INT_ID
                        && !intent.getStringExtra(Constants.GCM_MESSAGE_CONTENT).equals(Constants.INVALID_STRING_ID)
                        ) {
                    sendSystemMessage(new ChatMessageObject(
                            intent.getIntExtra(Constants.GCM_MESSAGE_ID, Constants.INVALID_INT_ID),
                            intent.getStringExtra(Constants.GCM_MESSAGE_CONTENT),
                            Constants.MESSAGE_TYPE_PUSH,
                            intent.getLongExtra(Constants.GCM_MESSAGE_TIMESTAMP, Constants.INVALID_LONG_ID)
                    ));
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set window styles. Needs to be done before adding content.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Client id
        mClientId = AppUser.get().getDeviceUID();

        // Navigation bar hiding
        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        if ((visibility) == View.VISIBLE) {
                            if (!mScreenLocked && mActivityRunning) {
                                log("screen is not locked, hide chat overlay");
                                hideChatOverlay();
                            }
                        }
                    }
                });

        // Initialize views, set fonts, set OnClickListeners, initialize animations and set up chat
        initializeViews();
        setFonts();
        setOnClickListeners();
        initializeAnimations();
        setUpChat();
        if (DefaultParameters.ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV) {
            setMacTextView();
        }


        // Register WIFI state change broadcast receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mWifiStateBroadcastReceiver = new WifiBroadcastReceiver(false);
        mWifiStateBroadcastReceiver.addListener(this);
        registerReceiver(mWifiStateBroadcastReceiver, intentFilter);

        Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));
        mKillingApp = false;
        mPendingPCClose = false;
        mShelterPeerConnectionState = 0;
        mIceConnected = false;
        mSignalingParameters = null;

        // PeerConnection parameters
        preparePeerConnectionParameters(false, true);

        VideoRendererGui.setView(glsvVideoView, new Runnable() {
            @Override
            public void run() {
                if (mCameraIsAvailable && mMicrophoneIsAvailable) {
                    log("mCameraIsAvailable==true and mMicrophoneIsAvailable=true, creating PC factory in onCreate");
                    createPeerConnectionFactory();
                } else {
                    log("mCameraIsAvailable!=true and mMicrophoneIsAvailable!=true, not creating PC factory");
                }
            }
        });

        mLocalRender = VideoRendererGui.create(0, 0, 0, 0, ScalingType.SCALE_ASPECT_FIT, false);

        initializeWebSocketConnection();
        initializeAudioManager();

        // Trigger initial alarm
        triggerAlarm(true);

        // Register local broadcast receiver for show GCM message in chat action
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalBroadcastReceiver, new IntentFilter(Constants.ACTION_SHOW_GCM_MESSAGE_IN_CHAT));

        // Check for GCM queued messages
        checkForGCMQueuedMessages();

        // Register volume changed broadcast receiver
        registerReceiver(mVolumeBroadcastReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));

        // Register GPS provider changes
        registerReceiver(mGPSBroadcastReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));

        // Register battery level broadcast receiver
        registerReceiver(mBatteryBroadcastReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // Auto fade AsyncTask
        mAutoFadeAsyncTask = new AutoFadeAsyncTask();
        mAutoFadeAsyncTask.execute();

        // Create and initialize the proximity sensor.
        // Tablet devices (e.g. Nexus 7) does not support proximity sensors.
        // Note that, the sensor will not be active until start() has been called.
        mProximitySensor = AppRTCProximitySensor.create(this, new Runnable() {
            // This method will be called each time a state change is detected.
            // Example: user holds his hand over the device (closer than ~5 cm),
            // or removes his hand from the device.
            public void run() {
                onProximitySensorChangedState();
            }
        });

        // Location
        buildGoogleApiClientIfNeeded();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
        startLocationUpdates();
    }

    /**
     * Prepares PeerConnection parameters depending on camera/microphone availability and usage
     */
    private void preparePeerConnectionParameters(boolean skipCheckers, boolean showMessages) {
        log("preparePeerConnectionParameters() skipCheckers=" + skipCheckers);
        boolean doesCameraExist = Utils.doesCameraExist(this) || skipCheckers;
        boolean doesMicrophoneExist = Utils.doesMicrophoneExist(this) || skipCheckers;
        log("CE=" + doesCameraExist + " ME=" + doesMicrophoneExist);

        if (!doesCameraExist && !doesMicrophoneExist) {
            if (showMessages) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.no_camera_and_microphone), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
                    }
                });
            }
        } else {
            if (!doesCameraExist) {
                if (showMessages) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.no_camera), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
                        }
                    });
                }
            } else if (!doesMicrophoneExist) {
                if (showMessages) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.no_microphone), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
                        }
                    });
                }
            }
        }

        boolean cameraAvailable = Utils.isCameraAvailable() || skipCheckers;
        boolean micAvailable = Utils.isMicrophoneAvailable(this) || skipCheckers;
        log("CA=" + cameraAvailable + " MA=" + micAvailable);

        if (!cameraAvailable && !micAvailable) {
            log("Camera and microphone is not available to use, starting checker");
            if (showMessages) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.camera_and_microphone_busy), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
                    }
                });
            }
            startCMChecker();
        } else {
            if (!cameraAvailable) {
                log("Only camera is not available to use, starting checker");
                if (showMessages) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.camera_busy), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
                        }
                    });
                }
                startCMChecker();
            } else if (!micAvailable) {
                log("Only microphone is not available to use, starting checker");
                if (showMessages) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.microphone_busy), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
                        }
                    });
                }
                startCMChecker();
            }
        }

        mCallIsNotAvailable = !(doesMicrophoneExist && micAvailable);

        if (!skipCheckers) {
            mCameraIsAvailable = (doesCameraExist && cameraAvailable);
            mMicrophoneIsAvailable = (doesMicrophoneExist && micAvailable);
        }

        log("CE=" + doesCameraExist + " ME=" + doesMicrophoneExist + " CA=" + cameraAvailable + " MA=" + micAvailable);

        mPeerConnectionParameters = new PeerConnectionClient.PeerConnectionParameters(
                // Video call - boolean true/false, default true
                doesCameraExist && cameraAvailable,
                // Loop back - boolean true/false
                false,
                // Video width - int - 1280x720, 640x480, 320x240, default 0 (Width/height)
                640,
                // Video height - int - 1280x720, 640x480, 320x240, default 0
                480,
                // FPS - int - 30, 15, default 0
                0,
                // Video start bitrate - int, default default 0
                0,
                // Video codec - String - VP8, VP9, H264, default VP8
                "VP8",
                // Video codec hardware acceleration, boolean true/false - default true
                true,
                // Audio bitrate - int, default 0
                0,
                // Audio coded - String, default OPUS
                "OPUS",
                // CPU overuse detection - boolean true/false, default true
                true);
    }

    /**
     * Initializes views
     */
    private void initializeViews() {
        rlRoot = (RelativeLayout) findViewById(R.id.rl_root);
        rlOverlay = (RelativeLayout) findViewById(R.id.rl_a_main_overlay);
        tvHideChat = (TextView) findViewById(R.id.tv_a_main_hide_chat);
        rlCallPolice = (RelativeLayout) findViewById(R.id.rl_alarm_call);
        tvCallPolice = (TextView) findViewById(R.id.tv_alarm_call);
        bHideChat = (Button) findViewById(R.id.b_hide_chat);
        rv = (RecyclerView) findViewById(R.id.rv_chat);
        etMessage = (EditText) findViewById(R.id.et_message);
        etMessage.setOnEditorActionListener(this);
        bSendMessage = (Button) findViewById(R.id.b_message);
        bSendMessage.setTransformationMethod(null);
        bCallCC = (Button) findViewById(R.id.b_chat_call);
        glsvVideoView = (GLSurfaceView) findViewById(R.id.gl_a_main);
        // Call police overlay
        tvCPOAlarmActive = (TextView) findViewById(R.id.tv_initial_overlay_alarm);
        tvCPOExplanation = (TextView) findViewById(R.id.tv_initial_overlay_explanation);
        tvCPOCallPolice = (TextView) findViewById(R.id.tv_initial_overlay_call_police);
        tvCPOTapToChat = (TextView) findViewById(R.id.tv_initial_overlay_chat);
        tvCPOTapToHide = (TextView) findViewById(R.id.tv_initial_overlay_hide);
        llCPO = (LinearLayout) findViewById(R.id.ll_initial_overlay);
        rlCPOChat = (RelativeLayout) findViewById(R.id.rl_initial_overlay_chat);
        rlCPOHide = (RelativeLayout) findViewById(R.id.rl_initial_overlay_hide);
        rlCPOCallPolice = (RelativeLayout) findViewById(R.id.rl_initial_overlay_call_police);

        //MAC address textview
        tvMac = (TextView) findViewById(R.id.tv_mac);
    }

    //Set Mac Address in TextView

    private void setMacTextView() {
        if (DefaultParameters.ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV) {
            tvMac.setText("MAC: " + AppUser.get().getDeviceMacAddress());
            tvMac.setVisibility(View.GONE);
        }
    }


    /**
     * Sets fonts
     */

    private void setFonts() {
        tvHideChat.setTypeface(FontsUtils.getInstance().getTfBold());
        etMessage.setTypeface(FontsUtils.getInstance().getTfLight());
        bSendMessage.setTypeface(FontsUtils.getInstance().getTfBold());
        tvCallPolice.setTypeface(FontsUtils.getInstance().getTfBold());
        bHideChat.setTypeface(FontsUtils.getInstance().getTfBold());
        // Call police overlay
        tvCPOAlarmActive.setTypeface(FontsUtils.getInstance().getTfBold());
        tvCPOExplanation.setTypeface(FontsUtils.getInstance().getTfLight());
        tvCPOCallPolice.setTypeface(FontsUtils.getInstance().getTfBold());
        tvCPOTapToChat.setTypeface(FontsUtils.getInstance().getTfBold());
        tvCPOTapToHide.setTypeface(FontsUtils.getInstance().getTfBold());
    }

    /**
     * Sets OnClickListeners
     */
    private void setOnClickListeners() {
        rlOverlay.setOnClickListener(this);
        rlCallPolice.setOnClickListener(this);
        bHideChat.setOnClickListener(this);
        bSendMessage.setOnClickListener(this);
        bCallCC.setOnClickListener(this);
        // Call police overlay
        rlCPOCallPolice.setOnClickListener(this);
        rlCPOChat.setOnClickListener(this);
        rlCPOHide.setOnClickListener(this);
    }

    /**
     * Initializes fade animations
     */
    private void initializeAnimations() {
        // Chat overlay fade in animation
        mFadeInOverlay = ObjectAnimator.ofFloat(rlOverlay, "alpha", 0f, 1f);
        mFadeInOverlay.setDuration(750L);
        mFadeInOverlay.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                toggleFullscreen(true);
                rlOverlay.setAlpha(0f);
                rlOverlay.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // NOTE needed to toggle fullscreen again because of devices with navigation bar
                // when soft keyboard is opened, first trigger doest go to fullscreen
                toggleFullscreen(true);

                mIsChatOverlayFading = false;
                mFadeOutChatText.start();
                if (llCPO.getVisibility() == View.VISIBLE) {
                    llCPO.setVisibility(View.GONE);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        // Chat overlay fade out animation
        mFadeOutOverlay = ObjectAnimator.ofFloat(rlOverlay, "alpha", 1f, 0f);
        mFadeOutOverlay.setDuration(750L);
        mFadeOutOverlay.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                toggleFullscreen(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                rlOverlay.setVisibility(View.GONE);
                tvHideChat.setVisibility(View.VISIBLE);
                tvHideChat.setAlpha(1f);
                if (mPendingShowChatOverlay) {
                    mPendingShowChatOverlay = false;
                    mFadeInOverlay.start();
                } else {
                    mIsChatOverlayFading = false;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        // Chat text fade out animation
        mFadeOutChatText = ObjectAnimator.ofFloat(tvHideChat, "alpha", 1f, 0f);
        mFadeOutChatText.setDuration(750L);
        mFadeOutChatText.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                tvHideChat.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    /**
     * Sets up chat
     */
    private void setUpChat() {
        mData = new ArrayList<>();
        mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setStackFromEnd(true);
        rv.setLayoutManager(mLayoutManager);
        mAdapter = new RecyclerViewAdapter(mData, this);
        rv.setAdapter(mAdapter);
        if (AppUser.get().getDevMode()) {
            sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, "Shelter ID=" + AppUser.get().getBCSId(), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
        }
    }


    /**
     * Initializes WebSocket connection
     */
    private void initializeWebSocketConnection() {
        log("initializeWebSocketConnection()");
        if (mAppRtcClient == null) {
            sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.connecting_to_shelter), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));

            // Start WebSocket connection
            log("Starting WebSocket connection");
            mAppRtcClient = new WebSocketRTCClient(this);
            mAppRtcClient.connectToShelter();
        } else {
            log("Not starting WebSocket connection, already started");
        }
    }

    /**
     * Initializes audio manager
     */
    private void initializeAudioManager() {
        // Create and audio manager that will take care of audio routing, audio modes, audio device enumeration etc.
        mAudioManager = AppRTCAudioManager.create(this, new Runnable() {
            // This method will be called each time the audio state (number and type of devices) has been changed.
            @Override
            public void run() {
                onAudioManagerChangedState();
            }
        });
        // Store existing audio settings and change audio mode to MODE_IN_COMMUNICATION for best possible VoIP performance.
        log("Initializing the audio manager");
        mAudioManager.init();
    }

    /**
     * Creates peer connection factory when EGL context is ready.
     */
    private void createPeerConnectionFactory() {
        log("createPeerConnectionFactory()");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mPCC == null) {
                    log("Creating pcc");
                    mPCC = new PeerConnectionClient();
                    mPCC.createPeerConnectionFactory(MainActivity.this, VideoRendererGui.getEglBaseContext(), mPeerConnectionParameters, MainActivity.this);
                }
                if (mSignalingParameters != null) {
                    log("EGL context is ready after websocket connection. (signalingParameters!=null)");
                    onConnectedToShelterInternal(mSignalingParameters);
                } else {
                    log("signalingParameters==null");
                }
            }
        });
    }

    /**
     * Creates peer connection
     */
    private void createPeerConnection() {
        log("createPeerConnection()");
        if (mPCC != null) {
            if (!mPeerConnectionCreating) {
                mPeerConnectionCreating = true;
                while (mCanCreatePeerConnectionChecking) {
                    // Wait
                }
                mPCC.createPeerConnection(mLocalRender, null, mSignalingParameters);
                if (mSignalingParameters.initiator) {
                    log("Creating OFFER");
                    // Create offer. Offer SDP will be sent to answering client in PeerConnectionEvents.onLocalDescription event.
                    mPCC.createOffer();
                }
            } else {
                log("PeerConnection is already creating");
            }
        } else {
            log("PeerConnectionClient not initialized yet");
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        log("onPause()");
        mActivityRunning = false;
        glsvVideoView.onPause();
        // Unregister receiver
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            // Nothing to do
        }
        // Proximity sensor
        mProximitySensor.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        log("onResume()");
        mActivityRunning = true;
        glsvVideoView.onResume();
        // Check existing requests results
        if (mTriggerAlarmRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mTriggerAlarmRequestId)) {
            handleTriggerAlarmRequestResult(mTriggerAlarmRequestId, false);
        }
        if (mTriggerAlarmInitialRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mTriggerAlarmInitialRequestId)) {
            handleTriggerAlarmRequestResult(mTriggerAlarmInitialRequestId, true);
        }
        // Register broadcast receiver for completed requests
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.REQUEST_COMPLETED_ACTION));
        // Proximity sensor
        mProximitySensor.start();
        // Toggle fullscreen again if chat overlay is visible
        if (rlOverlay.getVisibility() == View.VISIBLE) {
            toggleFullscreen(true);
        }
        // Check GPS status message
        checkGPSStatusMessage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        log("onDestroy()");
        mShelterStatus = Constants.SHELTER_STATUS_NOT_KNOWN;
        // Disconnect and release resources
        disconnectAndCleanUp();
        // Close audio manager
        if (mAudioManager != null) {
            mAudioManager.close();
            mAudioManager = null;
        }
        // Unregister broadcast receiver for completed requests
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalBroadcastReceiver);
        } catch (Exception e) {
            // Nothing to do
        }
        // Unregister volume change broadcast receiver
        unregisterReceiver(mVolumeBroadcastReceiver);
        // Unregister GPS provider changes broadcast receiver
        unregisterReceiver(mGPSBroadcastReceiver);
        // Unregister battery level change broadcast receiver
        unregisterReceiver(mBatteryBroadcastReceiver);
        // Unregister wifi state broadcast receiver
        unregisterWiFiStateBroadcastReceiver();
        // Restore user sounds if app is not killing
        if (!mKillingApp) {
            Utils.unmuteSounds(this);
        }
        // Stop camera and microphone in use checker
        stopCMChecker();
        // Stop WS reconnection
        if (mWSReconnectionHandler != null) {
            mWSReconnectionHandler.removeCallbacks(mWSReconnection);
        }
        mWSReconnectionHandler = null;
        // Stop proximity sensor
        if (mProximitySensor != null) {
            mProximitySensor.stop();
            mProximitySensor = null;
        }
        // Clear request store
        AppContext.get().getRequestManager().clearRequestStore();
        // Finalize remote logs
        RemoteLogUtils.getInstance().finalizeAndCleanUp();
        // Location
        stopLocationUpdates();
    }

    @SuppressWarnings("EmptyMethod")
    private void onAudioManagerChangedState() {
        // NOTE actions if needed according to which AppRTCAudioManager.AudioDevice is active.
        // check webrtc updates
    }

    /**
     * Disconnects from remote resources, dispose of local resources.
     * But does not destroy peer connection factory.
     *
     * @param disconnectWS boolean - should disconnect WebSocket connection
     */
    private void disconnect(boolean disconnectWS) {
        log("disconnect() disconnectWS=" + disconnectWS);
        mShelterVideoState = Constants.SHELTER_NOT_WATCHING;
        mIceConnected = false;

        if (disconnectWS) {
            mSignalingParameters = null;
        }

        if (mAppRtcClient != null && disconnectWS) {
            mAppRtcClient.disconnectFromShelter();
            mAppRtcClient = null;
        }

        if (mPCC != null && !mPendingPCClose) {
            mPendingPCClose = true;
            mPCC.stopVideoSource();
            mPCC.closePeerConnection();
        }
    }

    /**
     * Disconnects from remote resources, dispose of local resources.
     */
    private void disconnectAndCleanUp() {
        mIceConnected = false;
        mSignalingParameters = null;

        if (mAppRtcClient != null) {
            mAppRtcClient.disconnectFromShelter();
            mAppRtcClient = null;
        }

        if (mPCC != null) {
            mPCC.stopVideoSource();
            mPCC.close();
            mPCC = null;
        }
    }

    // Implementation of AppRTCClient.AppRTCSignalingEvents
    // All callbacks are invoked from WebSocket signaling looper thread and are routed to UI thread.
    private void onConnectedToShelterInternal(final SignalingParameters params) {
        log("onConnectedToShelterInternal()");
        mSignalingParameters = params;
        if (mPCC == null) {
            log("Connected to shelter, but EGL context is not ready yet.");
            return;
        }
        if (canCreatePeerConnection("onConnectedToShelterInternal()")) {
            log("Creating peer connection");
            createPeerConnection();
        }
    }

    @Override
    public void onWebSocketOpen() {
        log("onWebSocketOpen()");
        // NOTE callback not needed at the moment
    }

    @Override
    public void onWebSocketRegistered() {
        log("onWebSocketRegistered()");
        mWasWSConnected = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Send system message
                sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.connected_to_shelter), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
                // Update UI according to shelter status
                onShelterStatus();
                // Check GPS status
                checkGPSStatus();
            }
        });
        // Check chat messages queue
        if (mChatMessagesQueue.size() > 0) {
            for (ChatMessageObject cmo : mChatMessagesQueue) {
                sendOwnMessage(cmo);
            }
            mChatMessagesQueue.clear();
        }
    }

    @Override
    public void onConnectedToShelter(final SignalingParameters params) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToShelterInternal(params);
            }
        });
    }

    @Override
    public void onRemoteDescription(final SessionDescription sdp) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mPCC == null) {
                    return;
                }
                log("Received remote " + sdp.type);
                mPCC.setRemoteDescription(sdp);
                if (!mSignalingParameters.initiator) {
                    log("Creating ANSWER");
                    // Create answer. Answer SDP will be sent to offering client in PeerConnectionEvents.onLocalDescription event.
                    mPCC.createAnswer();
                }
            }
        });
    }

    @Override
    public void onRemoteIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mPCC != null) {
                    mPCC.addRemoteIceCandidate(candidate);
                }
            }
        });
    }

    @Override
    public void onWebSocketClose() {
        log("onWebSocketClose()");
        onWebSocketCloseOrErrorInternal();
    }


    @Override
    public void onWebSocketError(final String description) {
        log("onWebSocketError() description=" + description);
        onWebSocketCloseOrErrorInternal();
    }

    private void onWebSocketCloseOrErrorInternal() {
        log("onWebSocketCloseOrErrorInternal()");

        if (mWasWSConnected) {
            onNewSystemMessage(getString(R.string.connection_lost), System.currentTimeMillis());
        }

        mWasWSConnected = false;

        if (mAppRtcClient != null) {
            log("Running WS reconnection");

            mShelterPeerConnectionState = 0;
            disconnect(true);
            mShelterStatus = Constants.SHELTER_STATUS_NOT_KNOWN;
            onShelterStatus(Constants.SHELTER_STATUS_OFF);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mWSReconnectionHandler == null) {
                        mWSReconnectionHandler = new Handler();
                    }
                    mWSReconnectionHandler.postDelayed(mWSReconnection, WS_RECONNECTION_INTERVAL);
                }
            });
        } else {
            log("Not running WS reconnection");
        }
    }

    // Implementation of PeerConnectionClient.PeerConnectionEvents.
    // Send local peer connection SDP and ICE candidates to remote party.
    // All callbacks are invoked from peer connection client looper thread and are routed to UI thread.
    @Override
    public void onShelterStatusGot(int shelterStatus) {
        log("onShelterStatusGot() shelterStatus=" + shelterStatus);
        if (mShelterStatus != shelterStatus) {
            log("Shelter status changed");
            switch (shelterStatus) {
                case Constants.SHELTER_STATUS_ON:
                    mShelterStatus = shelterStatus;
                    onShelterStatus();
                    // NOTE peer connection creation moved to onShelterPeerConnectionStateGot()
                    // Recreate peer connection
                    if (canCreatePeerConnection("onShelterStatusGot() 1")) {
                        createPeerConnection();
                    }
                    checkPendingLocation();
                    break;
                case Constants.SHELTER_STATUS_OFF:
                    mShelterStatus = shelterStatus;
                    onShelterStatus();
                    disconnect(false);
                    break;
                default:
                    log("Unknown shelter status, ignoring");
                    break;
            }
        } else {
            log("Shelter status not changed, ignoring");
        }
    }

    /**
     * On shelter reset got
     */
    @Override
    public void onShelterResetGot() {
        log("onShelterResetGot()");
        if (!mKillingApp) {
            mKillingApp = true;
            Intent intent = new Intent(MainActivity.this, AlarmActivity.class);
            Bundle extras = new Bundle();
            extras.putBoolean(Constants.EXTRA_SKIP_INITIAL_MUTE, true);
            intent.putExtras(extras);
            startActivity(intent);
            finish();
        } else {
            log("Already killing app, ignoring");
        }
    }

    @Override
    public void onLocalDescription(final SessionDescription sdp) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mAppRtcClient != null) {
                    log("Sending " + sdp.type);
                    if (mSignalingParameters.initiator) {
                        mAppRtcClient.sendOfferSdp(sdp, mSignalingParameters);
                    } else {
                        mAppRtcClient.sendAnswerSdp(sdp);
                    }
                }
            }
        });
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mAppRtcClient != null) {
                    mAppRtcClient.sendLocalIceCandidate(candidate, mSignalingParameters);
                }
            }
        });
    }

    @Override
    public void onIceConnected() {
        log("onIceConnected()");
        mIceConnected = true;
        // Check shelter audio stream
        if (mPCC != null && mCCCallStatus != Constants.CC_CALL_ONGOING) {
            mPCC.muteRemoteAudioStream();
        }
        // Check shelter status and update CT button
        // This case is needed after reconnection after cam/mic being used
        onShelterStatus();
    }

    @Override
    public void onIceCompleted() {
        log("onIceCompleted()");
        // NOTE callback not needed at the moment
    }

    @Override
    public void onIceNew() {
        log("onIceNew()");
        mIceConnectionClosed = false;
    }


    @Override
    public void onIceChecking() {
        log("onIceChecking()");
        mIceConnectionClosed = false;
    }

    @Override
    public void onIceClosed() {
        log("onIceClosed()");
        mIceConnected = false;
        mIceConnectionClosed = true;
        if (canCreatePeerConnection("onIceClosed()")) {
            log("Creating peer connection");
            createPeerConnection();
        } else {
            log("Not creating peer connection");
        }
    }

    @Override
    public void onIceDisconnected() {
        log("onIceDisconnected()");
        onIceDisconnectedOrPeerConnectionError();
    }

    @Override
    public void onPeerConnectionClosed() {
        log("onPeerConnectionClosed()");
        mPeerConnectionCreating = false;
        if (!mKillingApp) {
            mPendingPCClose = false;
            if (canCreatePeerConnection("onPeerConnectionClosed()")) {
                log("Creating peer connection");
                createPeerConnection();
            } else {
                log("Not creating peer connection");
            }
        } else {
            log("App is killing, ignoring");
        }
    }

    @Override
    public void onPeerConnectionError(final String description) {
        log("onPeerConnectionError() description=" + description);
        onIceDisconnectedOrPeerConnectionError();
    }

    private void onIceDisconnectedOrPeerConnectionError() {
        log("onIceDisconnectedOrPeerConnectionError()");
        mIceConnected = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onShelterStatus(Constants.SHELTER_STATUS_OFF);
                disconnect(false);
            }
        });
    }

    /**
     * On new chat message got
     *
     * @param msg       String
     * @param timestamp long
     */
    @Override
    public void onNewChatMessageGot(final String msg, final long timestamp) {
        log("onNewChatMessageGot()");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendOperatorMessage(new ChatMessageObject(Constants.INVALID_INT_ID, msg, Constants.MESSAGE_TYPE_OPERATOR, timestamp), true, true);
            }
        });
    }

    /**
     * On new chat messages got
     *
     * @param messages JSONArray
     */
    @Override
    public void onNewChatMessagesGot(final JSONArray messages) {
        log("onNewChatMessagesGot()");
        if (messages != null && messages.length() > 0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean shouldScrollToBottom = mLayoutManager.findLastCompletelyVisibleItemPosition() == mData.size() - 1;
                    for (int i = 0; i < messages.length(); i++) {
                        try {
                            JSONObject singleMessageObject = messages.getJSONObject(i);
                            String msg = singleMessageObject.getString(Constants.WEBSOCKET_PARAM_DATA);
                            long timestamp = singleMessageObject.getLong(Constants.WEBSOCKET_PARAM_TIMESTAMP);
                            sendOperatorMessage(new ChatMessageObject(Constants.INVALID_INT_ID, msg, Constants.MESSAGE_TYPE_OPERATOR, timestamp), true, false);
                        } catch (JSONException e) {
                            log("Unable to get message at index " + i + " from messages");
                        }
                    }
                    if (messages.length() == 1) {
                        sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, "Added " + messages.length() + " message, scroll up to see it", Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()), false);
                    } else {
                        sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, "Added " + messages.length() + " messages, scroll up to see them", Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()), false);
                    }
                    if (shouldScrollToBottom) {
                        rv.scrollToPosition(mData.size() - 1);
                    }
                }
            });
        }
    }

    /**
     * On new system chat message got
     *
     * @param dataString String
     */
    private void onNewSystemMessage(final String dataString, final long timestamp) {
        log("onNewSystemMessage()");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, dataString, Constants.MESSAGE_TYPE_APP, timestamp));
            }
        });
    }

    /**
     * On shelter status UI changes
     */
    private void onShelterStatus() {
        log("onShelterStatus() mShelterStatus=" + mShelterStatus);
        onShelterStatus(mShelterStatus);
    }

    /**
     * On shelter status UI changes
     */
    private void onShelterStatus(int shelterStatus) {
        log("onShelterStatus() shelterStatus=" + shelterStatus);
        //noinspection StatementWithEmptyBody
        if (shelterStatus == Constants.SHELTER_STATUS_ON && !mCallIsNotAvailable) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bCallCC.setBackgroundResource(R.drawable.button_call_ct);
                    bCallCC.setEnabled(true);
                }
            });
            if (mCCCallStatus == Constants.CC_CALL_ONGOING || mCCCallStatus == Constants.CC_CALL_ONHOLD) {
                onNewSystemMessage(getString(R.string.call_crisis_center_ended), System.currentTimeMillis());
            }
            mCCCallStatus = Constants.CC_CALL_AVAILABLE;
        } else if (shelterStatus == Constants.SHELTER_STATUS_OFF || mCallIsNotAvailable) {
            if (mCCCallStatus == Constants.CC_CALL_ONGOING || mCCCallStatus == Constants.CC_CALL_ONHOLD) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.audio_connection_lost), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
                    }
                });
            } else if (mCCCallStatus == Constants.CC_CALL_WAITING_ANSWER) {
                onNewSystemMessage(getString(R.string.call_crisis_center_ended), System.currentTimeMillis());
            }
            mCCCallStatus = Constants.CC_CALL_NOT_AVAILABLE;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bCallCC.setBackgroundResource(R.drawable.button_call_ct);
                    bCallCC.setEnabled(false);
                }
            });
        } else {
            // Nothing to do
            log("Unknown shelter status or call is not available, ignoring");
        }
    }

    /**
     * On shelter listening got
     * Determine if shelter is listening to us or not - change button
     *
     * @param shelterListening int
     */
    @Override
    public void onShelterListeningGot(int shelterListening) {
        log("onShelterListeningGot()");
        if (shelterListening == Constants.SHELTER_LISTENING) {
            if (mCCCallStatus == Constants.CC_CALL_WAITING_ANSWER) {
                mCCCallStatus = Constants.CC_CALL_ONGOING;
                onNewSystemMessage(getString(R.string.crisis_center_answered), System.currentTimeMillis());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bCallCC.setBackgroundResource(R.drawable.button_drop_ct);
                        bCallCC.setEnabled(true);
                    }
                });
                if (mPCC != null) {
                    mPCC.unmuteRemoteAudioStream();
                }
            } else if (mCCCallStatus == Constants.CC_CALL_ONHOLD) {
                mCCCallStatus = Constants.CC_CALL_ONGOING;
                onNewSystemMessage(getString(R.string.crisis_center_resumed), System.currentTimeMillis());
                if (mPCC != null) {
                    mPCC.unmuteRemoteAudioStream();
                }
            }
        } else if (shelterListening == Constants.SHELTER_NOT_LISTENING) {
            if (mCCCallStatus == Constants.CC_CALL_ONGOING) {
                mCCCallStatus = Constants.CC_CALL_ONHOLD;
                onNewSystemMessage(getString(R.string.crisis_center_hold), System.currentTimeMillis());
                if (mPCC != null) {
                    mPCC.muteRemoteAudioStream();
                }
            }
        } else {
            // Nothing to do
            log("Unknown shelter listening parameter, ignoring");
        }
    }

    /**
     * On shelter video got
     * Determine if shelter is watching us or not - pause/resume video stream
     *
     * @param shelterVideo int
     */
    @Override
    public void onShelterVideoGot(int shelterVideo) {
        log("onShelterVideoGot()");
        if (shelterVideo == Constants.SHELTER_WATCHING || shelterVideo == Constants.SHELTER_NOT_WATCHING) {

            if (mShelterVideoState != shelterVideo) {
                mShelterVideoState = shelterVideo;
                if (shelterVideo == Constants.SHELTER_WATCHING) {
                    // Check pending battery level
                    if (mPendingBatteryLevel != Constants.INVALID_INT_ID) {
                        sendBatteryLevel(mPendingBatteryLevel);
                    } else if (mPrevBatteryLevel != Constants.INVALID_INT_ID) { // Otherwise if prev battery level is valid
                        sendBatteryLevel(mPrevBatteryLevel);
                    }
                    // Resume video
                    resumeVideoSource();
                } else {
                    stopVideoSource();
                }
            } else {
                log("Shelter video not changed, ignoring");
            }
        } else {
            // Nothing to do
            log("Unknown shelter video parameter, ignoring");
        }
    }

    /**
     * On shelter peer connection state got
     *
     * @param state int
     */
    @Override
    public void onShelterPeerConnectionStateGot(int state) {
        log("onShelterPeerConnectionStateGot() state=" + state);
        if (mShelterPeerConnectionState != state) {
            switch (state) {
                case 0: // Should destroy peer connection
                    mShelterPeerConnectionState = state;
                    disconnect(false);
                    onShelterStatus(mShelterStatus);
                    break;
                case 1: // Should create peer connection
                    mShelterPeerConnectionState = state;
                    if (canCreatePeerConnection("onShelterPeerConnectionStateGot()")) {
                        log("Creating peer connection");
                        createPeerConnection();
                    } else {
                        log("Unable to create peer connection at this moment");
                    }
                    break;
                default:
                    log("Unknown peer connection state, ignoring");
                    break;
            }
        } else {
            log("Got peer connection state is the same, ignoring");
        }
    }

    /**
     * On back button pressed
     */
    @Override
    public void onBackPressed() {
        if (rlOverlay.getVisibility() == View.VISIBLE || mFadeInOverlay.isRunning()) { // Chat overlay is visible
            // Hide chat overlay
            mFadeInOverlay.cancel();
            hideChatOverlay();
        } else {
            // Close app
            finish();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.rl_a_main_overlay: // Chat overlay
                if (!mScreenLocked) {
                    log("Hiding chat overlay");
                    hideChatOverlay();
                } else {
                    log("Screen is locked, ignoring overlay click");
                }
                break;
            case R.id.rl_alarm_call: // Call police button
                if (!mScreenLocked) {
                    triggerAlarm(false);
                    callPolice();
                } else {
                    log("Screen is locked, ignoring call police click");
                }
                break;
            case R.id.b_hide_chat: // Hide chat button
                if (!mScreenLocked) {
                    showChatOverlay();
                } else {
                    log("Screen is locked, ignoring hide chat click");
                }
                break;
            case R.id.b_chat_call: // Call crisis center button
                if (!mScreenLocked) {
                    onCallCrisisCenterClick();
                } else {
                    log("Screen is locked, ignoring call CT click");
                }
                break;
            case R.id.b_message: // Send message button
                if (!mScreenLocked) {
                    onSendMessageClick();
                } else {
                    log("Screen is locked, ignoring send message click");
                }
                break;
            // Call police overlay
            case R.id.rl_initial_overlay_call_police: // Call police button
                if (!mScreenLocked) {
                    triggerAlarm(false);
                    callPolice();
                } else {
                    log("Screen is locked, ignoring CPO call click");
                }
                break;
            case R.id.rl_initial_overlay_chat: // Chat button
                if (!mScreenLocked) {
                    if (mAutoFadeAsyncTask != null) {
                        mAutoFadeAsyncTask.cancel(true);
                        mAutoFadeAsyncTask = null;
                    }
                    llCPO.setVisibility(View.GONE);
                } else {
                    log("Screen is locked, ignoring CPO hide click");
                }
                break;
            case R.id.rl_initial_overlay_hide: // Hide button
                if (!mScreenLocked) {
                    if (mAutoFadeAsyncTask != null) {
                        mAutoFadeAsyncTask.cancel(true);
                        mAutoFadeAsyncTask = null;
                    }
                    showChatOverlay();
                } else {
                    log("Screen is locked, ignoring CPO hide click");
                }
                break;
        }
    }

    /**
     * On call crisis center actions
     */
    private void onCallCrisisCenterClick() {
        if (mAppRtcClient == null) {
            return;
        }
        if (mShelterStatus == Constants.SHELTER_STATUS_ON && mCCCallStatus == Constants.CC_CALL_AVAILABLE) {
            mAppRtcClient.sendData(Constants.WEBSOCKET_TYPE_REQUEST_CALL, 1, mClientId);
            mCCCallStatus = Constants.CC_CALL_WAITING_ANSWER;
            bCallCC.setBackgroundResource(R.drawable.button_drop_ct);
            onNewSystemMessage(getString(R.string.calling_crisis_center), System.currentTimeMillis());
        } else if (mCCCallStatus == Constants.CC_CALL_WAITING_ANSWER || mCCCallStatus == Constants.CC_CALL_ONGOING || mCCCallStatus == Constants.CC_CALL_ONHOLD) {
            mAppRtcClient.sendData(Constants.WEBSOCKET_TYPE_REQUEST_CALL, 0, mClientId);
            mCCCallStatus = Constants.CC_CALL_AVAILABLE;
            bCallCC.setBackgroundResource(R.drawable.button_call_ct);
            onNewSystemMessage(getString(R.string.call_crisis_center_ended), System.currentTimeMillis());
            if (mPCC != null) {
                mPCC.muteRemoteAudioStream();
            }
        }
    }

    /**
     * On send chat message actions
     */
    private void onSendMessageClick() {
        log("onSendMessageClick()");
        String message = etMessage.getText().toString().trim();
        if (message.length() > 0) {
            long timestamp = System.currentTimeMillis();
            log("Message=" + message + "; type=OWN; timestamp=" + timestamp);
            sendOwnMessage(new ChatMessageObject(Constants.INVALID_INT_ID, message, Constants.MESSAGE_TYPE_OWN, timestamp));
            etMessage.setText("");
        }
    }

    /**
     * Sends user chat message
     *
     * @param cmo ChatMessageObject
     */
    private void sendOwnMessage(ChatMessageObject cmo) {
        boolean updateUI = !cmo.isFromQueue();

        if (mAppRtcClient != null && mAppRtcClient.getConnectionState() == WebSocketRTCClient.ConnectionState.CONNECTED) {
            log("sendOwnMessage() sending via websocket connection");
            // Send message over WebSocket connection
            mAppRtcClient.sendData(Constants.WEBSOCKET_TYPE_CHAT_MESSAGE, cmo.getMessageText(), mClientId);
        } else {
            log("sendOwnMessage() adding to queue");
            // Add message to queue, mark it as from queue
            cmo.setIsFromQueue(true);
            mChatMessagesQueue.add(cmo);
        }

        if (updateUI) {
            // Update UI only if message is not from queue
            boolean shouldScrollToBottom = mLayoutManager.findLastCompletelyVisibleItemPosition() == mData.size() - 1;
            mData.add(cmo);
            mAdapter.notifyItemInserted(mData.size() - 1);

            if (shouldScrollToBottom) {
                rv.scrollToPosition(mData.size() - 1);
            }
        }
    }

    /**
     * Sends battery level
     *
     * @param currentBatteryLevel int
     */
    private void sendBatteryLevel(int currentBatteryLevel) {
        log("sendBatteryLevel() currentBatteryLevel=" + currentBatteryLevel);
        if (mShelterStatus == Constants.SHELTER_STATUS_ON && mShelterVideoState == Constants.SHELTER_WATCHING &&
                mAppRtcClient != null && mAppRtcClient.getConnectionState() == WebSocketRTCClient.ConnectionState.CONNECTED) {
            if (mPendingBatteryLevel != Constants.INVALID_INT_ID) {
                mPendingBatteryLevel = Constants.INVALID_INT_ID;
            }
            mPrevBatteryLevel = currentBatteryLevel;
            // Send message over WebSocket connection
            mAppRtcClient.sendData(Constants.WEBSOCKET_TYPE_BATTERY_LEVEL, currentBatteryLevel, mClientId);
        } else {
            log("Not sending battery level, no connection, setting pending");
            mPendingBatteryLevel = currentBatteryLevel;
        }
    }

    /**
     * Sends operator message (only updates UI)
     *
     * @param cmo             ChatMessageObject
     * @param sortByTimestamp should message be added according to timestamp
     */
    private void sendOperatorMessage(ChatMessageObject cmo, boolean sortByTimestamp, boolean shouldScrollToBottom) {
        if (!cmo.getMessageText().contentEquals("")) {
            int insertedPosition = Constants.INVALID_INT_ID;
            if (sortByTimestamp) {
                // Find where to insert new message according to timestamp
                int dataSize = mData.size();
                if (dataSize > 0) {
                    for (int i = dataSize - 1; i >= 0; i--) {
                        ChatMessageObject cmoInList = mData.get(i); // From cmo object
                        if (cmo.getTimestamp() > cmoInList.getTimestamp()) {
                            insertedPosition = i + 1;
                            break;
                        }
                    }

                    if (insertedPosition == Constants.INVALID_INT_ID) {
                        insertedPosition = 0;
                    }
                } else {
                    insertedPosition = dataSize;
                }
            }

            // Should scroll to bottom
            if (shouldScrollToBottom) {
                shouldScrollToBottom = mLayoutManager.findLastCompletelyVisibleItemPosition() == mData.size() - 1;
            }

            if (insertedPosition == Constants.INVALID_INT_ID) { // Insert to the end
                mData.add(cmo);
                insertedPosition = mData.size() - 1;
            } else {
                mData.add(insertedPosition, cmo);
            }

            mAdapter.notifyItemInserted(insertedPosition);

            if (shouldScrollToBottom) {
                rv.scrollToPosition(mData.size() - 1);
            }
        }
    }

    /**
     * Sends system chat message (only updates ui)
     *
     * @param cmo ChatMessageObject
     */
    private void sendSystemMessage(ChatMessageObject cmo) {
        log("sendSystemMessage() message=" + cmo.getMessageText());
        sendSystemMessage(cmo, true);
    }

    /**
     * Sends system chat message (only updates ui)
     *
     * @param cmo                  ChatMessageObject
     * @param shouldScrollToBottom boolean
     */
    private void sendSystemMessage(ChatMessageObject cmo, boolean shouldScrollToBottom) {
        log("sendSystemMessage() message=" + cmo.getMessageText() + "; shouldScrollToBottom=" + shouldScrollToBottom);
        // If message is empty
        if (cmo.getMessageText().contentEquals("")) {
            return;
        }
        // If its app message and its equal to last one
        if (cmo.getMessageType() == Constants.MESSAGE_TYPE_APP && mData.size() > 0) {
            int size = mData.size();
            for (int i = size - 1; i >= 0; i--) {
                if (mData.get(i).getMessageType() == Constants.MESSAGE_TYPE_APP) {
                    if (mData.get(i).getMessageText().equals(cmo.getMessageText())) {
                        return;
                    }
                    break;
                }
            }
        }
        // Its good message
        // Should scroll to bottom
        if (shouldScrollToBottom) {
            shouldScrollToBottom = mLayoutManager.findLastCompletelyVisibleItemPosition() == mData.size() - 1;
        }
        // Add it
        mData.add(cmo);
        mAdapter.notifyItemInserted(mData.size() - 1);

        if (shouldScrollToBottom) {
            rv.scrollToPosition(mData.size() - 1);
        }
    }

    /**
     * Shows chat overlay
     */
    private void showChatOverlay() {
        if (!mIsChatOverlayFading) {
            if (rlOverlay.getVisibility() == View.GONE) {
                hideSoftKeyboard();
                mIsChatOverlayFading = true;
                mFadeInOverlay.start();
            }
        } else {
            mPendingShowChatOverlay = true;
        }
    }

    /**
     * Hides chat overlay
     */
    private void hideChatOverlay() {
        if (!mIsChatOverlayFading && rlOverlay.getVisibility() == View.VISIBLE) {
            mIsChatOverlayFading = true;
            mFadeOutOverlay.start();
        }
    }

    /**
     * Hides soft keyboard
     */
    private void hideSoftKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            View view = this.getCurrentFocus();
            if (view != null) {
                IBinder ib = view.getWindowToken();
                if (ib != null) {
                    imm.hideSoftInputFromWindow(ib, 0);
                }
            }
            rlRoot.requestFocus();
        } catch (Exception e) {
            // Nothing to do
        }
    }

    /**
     * Handles trigger alarm request result
     */
    private void handleTriggerAlarmRequestResult(long requestId, boolean isInitial) {
        log("handleTriggerAlarmRequestResult()");
        if (isInitial) {
            mTriggerAlarmInitialRequestId = Constants.INVALID_LONG_ID;
        } else {
            mTriggerAlarmRequestId = Constants.INVALID_LONG_ID;
        }

        // Check request HTTP status code
        int httpStatusCode = AppContext.get().getRequestManager().getRequestStatusCode(requestId);

        log("HTTP status code:" + httpStatusCode);

        boolean retryTriggerAlarm = false;

        switch (httpStatusCode) {
            case Constants.HTTP_STATUS_INTERNAL_APP_ERROR:
            case Constants.HTTP_STATUS_BAD_REQUEST:
            case Constants.HTTP_STATUS_CODE_INTERNAL_SERVER_ERROR:
            case Constants.HTTP_STATUS_CODE_NOT_AVAILABLE:
            case Constants.HTTP_STATUS_CODE_NOT_FOUND:
                log("Got request error");
                retryTriggerAlarm = true;
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
                        retryTriggerAlarm = true;
                    }

                    if (success) {
                        if (isInitial) {
                            if (!responseJson.isNull(Constants.REQUEST_RESPONSE_PARAM_MESSAGE)) {
                                String ctMsg = Constants.INVALID_STRING_ID;
                                try {
                                    ctMsg = responseJson.getString(Constants.REQUEST_RESPONSE_PARAM_MESSAGE);
                                } catch (JSONException e) {
                                    log("Initial alarm trigger response does not contain " + Constants.REQUEST_RESPONSE_PARAM_MESSAGE + " param");
                                }
                                if (!ctMsg.equals(Constants.INVALID_STRING_ID)) {
                                    sendOperatorMessage(new ChatMessageObject(Constants.INVALID_INT_ID, ctMsg, Constants.MESSAGE_TYPE_OPERATOR, System.currentTimeMillis()), false, true);
                                } else {
                                    log("Initial alarm trigger response does not contain " + Constants.REQUEST_RESPONSE_PARAM_MESSAGE + " param");
                                }
                            } else {
                                log("Initial alarm trigger response does not contain " + Constants.REQUEST_RESPONSE_PARAM_MESSAGE + " param");
                            }
                        }
                    } else {
                        retryTriggerAlarm = true;
                    }
                } else {
                    log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_SUCCESS);
                    retryTriggerAlarm = true;
                }
            } else {
                log("Unable to parse response JSONObject");
                retryTriggerAlarm = true;
            }
        }

        if (retryTriggerAlarm) {
            if (isInitial) {
                triggerAlarm(true);
            }
        }
    }

    /**
     * Triggers alarm
     *
     * @param isInitial if alarm trigger is initial
     */
    private void triggerAlarm(boolean isInitial) {
        log("triggerAlarm() isInitial=" + isInitial);
        if (isInitial) {
            mTriggerAlarmInitialRequestCount++;
            if (mTriggerAlarmInitialRequestCount > DefaultParameters.REQUEST_REPEAT_COUNT_ON_ERROR) {
                return;
            }
            log("triggerAlarm() initial count=" + mTriggerAlarmInitialRequestCount);
            mTriggerAlarmInitialRequestId = AppContext.get().getRequestManager().makeRequest(new TriggerAlarmRequest(AppUser.get().getDeviceUID(), 0));
        } else {
            if (mTriggerAlarmRequestId == Constants.INVALID_LONG_ID) {
                mTriggerAlarmRequestId = AppContext.get().getRequestManager().makeRequest(new TriggerAlarmRequest(AppUser.get().getDeviceUID(), 1));
            }
        }
    }

    /**
     * Checks for GCM queued messages
     */
    private void checkForGCMQueuedMessages() {
        int count = AppUser.get().getGCMMessagesQueueSize();
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                GCMMessageObject gcmMessageObject = AppUser.get().getGCMMessageFromQueue(i);
                sendSystemMessage(new ChatMessageObject(gcmMessageObject.getId(), gcmMessageObject.getContent(), Constants.MESSAGE_TYPE_PUSH, gcmMessageObject.getTimestamp()));
            }
            AppUser.get().clearGCMMessagesQueue();
        }
    }

    /**
     * Calls police number
     */
    private void callPolice() {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + AppUser.get().getPoliceNumber()));
        startActivity(callIntent);
    }

    /**
     * Stops video source
     */
    private void stopVideoSource() {
        log("stopVideoSource()");
        if (mPCC != null && mIceConnected) {
            log("stopVideoSource() stopping");
            mPCC.stopVideoSource();
        }
        log("stopVideoSource() end");
    }

    /**
     * Resumes video source
     */
    private void resumeVideoSource() {
        log("resumeVideoSource()");
        if (mPCC != null && mIceConnected) {
            log("resumeVideoSource() resuming");
            mPCC.startVideoSource();
        }
        log("resumeVideoSource() end");
    }

    @Override
    public void onGotItItemClick(int position) {
        log("onGotItItemClick() pos=" + position);
        try {
            log("Message id = " + mData.get(position).getId());
            AppContext.get().getRequestManager().makeRequest(new GotItRequest(mData.get(position).getId()));
            mData.get(position).setGotIt(true);
            mAdapter.notifyItemChanged(position);
        } catch (Exception e) {
            // Nothing to do
        }
    }

    @Override
    public void onGPSOffItemClick(int position) {
        log("onGPSOffItemClick() pos=" + position);
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (Exception ignored) {

        }
    }

    // WS reconnection
    private final Runnable mWSReconnection = new Runnable() {
        @Override
        public void run() {
            log("mWSReconnection run()");
            if (Utils.isWiFiConnected(getApplicationContext()) && !mPendingPCClose) {
                initializeWebSocketConnection();
            }
        }
    };

    // Camera and microphone in use checker
    private final Runnable mCMInUseChecker = new Runnable() {
        @Override
        public void run() {
            log("mCMInUseChecker run()");
            if ((Utils.isCameraAvailable()) && (Utils.isMicrophoneAvailable(MainActivity.this))) {
                log("mCMInUseChecker camera and microphone is now available");
                stopCMChecker();
                preparePeerConnectionParameters(true, false);
                createPeerConnectionFactory();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.camera_and_microphone_is_now_available), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
                    }
                });
            } else {
                log("mCMInUseChecker camera and microphone is still in use");
                mCMInUseHandler.postDelayed(mCMInUseChecker, CM_CHECKING_INTERVAL);
            }
        }
    };

    /**
     * Starts camera and microphone in use checker
     */
    private void startCMChecker() {
        log("startCMChecker()");
        if (mCMInUseHandler != null) {
            mCMInUseHandler.removeCallbacks(mCMInUseChecker);
        }
        mCMInUseHandler = new Handler();
        mCMInUseChecker.run();
    }

    /**
     * Stops camera and microphone in use checker
     */
    private void stopCMChecker() {
        log("stopCMChecker()");
        if (mCMInUseHandler != null) {
            mCMInUseHandler.removeCallbacks(mCMInUseChecker);
        }
        mCMInUseHandler = null;
    }

    /**
     * Auto fade AsyncTask, responsible for showing chat overlay automatically after 5s
     */
    private class AutoFadeAsyncTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                log("Launched AutoFadeAsyncTask");
                Thread.sleep(AUTO_FADE_AFTER_MS);
            } catch (Exception e) {
                // Nothing to do
            }
            return null;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            log("AutoFadeAsyncTask cancelled");
        }

        @Override
        protected void onPostExecute(Void result) {
            log("AutoFadeAsyncTask completed");
            if (!mIsChatOverlayFading && rlOverlay.getVisibility() != View.VISIBLE && !isCancelled()) {
                showChatOverlay();
            } else {
                log("AutoFade not needed or canceled");
            }
        }
    }

    // Proximity sensor
    // This method is called when the proximity sensor reports a state change,
    // e.g. from "NEAR to FAR" or from "FAR to NEAR".
    private void onProximitySensorChangedState() {
        if (mProximitySensor.sensorReportsNearState()) {
            // Sensor reports that a "handset is being held up to a person's ear",
            // or "something is covering the light sensor".
            if (mAutoFadeAsyncTask != null) {
                mAutoFadeAsyncTask.cancel(true);
                mAutoFadeAsyncTask = null;
            }
            mScreenLocked = true;
            showChatOverlay();
        } else {
            // Sensor reports that a "handset is removed from a person's ear", or
            // "the light sensor is no longer covered".
            mScreenLocked = false;
        }
    }

    /**
     * Toggles full screen mode
     *
     * @param fullscreen boolean
     */
    private void toggleFullscreen(boolean fullscreen) {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        View decorView = getWindow().getDecorView();
        if (fullscreen) {
            attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            if (Build.VERSION.SDK_INT >= 16) {
                uiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
            if (Build.VERSION.SDK_INT < 19) {
                decorView.setSystemUiVisibility(View.GONE);
            } else {
                uiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            decorView.setSystemUiVisibility(uiOptions);
        } else {
            attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
            int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
            if (Build.VERSION.SDK_INT < 19) {
                decorView.setSystemUiVisibility(View.VISIBLE);
            }
            decorView.setSystemUiVisibility(uiOptions);
        }
        getWindow().setAttributes(attrs);
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

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {
        log("onPeerConnectionStatsReady()");
    }

    /**
     * Overrides editor action "done" to send chat message instantly
     *
     * @param v        TextView
     * @param actionId int
     * @param event    KeyEvent
     * @return boolean
     */
    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (v.getId() == R.id.et_message) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onSendMessageClick();
                hideSoftKeyboard();
                return true;
            }
        }
        return false;
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
     * WifiBroadcastReceiver callback called when WiFi network is available
     */
    @Override
    public void onNetworkAvailable() {
        log("onNetworkAvailable()");
        if (mWifiStateInitial) {
            mWifiStateInitial = false;
        } else {
            mShelterStatus = Constants.SHELTER_STATUS_OFF;
            initializeWebSocketConnection();
        }
    }

    /**
     * WifiBroadcastReceiver callback called when WiFi network is unavailable
     */
    @Override
    public void onNetworkUnavailable() {
        log("onNetworkUnavailable()");
        if (!mWifiStateInitial && mWasWSConnected) {
            sendSystemMessage(new ChatMessageObject(Constants.INVALID_INT_ID, getString(R.string.connection_lost), Constants.MESSAGE_TYPE_APP, System.currentTimeMillis()));
        }
        mShelterPeerConnectionState = 0;
        disconnect(true);
        mShelterStatus = Constants.SHELTER_STATUS_NOT_KNOWN;
        onShelterStatus(Constants.SHELTER_STATUS_OFF);
    }

    private boolean canCreatePeerConnection(String tag) {
        log("canCreatePeerConnection() called from TAG=" + tag);
        boolean canCreate;
        if (!mPeerConnectionCreating
                && mShelterStatus == Constants.SHELTER_STATUS_ON
                && mAppRtcClient != null && mAppRtcClient.getConnectionState() == WebSocketRTCClient.ConnectionState.CONNECTED
                && !mIceConnected && !mPendingPCClose && mShelterPeerConnectionState == 1
                && mIceConnectionClosed && mSignalingParameters != null && mPCC != null
                ) {
            while (mCanCreatePeerConnectionChecking) {
                // Wait
            }
            mCanCreatePeerConnectionChecking = true;
            preparePeerConnectionParameters(false, true);
            canCreate = Utils.isWiFiConnected(this) && mCameraIsAvailable && mMicrophoneIsAvailable;
            mCanCreatePeerConnectionChecking = false;
        } else {
            canCreate = false;
        }
        log("canCreatePeerConnection() canCreate=" + canCreate);
        return canCreate;
    }

    // Location part

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * LocationServices API.
     */
    private synchronized void buildGoogleApiClientIfNeeded() {
        log("buildGoogleApiClient()");
        if (mGoogleApiClient == null) {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
                mGoogleApiClient = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
                createLocationRequest();
            } else {
                mGoogleApiClient = null;
                log("buildGoogleApiClient() unable to connect to google play services");
            }
        }
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    private void createLocationRequest() {
        log("createLocationRequest()");
        if (mLocationRequest == null) {
            mLocationRequest = new LocationRequest();

            // Sets the desired interval for active location updates. This interval is
            // inexact. You may not receive updates at all if no location sources are available, or
            // you may receive them slower than requested. You may also receive updates faster than
            // requested if other applications are requesting location at a faster interval.
            mLocationRequest.setInterval(DefaultParameters.GPS_UPDATE_INTERVAL_IN_MILLISECONDS);

            // Sets the fastest rate for active location updates. This interval is exact, and your
            // application will never receive updates faster than this value.
            mLocationRequest.setFastestInterval(DefaultParameters.GPS_UPDATE_INTERVAL_IN_MILLISECONDS_FASTEST);

            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }
    }

    /**
     * Requests location updates from the FusedLocationApi.
     */
    private void startLocationUpdates() {
        log("startLocationUpdates()");
        if (AppUser.get().getBCSUseGPS()) {
            try {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            } catch (Exception e) {
                log("Error starting location updates:" + e.getMessage());
            }
        }
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private void stopLocationUpdates() {
        log("stopLocationUpdates()");
        try {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        } catch (Exception e) {
            log("Error stopping location updates:" + e.getMessage());
        }
    }

    // Callbacks
    @Override
    public void onConnected(Bundle bundle) {
        log("Connected to GoogleApiClient");
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        log("onConnectionSuspended()");
        buildGoogleApiClientIfNeeded();
        if (mGoogleApiClient != null) {
            try {
                mGoogleApiClient.connect();
            } catch (Exception e) {
                log("Error connecting google api client:" + e.getMessage());
            }
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        log("onConnectionFailed()");
        buildGoogleApiClientIfNeeded();
        if (mGoogleApiClient != null) {
            try {
                mGoogleApiClient.connect();
            } catch (Exception e) {
                log("Error connecting google api client:" + e.getMessage());
            }
        }
    }

    @Override
    public void onLocationChanged(Location currentLocation) {
        log("onLocationChanged() location=" + (currentLocation != null ? currentLocation.toString() : "null"));
        if (AppUser.get().getBCSUseGPS()) {
            if (currentLocation != null && (mPrevLocation == null ||
                    (currentLocation.getLatitude() != mPrevLocation.getLatitude() &&
                            currentLocation.getLongitude() != mPrevLocation.getLongitude()))) {
                sendLocation(currentLocation);
            }
        } else {
            log("onLocationChanged() functionality is disabled by shelter");
        }
    }

    /**
     * Sends location
     *
     * @param currentLocation Location
     */
    private void sendLocation(Location currentLocation) {
        log("sendLocation() currentLocation=" + (currentLocation != null ? currentLocation.toString() : "null"));
        if (AppUser.get().getBCSUseGPS()) {
            if (currentLocation != null) {
                if (mShelterStatus == Constants.SHELTER_STATUS_ON &&
                        mAppRtcClient != null && mAppRtcClient.getConnectionState() == WebSocketRTCClient.ConnectionState.CONNECTED) {
                    if (mPendingLocation != null) {
                        mPendingLocation = null;
                    }
                    mPrevLocation = currentLocation;
                    // Send message over WebSocket connection
                    JSONObject locationJson;
                    try {
                        locationJson = new JSONObject();
                        locationJson.put("LAT", currentLocation.getLatitude());
                        locationJson.put("LON", currentLocation.getLongitude());
                    } catch (JSONException jsonE) {
                        log("failed to send location");
                        locationJson = null;
                    }

                    if (locationJson != null) {
                        mAppRtcClient.sendData(Constants.WEBSOCKET_TYPE_LOCATION, locationJson.toString(), mClientId);
                    }
                } else {
                    log("Not sending location, no connection, setting pending");
                    mPendingLocation = currentLocation;
                }
            }
        } else {
            log("onLocationChanged() functionality is disabled by shelter");
        }
    }

    private void checkPendingLocation() {
        log("checkPendingLocation()");
        if (mPrevLocation != null) {
            sendLocation(mPrevLocation);
        }
    }

    private void checkGPSStatus() {
        log("checkGPSStatus()");
        if (AppUser.get().getBCSUseGPS()) {
            // Remove any GPS status message before
            if (mData != null && mData.size() > 0) {
                Iterator<ChatMessageObject> iter = mData.iterator();

                while (iter.hasNext()) {
                    ChatMessageObject messageObject = iter.next();
                    if (messageObject.getMessageType() == Constants.MESSAGE_TYPE_GPS_OFF) {
                        iter.remove();
                        mAdapter.notifyDataSetChanged();
                    }
                }
            }

            if (!isGPSOn()) {
                // Show new GPS status message
                sendSystemMessage(new ChatMessageObject(
                        Constants.INVALID_INT_ID,
                        getString(R.string.gps_is_off),
                        Constants.MESSAGE_TYPE_GPS_OFF,
                        System.currentTimeMillis()));
            }
        } else {
            log("checkGPSStatus() funcionality is disabled by shelter");
        }
    }

    private boolean isGPSOn() {
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager != null && !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return false;
        }
        return true;
    }

    private void checkGPSStatusMessage() {
        log("checkGPSStatusMessage()");
        if (mData != null && mData.size() > 0 && isGPSOn()) {
            Iterator<ChatMessageObject> iter = mData.iterator();

            while (iter.hasNext()) {
                ChatMessageObject messageObject = iter.next();
                if (messageObject.getMessageType() == Constants.MESSAGE_TYPE_GPS_OFF) {
                    iter.remove();
                    mAdapter.notifyDataSetChanged();
                }
            }
        }
    }
}