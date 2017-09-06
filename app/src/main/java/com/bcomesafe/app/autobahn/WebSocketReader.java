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
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import com.bcomesafe.app.autobahn.WebSocketMessage.BinaryMessage;
import com.bcomesafe.app.autobahn.WebSocketMessage.Close;
import com.bcomesafe.app.autobahn.WebSocketMessage.ConnectionLost;
import com.bcomesafe.app.autobahn.WebSocketMessage.Error;
import com.bcomesafe.app.autobahn.WebSocketMessage.Ping;
import com.bcomesafe.app.autobahn.WebSocketMessage.Pong;
import com.bcomesafe.app.autobahn.WebSocketMessage.ProtocolViolation;
import com.bcomesafe.app.autobahn.WebSocketMessage.RawTextMessage;
import com.bcomesafe.app.autobahn.WebSocketMessage.ServerError;
import com.bcomesafe.app.autobahn.WebSocketMessage.ServerHandshake;
import com.bcomesafe.app.autobahn.WebSocketMessage.TextMessage;
import com.bcomesafe.app.utils.RemoteLogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

@SuppressWarnings("WeakerAccess")
public class WebSocketReader extends Thread {

    private static final boolean D = false;
    private static final String TAG = WebSocketReader.class.getCanonicalName();

    private final Handler mWebSocketConnectionHandler;
    private final Socket mSocket;
    private final WebSocketOptions mWebSocketOptions;
    private volatile boolean mStopped = false;
    private final byte[] mNetworkBuffer;
    private final ByteBuffer mApplicationBuffer;
    private final NoCopyByteArrayOutputStream mMessagePayload;
    private WebSocketReader.ReaderState mState;
    private boolean mInsideMessage = false;
    private int mMessageOpcode;
    private WebSocketFrameHeader mFrameHeader;
    private final Utf8Validator mUTF8Validator = new Utf8Validator();

    @SuppressWarnings("SameParameterValue")
    public WebSocketReader(Handler master, Socket socket, WebSocketOptions options, String threadName) {
        super(threadName);
        this.mWebSocketConnectionHandler = master;
        this.mSocket = socket;
        this.mWebSocketOptions = options;
        this.mNetworkBuffer = new byte[4096];
        this.mApplicationBuffer = ByteBuffer.allocateDirect(options.getMaxFramePayloadSize() + 14);
        this.mMessagePayload = new NoCopyByteArrayOutputStream(options.getMaxMessagePayloadSize());
        this.mFrameHeader = null;
        this.mState = WebSocketReader.ReaderState.STATE_CONNECTING;
        log("WebSocket reader created.");
    }

    public void quit() {
        this.mStopped = true;
        log("quit");
    }

    protected void notify(Object message) {
        Message msg = this.mWebSocketConnectionHandler.obtainMessage();
        msg.obj = message;
        this.mWebSocketConnectionHandler.sendMessage(msg);
    }

