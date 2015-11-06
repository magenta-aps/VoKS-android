/******************************************************************************
 *
 *  Copyright 2011-2012 Tavendo GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Implements the algorithm "Flexible and Economical UTF-8 Decoder" by
 *  Bjoern Hoehrmann (http://bjoern.hoehrmann.de/utf-8/decoder/dfa/).
 *
 ******************************************************************************/

/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.autobahn;

import java.net.URI;

@SuppressWarnings("unused")
public interface WebSocket {
    String UTF8_ENCODING = "UTF-8";

    void connect(URI paramURI, WebSocketConnectionObserver paramWebSocketConnectionObserver)
            throws WebSocketException;

    void connect(URI paramURI, WebSocketConnectionObserver paramWebSocketConnectionObserver, WebSocketOptions paramWebSocketOptions)
            throws WebSocketException;

    void disconnect();

    boolean isConnected();

    void sendBinaryMessage(byte[] paramArrayOfByte);

    void sendRawTextMessage(byte[] paramArrayOfByte);

    void sendTextMessage(String paramString);

    interface WebSocketConnectionObserver {
        void onOpen();

        void onClose(WebSocketCloseNotification paramWebSocketCloseNotification, String paramString);

        void onTextMessage(String paramString);

        @SuppressWarnings("EmptyMethod")
        void onRawTextMessage(byte[] paramArrayOfByte);

        @SuppressWarnings("EmptyMethod")
        void onBinaryMessage(byte[] paramArrayOfByte);

        enum WebSocketCloseNotification {
            NORMAL,
            CANNOT_CONNECT,
            CONNECTION_LOST,
            PROTOCOL_ERROR,
            INTERNAL_ERROR,
            SERVER_ERROR,
            RECONNECT
        }
    }
}