/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.objects;


import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import com.bcomesafe.app.Constants;
import com.bcomesafe.app.utils.RemoteLogUtils;

/**
 * GCM message object
 */
public class GCMMessageObject {

    // Debugging
    private final static boolean D = true;
    private final static String TAG = GCMMessageObject.class.getSimpleName();

    // Variables
    private int mId = Constants.INVALID_INT_ID;
    private String mTitle = Constants.INVALID_STRING_ID;
    private String mContent = Constants.INVALID_STRING_ID;
    private long mTimestamp = Constants.INVALID_LONG_ID;

    public GCMMessageObject() {
    }

    public String getTitle() {
        return mTitle;
    }

    public void setId(int id) {
        this.mId = id;
    }

    public int getId() {
        return this.mId;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public String getContent() {
        return mContent;
    }

    public void setContent(String content) {
        this.mContent = content;
    }

    public long getTimestamp() {
        return  this.mTimestamp;
    }

    public void setTimestamp(long timestamp) {
        this.mTimestamp = timestamp;
    }

    /**
     * Parses GCM message object values from JSONObject
     *
     * @param gcmMessageObject GCM message JSONObject
     */
    public void fromJSON(JSONObject gcmMessageObject) {
        // id
        if (!gcmMessageObject.isNull(Constants.GCM_MESSAGE_ID)) {
            log("gcmMessageObject has " + Constants.GCM_MESSAGE_ID);
            try {
                setId(gcmMessageObject.getInt(Constants.GCM_MESSAGE_ID));
            } catch (JSONException e) {
                log("gcmMessageObject unable to get " + Constants.GCM_MESSAGE_ID);
            }
        } else {
            log("gcmMessageObject does not have " + Constants.GCM_MESSAGE_ID + " or its null");
        }
        // title
        if (!gcmMessageObject.isNull(Constants.GCM_MESSAGE_TITLE)) {
            log("gcmMessageObject has " + Constants.GCM_MESSAGE_TITLE);
            try {
                setTitle(gcmMessageObject.getString(Constants.GCM_MESSAGE_TITLE));
            } catch (JSONException e) {
                log("gcmMessageObject unable to get " + Constants.GCM_MESSAGE_TITLE);
            }
        } else {
            log("gcmMessageObject does not have " + Constants.GCM_MESSAGE_TITLE + " or its null");
        }
        // content
        if (!gcmMessageObject.isNull(Constants.GCM_MESSAGE_CONTENT)) {
            log("gcmMessageObject has " + Constants.GCM_MESSAGE_CONTENT);
            try {
                setContent(gcmMessageObject.getString(Constants.GCM_MESSAGE_CONTENT));
            } catch (JSONException e) {
                log("gcmMessageObject unable to get " + Constants.GCM_MESSAGE_CONTENT);
            }
        } else {
            log("gcmMessageObject does not have " + Constants.GCM_MESSAGE_CONTENT + " or its null");
        }
        // timestamp
        if (!gcmMessageObject.isNull(Constants.GCM_MESSAGE_TIMESTAMP)) {
            log("gcmMessageObject has " + Constants.GCM_MESSAGE_TIMESTAMP);
            try {
                setTimestamp(gcmMessageObject.getLong(Constants.GCM_MESSAGE_TIMESTAMP));
            } catch (JSONException e) {
                log("gcmMessageObject unable to get " + Constants.GCM_MESSAGE_TIMESTAMP);
            }
        } else {
            log("gcmMessageObject does not have " + Constants.GCM_MESSAGE_TIMESTAMP + " or its null");
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