    private boolean processData() throws Exception {
        int rsv;
        if (this.mFrameHeader == null) {
            if (this.mApplicationBuffer.position() >= 2) {
                byte b0 = this.mApplicationBuffer.get(0);
                boolean fin = (b0 & 0x80) != 0;
                rsv = (b0 & 0x70) >> 4;
                int opcode = b0 & 0x0f;
                byte b1 = this.mApplicationBuffer.get(1);
                boolean masked = (b1 & 0x80) != 0;
                int payload_len1 = b1 & 0x7f;
                if (rsv != 0) {
                    throw new WebSocketException("RSV != 0 and no extension negotiated");
                } else if (masked) {
                    throw new WebSocketException("masked server frame");
                } else {
                    if (opcode > 7) {
                        if (!fin) {
                            throw new WebSocketException("fragmented control frame");
                        }

                        if (payload_len1 > 125) {
                            throw new WebSocketException("control frame with payload length > 125 octets");
                        }

                        if (opcode != 8 && opcode != 9 && opcode != 10) {
                            throw new WebSocketException("control frame using reserved opcode " + opcode);
                        }

                        if (opcode == 8 && payload_len1 == 1) {
                            throw new WebSocketException("received close control frame with payload len 1");
                        }
                    } else {
                        if (opcode != 0 && opcode != 1 && opcode != 2) {
                            throw new WebSocketException("data frame using reserved opcode " + opcode);
                        }

                        if (!this.mInsideMessage && opcode == 0) {
                            throw new WebSocketException("received continuation data frame outside fragmented message");
                        }

                        if (this.mInsideMessage && opcode != 0) {
                            throw new WebSocketException("received non-continuation data frame while inside fragmented message");
                        }
                    }

                    //noinspection ConstantConditions
                    int mask_len = masked ? 4 : 0;
                    int header_len;
                    if (payload_len1 < 126) {
                        header_len = 2 + mask_len;
                    } else if (payload_len1 == 126) {
                        header_len = 4 + mask_len;
                    } else {
                        if (payload_len1 != 127) {
                            throw new Exception("logic error");
                        }

                        header_len = 10 + mask_len;
                    }

                    if (this.mApplicationBuffer.position() >= header_len) {
                        int i = 2;
                        long payload_len;
                        if (payload_len1 == 126) {
                            payload_len = (long) ((0xff & this.mApplicationBuffer.get(i)) << 8 | 0xff & this.mApplicationBuffer.get(i + 1));
                            if (payload_len < 126L) {
                                throw new WebSocketException("invalid data frame length (not using minimal length encoding)");
                            }

                            i += 2;
                        } else if (payload_len1 == 127) {
                            //noinspection PointlessArithmeticExpression
                            if ((128 & this.mApplicationBuffer.get(i + 0)) != 0) {
                                throw new WebSocketException("invalid data frame length (> 2^63)");
                            }

                            //noinspection ShiftOutOfRange,PointlessArithmeticExpression
                            payload_len = (long) ((0xff & this.mApplicationBuffer.get(i + 0)) << 56 | (0xff & this.mApplicationBuffer.get(i + 1)) << 48 | (0xff & this.mApplicationBuffer.get(i + 2)) << 40 | (0xff & this.mApplicationBuffer.get(i + 3)) << 32 | (0xff & this.mApplicationBuffer.get(i + 4)) << 24 | (0xff & this.mApplicationBuffer.get(i + 5)) << 16 | (0xff & this.mApplicationBuffer.get(i + 6)) << 8 | 0xff & this.mApplicationBuffer.get(i + 7));
                            if (payload_len < 65536L) {
                                throw new WebSocketException("invalid data frame length (not using minimal length encoding)");
                            }

                            i += 8;
                        } else {
                            payload_len = (long) payload_len1;
                        }

                        if (payload_len > (long) this.mWebSocketOptions.getMaxFramePayloadSize()) {
                            throw new WebSocketException("frame payload too large");
                        } else {
                            this.mFrameHeader = new WebSocketFrameHeader();
                            this.mFrameHeader.setOpcode(opcode);
                            this.mFrameHeader.setFin(fin);
                            this.mFrameHeader.setReserved(rsv);
                            this.mFrameHeader.setPayloadLength((int) payload_len);
                            this.mFrameHeader.setHeaderLength(header_len);
                            this.mFrameHeader.setTotalLen(this.mFrameHeader.getHeaderLength() + this.mFrameHeader.getPayloadLength());
                            //noinspection ConstantConditions
                            if (masked) {
                                byte[] mask = new byte[4];

                                for (int j = 0; j < 4; ++j) {
                                    mask[i] = (byte) (0xff & this.mApplicationBuffer.get(i + j));
                                }

                                this.mFrameHeader.setMask(mask);
                                //noinspection UnusedAssignment
                                i += 4;
                            } else {
                                this.mFrameHeader.setMask(null);
                            }

                            return this.mFrameHeader.getPayloadLength() == 0 || this.mApplicationBuffer.position() >= this.mFrameHeader.getTotalLength();
                        }
                    } else {
                        return false;
                    }
                }
            } else {
                return false;
            }
        } else if (this.mApplicationBuffer.position() >= this.mFrameHeader.getTotalLength()) {
            byte[] framePayload = null;
            int oldPosition = this.mApplicationBuffer.position();
            if (this.mFrameHeader.getPayloadLength() > 0) {
                framePayload = new byte[this.mFrameHeader.getPayloadLength()];
                this.mApplicationBuffer.position(this.mFrameHeader.getHeaderLength());
                this.mApplicationBuffer.get(framePayload, 0, this.mFrameHeader.getPayloadLength());
            }

            this.mApplicationBuffer.position(this.mFrameHeader.getTotalLength());
            this.mApplicationBuffer.limit(oldPosition);
            this.mApplicationBuffer.compact();
            if (this.mFrameHeader.getOpcode() > 7) {
                if (this.mFrameHeader.getOpcode() != 8) {
                    if (this.mFrameHeader.getOpcode() == 9) {
                        this.onPing(framePayload);
                    } else {
                        if (this.mFrameHeader.getOpcode() != 10) {
                            throw new Exception("logic error");
                        }

                        this.onPong(framePayload);
                    }
                } else {
                    rsv = 1005;
                    String reason = null;
                    if (this.mFrameHeader.getPayloadLength() >= 2) {
                        //noinspection ConstantConditions
                        rsv = (framePayload[0] & 0xff) * 256 + (framePayload[1] & 0xff);
                        if (rsv < 1000 || rsv >= 1000 && rsv <= 2999 && rsv != 1000 && rsv != 1001 && rsv != 1002 && rsv != 1003 && rsv != 1007 && rsv != 1008 && rsv != 1009 && rsv != 1010 && rsv != 1011 || rsv >= 5000) {
                            throw new WebSocketException("invalid close code " + rsv);
                        }

                        if (this.mFrameHeader.getPayloadLength() > 2) {
                            byte[] ra = new byte[this.mFrameHeader.getPayloadLength() - 2];
                            System.arraycopy(framePayload, 2, ra, 0, this.mFrameHeader.getPayloadLength() - 2);
                            Utf8Validator val = new Utf8Validator();
                            val.validate(ra);
                            if (!val.isValid()) {
                                throw new WebSocketException("invalid close reasons (not UTF-8)");
                            }

                            reason = new String(ra, "UTF-8");
                        }
                    }

                    this.onClose(rsv, reason);
                }
            } else {
                if (!this.mInsideMessage) {
                    this.mInsideMessage = true;
                    this.mMessageOpcode = this.mFrameHeader.getOpcode();
                    if (this.mMessageOpcode == 1 && this.mWebSocketOptions.getValidateIncomingUtf8()) {
                        this.mUTF8Validator.reset();
                    }
                }

                if (framePayload != null) {
                    if (this.mMessagePayload.size() + framePayload.length > this.mWebSocketOptions.getMaxMessagePayloadSize()) {
                        throw new WebSocketException("message payload too large");
                    }

                    if (this.mMessageOpcode == 1 && this.mWebSocketOptions.getValidateIncomingUtf8() && !this.mUTF8Validator.validate(framePayload)) {
                        throw new WebSocketException("invalid UTF-8 in text message payload");
                    }

                    this.mMessagePayload.write(framePayload);
                }

                if (this.mFrameHeader.isFin()) {
                    if (this.mMessageOpcode == 1) {
                        if (this.mWebSocketOptions.getValidateIncomingUtf8() && !this.mUTF8Validator.isValid()) {
                            throw new WebSocketException("UTF-8 text message payload ended within Unicode code point");
                        }

                        if (this.mWebSocketOptions.getReceiveTextMessagesRaw()) {
                            this.onRawTextMessage(this.mMessagePayload.toByteArray());
                        } else {
                            String s = new String(this.mMessagePayload.toByteArray(), "UTF-8");
                            this.onTextMessage(s);
                        }
                    } else {
                        if (this.mMessageOpcode != 2) {
                            throw new Exception("logic error");
                        }

                        this.onBinaryMessage(this.mMessagePayload.toByteArray());
                    }

                    this.mInsideMessage = false;
                    this.mMessagePayload.reset();
                }
            }

            this.mFrameHeader = null;
            return this.mApplicationBuffer.position() > 0;
        } else {
            return false;
        }
    }

