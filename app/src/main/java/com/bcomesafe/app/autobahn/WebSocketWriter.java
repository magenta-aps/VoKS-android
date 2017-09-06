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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

import com.bcomesafe.app.autobahn.WebSocketMessage.BinaryMessage;
import com.bcomesafe.app.autobahn.WebSocketMessage.ClientHandshake;
import com.bcomesafe.app.autobahn.WebSocketMessage.Close;
import com.bcomesafe.app.autobahn.WebSocketMessage.ConnectionLost;
import com.bcomesafe.app.autobahn.WebSocketMessage.Error;
import com.bcomesafe.app.autobahn.WebSocketMessage.Ping;
import com.bcomesafe.app.autobahn.WebSocketMessage.Pong;
import com.bcomesafe.app.autobahn.WebSocketMessage.Quit;
import com.bcomesafe.app.autobahn.WebSocketMessage.RawTextMessage;
import com.bcomesafe.app.autobahn.WebSocketMessage.TextMessage;
import com.bcomesafe.app.utils.RemoteLogUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Random;

@SuppressWarnings("unused")
public class WebSocketWriter extends Thread {

    private static final boolean D = false;
    private static final String TAG = WebSocketWriter.class.getCanonicalName();

    private static final int WEB_SOCKETS_VERSION = 13;
    private static final String CRLF = "\r\n";
    private final Random mRandom = new Random();
    private final Handler mWebSocketConnectionHandler;
    private final WebSocketOptions mWebSocketOptions;
    private final ByteBuffer mApplicationBuffer;
    private final Socket mSocket;
    private OutputStream mOutputStream;
    private Handler mHandler;

    public WebSocketWriter(Handler master, Socket socket, WebSocketOptions options, String threadName) {
        super(threadName);
        this.mWebSocketConnectionHandler = master;
        this.mWebSocketOptions = options;
        this.mSocket = socket;
        this.mApplicationBuffer = ByteBuffer.allocate(options.getMaxFramePayloadSize() + 14);
        log("WebSocket writer created.");
    }

    public void forward(Object message) {
        Message msg = this.mHandler.obtainMessage();
        msg.obj = message;
        this.mHandler.sendMessage(msg);
    }

    private void notify(Object message) {
        Message msg = this.mWebSocketConnectionHandler.obtainMessage();
        msg.obj = message;
        this.mWebSocketConnectionHandler.sendMessage(msg);
    }

    private String newHandshakeKey() {
        byte[] ba = new byte[16];
        this.mRandom.nextBytes(ba);
        return Base64.encodeToString(ba, 2);
    }

    private byte[] newFrameMask() {
        byte[] ba = new byte[4];
        this.mRandom.nextBytes(ba);
        return ba;
    }

    private void sendClientHandshake(ClientHandshake message) throws IOException {
        String path = message.getURI().getPath();
        if (path == null || path.length() == 0) {
            path = "/";
        }

        String query = message.getURI().getQuery();
        if (query != null && query.length() > 0) {
            path = path + "?" + query;
        }

        this.mApplicationBuffer.put(("GET " + path + " HTTP/1.1" + "\r\n").getBytes());
        this.mApplicationBuffer.put(("Host: " + message.getURI().getHost() + "\r\n").getBytes());
        this.mApplicationBuffer.put("Upgrade: WebSocket\r\n".getBytes());
        this.mApplicationBuffer.put("Connection: Upgrade\r\n".getBytes());
        this.mApplicationBuffer.put(("Sec-WebSocket-Key: " + this.newHandshakeKey() + "\r\n").getBytes());
        this.mApplicationBuffer.put("Origin: https://www.google.com\r\n".getBytes());
        if (message.getSubprotocols() != null && message.getSubprotocols().length > 0) {
            this.mApplicationBuffer.put("Sec-WebSocket-Protocol: ".getBytes());

            for (int i = 0; i < message.getSubprotocols().length; ++i) {
                this.mApplicationBuffer.put(message.getSubprotocols()[i].getBytes());
                this.mApplicationBuffer.put(", ".getBytes());
            }

            this.mApplicationBuffer.put("\r\n".getBytes());
        }

        this.mApplicationBuffer.put("Sec-WebSocket-Version: 13\r\n".getBytes());
        this.mApplicationBuffer.put("\r\n".getBytes());
    }

