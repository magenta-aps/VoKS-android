/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.objects;

import com.bcomesafe.app.Constants;

/*
 * Remote log object
 */
public class RemoteLogObject {

    private String mTag = Constants.INVALID_STRING_ID;
    private String mMessage = Constants.INVALID_STRING_ID;
    private long mTimestamp = Constants.INVALID_LONG_ID;

    public RemoteLogObject(String tag, String message, long timestamp) {
        this.setTag(tag);
        this.setMessage(message);
        this.setTimestamp(timestamp);
    }

    public String getTag() {
        return this.mTag;
    }

    private void setTag(String tag) {
        this.mTag = tag;
    }

    public String getMessage() {
        return this.mMessage;
    }

    private void setMessage(String message) {
        this.mMessage = message;
    }

    public long getTimestamp() {
        return this.mTimestamp;
    }

    private void setTimestamp(long timestamp) {
        this.mTimestamp = timestamp;
    }
}