    protected void onHandshake(boolean success) {
        this.notify(new ServerHandshake(success));
    }

    protected void onClose(int code, String reason) {
        this.notify(new Close(code, reason));
    }

    protected void onPing(byte[] payload) {
        this.notify(new Ping(payload));
    }

    protected void onPong(byte[] payload) {
        this.notify(new Pong(payload));
    }

    protected void onTextMessage(String payload) {
        this.notify(new TextMessage(payload));
    }

    protected void onRawTextMessage(byte[] payload) {
        this.notify(new RawTextMessage(payload));
    }

    protected void onBinaryMessage(byte[] payload) {
        this.notify(new BinaryMessage(payload));
    }

    private boolean processHandshake() throws UnsupportedEncodingException {
        boolean res = false;

        for (int pos = this.mApplicationBuffer.position() - 4; pos >= 0; --pos) {
            //noinspection PointlessArithmeticExpression
            if (this.mApplicationBuffer.get(pos + 0) == 0x0d && this.mApplicationBuffer.get(pos + 1) == 0x0a && this.mApplicationBuffer.get(pos + 2) == 0x0d && this.mApplicationBuffer.get(pos + 3) == 0x0a) {
                int oldPosition = this.mApplicationBuffer.position();
                boolean serverError = false;
                if (this.mApplicationBuffer.get(0) == 72 && this.mApplicationBuffer.get(1) == 84 && this.mApplicationBuffer.get(2) == 84 && this.mApplicationBuffer.get(3) == 80) {
                    Pair status = this.parseHTTPStatus();
                    if ((Integer) status.first >= 300) {
                        this.notify(new ServerError(((Integer) status.first).intValue(), (String) status.second));
                        serverError = true;
                    }
                }

                this.mApplicationBuffer.position(pos + 4);
                this.mApplicationBuffer.limit(oldPosition);
                this.mApplicationBuffer.compact();
                if (!serverError) {
                    res = this.mApplicationBuffer.position() > 0;
                    this.mState = WebSocketReader.ReaderState.STATE_OPEN;
                } else {
                    res = true;
                    this.mState = WebSocketReader.ReaderState.STATE_CLOSED;
                    this.mStopped = true;
                }

                this.onHandshake(!serverError);
                break;
            }
        }

        return res;
    }

