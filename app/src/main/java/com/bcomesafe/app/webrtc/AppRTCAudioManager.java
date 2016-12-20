/*
 * libjingle
 * Copyright 2014 Google Inc.
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

package com.bcomesafe.app.webrtc;

import com.bcomesafe.app.utils.RemoteLogUtils;
import com.bcomesafe.app.webrtcutils.AppRTCUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.util.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * AppRTCAudioManager manages all audio related parts.
 */
public class AppRTCAudioManager {

    // Debugging
    private static final boolean D = true;
    private static final String TAG = AppRTCAudioManager.class.getSimpleName();

    /**
     * AudioDevice is the names of possible audio devices that we currently
     * support.
     */
    // TODO add support for BLUETOOTH as well
    public enum AudioDevice {
        SPEAKER_PHONE,
        WIRED_HEADSET,
        EARPIECE,
    }

    // Variables
    private final Context mAppRTCContext;
    private final Runnable mOnStateChangeListener;
    private boolean mInitialized = false;
    private final AudioManager mAudioManager;
    private int mSavedAudioMode = AudioManager.MODE_INVALID;
    // TODO change this value if needed to mute speaker from start
    private boolean mSavedIsSpeakerPhoneOn = false;
    private boolean mSavedIsMicrophoneMute = false;

    // TODO change this value if needed to change default audio device
    private final AudioDevice mDefaultAudioDevice = AudioDevice.EARPIECE;

    // Contains the currently selected audio device.
    private AudioDevice mSelectedAudioDevice;

    // Contains a list of available audio devices. A Set collection is used to
    // avoid duplicate elements.
    private final Set<AudioDevice> mAudioDevices = new HashSet<>();

    // Broadcast receiver for wired headset intent broadcasts.
    private BroadcastReceiver mWiredHeadsetReceiver;


    /**
     * Construction
     */
    public static AppRTCAudioManager create(Context context, Runnable deviceStateChangeListener) {
        return new AppRTCAudioManager(context, deviceStateChangeListener);
    }

    private AppRTCAudioManager(Context context, Runnable deviceStateChangeListener) {
        mAppRTCContext = context;
        mOnStateChangeListener = deviceStateChangeListener;
        mAudioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        if (D) {
            AppRTCUtils.logDeviceInfo(TAG);
        }
    }

    public void init() {
        log("init");
        if (mInitialized) {
            return;
        }

        // Store current audio state so we can restore it when close() is called.
        mSavedAudioMode = mAudioManager.getMode();

        // TODO added to mute speaker.
        setSpeakerphoneOn(mSavedIsSpeakerPhoneOn);

        mSavedIsSpeakerPhoneOn = mAudioManager.isSpeakerphoneOn();
        mSavedIsMicrophoneMute = mAudioManager.isMicrophoneMute();

        // Request audio focus before making any device switch.
        mAudioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

        // OLD CODE:
        // The app shall always run in COMMUNICATION mode since it will
        // result in best possible "VoIP settings", like audio routing, volume
        // control etc.
        // audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        // UPDATED 2015 05 20:
        // Start by setting RINGTONE as default audio mode. The native WebRTC
        // audio layer will switch to COMMUNICATION mode when the first
        // streaming
        // session starts and return to RINGTONE mode when all streaming stops.
        // audioManager.setMode(AudioManager.MODE_RINGTONE);
        // UPDATED 2015 07 07:
        // Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
        // required to be in this mode when playout and/or recording starts for
        // best possible VoIP performance.
        // TODO(henrika): we migh want to start with RINGTONE mode here instead.
        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        // Always disable microphone mute during a WebRTC call.
        setMicrophoneMute(false);

        // Do initial selection of audio device. This setting can later be changed
        // by adding/removing a wired headset.
        updateAudioDeviceState(hasWiredHeadset());

        // Register receiver for broadcast intents related to adding/removing a
        // wired headset (Intent.ACTION_HEADSET_PLUG).
        registerForWiredHeadsetIntentBroadcast();

        mInitialized = true;
    }

    public void close() {
        log("close");
        if (!mInitialized) {
            return;
        }

        unregisterForWiredHeadsetIntentBroadcast();

        // Restore previously stored audio states.
        setSpeakerphoneOn(mSavedIsSpeakerPhoneOn);
        setMicrophoneMute(mSavedIsMicrophoneMute);
        mAudioManager.setMode(mSavedAudioMode);
        mAudioManager.abandonAudioFocus(null);

        mInitialized = false;
    }

    /**
     * Changes selection of the currently active audio device.
     */
    private void setAudioDevice(AudioDevice device) {
        log("setAudioDevice(device=" + device + ")");
        // TODO removed
        // AppRTCUtils.assertIsTrue(mAudioDevices.contains(device));
        switch (device) {
            case SPEAKER_PHONE:
                setSpeakerphoneOn(true);
                mSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
                break;
            case EARPIECE:
                setSpeakerphoneOn(false);
                mSelectedAudioDevice = AudioDevice.EARPIECE;
                break;
            case WIRED_HEADSET:
                setSpeakerphoneOn(false);
                mSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
                break;
            default:
                log("Invalid audio device selection");
                break;
        }
        onAudioManagerChangedState();
    }

