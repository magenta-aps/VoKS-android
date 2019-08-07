/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.bcomesafe.app.objects.GCMMessageObject;
import com.bcomesafe.app.utils.VolumeBroadcastReceiver;

/**
 * User information storage on shared preferences
 */
@SuppressLint("CommitPrefEdits")
public class AppUser {
    // AppUser
    private static final AppUser mUser = new AppUser();

    // Shared preferences
    private final SharedPreferences mUserStore = AppContext.get().getSharedPreferences(Constants.SHARED_PREFERENCES_APP_USER, Context.MODE_PRIVATE);

    /**
     * Returns instance of AppUser
     *
     * @return AppUser
     */
    public static AppUser get() {
        return mUser;
    }

    /**
     * Sets unique device identification number
     *
     * @param uid - unique device identification number
     */
    public void setDeviceUID(String uid) {
        mUserStore.edit().putString("device_uid", uid + "_" + Constants.REQUEST_PARAM_ANDROID).apply();
    }

    /**
     * Returns device unique identification number
     *
     * @return String - unique device identification number
     */
    public String getDeviceUID() {
        return mUserStore.getString("device_uid", Constants.INVALID_STRING_ID);
    }

    /**
     * Sets device wifi mac address
     *
     * @param mac_address - device wifi mac address
     */
    public void setDeviceMacAddress(String mac_address) {
        mUserStore.edit().putString("device_mac_address", mac_address).apply();
    }

    /**
     * Returns device wifi mac address
     *
     * @return String - device wifi mac address
     */
    public String getDeviceMacAddress() {
        return mUserStore.getString("device_mac_address", Constants.INVALID_STRING_ID);
    }

    /**
     * Sets GCM id
     *
     * @param gcmId - GCM id
     */
    public void setGCMId(String gcmId) {
        mUserStore.edit().putString("gcm_id", gcmId).apply();
    }

    /**
     * Returns GCM id
     *
     * @return String - GCM id
     */
    public String getGCMId() {
        return mUserStore.getString("gcm_id", Constants.INVALID_STRING_ID);
    }

    /**
     * Clears users data
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public void clearUserData() {
        SharedPreferences.Editor userStore = mUserStore.edit();
        userStore.clear();
        userStore.apply();
    }

    /**
     * Sets application version which was when GCM was registered
     */
    public void setGCMRegisteredAppVersion(int version) {
        mUserStore.edit().putInt("gcm_registered_app_version", version).apply();
    }

    /**
     * Gets application version which was when GCM was registered
     *
     * @return app version
     */
    public int getGCMRegisteredAppVersion() {
        return mUserStore.getInt("gcm_registered_app_version", Constants.INVALID_INT_ID);
    }

    /**
     * Sets OS version which was when GCM was registered
     */
    public void setGCMRegisteredOSVersion(int version) {
        mUserStore.edit().putInt("gcm_registered_os_version", version).apply();
    }

    /**
     * Gets OS version which was when GCM was registered
     *
     * @return OS version
     */
    public int getGCMRegisteredOSVersion() {
        return mUserStore.getInt("gcm_registered_os_version", Constants.INVALID_INT_ID);
    }


    /**
     * Sets if is device registered on BCS
     */
    @SuppressWarnings("SameParameterValue")
    public void setIsRegisteredOnBCS(boolean val) {
        mUserStore.edit().putBoolean("device_registered_on_bcs", val).apply();
    }

    /**
     * Gets if device is registered on BCS
     *
     * @return boolean
     */
    public boolean getIsRegisteredOnBCS() {
        return mUserStore.getBoolean("device_registered_on_bcs", false);
    }

    // GCM messages queue
    public void clearGCMMessagesQueue() {
        int count = getGCMMessagesQueueSize();
        for (int i = 0; i < count; i++) {
            mUserStore.edit().remove("gcm_messages_queue_id_" + i).apply();
            mUserStore.edit().remove("gcm_messages_queue_title_" + i).apply();
            mUserStore.edit().remove("gcm_messages_queue_content_" + i).apply();
            mUserStore.edit().remove("gcm_messages_queue_timestamp_" + i).apply();
        }
        mUserStore.edit().putInt("gcm_messages_queue_size", 0).apply();
    }

    public int getGCMMessagesQueueSize() {
        return mUserStore.getInt("gcm_messages_queue_size", 0);
    }