    private Pair<Integer, String> parseHTTPStatus() throws UnsupportedEncodingException {
        int beg;
        //noinspection StatementWithEmptyBody
        for (beg = 4; beg < this.mApplicationBuffer.position() && this.mApplicationBuffer.get(beg) != 32; ++beg) {
            // if (mApplicationBuffer.get(beg) == ' ') break;
        }

        int end;
        //noinspection StatementWithEmptyBody
        for (end = beg + 1; end < this.mApplicationBuffer.position() && this.mApplicationBuffer.get(end) != 32; ++end) {
            // if (mApplicationBuffer.get(end) == ' ') break;
        }

        ++beg;
        int statusCode = 0;

        int eol;
        int statusMessageLength;
        for (eol = 0; beg + eol < end; ++eol) {
            statusMessageLength = this.mApplicationBuffer.get(beg + eol) - 0x30;
            statusCode *= 10;
            statusCode += statusMessageLength;
        }

        ++end;

        //noinspection StatementWithEmptyBody
        for (eol = end; eol < this.mApplicationBuffer.position() && this.mApplicationBuffer.get(eol) != 0x0d; ++eol) {
            // if (mApplicationBuffer.get(eol) == 0x0d) break;
        }

        statusMessageLength = eol - end;
        byte[] statusBuf = new byte[statusMessageLength];
        this.mApplicationBuffer.position(end);
        this.mApplicationBuffer.get(statusBuf, 0, statusMessageLength);
        String statusMessage = new String(statusBuf, "UTF-8");
        //noinspection RedundantArrayCreation
        log(String.format("Status: %d (%s)", new Object[]{statusCode, statusMessage}));
        //noinspection unchecked
        return new Pair(statusCode, statusMessage);
    }

    private boolean consumeData() throws Exception {
        if (mState == ReaderState.STATE_OPEN || mState == ReaderState.STATE_CLOSING) {

            return processData();

        } else if (mState == ReaderState.STATE_CONNECTING) {

            return processHandshake();

        } else if (mState == ReaderState.STATE_CLOSED) {

            return false;

        } else {
            // should not arrive here
            return false;
        }
    }

    public void run() {
        synchronized (this) {
            this.notifyAll();
        }

        InputStream inputStream;

        try {
            inputStream = this.mSocket.getInputStream();
        } catch (IOException ioe) {
            log(ioe.getLocalizedMessage());
            return;
        }

        InputStream mInputStream = inputStream;
        log("WebSocker reader running.");
        this.mApplicationBuffer.clear();

        while (!this.mStopped) {
            try {
                int e = mInputStream.read(this.mNetworkBuffer);
                if (e > 0) {
                    this.mApplicationBuffer.put(this.mNetworkBuffer, 0, e);

                    //noinspection StatementWithEmptyBody
                    while (this.consumeData()) {

                    }
                } else if (e == -1) {
                    log( "run() : ConnectionLost");
                    this.notify(new ConnectionLost());
                    this.mStopped = true;
                } else {
                    log("WebSocketReader read() failed.");
                }
            } catch (WebSocketException wse) {
                log("run() : WebSocketException (" + wse.toString() + ")");
                this.notify(new ProtocolViolation(wse));
            } catch (SocketException se) {
                log("run() : SocketException (" + se.toString() + ")");
                this.notify(new ConnectionLost());
            } catch (IOException ioe) {
                log("run() : IOException (" + ioe.toString() + ")");
                this.notify(new ConnectionLost());
            } catch (Exception e) {
                log("run() : Exception (" + e.toString() + ")");
                this.notify(new Error(e));
            }
        }

        log("WebSocket reader ended.");
    }

    private enum ReaderState {
        STATE_CLOSED,
        STATE_CONNECTING,
        STATE_CLOSING,
        STATE_OPEN;

        ReaderState() {
        }
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