    private void sendClose(Close message) throws IOException, WebSocketException {
        if (message.getCode() > 0) {
            byte[] payload;
            if (message.getReason() != null && message.getReason().length() <= 0) {
                byte[] pReason = message.getReason().getBytes("UTF-8");
                payload = new byte[2 + pReason.length];

                //noinspection ManualArrayCopy
                for (int i = 0; i < pReason.length; ++i) {
                    payload[i + 2] = pReason[i];
                }
            } else {
                payload = new byte[2];
            }

            //noinspection ConstantConditions
            if (payload != null && payload.length > 125) {
                throw new WebSocketException("close payload exceeds 125 octets");
            }

            payload[0] = (byte) (message.getCode() >> 8 & 0xff);
            payload[1] = (byte) (message.getCode() & 0xff);
            this.sendFrame(8, true, payload);
        } else {
            this.sendFrame(8, true, null);
        }

    }

    private void sendPing(Ping message) throws IOException, WebSocketException {
        if (message.mPayload != null && message.mPayload.length > 125) {
            throw new WebSocketException("ping payload exceeds 125 octets");
        } else {
            this.sendFrame(9, true, message.mPayload);
        }
    }

    private void sendPong(Pong message) throws IOException, WebSocketException {
        if (message.mPayload != null && message.mPayload.length > 125) {
            throw new WebSocketException("pong payload exceeds 125 octets");
        } else {
            this.sendFrame(10, true, message.mPayload);
        }
    }

    private void sendBinaryMessage(BinaryMessage message) throws IOException, WebSocketException {
        if (message.mPayload.length > this.mWebSocketOptions.getMaxMessagePayloadSize()) {
            throw new WebSocketException("message payload exceeds payload limit");
        } else {
            this.sendFrame(2, true, message.mPayload);
        }
    }

    private void sendTextMessage(TextMessage message) throws IOException, WebSocketException {
        byte[] payload = message.mPayload.getBytes("UTF-8");
        if (payload.length > this.mWebSocketOptions.getMaxMessagePayloadSize()) {
            throw new WebSocketException("message payload exceeds payload limit");
        } else {
            this.sendFrame(1, true, payload);
        }
    }

    private void sendRawTextMessage(RawTextMessage message) throws IOException, WebSocketException {
        if (message.mPayload.length > this.mWebSocketOptions.getMaxMessagePayloadSize()) {
            throw new WebSocketException("message payload exceeds payload limit");
        } else {
            this.sendFrame(1, true, message.mPayload);
        }
    }

    protected void sendFrame(int opcode, boolean fin, byte[] payload) throws IOException {
        if (payload != null) {
            this.sendFrame(opcode, fin, payload, 0, payload.length);
        } else {
            this.sendFrame(opcode, fin, null, 0, 0);
        }

    }

