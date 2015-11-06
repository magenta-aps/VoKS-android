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

import java.util.LinkedList;
import java.util.Locale;

import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.DefaultParameters;
import com.bcomesafe.app.utils.RemoteLogUtils;
import com.bcomesafe.app.webrtc.WebSocketChannelClient.WebSocketChannelEvents;
import com.bcomesafe.app.webrtc.WebSocketChannelClient.WebSocketConnectionState;
import com.bcomesafe.app.webrtcutils.LooperExecutor;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

/**
 * Negotiates signaling for chatting with shelter.
 * Uses the client<->server specifics of the shelter.
 * To use: create an instance of this object (registering a message handler) and call connectToShelter().
 * Once connection with shelter is established onConnectedToShelter() callback with signaling parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can be sent after WebSocket connection is established.
 */
public class WebSocketRTCClient implements AppRTCClient, WebSocketChannelEvents {

    // Debugging
    private static final boolean D = true;
    private static final String TAG = WebSocketRTCClient.class.getSimpleName();

    // WebSocket connection states
    public enum ConnectionState {
        NEW, CONNECTED, CLOSED, ERROR
    }

    // Constants
    private final static String DTLS_SRTP_KEY_AGREEMENT = "DtlsSrtpKeyAgreement";

    // Variables
    private final LooperExecutor mExecutor;
    // TODO remove this parameter, since its always true
    private boolean mInitiator;
    private final SignalingEvents mEvents;
    private WebSocketChannelClient mWSClient;
    private ConnectionState mConnectionState;

    public WebSocketRTCClient(SignalingEvents events) {
        this.mEvents = events;
        mExecutor = new LooperExecutor();
    }

    // AppRTCClient interface implementation.

