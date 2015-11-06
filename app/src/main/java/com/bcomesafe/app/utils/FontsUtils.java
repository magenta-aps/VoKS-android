/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.utils;

import com.bcomesafe.app.AppContext;

import android.graphics.Typeface;

/**
 * Fonts utilities, used for loading fonts only once
 */
public class FontsUtils {
    private static FontsUtils mInstance = null;

    private final Typeface tfBold;
    private final Typeface tfLight;

    private FontsUtils() {
        // Initialize all needed fonts for app
        tfBold = Typeface.createFromAsset(AppContext.get().getAssets(), "fonts/Roboto-Bold.ttf");
        tfLight = Typeface.createFromAsset(AppContext.get().getAssets(), "fonts/Roboto-Light.ttf");
    }

    public static FontsUtils getInstance() {
        if (mInstance == null) {
            mInstance = new FontsUtils();
        }
        return mInstance;
    }

    public Typeface getTfBold() {
        return this.tfBold;
    }

    public Typeface getTfLight() {
        return this.tfLight;
    }
}
