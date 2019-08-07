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
    public static final int ENVIRONMENT_ID = Constants.ENVIRONMENT_RELEASE;
    // Should app use SSL or not
    public static final boolean SHOULD_USE_SSL = true;
    // Enable/disable remote logs
    public static final boolean REMOTE_LOGS_ENABLED = false;
    // Threshold after which GCM message is treated as outdated
    public static final long GCM_MESSAGE_THRESHOLD_MS = 60 * 60 * 1000; // 1 hour in milliseconds
    // Is WiFi requirement disabled?
    public static final boolean WIFI_REQUIREMENT_DISABLED = true;
    // Is BCS registration required every time
    public static final boolean BCS_REGISTRATION_REQUIRED_EVERY_TIME = true;
    public static final long WORKER_ASYNC_TASK_LAUNCH_INTERVAL = 3000L;
    // Is BCS settings functionality enabled
    public static final boolean BCS_SETTINGS_FUNCTIONALITY_ENABLED = false;

    // Location
    public static final long GPS_UPDATE_INTERVAL_IN_MILLISECONDS = 5000;
    public static final long GPS_UPDATE_INTERVAL_IN_MILLISECONDS_FASTEST = GPS_UPDATE_INTERVAL_IN_MILLISECONDS / 2;

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

    // Connection check URL end
    public static final String DEFAULT_CHECK_URL_END = "/check_connection/check.txt";

    // URL configuration
    // --------------------------------------------------------------------------------------------
    // Shelter id
    private static final String DEFAULT_SHELTER_ID = "shelter";
    // Base URLs
    public static final String DEFAULT_API_URL_SUFFIX = "/api/bcs";
    // SSL URLs
    // TODO This has to be changed when going live
    private static final String SSL_DEFAULT_URL = "YOU_SHOULD_CHANGE_THIS_TO_YOUR_GOOGLE_PROJECT_ID"; // LIVE SSL SHELTER URL
    private static final String SSL_DEFAULT_WS_URL = "wss://" + SSL_DEFAULT_URL + ":9000/";
    private static final String SSL_DEFAULT_API_URL_PREFIX = "https://" + SSL_DEFAULT_URL;
    private static final String SSL_DEFAULT_CHECK_URL = "https://" + SSL_DEFAULT_URL + "/check_connection/check.txt";
    // Non SSL URLs
    // TODO This has to be changed when going live
    private static final String DEFAULT_URL = "YOU_SHOULD_CHANGE_THIS_TO_YOUR_GOOGLE_PROJECT_ID"; // LIVE SHELTER URL
    private static final String DEFAULT_WS_URL = "ws://" + DEFAULT_URL + ":9000/";
    private static final String DEFAULT_API_URL_PREFIX = "http://" + DEFAULT_URL;
    private static final String DEFAULT_CHECK_URL = "http://" + DEFAULT_URL + "/check_connection/check.txt";

    //SSL DEV URLs
    private static final String SSL_DEV_URL = "DEV SSL SHELTER URL";
    private static final String SSL_DEV_WS_URL = "wss://" + SSL_DEV_URL + ":9000/";
    private static final String SSL_DEV_API_URL_PREFIX = "https://" + SSL_DEV_URL;
    private static final String SSL_DEV_CHECK_URL = "https://" + SSL_DEV_URL + "/check_connection/check.txt";

    // Non SSL DEV URLs
    private static final String DEV_URL = "DEV SHELTER URL";
    private static final String DEV_WS_URL = "ws://" + DEV_URL + ":9000/";
    private static final String DEV_API_URL_PREFIX = "http://" + DEV_URL;
    private static final String DEV_CHECK_URL = "http://" + DEV_URL + "/check_connection/check.txt";

    // Getters
    public static String getDefaultShelterId() {
        return DEFAULT_SHELTER_ID;
    }

    public static String getDefaultWSURL() {
        if (ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV) {
            if (SHOULD_USE_SSL) {
                return SSL_DEV_WS_URL;
            } else {
                return DEV_WS_URL;
            }
        } else if (ENVIRONMENT_ID == Constants.ENVIRONMENT_RELEASE) {
            if (SHOULD_USE_SSL) {
                return SSL_DEFAULT_WS_URL;
            } else {
                return DEFAULT_WS_URL;
            }
        } else return null;
    }

    public static String getDefaultAPIURL() {
        if (ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV) {
            if (SHOULD_USE_SSL) {
                return SSL_DEV_API_URL_PREFIX + DEFAULT_API_URL_SUFFIX;
            } else {
                return DEV_API_URL_PREFIX + DEFAULT_API_URL_SUFFIX;
            }
        } else if (ENVIRONMENT_ID == Constants.ENVIRONMENT_RELEASE) {
            if (SHOULD_USE_SSL) {
                return SSL_DEFAULT_API_URL_PREFIX + DEFAULT_API_URL_SUFFIX;
            } else {
                return DEFAULT_API_URL_PREFIX + DEFAULT_API_URL_SUFFIX;
            }
        } else return null;
    }

    public static String getDefaultCheckURL() {
        if (ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV) {
            if (SHOULD_USE_SSL) {
                return SSL_DEV_CHECK_URL;
            } else {
                return DEV_CHECK_URL;
            }
        } else if (ENVIRONMENT_ID == Constants.ENVIRONMENT_RELEASE) {
            if (SHOULD_USE_SSL) {
                return SSL_DEFAULT_CHECK_URL;
            } else {
                return DEFAULT_CHECK_URL;
            }
        } else return null;
    }
}