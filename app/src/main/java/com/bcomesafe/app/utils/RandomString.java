/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.utils;

import java.util.Random;

/**
 * Used for generating random string
 */
public class RandomString {

    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_RANDOM_LENGTH = 16;
    private static final char[] mSymbols;
    private final Random mRandom = new Random();
    private final char[] mBuf;

    static {
        StringBuilder temp = new StringBuilder();
        for (char c = '0'; c <= '9'; c++) {
            temp.append(c);
        }
        for (char c = 'a'; c <= 'z'; c++) {
            temp.append(c);
        }
        mSymbols = temp.toString().toCharArray();
    }

    @SuppressWarnings("SameParameterValue")
    public RandomString(int length) {
        if (length < 1) {
            length = DEFAULT_RANDOM_LENGTH;
        }
        mBuf = new char[length];
    }

    public String nextString() {
        for (int idx = 0; idx < mBuf.length; ++idx)
            mBuf[idx] = mSymbols[mRandom.nextInt(mSymbols.length)];
        return new String(mBuf);
    }
}