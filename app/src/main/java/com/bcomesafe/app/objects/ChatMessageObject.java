/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.objects;

import com.bcomesafe.app.Constants;

/*
 * Chat message object
 */
@SuppressWarnings("WeakerAccess")
public class ChatMessageObject {

    private int mId = Constants.INVALID_INT_ID;
    private String mMessageText = Constants.INVALID_STRING_ID;
    private int mMessageType = Constants.INVALID_INT_ID;
    private boolean mGotIt = false;
    private long mTimestamp = Constants.INVALID_LONG_ID;
    private boolean mIsFromQueue = false;

    public ChatMessageObject(int id, String text, int messageType, long timestamp) {
        this.setId(id);
        this.setMessageText(text);
        this.setMessageType(messageType);
        this.setTimestamp(timestamp);
    }

    public int getId() {
        return this.mId;
    }

    public void setId(int id) {
        this.mId = id;
    }

    public String getMessageText() {
        return this.mMessageText;
    }

    public void setMessageText(String mMessageText) {
        this.mMessageText = mMessageText;
    }

    public int getMessageType() {
        return this.mMessageType;
    }

    public void setMessageType(int messageType) {
        this.mMessageType = messageType;
    }

    public boolean getGotIt() {
        return this.mGotIt;
    }

    @SuppressWarnings("SameParameterValue")
    public void setGotIt(boolean gotIt) {
        this.mGotIt = gotIt;
    }

    public long getTimestamp() {
        return this.mTimestamp;
    }

    public void setTimestamp(long timestamp) {
        this.mTimestamp = timestamp;
    }

    public boolean isFromQueue() {
        return this.mIsFromQueue;
    }

    @SuppressWarnings("SameParameterValue")
    public void setIsFromQueue(boolean val) {
        this.mIsFromQueue = val;
    }
}