    public void addGCMMessageToQueue(int id, String title, String content, long timestamp) {
        int count = getGCMMessagesQueueSize();
        mUserStore.edit().putInt("gcm_messages_queue_id_" + count, id).apply();
        mUserStore.edit().putString("gcm_messages_queue_title_" + count, title).apply();
        mUserStore.edit().putString("gcm_messages_queue_content_" + count, content).apply();
        mUserStore.edit().putLong("gcm_messages_queue_timestamp_" + count, timestamp).apply();
        mUserStore.edit().putInt("gcm_messages_queue_size", (count + 1)).apply();
    }

    public GCMMessageObject getGCMMessageFromQueue(int id) {
        GCMMessageObject gcmMessageObject = new GCMMessageObject();
        gcmMessageObject.setId(mUserStore.getInt("gcm_messages_queue_id_" + id, Constants.INVALID_INT_ID));
        gcmMessageObject.setTitle(mUserStore.getString("gcm_messages_queue_title_" + id, Constants.INVALID_STRING_ID));
        gcmMessageObject.setContent(mUserStore.getString("gcm_messages_queue_content_" + id, Constants.INVALID_STRING_ID));
        gcmMessageObject.setTimestamp(mUserStore.getLong("gcm_messages_queue_timestamp_" + id, Constants.INVALID_LONG_ID));
        return gcmMessageObject;
    }

    // User ringer mode

    /**
     * Sets user ringer mode
     *
     * @param mode - user ringer mode
     */
    public void setUserRingerMode(int mode) {
        mUserStore.edit().putInt("user_ringer_mode", mode).apply();
    }

    /**
     * Gets user ringer mode
     *
     * @return ringer mode
     */
    public int getUserRingerMode() {
        return mUserStore.getInt("user_ringer_mode", Constants.INVALID_INT_ID);
    }

    // User stream volumes

    /**
     * Sets user alarm stream volume
     *
     * @param volume - int volume level
     */
    public void setUserStreamAlarmVolume(int volume) {
        mUserStore.edit().putInt("user_stream_alarm_volume", volume).apply();
    }

    /**
     * Gets user alarm stream volume
     *
     * @return volume
     */
    public int getUserStreamAlarmVolume() {
        return mUserStore.getInt("user_stream_alarm_volume", 0);
    }

    /**
     * Sets user music stream volume
     *
     * @param volume - int volume level
     */
    public void setUserStreamMusicVolume(int volume) {
        mUserStore.edit().putInt("user_stream_music_volume", volume).apply();
    }

    /**
     * Gets user music stream volume
     *
     * @return volume
     */
    public int getUserStreamMusicVolume() {
        return mUserStore.getInt("user_stream_music_volume", 0);
    }

    /**
     * Sets user notification stream volume
     *
     * @param volume - int volume level
     */
    public void setUserStreamNotificationVolume(int volume) {
        mUserStore.edit().putInt("user_stream_notification_volume", volume).apply();
    }

    /**
     * Gets user notification stream volume
     *
     * @return volume
     */
    public int getUserStreamNotificationVolume() {
        return mUserStore.getInt("user_stream_notification_volume", 0);
    }

    /**
     * Sets user system stream volume
     *
     * @param volume - int volume level
     */
    public void setUserStreamSystemVolume(int volume) {
        mUserStore.edit().putInt("user_stream_system_volume", volume).apply();
    }

    /**
     * Gets user system stream volume
     *
     * @return volume
     */
    public int getUserStreamSystemVolume() {
        return mUserStore.getInt("user_stream_system_volume", 0);
    }

    /**
     * Sets user ring stream volume
     *
     * @param volume - int volume level
     */
    public void setUserStreamRingVolume(int volume) {
        mUserStore.edit().putInt("user_stream_ring_volume", volume).apply();
    }

    /**
     * Gets user ring stream volume
     *
     * @return volume
     */
    public int getUserStreamRingVolume() {
        return mUserStore.getInt("user_stream_ring_volume", 0);
    }

    /**
     * Sets user ringer vibrate type
     *
     * @param type - int ringer type
     */
    public void setUserVibrateTypeRinger(int type) {
        mUserStore.edit().putInt("user_vibrate_type_ringer", type).apply();
    }