    /**
     * Returns current set of available/selectable audio devices.
     */
    @SuppressWarnings("unused")
    public Set<AudioDevice> getAudioDevices() {
        return Collections.unmodifiableSet(new HashSet<>(mAudioDevices));
    }

    /**
     * Returns the currently selected audio device.
     */
    @SuppressWarnings("unused")
    public AudioDevice getSelectedAudioDevice() {
        return mSelectedAudioDevice;
    }

    /**
     * Registers receiver for the broadcasted intent when a wired headset is
     * plugged in or unplugged. The received intent will have an extra
     * 'state' value where 0 means unplugged, and 1 means plugged.
     */
    private void registerForWiredHeadsetIntentBroadcast() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);

        /** Receiver which handles changes in wired headset availability. */
        mWiredHeadsetReceiver = new BroadcastReceiver() {
            private static final int STATE_UNPLUGGED = 0;
            private static final int STATE_PLUGGED = 1;
            private static final int HAS_NO_MIC = 0;
            private static final int HAS_MIC = 1;

            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra("state", STATE_UNPLUGGED);
                int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
                String name = intent.getStringExtra("name");
                log("BroadcastReceiver.onReceive" + AppRTCUtils.getThreadInfo()
                        + ": "
                        + "a=" + intent.getAction()
                        + ", s=" + (state == STATE_UNPLUGGED ? "unplugged" : "plugged")
                        + ", m=" + (microphone == HAS_MIC ? "mic" : "no mic")
                        + ", n=" + name
                        + ", sb=" + isInitialStickyBroadcast());

                switch (state) {
                    case STATE_UNPLUGGED:
                        updateAudioDeviceState(false);
                        break;
                    case STATE_PLUGGED:
                        if (mSelectedAudioDevice != AudioDevice.WIRED_HEADSET) {
                            updateAudioDeviceState(true);
                        }
                        break;
                    default:
                        log("Invalid state");
                        break;
                }
            }
        };
        mAppRTCContext.registerReceiver(mWiredHeadsetReceiver, filter);
    }

    /**
     * Unregister receiver for broadcasted ACTION_HEADSET_PLUG intent.
     */
    private void unregisterForWiredHeadsetIntentBroadcast() {
        mAppRTCContext.unregisterReceiver(mWiredHeadsetReceiver);
        mWiredHeadsetReceiver = null;
    }

    /**
     * Sets the speaker phone mode.
     */
    private void setSpeakerphoneOn(boolean on) {
        boolean wasOn = mAudioManager.isSpeakerphoneOn();
        if (wasOn == on) {
            return;
        }
        mAudioManager.setSpeakerphoneOn(on);
    }

    /**
     * Sets the microphone mute state.
     */
    private void setMicrophoneMute(boolean on) {
        boolean wasMuted = mAudioManager.isMicrophoneMute();
        if (wasMuted == on) {
            return;
        }
        mAudioManager.setMicrophoneMute(on);
    }

    /**
     * Gets the current earpiece state.
     */
    private boolean hasEarpiece() {
        return mAppRTCContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
    }

    /**
     * Checks whether a wired headset is connected or not.
     * This is not a valid indication that audio playback is actually over
     * the wired headset as audio routing depends on other conditions. We
     * only use it as an early indicator (during initialization) of an attached
     * wired headset.
     */
    @SuppressWarnings("deprecation")
    private boolean hasWiredHeadset() {
        return mAudioManager.isWiredHeadsetOn();
    }

    /**
     * Update list of possible audio devices and make new device selection.
     */
    private void updateAudioDeviceState(boolean hasWiredHeadset) {
        // Update the list of available audio devices.
        mAudioDevices.clear();
        if (hasWiredHeadset) {
            // If a wired headset is connected, then it is the only possible option.
            mAudioDevices.add(AudioDevice.WIRED_HEADSET);
        } else {
            // No wired headset, hence the audio-device list can contain speaker
            // phone (on a tablet), or speaker phone and earpiece (on mobile phone).
            mAudioDevices.add(AudioDevice.SPEAKER_PHONE);
            if (hasEarpiece()) {
                mAudioDevices.add(AudioDevice.EARPIECE);
            }
        }
        log("audioDevices: " + mAudioDevices);

        // Switch to correct audio device given the list of available audio devices.
        if (hasWiredHeadset) {
            setAudioDevice(AudioDevice.WIRED_HEADSET);
        } else {
            setAudioDevice(mDefaultAudioDevice);
        }
    }

    /**
     * Called each time a new audio device has been added or removed.
     */
    private void onAudioManagerChangedState() {
        log("onAudioManagerChangedState: devices=" + mAudioDevices + ", selected=" + mSelectedAudioDevice);
        if (mOnStateChangeListener != null) {
            // Run callback to notify a listening client. The client can then
            // use public getters to query the new state.
            mOnStateChangeListener.run();
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
