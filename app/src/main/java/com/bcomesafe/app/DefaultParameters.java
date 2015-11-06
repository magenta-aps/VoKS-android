/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app;

/**
 * Application default parameters
 */
public final class DefaultParameters {

    // Settings
    // Should app use SSL or not
    public static final boolean SHOULD_USE_SSL = false;
    // Enable/disable remote logs
    public static final boolean REMOTE_LOGS_ENABLED = false;
    // Threshold after which GCM message is treated as outdated
    public static final long GCM_MESSAGE_THRESHOLD_MS = 60 * 60 * 1000; // 1 hour in milliseconds

    // Other settings
    // General
    // Police number
    public static final String POLICE_NUMBER = "";

    // Request timeouts
    public static final int REQUEST_READ_TIMEOUT = 10000;
    public static final int REQUEST_CONNECTION_TIMEOUT = 10000;
    // Request repeat count on error
    public static final int REQUEST_REPEAT_COUNT_ON_ERROR = 15;

    // GCM project ID
    // TODO This has to be changed when going live
    public static final String GCM_SENDER_ID = "YOU_SHOULD_CHANGE_THIS_TO_YOUR_GOOGLE_PROJECT_ID";

    // URL configuration
    // --------------------------------------------------------------------------------------------
    // Shelter id
    private static final String DEFAULT_SHELTER_ID = "shelter";
    // SSL URLs
    private static final String SSL_DEFAULT_URL = "";
    private static final String SSL_DEFAULT_WS_URL = "wss://" + SSL_DEFAULT_URL + ":9000/";
    private static final String SSL_DEFAULT_API_URL = "https://" + SSL_DEFAULT_URL + "/api/voks";
    private static final String SSL_DEFAULT_CHECK_URL = "https://" + SSL_DEFAULT_URL + "/check_connection/check.txt";
    // Non SSL URLs
    private static final String DEFAULT_URL = "";
    private static final String DEFAULT_WS_URL = "ws://" + DEFAULT_URL + ":9000/";
    private static final String DEFAULT_API_URL = "http://" + DEFAULT_URL + "/api/voks";
    private static final String DEFAULT_CHECK_URL = "http://" + DEFAULT_URL + "/check_connection/check.txt";

    // Getters
    public static String getDefaultShelterId() {
        return DEFAULT_SHELTER_ID;
    }

    public static String getDefaultWSURL() {
        if (SHOULD_USE_SSL) {
            return SSL_DEFAULT_WS_URL;
        } else {
            return DEFAULT_WS_URL;
        }
    }

    public static String getDefaultAPIURL() {
        if (SHOULD_USE_SSL) {
            return SSL_DEFAULT_API_URL;
        } else {
            return DEFAULT_API_URL;
        }
    }

    public static String getDefaultCheckURL() {
        if (SHOULD_USE_SSL) {
            return SSL_DEFAULT_CHECK_URL;
        } else {
            return DEFAULT_CHECK_URL;
        }
    }
}