    /**
     * Gets user ringer vibrate type
     *
     * @return type
     */
    public int getUserVibrateTypeRinger() {
        return mUserStore.getInt("user_vibrate_type_ringer", 0);
    }

    /**
     * Sets user notification vibrate type
     *
     * @param type - int ringer type
     */
    public void setUserVibrateTypeNotification(int type) {
        mUserStore.edit().putInt("user_vibrate_type_notification", type).apply();
    }

    /**
     * Gets user notification vibrate type
     *
     * @return type
     */
    public int getUserVibrateTypeNotification() {
        return mUserStore.getInt("user_vibrate_type_notification", 0);
    }

    // BCS parameters

    /**
     * Sets BCS id
     *
     * @param BCSId - string
     */
    public void setBCSId(String BCSId) {
        mUserStore.edit().putString("bcs_id", BCSId).apply();
    }

    /**
     * Gets BCS id
     *
     * @return BCSId
     */
    public String getBCSId() {
        return mUserStore.getString("bcs_id", Constants.INVALID_STRING_ID);
    }

    /**
     * Sets BCS name
     *
     * @param BCSName - string
     */
    public void setBCSName(String BCSName) {
        mUserStore.edit().putString("bcs_name", BCSName).apply();
    }

    /**
     * Gets BCS name
     *
     * @return BCSName
     */
    public String getBCSName() {
        return mUserStore.getString("bcs_name", Constants.INVALID_STRING_ID);
    }

    /**
     * Sets BCS public
     *
     * @param isPublic - boolean
     */
    public void setBCSPublic(Boolean isPublic) {
        if (isPublic == null) {
            mUserStore.edit().remove("bcs_public").apply();
        } else {
            mUserStore.edit().putBoolean("bcs_public", isPublic).apply();
        }
    }

    /**
     * Gets BCS public
     *
     * @return is BCS public
     */
    public boolean getBCSPublic() {
        return mUserStore.getBoolean("bcs_public", true);
    }

    /**
     * Sets WS URL
     *
     * @param wsURL - string
     */
    public void setWsURL(String wsURL) {
        mUserStore.edit().putString("ws_url", wsURL).apply();
    }

    /**
     * Gets WS URL
     *
     * @return wsURL
     */
    public String getWsURL() {
        return mUserStore.getString("ws_url", Constants.INVALID_STRING_ID);
    }

    /**
     * Sets API URL
     *
     * @param apiURL - string
     */
    public void setAPIURL(String apiURL) {
        mUserStore.edit().putString("api_url", apiURL).apply();
    }

    /**
     * Gets API URL
     *
     * @return apiURL
     */
    public String getAPIURL() {
        return mUserStore.getString("api_url", Constants.INVALID_STRING_ID);
    }

    /**
     * Sets BCS use gps
     *
     * @param val - boolean
     */
    public void setBCSUseGPS(Boolean val) {
        mUserStore.edit().putBoolean("bcs_use_gps", val != null ? val : false).apply();
    }

    /**
     * Gets BCS use gps
     *
     * @return val - boolean
     */
    public boolean getBCSUseGPS() {
        return mUserStore.getBoolean("bcs_use_gps", false);
    }

    /**
     * Sets dev mode
     *
     * @param val - boolean
     */
    public void setDevMode(boolean val) {
        mUserStore.edit().putBoolean("dev_mode", val).apply();
    }

    /**
     * Gets dev mode
     *
     * @return devMode
     */
    public boolean getDevMode() {
        return mUserStore.getBoolean("dev_mode", false);
    }

    // User parameters

    /**
     * Sets user name
     *
     * @param userName - string
     */
    public void setUserName(String userName) {
        mUserStore.edit().putString("user_name", userName).apply();
    }

    /**
     * Gets user name
     *
     * @return userName
     */
    public String getUserName() {
        return mUserStore.getString("user_name", Constants.INVALID_STRING_ID);
    }

    /**
     * Sets user email
     *
     * @param userEmail - string
     */
    public void setUserEmail(String userEmail) {
        mUserStore.edit().putString("user_email", userEmail).apply();
    }

    /**
     * Gets user email
     *
     * @return userEmail
     */
    public String getUserEmail() {
        return mUserStore.getString("user_email", Constants.INVALID_STRING_ID);
    }

