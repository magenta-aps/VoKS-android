/*
 * libjingle
 * Copyright 2013 Google Inc.
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

import org.json.JSONArray;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

/**
 * AppRTCClient is the interface representing an AppRTC client.
 */
public interface AppRTCClient {
    /**
     * Asynchronously connect shelter.
     * Once connection is established onConnectedToShelter() callback with signaling parameters is invoked.
     */
    void connectToShelter();

    /**
     * Send offer SDP to the other participant.
     */
    void sendOfferSdp(final SessionDescription sdp, SignalingParameters signalingParameters);

    /**
     * Send answer SDP to the other participant.
     */
    void sendAnswerSdp(final SessionDescription sdp);

    /**
     * Send Ice candidate to the other participant.
     */
    void sendLocalIceCandidate(final IceCandidate candidate, SignalingParameters signalingParameters);

    /**
     * Disconnect from shelter.
     */
    void disconnectFromShelter();

    /**
     * Gets WebSocket connection state
     *
     * @return WebSocketRTCClient.ConnectionState
     */
    WebSocketRTCClient.ConnectionState getConnectionState();

    /**
     * Sends data through WebSocket connection
     *
     * @param type       Data type string
     * @param dataObject Data object
     * @param clientId   String
     */
    void sendData(String type, Object dataObject, String clientId);

    /**
     * Struct holding the signaling parameters of Shelter.
     */
    class SignalingParameters {
        public final List<PeerConnection.IceServer> iceServers;
        public final boolean initiator;
        @SuppressWarnings("unused")
        public final MediaConstraints pcConstraints;
        @SuppressWarnings("unused")
        public final MediaConstraints videoConstraints;
        @SuppressWarnings("unused")
        public final MediaConstraints audioConstraints;
        @SuppressWarnings("unused")
        public final String shelterId;
        public final String clientId;
        public final String wssUrl;
        public final SessionDescription offerSdp;
        public final List<IceCandidate> iceCandidates;

        @SuppressWarnings("SameParameterValue")
        public SignalingParameters(List<PeerConnection.IceServer> iceServers, boolean initiator, MediaConstraints pcConstraints,
                                   MediaConstraints videoConstraints, MediaConstraints audioConstraints, String shelterId, String clientId,
                                   String wssUrl, SessionDescription offerSdp, List<IceCandidate> iceCandidates) {
            this.iceServers = iceServers;
            this.initiator = initiator;
            this.pcConstraints = pcConstraints;
            this.videoConstraints = videoConstraints;
            this.audioConstraints = audioConstraints;
            this.shelterId = shelterId;
            this.clientId = clientId;
            this.wssUrl = wssUrl;
            this.offerSdp = offerSdp;
            this.iceCandidates = iceCandidates;
        }
    }

    /**
     * Callback interface for messages delivered on signaling channel.
     * Methods are guaranteed to be invoked on the UI thread of |activity|.
     */
    interface SignalingEvents {
        /**
         * Callback fired once the shelter's SignalingParameters are extracted.
         */
        void onConnectedToShelter(final SignalingParameters params);

        /**
         * Callback fired once remote SDP is received.
         */
        void onRemoteDescription(final SessionDescription sdp);

        /**
         * Callback fired once remote Ice candidate is received.
         */
        void onRemoteIceCandidate(final IceCandidate candidate);

        /**
         * Callback fired once WebSocket connection is closed.
         */
        void onWebSocketClose();

        /**
         * Callback fired once WebSocket connection error happened.
         */
        void onWebSocketError(final String description);

        /**
         * Callback fired once shelter status got.
         */
        void onShelterStatusGot(final int shelterStatus);

        /**
         * Callback fired once shelter reset got.
         */
        void onShelterResetGot();

        /**
         * Callback fired once WebSocket connection is opened.
         */
        void onWebSocketOpen();

        /**
         * Callback fired once WebSocket connection is registered.
         */
        void onWebSocketRegistered();

        /**
         * Callback fired once new chat message got.
         */
        void onNewChatMessageGot(String msg, long timestamp);

        /**
         * Callback fired once new chat messages got.
         */
        void onNewChatMessagesGot(JSONArray messages);

        /**
         * Callback fired once shelter listening state got.
         */
        void onShelterListeningGot(int state);

        /**
         * Callback fired once shelter video state got.
         */
        void onShelterVideoGot(int state);

        /**
         * Callback fired once shelter peer connection state got.
         */
        void onShelterPeerConnectionStateGot(int state);
    }
}
