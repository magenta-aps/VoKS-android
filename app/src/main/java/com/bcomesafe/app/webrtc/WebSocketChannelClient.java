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
import com.bcomesafe.app.webrtcutils.LooperExecutor;

import android.util.Log;

import com.bcomesafe.app.autobahn.WebSocket.WebSocketConnectionObserver;
import com.bcomesafe.app.autobahn.WebSocketConnection;
import com.bcomesafe.app.autobahn.WebSocketException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;

/**
 * WebSocket client implementation.
 * All public methods should be called from a looper executor thread
 * passed in a constructor, otherwise exception will be thrown.
 * All events are dispatched on the same thread.
 */

public class WebSocketChannelClient {

    // Debugging
    private static final boolean D = true;
    private static final String TAG = WebSocketChannelClient.class.getSimpleName();

    private static final int CLOSE_TIMEOUT = 1000;
    private final WebSocketChannelEvents mEvents;
    private final LooperExecutor mExecutor;
    private WebSocketConnection mWS;
    @SuppressWarnings("FieldCanBeLocal")
    private WebSocketObserver mWSObserver;
    private String mWSServerUrl;
    private WebSocketConnectionState mState;
    private final Object mCloseEventLock = new Object();
    private boolean mCloseEvent;
    // WebSocket send queue. Messages are added to the queue when WebSocket
    // client is not registered and are consumed in register() call.
    private final LinkedList<String> mWSSendQueue;

    /**
     * Possible WebSocket connection states.
     */
    public enum WebSocketConnectionState {
        NEW, CONNECTED, REGISTERED, CLOSED, ERROR
    }

    /**
     * Callback interface for messages delivered on WebSocket.
     * All events are dispatched from a looper executor thread.
     */
    public interface WebSocketChannelEvents {
        void onWebSocketOpen();

        void onWebSocketRegistered();

        void onWebSocketMessage(final String message);

        void onWebSocketClose();

        void onWebSocketError(final String description);
    }

    public WebSocketChannelClient(LooperExecutor executor, WebSocketChannelEvents events) {
        this.mExecutor = executor;
        this.mEvents = events;
        mWSSendQueue = new LinkedList<>();
        mState = WebSocketConnectionState.NEW;
    }

    public WebSocketConnectionState getState() {
        return mState;
    }

    public void connect(final String wsUrl) {
        checkIfCalledOnValidThread();
        if (mState != WebSocketConnectionState.NEW) {
            log("WebSocket is already connected.");
            return;
        }
        mWSServerUrl = wsUrl;
        mCloseEvent = false;

        log("Connecting WebSocket to: " + wsUrl);

        mWS = new WebSocketConnection();
        mWSObserver = new WebSocketObserver();
        try {
            mWS.connect(new URI(mWSServerUrl), mWSObserver);
        } catch (URISyntaxException e) {
            reportError("URI error: " + e.getMessage());
        } catch (WebSocketException e) {
            reportError("WebSocket connection error: " + e.getMessage());
        }
    }

    public void register() {
        checkIfCalledOnValidThread();
        if (mState != WebSocketConnectionState.CONNECTED) {
            log("WebSocket register() in state " + mState);
            return;
        }
        mState = WebSocketConnectionState.REGISTERED;
        // Send any previously accumulated messages.
        for (String sendMessage : mWSSendQueue) {
            send(sendMessage);
        }
        mWSSendQueue.clear();
        mEvents.onWebSocketRegistered();
    }

    public void send(String message) {
        checkIfCalledOnValidThread();
        switch (mState) {
            case NEW:
            case CONNECTED:
                // Store outgoing messages and send them after WebSocket client is registered.
                log("WS ACC: " + message);
                mWSSendQueue.add(message);
                return;
            case ERROR:
            case CLOSED:
                log("WebSocket send() in error or closed state : " + message);
                return;
            case REGISTERED:
                log("C->WSS: " + message);
                mWS.sendTextMessage(message);
                break;
        }
    }

    @SuppressWarnings("SameParameterValue")
    public void disconnect(boolean waitForComplete) {
        checkIfCalledOnValidThread();
        log("Disconnect WebSocket. State: " + mState);
        if (mState == WebSocketConnectionState.REGISTERED) {
            // NOTE not needed atm
            // send("{\"type\": \"bye\"}");
            mState = WebSocketConnectionState.CONNECTED;
        }
        // Close WebSocket in CONNECTED or ERROR states only.
        if (mState == WebSocketConnectionState.CONNECTED || mState == WebSocketConnectionState.ERROR) {
            mWS.disconnect();
            mState = WebSocketConnectionState.CLOSED;
            // Wait for WebSocket close event to prevent WebSocket library from
            // sending any pending messages to deleted looper thread.
            if (waitForComplete) {
                synchronized (mCloseEventLock) {
                    while (!mCloseEvent) {
                        try {
                            mCloseEventLock.wait(CLOSE_TIMEOUT);
                            break;
                        } catch (InterruptedException e) {
                            log("Wait error: " + e.toString());
                        }
                    }
                }
            }
        }
        log("Disconnecting WebSocket done.");
    }

    private void reportError(final String errorMessage) {
        log(errorMessage);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mState != WebSocketConnectionState.ERROR) {
                    mState = WebSocketConnectionState.ERROR;
                    mEvents.onWebSocketError(errorMessage);
                }
            }
        });
    }


    // Helper method for debugging purposes. Ensures that WebSocket method is called on a looper thread.
    private void checkIfCalledOnValidThread() {
        if (!mExecutor.checkOnLooperThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    private class WebSocketObserver implements WebSocketConnectionObserver {
        @Override
        public void onOpen() {
            log("WebSocket connection opened to: " + mWSServerUrl);
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    mState = WebSocketConnectionState.CONNECTED;
                    mEvents.onWebSocketOpen();
                }
            });
        }

        @Override
        public void onClose(WebSocketCloseNotification code, String reason) {
            log("WebSocket connection closed. Code: " + code + ". Reason: " + reason + ". State: " + mState);

            synchronized (mCloseEventLock) {
                mCloseEvent = true;
                mCloseEventLock.notify();
            }
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mState != WebSocketConnectionState.CLOSED) {
                        mState = WebSocketConnectionState.CLOSED;
                        mEvents.onWebSocketClose();
                    }
                }
            });
        }

        @Override
        public void onTextMessage(String payload) {
            log("WSS->C: " + payload);
            final String message = payload;
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mState == WebSocketConnectionState.CONNECTED || mState == WebSocketConnectionState.REGISTERED) {
                        mEvents.onWebSocketMessage(message);
                    }
                }
            });
        }

        @Override
        public void onRawTextMessage(byte[] payload) {
        }

        @Override
        public void onBinaryMessage(byte[] payload) {
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