    protected void sendFrame(int opcode, boolean fin, byte[] payload, int offset, int length) throws IOException {
        byte b0 = 0;
        if (fin) {
            b0 |= -128;
        }

        b0 |= (byte) opcode;
        this.mApplicationBuffer.put(b0);
        byte b1 = 0;
        if (this.mWebSocketOptions.getMaskClientFrames()) {
            b1 = -128;
        }

        long len = (long) length;
        if (len <= 125L) {
            b1 |= (byte) ((int) len);
            this.mApplicationBuffer.put(b1);
        } else if (len <= 65535L) {
            b1 = (byte) (b1 | 126);
            this.mApplicationBuffer.put(b1);
            this.mApplicationBuffer.put(new byte[]{(byte) ((int) (len >> 8 & 0xff)), (byte) ((int) (len & 0xff))});
        } else {
            b1 = (byte) (b1 | 127);
            this.mApplicationBuffer.put(b1);
            this.mApplicationBuffer.put(new byte[]{(byte) ((int) (len >> 56 & 0xff)), (byte) ((int) (len >> 48 & 0xff)), (byte) ((int) (len >> 40 & 0xff)), (byte) ((int) (len >> 32 & 0xff)), (byte) ((int) (len >> 24 & 0xff)), (byte) ((int) (len >> 16 & 0xff)), (byte) ((int) (len >> 8 & 0xff)), (byte) ((int) (len & 0xff))});
        }

        byte[] mask = null;
        if (this.mWebSocketOptions.getMaskClientFrames()) {
            mask = this.newFrameMask();
            this.mApplicationBuffer.put(mask[0]);
            this.mApplicationBuffer.put(mask[1]);
            this.mApplicationBuffer.put(mask[2]);
            this.mApplicationBuffer.put(mask[3]);
        }

        if (len > 0L) {
            if (this.mWebSocketOptions.getMaskClientFrames()) {
                for (int i = 0; (long) i < len; ++i) {
                    if (mask != null) {
                        payload[i + offset] ^= mask[i % 4];
                    }
                }
            }

            this.mApplicationBuffer.put(payload, offset, length);
        }

    }

    protected void processMessage(Object msg) throws IOException, WebSocketException {
        if (msg instanceof TextMessage) {
            this.sendTextMessage((TextMessage) msg);
        } else if (msg instanceof RawTextMessage) {
            this.sendRawTextMessage((RawTextMessage) msg);
        } else if (msg instanceof BinaryMessage) {
            this.sendBinaryMessage((BinaryMessage) msg);
        } else if (msg instanceof Ping) {
            this.sendPing((Ping) msg);
        } else if (msg instanceof Pong) {
            this.sendPong((Pong) msg);
        } else if (msg instanceof Close) {
            this.sendClose((Close) msg);
        } else if (msg instanceof ClientHandshake) {
            this.sendClientHandshake((ClientHandshake) msg);
        } else if (msg instanceof Quit) {
            //noinspection ConstantConditions
            Looper.myLooper().quit();
            log("WebSocket writer ended.");
        } else {
            this.processAppMessage(msg);
        }

    }

    public void writeMessageToBuffer(Message message) {
        try {
            this.mApplicationBuffer.clear();
            this.processMessage(message.obj);
            this.mApplicationBuffer.flip();
            this.mOutputStream.write(this.mApplicationBuffer.array(), this.mApplicationBuffer.position(), this.mApplicationBuffer.limit());
        } catch (SocketException se) {
            log("run() : SocketException (" + se.toString() + ")");
            this.notify(new ConnectionLost());
        } catch (IOException ioe) {
            log("run() : IOException (" + ioe.toString() + ")");
        } catch (Exception e) {
            log("run() : Exception (" + e.toString() + ")");
            this.notify(new Error(e));
        }

    }

    @SuppressWarnings("UnusedParameters")
    protected void processAppMessage(Object msg) throws WebSocketException, IOException {
        throw new WebSocketException("unknown message received by WebSocketWriter");
    }

    public void run() {
        OutputStream outputStream = null;

        try {
            outputStream = this.mSocket.getOutputStream();
        } catch (IOException ioe) {
            log(ioe.getLocalizedMessage());
        }

        this.mOutputStream = outputStream;
        Looper.prepare();
        this.mHandler = new WebSocketWriter.ThreadHandler(this);
        synchronized (this) {
            log("WebSocker writer running.");
            this.notifyAll();
        }

        Looper.loop();
    }

    private static class ThreadHandler extends Handler {
        private final WeakReference<WebSocketWriter> mWebSocketWriterReference;

        public ThreadHandler(WebSocketWriter webSocketWriter) {
            //noinspection unchecked
            this.mWebSocketWriterReference = new WeakReference(webSocketWriter);
        }

        public void handleMessage(Message message) {
            WebSocketWriter webSocketWriter = this.mWebSocketWriterReference.get();
            if (webSocketWriter != null) {
                webSocketWriter.writeMessageToBuffer(message);
            }

        }
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}