    /**
     * Asynchronously connects to shelter
     */
    @Override
    public void connectToShelter() {
        mExecutor.requestStart();
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                connectToShelterInternal();
            }
        });
    }

    /**
     * Asynchronously disconnects from shelter
     */
    @Override
    public void disconnectFromShelter() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                disconnectFromShelterInternal();
            }
        });
        mExecutor.requestStop();
    }

    /**
     * Connects to shelter.
     * Function runs on a local looper thread.
     */
    private void connectToShelterInternal() {
        log("connectToShelterInternal()");
        mConnectionState = ConnectionState.NEW;
        // Create WebSocket client
        mWSClient = new WebSocketChannelClient(mExecutor, this);
        // Prepare signaling parameters
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                signalingParametersReady(prepareSignalingParameters());
            }
        });
    }

    /**
     * Prepares signaling parameters
     *
     * @return SignalingParameters
     */
    private SignalingParameters prepareSignalingParameters() {
        String shelterId = AppUser.get().getShelterID().equals(Constants.INVALID_STRING_ID) ? DefaultParameters.getDefaultShelterId() : AppUser.get().getShelterID();
        String clientId = AppUser.get().getDeviceUID();
        String wssUrl = AppUser.get().getWsURL().equals(Constants.INVALID_STRING_ID) ? DefaultParameters.getDefaultWSURL() + clientId : AppUser.get().getWsURL();

        log("ShelterId: " + shelterId + " clientId: " + clientId);
        log("Initiator: " + true);
        log("WSS url: " + wssUrl);

        LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();

        MediaConstraints pcConstraints = new MediaConstraints();
        MediaConstraints videoConstraints = new MediaConstraints();
        MediaConstraints audioConstraints = new MediaConstraints();

        addDTLSConstraintIfMissing(pcConstraints);

        return new SignalingParameters(iceServers, true, pcConstraints, videoConstraints, audioConstraints, shelterId, clientId, wssUrl, null, null);
    }


    /**
     * Mimic Chrome and set DtlsSrtpKeyAgreement to true if not set to false by the web-app.
     *
     * @param pcConstraints MediaConstraints
     */
    private void addDTLSConstraintIfMissing(MediaConstraints pcConstraints) {
        for (MediaConstraints.KeyValuePair pair : pcConstraints.mandatory) {
            if (pair.getKey().equals(DTLS_SRTP_KEY_AGREEMENT)) {
                return;
            }
        }
        for (MediaConstraints.KeyValuePair pair : pcConstraints.optional) {
            if (pair.getKey().equals(DTLS_SRTP_KEY_AGREEMENT)) {
                return;
            }
        }
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT, "true"));
    }


    /**
     * Disconnect from shelter and send bye message.
     * Function runs on a local looper thread.
     */
    private void disconnectFromShelterInternal() {
        log("Disconnect. Connection state: " + mConnectionState);
        if (mConnectionState == ConnectionState.CONNECTED) {
            log("Closing connection.");
        }
        mConnectionState = ConnectionState.CLOSED;
        if (mWSClient != null) {
            mWSClient.disconnect(true);
        }
    }

    /**
     * Callback issued when signaling parameters are extracted.
     * Function runs on local looper thread.
     *
     * @param params SignalingParameters
     */
    private void signalingParametersReady(final SignalingParameters params) {
        log("Connection completed.");
        if (!params.initiator && params.offerSdp == null) {
            log("No offer SDP in shelter response.");
        }
        mInitiator = params.initiator;
        mConnectionState = ConnectionState.CONNECTED;

        // Fire connection and signaling parameters events.
        mEvents.onConnectedToShelter(params);

        // Connect to WebSocket server.
        mWSClient.connect(params.wssUrl);

        // For call receiver get sdp offer and ice candidates from signaling parameters and fire corresponding events.
        if (!params.initiator) {
            if (params.offerSdp != null) {
                mEvents.onRemoteDescription(params.offerSdp);
            }
            if (params.iceCandidates != null) {
                for (IceCandidate iceCandidate : params.iceCandidates) {
                    mEvents.onRemoteIceCandidate(iceCandidate);
                }
            }
        }
    }

    /**
     * Send local offer SDP to the other participant.
     *
     * @param sdp                 SessionDescription
     * @param signalingParameters SignalingParameters
     */
    @Override
    public void sendOfferSdp(final SessionDescription sdp, final SignalingParameters signalingParameters) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mConnectionState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }

                JSONObject json = new JSONObject();
                jsonPut(json, Constants.WEBSOCKET_PARAM_TYPE, Constants.WEBSOCKET_DATA_OFFER_UP);
                JSONObject payloadObj = new JSONObject();
                jsonPut(payloadObj, Constants.WEBSOCKET_PARAM_SDP, sdp.description);
                jsonPut(payloadObj, Constants.WEBSOCKET_PARAM_TYPE, Constants.WEBSOCKET_DATA_OFFER_LO);
                jsonPut(json, Constants.WEBSOCKET_PARAM_PAYLOAD, payloadObj);
                jsonPut(json, Constants.WEBSOCKET_PARAM_DST, AppUser.get().getShelterID().equals(Constants.INVALID_STRING_ID) ? DefaultParameters.getDefaultShelterId() : AppUser.get().getShelterID());
                jsonPut(json, Constants.WEBSOCKET_PARAM_SRC, signalingParameters.clientId);

                log("Sending offer sdp:" + json.toString());
                mWSClient.send(json.toString());
            }
        });
    }

    /**
     * Send local answer SDP to the other participant.
     *
     * @param sdp SessionDescription
     */
    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mWSClient.getState() != WebSocketConnectionState.REGISTERED) {
                    reportError("Sending answer SDP in non registered state.");
                    return;
                }
                JSONObject json = new JSONObject();
                jsonPut(json, Constants.WEBSOCKET_PARAM_SDP, sdp.description);
                jsonPut(json, Constants.WEBSOCKET_PARAM_TYPE, Constants.WEBSOCKET_DATA_ANSWER_LO);
                mWSClient.send(json.toString());
            }
        });
    }

    /**
     * Send Ice candidate to the other participant.
     *
     * @param candidate           IceCandidate
     * @param signalingParameters SignalingParameters
     */
    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate, final SignalingParameters signalingParameters) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, Constants.WEBSOCKET_PARAM_TYPE, Constants.WEBSOCKET_DATA_CANDIDATE_UP);
                jsonPut(json, Constants.WEBSOCKET_PARAM_DST, AppUser.get().getShelterID().equals(Constants.INVALID_STRING_ID) ? DefaultParameters.getDefaultShelterId() : AppUser.get().getShelterID());
                jsonPut(json, Constants.WEBSOCKET_PARAM_SRC, signalingParameters.clientId);
                JSONObject payloadObject = new JSONObject();
                jsonPut(payloadObject, Constants.WEBSOCKET_PARAM_SDP_MID, candidate.sdpMid);
                jsonPut(payloadObject, Constants.WEBSOCKET_PARAM_SDP_M_LINE_INDEX, candidate.sdpMLineIndex);
                jsonPut(payloadObject, Constants.WEBSOCKET_PARAM_CANDIDATE, candidate.sdp);
                jsonPut(json, Constants.WEBSOCKET_PARAM_PAYLOAD, payloadObject);

                if (mInitiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (mConnectionState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate in non connected state.");
                        return;
                    }
                    mWSClient.send(json.toString());
                } else {
                    // Call receiver sends ice candidates to WebSocket server.
                    if (mWSClient.getState() != WebSocketConnectionState.REGISTERED) {
                        reportError("Sending ICE candidate in non registered state.");
                        return;
                    }
                    mWSClient.send(json.toString());
                }
            }
        });
    }

    /**
     * Sends pong through the WebSocket connection
     */
    private void sendPong() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                log("sendPong()");
                JSONObject jsonData = new JSONObject();
                try {
                    jsonData.put(Constants.WEBSOCKET_PARAM_TYPE, Constants.WEBSOCKET_TYPE_PONG);
                    mWSClient.send(jsonData.toString());
                } catch (JSONException e) {
                    // Nothing to do
                    log("sendPong() unable to create data json object");
                }
            }
        });
    }

    /**
     * Sends data through the WebSocket connection
     *
     * @param type       Data type string
     * @param dataObject Data object
     */
    public void sendData(final String type, final Object dataObject, final String clientId) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                log("sendData() type=" + type + "; dataObject=" + dataObject + ";");
                JSONObject jsonData = new JSONObject();
                try {
                    jsonData.put(Constants.WEBSOCKET_PARAM_TYPE, type);
                    jsonData.put(Constants.WEBSOCKET_PARAM_DATA, dataObject);
                    jsonData.put(Constants.WEBSOCKET_PARAM_DST, AppUser.get().getShelterID().equals(Constants.INVALID_STRING_ID) ? DefaultParameters.getDefaultShelterId() : AppUser.get().getShelterID());
                    jsonData.put(Constants.WEBSOCKET_PARAM_SRC, clientId);
                    mWSClient.send(jsonData.toString());
                } catch (JSONException e) {
                    // Nothing to do
                    log("sendData() unable to create data json object");
                }
            }
        });
    }

    /**
     * Returns WebSocket connection state
     *
     * @return WebSocketRTCClient.ConnectionState
     */
    @Override
    public WebSocketRTCClient.ConnectionState getConnectionState() {
        return mConnectionState;
    }

    // ------------------- WebSocketChannelEvents interface implementation -------------------------
    // All events are called by WebSocketChannelClient on a local looper thread (passed to WebSocket client constructor).
    @Override
    public void onWebSocketOpen() {
        log("WebSocket connection completed.");
        mEvents.onWebSocketOpen();
        mWSClient.register();
    }

    @Override
    public void onWebSocketRegistered() {
        log("WebSocket connection registered.");
        mEvents.onWebSocketRegistered();
    }

    @Override
    public void onWebSocketMessage(final String msg) {
        if (mWSClient.getState() != WebSocketConnectionState.REGISTERED) {
            log("Got WebSocket message in non registered state.");
            return;
        }
        try {
            JSONObject json = new JSONObject(msg);
            String type = json.optString(Constants.WEBSOCKET_PARAM_TYPE).toUpperCase(Locale.ENGLISH);
            switch (type) {
                case Constants.WEBSOCKET_TYPE_CANDIDATE:
                    // id -> mid
                    // label -> index
                    // candidate -> candidate
                    String stringId = json.getJSONObject(Constants.WEBSOCKET_PARAM_PAYLOAD).getString(Constants.WEBSOCKET_PARAM_SDP_MID);
                    int intIndex = json.getJSONObject(Constants.WEBSOCKET_PARAM_PAYLOAD).getInt(Constants.WEBSOCKET_PARAM_SDP_M_LINE_INDEX);
                    String stringCandidate = json.getJSONObject(Constants.WEBSOCKET_PARAM_PAYLOAD).getString(Constants.WEBSOCKET_PARAM_CANDIDATE);

                    IceCandidate candidate = new IceCandidate(stringId, intIndex, stringCandidate);
                    mEvents.onRemoteIceCandidate(candidate);
                    break;
                case Constants.WEBSOCKET_TYPE_ANSWER:
                    if (mInitiator) {
                        String sdpString = json.getJSONObject(Constants.WEBSOCKET_PARAM_PAYLOAD).getString(Constants.WEBSOCKET_PARAM_SDP);
                        SessionDescription sdp = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdpString);
                        mEvents.onRemoteDescription(sdp);
                    } else {
                        reportError("Received answer for call initiator: " + msg);
                    }
                    break;
                case Constants.WEBSOCKET_TYPE_OFFER:
                    if (!mInitiator) {
                        SessionDescription sdp = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), json.getString(Constants.WEBSOCKET_PARAM_SDP));
                        mEvents.onRemoteDescription(sdp);
                    } else {
                        reportError("Received offer for call receiver: " + msg);
                    }
                    break;
                case Constants.WEBSOCKET_TYPE_BYE:
                    mEvents.onWebSocketClose();
                    break;
                case Constants.WEBSOCKET_TYPE_SHELTER_STATUS:
                    if (!json.isNull(Constants.WEBSOCKET_PARAM_DATA)) {
                        int shelterStatus = json.getInt(Constants.WEBSOCKET_PARAM_DATA);
                        mEvents.onShelterStatusGot(shelterStatus);
                    } else {
                        log("Missing data parameter");
                    }
                    break;
                case Constants.WEBSOCKET_TYPE_SHELTER_RESET:
                    if (!json.isNull(Constants.WEBSOCKET_PARAM_DATA)) {
                        int shelterReset = json.getInt(Constants.WEBSOCKET_PARAM_DATA);
                        if (shelterReset == 1) {
                            mEvents.onShelterResetGot();
                        } else {
                            log("Got shelter_reset data parameter = 0");
                        }
                    } else {
                        log("Missing data parameter");
                    }
                    break;
                case Constants.WEBSOCKET_TYPE_CHAT_MESSAGE:
                    mEvents.onNewChatMessageGot(json.getString(Constants.WEBSOCKET_PARAM_DATA), json.getLong(Constants.WEBSOCKET_PARAM_TIMESTAMP));
                    break;
                case Constants.WEBSOCKET_TYPE_CHAT_MESSAGES:
                    mEvents.onNewChatMessagesGot(json.getJSONArray(Constants.WEBSOCKET_PARAM_DATA));
                    break;
                case Constants.WEBSOCKET_TYPE_LISTENING:
                    mEvents.onShelterListeningGot(json.getInt(Constants.WEBSOCKET_PARAM_DATA));
                    break;
                case Constants.WEBSOCKET_TYPE_VIDEO:
                    mEvents.onShelterVideoGot(json.getInt(Constants.WEBSOCKET_PARAM_DATA));
                    break;
                case Constants.WEBSOCKET_TYPE_PEER_CONNECTION:
                    mEvents.onShelterPeerConnectionStateGot(json.getInt(Constants.WEBSOCKET_PARAM_DATA));
                    break;
                case Constants.WEBSOCKET_TYPE_PING:
                    sendPong();
                    break;
                default:
                    log("Unexpected WebSocket message (ignoring): " + msg);
                    break;
            }
        } catch (JSONException e) {
            log("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketClose() {
        log("onWebSocketClose()");
        mEvents.onWebSocketClose();
    }

    @Override
    public void onWebSocketError(String description) {
        log("onWebSocketError() description=" + description);
        mEvents.onWebSocketError(description);
    }

    // ------------------------------- Helper functions --------------------------------------------

    /**
     * Reports error message
     *
     * @param errorMessage string
     */
    private void reportError(final String errorMessage) {
        log(errorMessage);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mConnectionState != ConnectionState.ERROR) {
                    mConnectionState = ConnectionState.ERROR;
                    mEvents.onWebSocketError(errorMessage);
                }
            }
        });
    }

    /**
     * Puts a |key|->|value| mapping in |json|.
     *
     * @param json  JSONObject
     * @param key   String
     * @param value Object
     */
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
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
