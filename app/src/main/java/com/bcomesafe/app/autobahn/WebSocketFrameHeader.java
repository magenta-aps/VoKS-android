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

@SuppressWarnings("unused")
public class WebSocketFrameHeader {
    private int mOpcode;
    private boolean mFin;
    private int mReserved;
    private int mHeaderLen;
    private int mPayloadLen;
    private int mTotalLen;
    private byte[] mMask;

    public int getOpcode() {
        return this.mOpcode;
    }

    public void setOpcode(int opcode) {
        this.mOpcode = opcode;
    }

    public boolean isFin() {
        return this.mFin;
    }

    public void setFin(boolean fin) {
        this.mFin = fin;
    }

    public int getReserved() {
        return this.mReserved;
    }

    public void setReserved(int reserved) {
        this.mReserved = reserved;
    }

    public int getHeaderLength() {
        return this.mHeaderLen;
    }

    public void setHeaderLength(int headerLength) {
        this.mHeaderLen = headerLength;
    }

    public int getPayloadLength() {
        return this.mPayloadLen;
    }

    public void setPayloadLength(int payloadLength) {
        this.mPayloadLen = payloadLength;
    }

    public int getTotalLength() {
        return this.mTotalLen;
    }

    public void setTotalLen(int totalLength) {
        this.mTotalLen = totalLength;
    }

    public byte[] getMask() {
        return this.mMask;
    }

    public void setMask(byte[] mask) {
        this.mMask = mask;
    }
}