    /**
     * Adds alarm cmd
     *
     * @param cmd - string
     */
    public void addAlarmCmd(String cmd) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - mUserStore.getLong("alarm_cmd_last", 0L) > VolumeBroadcastReceiver.TRESHOLD_BETWEEN_PRESSES) {
            mUserStore.edit().putString("alarm_cmd", cmd).apply();
        } else {
            mUserStore.edit().putString("alarm_cmd", getAlarmCmd() + cmd).apply();
        }
        mUserStore.edit().putLong("alarm_cmd_last", currentTime).apply();
    }

    /**
     * Gets alarm cmd
     *
     * @return cmd
     */
    public String getAlarmCmd() {
        return mUserStore.getString("alarm_cmd", "");
    }

    /**
     * Clears alarm cmd
     */
    public void clearAlarmCmd() {
        mUserStore.edit().remove("alarm_cmd").apply();
    }

    /**
     * Gets police number
     *
     * @return policeNumber String
     */
    public String getPoliceNumber() {
        String policeNumber = mUserStore.getString("police_number", "");
        if (policeNumber.trim().length() == 0) {
            policeNumber = DefaultParameters.POLICE_NUMBER;
        }
        return policeNumber;
    }

    /**
     * Sets police number
     *
     * @param policeNumber String
     */
    public void setPoliceNumber(String policeNumber) {
        mUserStore.edit().putString("police_number", policeNumber != null ? policeNumber : "").apply();
    }

    /**
     * Sets BCS debug
     *
     * @param debug - boolean
     */
    public void setBCSDebug(Boolean debug) {
        if (debug == null) {
            debug = false;
        }
        mUserStore.edit().putBoolean("bcs_debug", debug).apply();
    }

    /**
     * Gets BCS debug
     *
     * @return BCSDebug
     */
    public boolean getBCSDebug() {
        return mUserStore.getBoolean("bcs_debug", false);
    }

    /**
     * Sets need tac
     *
     * @param val - boolean
     */
    public void setNeedTac(Boolean val) {
        if (val == null) {
            mUserStore.edit().remove("need_tac").apply();
        } else {
            mUserStore.edit().putBoolean("need_tac", val).apply();
        }
    }

    /**
     * Gets need tac
     *
     * @return val - Boolean
     */
    public Boolean getNeedTac() {
        if (mUserStore.contains("need_tac")) {
            return mUserStore.getBoolean("need_tac", false);
        }
        return null;
    }

    /**
     * Sets tac text
     *
     * @param val - String
     */
    public void setTacText(String val) {
        if (val == null) {
            mUserStore.edit().remove("tac_text").apply();
        } else {
            mUserStore.edit().putString("tac_text", val).apply();
        }
    }

    /**
     * Gets tac text
     *
     * @return val - String
     */
    public String getTacText() {
        return mUserStore.getString("tac_text", "");
    }

    /**
     * Sets need phone
     *
     * @param val - boolean
     */
    public void setNeedPhone(Boolean val) {
        if (val == null) {
            mUserStore.edit().remove("need_phone").apply();
        } else {
            mUserStore.edit().putBoolean("need_phone", val).apply();
        }
    }

    /**
     * Gets need phone
     *
     * @return val - Boolean
     */
    public Boolean getNeedPhone() {
        if (mUserStore.contains("need_phone")) {
            return mUserStore.getBoolean("need_phone", false);
        }
        return null;
    }

    /**
     * Sets phone
     *
     * @param val - String
     */
    public void setPhone(String val) {
        if (val == null) {
            mUserStore.edit().remove("phone").apply();
        } else {
            mUserStore.edit().putString("phone", val).apply();
        }
    }

    /**
     * Gets phone
     *
     * @return val - String
     */
    public String getPhone() {
        return mUserStore.getString("phone", "");
    }

    /**
     * Sets phone confirmed
     *
     * @param val - boolean
     */
    public void setPhoneConfirmed(Boolean val) {
        if (val == null) {
            mUserStore.edit().remove("phone_confirmed").apply();
        } else {
            mUserStore.edit().putBoolean("phone_confirmed", val).apply();
        }
    }

    /**
     * Gets phone confirmed
     *
     * @return val - Boolean
     */
    public Boolean getPhoneConfirmed() {
        if (mUserStore.contains("phone_confirmed")) {
            return mUserStore.getBoolean("phone_confirmed", false);
        }
        return null;
    }
}
