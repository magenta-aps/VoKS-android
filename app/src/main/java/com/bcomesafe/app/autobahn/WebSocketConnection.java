/**
 * Copyright 2011-2012 Tavendo GmbH
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Implements the algorithm "Flexible and Economical UTF-8 Decoder" by
 * Bjoern Hoehrmann (http://bjoern.hoehrmann.de/utf-8/decoder/dfa/).
 * <p>
 * <p>
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.autobahn;

import android.net.SSLCertificateSocketFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.net.URI;

import javax.net.SocketFactory;

import com.bcomesafe.app.Constants;
import com.bcomesafe.app.DefaultParameters;
import com.bcomesafe.app.utils.RemoteLogUtils;
import com.bcomesafe.app.utils.Utils;

@SuppressWarnings({"unused", "SynchronizeOnNonFinalField"})
public class WebSocketConnection implements WebSocket {

    private static final boolean D = false;
    private static final String TAG = WebSocketConnection.class.getSimpleName();

    private static final String WS_URI_SCHEME = "ws";
    private static final String WSS_URI_SCHEME = "wss";
    private static final String WS_WRITER = "WebSocketWriter";
    private static final String WS_READER = "WebSocketReader";
    private final Handler mHandler;
    private WebSocketReader mWebSocketReader;
    private WebSocketWriter mWebSocketWriter;
    private Socket mSocket;
    private SocketThread mSocketThread;
    private URI mWebSocketURI;
    private String[] mWebSocketSubprotocols;
    private WeakReference<WebSocketConnectionObserver> mWebSocketConnectionObserver;
    private WebSocketOptions mWebSocketOptions;
    private boolean mPreviousConnection = false;

    public WebSocketConnection() {
        log("WebSocket connection created.");
        this.mHandler = new ThreadHandler(this);
    }

    public void sendTextMessage(String payload) {
        this.mWebSocketWriter.forward(new WebSocketMessage.TextMessage(payload));
    }

    public void sendRawTextMessage(byte[] payload) {
        this.mWebSocketWriter.forward(new WebSocketMessage.RawTextMessage(payload));
    }

    public void sendBinaryMessage(byte[] payload) {
        this.mWebSocketWriter.forward(new WebSocketMessage.BinaryMessage(payload));
    }

    public boolean isConnected() {
        return (this.mSocket != null) && (this.mSocket.isConnected()) && (!this.mSocket.isClosed());
    }

    private void failConnection(WebSocketConnectionObserver.WebSocketCloseNotification code, String reason) {
        log("fail connection [code = " + code + ", reason = " + reason);

        if (this.mWebSocketReader != null) {
            this.mWebSocketReader.quit();
            try {
                this.mWebSocketReader.join();
            } catch (InterruptedException e) {
                if (D) {
                    e.printStackTrace();
                }
            }
        } else {
            log("mReader already NULL");
        }

        if (this.mWebSocketWriter != null) {
            this.mWebSocketWriter.forward(new WebSocketMessage.Quit());
            try {
                this.mWebSocketWriter.join();
            } catch (InterruptedException e) {
                if (D) {
                    e.printStackTrace();
                }
            }
        } else {
            log("mWriter already NULL");
        }

        if (this.mSocket != null)
            this.mSocketThread.getHandler().post(new Runnable() {
                public void run() {
                    WebSocketConnection.this.mSocketThread.stopConnection();
                }
            });
        else {
            log("mTransportChannel already NULL");
        }

        this.mSocketThread.getHandler().post(new Runnable() {
            public void run() {
                //noinspection ConstantConditions
                Looper.myLooper().quit();
            }
        });
        onClose(code, reason);

        log("worker threads stopped");
    }

    public void connect(URI webSocketURI, WebSocketConnectionObserver connectionObserver)
            throws WebSocketException {
        connect(webSocketURI, connectionObserver, new WebSocketOptions());
    }

    public void connect(URI webSocketURI, WebSocketConnectionObserver connectionObserver, WebSocketOptions options) throws WebSocketException {
        connect(webSocketURI, null, connectionObserver, options);
    }

    public void connect(URI webSocketURI, String[] subprotocols, WebSocketConnectionObserver connectionObserver, WebSocketOptions options) throws WebSocketException {
        if (isConnected()) {
            throw new WebSocketException("already connected");
        }

        if (webSocketURI == null) {
            throw new WebSocketException("WebSockets URI null.");
        }
        this.mWebSocketURI = webSocketURI;
        if ((!this.mWebSocketURI.getScheme().equals("ws")) && (!this.mWebSocketURI.getScheme().equals("wss"))) {
            throw new WebSocketException("unsupported scheme for WebSockets URI");
        }

        this.mWebSocketSubprotocols = subprotocols;
        //noinspection unchecked
        this.mWebSocketConnectionObserver = new WeakReference(connectionObserver);
        this.mWebSocketOptions = new WebSocketOptions(options);

        connect();
    }

    public void disconnect() {
        if ((this.mWebSocketWriter != null) && (this.mWebSocketWriter.isAlive()))
            this.mWebSocketWriter.forward(new WebSocketMessage.Close());
        else {
            log("Could not send WebSocket Close .. writer already null");
        }

        this.mPreviousConnection = false;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean reconnect() {
        if ((!isConnected()) && (this.mWebSocketURI != null)) {
            connect();
            return true;
        }
        return false;
    }

    private void connect() {
        this.mSocketThread = new SocketThread(this.mWebSocketURI, this.mWebSocketOptions);

        this.mSocketThread.start();
        synchronized (this.mSocketThread) {
            try {
                this.mSocketThread.wait();
            } catch (InterruptedException localInterruptedException) {
                log("mSocketThread interrupted");
            }
        }
        this.mSocketThread.getHandler().post(new Runnable() {
            public void run() {
                WebSocketConnection.this.mSocketThread.startConnection();
            }
        });
        synchronized (this.mSocketThread) {
            try {
                this.mSocketThread.wait();
            } catch (InterruptedException localInterruptedException1) {
                log("mSocketThread interrupted");
            }
        }
        this.mSocket = this.mSocketThread.getSocket();

        if (this.mSocket == null)
            onClose(WebSocketConnectionObserver.WebSocketCloseNotification.CANNOT_CONNECT, this.mSocketThread.getFailureMessage());
        else if (this.mSocket.isConnected())
            try {
                createReader();
                createWriter();

                WebSocketMessage.ClientHandshake clientHandshake = new WebSocketMessage.ClientHandshake(this.mWebSocketURI, null, this.mWebSocketSubprotocols);
                this.mWebSocketWriter.forward(clientHandshake);
            } catch (Exception e) {
                onClose(WebSocketConnectionObserver.WebSocketCloseNotification.INTERNAL_ERROR, e.getLocalizedMessage());
            }
        else
            onClose(WebSocketConnectionObserver.WebSocketCloseNotification.CANNOT_CONNECT, "could not connect to WebSockets server");
    }

    protected boolean scheduleReconnect() {
        int interval = this.mWebSocketOptions.getReconnectInterval();
        boolean shouldReconnect = (this.mSocket != null) &&
                (this.mSocket.isConnected()) &&
                (this.mPreviousConnection) && (
                interval > 0);
        if (shouldReconnect) {
            log("WebSocket reconnection scheduled");

            this.mHandler.postDelayed(new Runnable() {
                public void run() {
                    log("WebSocket reconnecting...");
                    WebSocketConnection.this.reconnect();
                }
            }, interval);
        }
        return shouldReconnect;
    }

    private void onClose(WebSocketConnectionObserver.WebSocketCloseNotification code, String reason) {
        boolean reconnecting = false;

        if ((code == WebSocketConnectionObserver.WebSocketCloseNotification.CANNOT_CONNECT) || (code == WebSocketConnectionObserver.WebSocketCloseNotification.CONNECTION_LOST)) {
            reconnecting = scheduleReconnect();
        }

        WebSocketConnectionObserver webSocketObserver = this.mWebSocketConnectionObserver.get();
        if (webSocketObserver != null) {
            try {
                if (reconnecting)
                    webSocketObserver.onClose(WebSocketConnectionObserver.WebSocketCloseNotification.RECONNECT, reason);
                else
                    webSocketObserver.onClose(code, reason);
            } catch (Exception e) {
                if (D) {
                    e.printStackTrace();
                }
            }
        } else {
            log("WebSocketObserver null");
        }
    }

    @SuppressWarnings("EmptyMethod")
    protected void processAppMessage(Object message) {
    }

    protected void createWriter() {
        this.mWebSocketWriter = new WebSocketWriter(this.mHandler, this.mSocket, this.mWebSocketOptions, "WebSocketWriter");
        this.mWebSocketWriter.start();

        synchronized (this.mWebSocketWriter) {
            try {
                this.mWebSocketWriter.wait();
            } catch (InterruptedException localInterruptedException) {
                log("mSocketThread interrupted");
            }
        }
        log("WebSocket writer created and started.");
    }

    protected void createReader() {
        this.mWebSocketReader = new WebSocketReader(this.mHandler, this.mSocket, this.mWebSocketOptions, "WebSocketReader");
        this.mWebSocketReader.start();

        synchronized (this.mWebSocketReader) {
            try {
                this.mWebSocketReader.wait();
            } catch (InterruptedException localInterruptedException) {
                log("mSocketThread interrupted");
            }
        }
        log("WebSocket reader created and started.");
    }

    private void handleMessage(Message message) {
        WebSocketConnectionObserver webSocketObserver = this.mWebSocketConnectionObserver.get();

        if ((message.obj instanceof WebSocketMessage.TextMessage)) {
            WebSocketMessage.TextMessage textMessage = (WebSocketMessage.TextMessage) message.obj;

            if (webSocketObserver != null)
                webSocketObserver.onTextMessage(textMessage.mPayload);
            else {
                log("could not call onTextMessage() .. handler already NULL");
            }
        } else if ((message.obj instanceof WebSocketMessage.RawTextMessage)) {
            WebSocketMessage.RawTextMessage rawTextMessage = (WebSocketMessage.RawTextMessage) message.obj;

            if (webSocketObserver != null)
                webSocketObserver.onRawTextMessage(rawTextMessage.mPayload);
            else {
                log("could not call onRawTextMessage() .. handler already NULL");
            }
        } else if ((message.obj instanceof WebSocketMessage.BinaryMessage)) {
            WebSocketMessage.BinaryMessage binaryMessage = (WebSocketMessage.BinaryMessage) message.obj;

            if (webSocketObserver != null)
                webSocketObserver.onBinaryMessage(binaryMessage.mPayload);
            else {
                log("could not call onBinaryMessage() .. handler already NULL");
            }
        } else if ((message.obj instanceof WebSocketMessage.Ping)) {
            WebSocketMessage.Ping ping = (WebSocketMessage.Ping) message.obj;
            log("WebSockets Ping received");
            WebSocketMessage.Pong pong = new WebSocketMessage.Pong();
            pong.mPayload = ping.mPayload;
            this.mWebSocketWriter.forward(pong);
        } else if ((message.obj instanceof WebSocketMessage.Pong)) {
            WebSocketMessage.Pong pong = (WebSocketMessage.Pong) message.obj;
            //noinspection ImplicitArrayToString
            log("WebSockets Pong received" + pong.mPayload);
        } else if ((message.obj instanceof WebSocketMessage.Close)) {
            WebSocketMessage.Close close = (WebSocketMessage.Close) message.obj;

            log("WebSockets Close received (" + close.getCode() + " - " + close.getReason() + ")");

            this.mWebSocketWriter.forward(new WebSocketMessage.Close(1000));
        } else if ((message.obj instanceof WebSocketMessage.ServerHandshake)) {
            WebSocketMessage.ServerHandshake serverHandshake = (WebSocketMessage.ServerHandshake) message.obj;

            log("opening handshake received");

            if (serverHandshake.mSuccess) {
                if (webSocketObserver != null)
                    webSocketObserver.onOpen();
                else {
                    log("could not call onOpen() .. handler already NULL");
                }
                this.mPreviousConnection = true;
            }
        } else if ((message.obj instanceof WebSocketMessage.ConnectionLost)) {
            failConnection(WebSocketConnectionObserver.WebSocketCloseNotification.CONNECTION_LOST, "WebSockets connection lost");
        } else if ((message.obj instanceof WebSocketMessage.ProtocolViolation)) {
            failConnection(WebSocketConnectionObserver.WebSocketCloseNotification.PROTOCOL_ERROR, "WebSockets protocol violation");
        } else if ((message.obj instanceof WebSocketMessage.Error)) {
            WebSocketMessage.Error error = (WebSocketMessage.Error) message.obj;
            failConnection(WebSocketConnectionObserver.WebSocketCloseNotification.INTERNAL_ERROR, "WebSockets internal error (" + error.mException.toString() + ")");
        } else if ((message.obj instanceof WebSocketMessage.ServerError)) {
            WebSocketMessage.ServerError error = (WebSocketMessage.ServerError) message.obj;
            failConnection(WebSocketConnectionObserver.WebSocketCloseNotification.SERVER_ERROR, "Server error " + error.mStatusCode + " (" + error.mStatusMessage + ")");
        } else {
            processAppMessage(message.obj);
        }
    }

    public static class SocketThread extends Thread {
        private static final String WS_CONNECTOR = "WebSocketConnector";
        private final URI mWebSocketURI;
        private Socket mSocket = null;
        private String mFailureMessage = null;
        private Handler mHandler;

        public SocketThread(URI uri, WebSocketOptions options) {
            setName("WebSocketConnector");

            this.mWebSocketURI = uri;
        }

        public void run() {
            Looper.prepare();
            this.mHandler = new Handler();
            synchronized (this) {
                notifyAll();
            }

            Looper.loop();
            log2("SocketThread exited.");
        }

        public void startConnection() {
            try {
                String host = this.mWebSocketURI.getHost();
                int port = this.mWebSocketURI.getPort();

                if (port == -1) {
                    if (this.mWebSocketURI.getScheme().equals("wss"))
                        port = 443;
                    else {
                        port = 80;
                    }
                }

                log2("WebSock connection port = " + port);

                SocketFactory factory;
                if (this.mWebSocketURI.getScheme().equalsIgnoreCase("wss")) {
                    if (DefaultParameters.SHOULD_USE_SSL && DefaultParameters.ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV) {
                        factory = Utils.getBypassedSSLSocketFactory(null);
                    } else {
                        factory = SSLCertificateSocketFactory.getDefault();
                    }
                    if (factory == null) {
                        log2("factory == null");
                    } else {
                        log2("factory != null");
                    }
                } else {
                    factory = SocketFactory.getDefault();
                }

                if (factory != null) {
                    this.mSocket = factory.createSocket(host, port);
                } else {
                    this.mFailureMessage = "factory == null";
                }
            } catch (Exception e) {
                if (D) {
                    e.printStackTrace();
                }
                this.mFailureMessage = e.getLocalizedMessage();
            }

            synchronized (this) {
                notifyAll();
            }
        }

        public void stopConnection() {
            try {
                this.mSocket.close();
                this.mSocket = null;
            } catch (IOException e) {
                this.mFailureMessage = e.getLocalizedMessage();
            }
        }

        public Handler getHandler() {
            return this.mHandler;
        }

        public Socket getSocket() {
            return this.mSocket;
        }

        public String getFailureMessage() {
            return this.mFailureMessage;
        }
    }

    private static class ThreadHandler extends Handler {
        private final WeakReference<WebSocketConnection> mWebSocketConnection;

        public ThreadHandler(WebSocketConnection webSocketConnection) {
            //noinspection unchecked
            this.mWebSocketConnection = new WeakReference(webSocketConnection);
        }

        public void handleMessage(Message message) {
            WebSocketConnection webSocketConnection = this.mWebSocketConnection.get();
            if (webSocketConnection != null)
                webSocketConnection.handleMessage(message);
        }
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }

    private static void log2(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
 