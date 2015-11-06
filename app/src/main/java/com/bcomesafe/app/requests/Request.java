/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.requests;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.DefaultParameters;
import com.bcomesafe.app.utils.RemoteLogUtils;

/**
 * Single request
 */
@SuppressWarnings("unused")
abstract class Request {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = Request.class.getSimpleName();

    // Constants
    public static final String REQUEST_METHOD_PUT = "PUT";
    public static final String REQUEST_METHOD_GET = "GET";
    public static final String REQUEST_METHOD_POST = "POST";

    public static final String DEFAULT_REQUEST_METHOD = REQUEST_METHOD_GET;

    // POST parameters types
    public static final String POST_PARAMETERS_URL = "URL";
    public static final String POST_PARAMETERS_BODY = "BODY";

    public static final String DEFAULT_POST_PARAMETERS = POST_PARAMETERS_URL;

    // Variables
    // Request URL
    private String mURL = Constants.INVALID_STRING_ID;
    // Request method
    private String mRequestMethod = DEFAULT_REQUEST_METHOD;
    // Request POST parameters type
    private String mPostParametersType = DEFAULT_POST_PARAMETERS;
    // Request parameters
    private final HashMap<String, String> mParameters = new HashMap<>();
    // Request parameters as JSONObject
    private JSONObject mParametersJSON;
    // Request headers
    private final HashMap<String, String> mHeaders = new HashMap<>();
    // Should request be executed on executor
    private boolean executeOnExecutor = false;
    // Should we override URL base with API URL from registration response
    private boolean mShouldOverrideBaseURL = true;

    public Request(String url) {
        setURL(url);
    }

    /**
     * Returns request URL
     *
     * @return string URL
     */
    public String getURL() {
        if (mRequestMethod.equals(REQUEST_METHOD_GET) || (mRequestMethod.equals(REQUEST_METHOD_POST) && mPostParametersType.equals(POST_PARAMETERS_URL))) {
            if (mShouldOverrideBaseURL) {
                String apiURL = AppUser.get().getAPIURL().equals(Constants.INVALID_STRING_ID) ? DefaultParameters.getDefaultAPIURL() : AppUser.get().getAPIURL();
                if (mURL.length() > 0 && '/' == mURL.charAt(0) && apiURL.length() > 0 && '/' == apiURL.charAt(apiURL.length() - 1)) {
                    mURL = mURL.substring(1, mURL.length());
                }
                return apiURL + mURL + getParametersAsURLParameters();
            } else {
                return DefaultParameters.getDefaultAPIURL() + mURL + getParametersAsURLParameters();
            }
        } else {
            if (mShouldOverrideBaseURL) {
                String apiURL = AppUser.get().getAPIURL().equals(Constants.INVALID_STRING_ID) ? DefaultParameters.getDefaultAPIURL() : AppUser.get().getAPIURL();
                if (mURL.length() > 0 && '/' == mURL.charAt(0) && apiURL.length() > 0 && '/' == apiURL.charAt(apiURL.length() - 1)) {
                    mURL = mURL.substring(1, mURL.length());
                }
                return apiURL + mURL;
            } else {
                return DefaultParameters.getDefaultAPIURL() + mURL;
            }
        }
    }

    /**
     * Sets request URL
     *
     * @param url as string
     */
    public void setURL(String url) {
        mURL = url;
    }

    /**
     * Returns request method
     *
     * @return string method
     */
    public String getMethod() {
        return mRequestMethod;
    }

    /**
     * Sets request method
     *
     * @param method as string
     */
    public void setMethod(String method) {
        mRequestMethod = method;
    }

    /**
     * Returns request post parameters type
     *
     * @return string request post parameters type
     */
    public String getPostParametersType() {
        return mPostParametersType;
    }

    /**
     * Sets request post parameters type
     *
     * @param type as string
     */
    public void setPostParametersType(String type) {
        mPostParametersType = type;
    }

    /**
     * Adds parameter to request
     *
     * @param key   - parameter key
     * @param value - parameter value
     */
    public void addParameter(String key, String value) {
        mParameters.put(key, value);
    }

    /**
     * Gets all request parameters
     *
     * @return HashMap of parameters
     */
    public HashMap<String, String> getParameters() {
        return mParameters;
    }

    /**
     * Adds header to request
     *
     * @param key   - header key
     * @param value - header value
     */
    public void addHeader(String key, String value) {
        mHeaders.put(key, value);
    }

    /**
     * Gets all request headers
     *
     * @return HashMap of headers
     */
    public HashMap<String, String> getHeaders() {
        return mHeaders;
    }

    /**
     * Gets if request should be executed on executor
     *
     * @return boolean
     */
    public boolean getExecuteOnExecutor() {
        return executeOnExecutor;
    }

    /**
     * Sets if request should be executed on executor
     *
     * @param executeOnExecutor - boolean
     */
    public void setExecuteOnExecutor(boolean executeOnExecutor) {
        this.executeOnExecutor = executeOnExecutor;
    }

    /**
     * Converts parameters to JSON string
     *
     * @return String - JSON parameters
     */
    public String parametersToJSONString() {
        if (mParametersJSON != null) {
            return mParametersJSON.toString();
        } else if (mParameters.size() > 0) {
            JSONObject holder = new JSONObject();
            for (Map.Entry<String, String> entry : mParameters.entrySet()) {
                try {
                    holder.put(entry.getKey(), entry.getValue());
                } catch (JSONException e) {
                    log("Unable to add parameter:" + entry.getKey() + "=" + entry.getValue());
                }
            }
            return holder.toString();
        } else {
            return "{}";
        }
    }

    /**
     * Converts parameters to string
     *
     * @return String - parameters
     */
    public String getParametersAsString() {
        if (mParameters.size() > 0) {
            String holder = "";
            boolean first = true;
            for (Map.Entry<String, String> entry : mParameters.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    holder += "&";
                }
                holder += entry.getKey() + "=" + entry.getValue();
            }
            return holder;
        } else {
            return "";
        }
    }

    /**
     * Gets parameters as JSONObject
     *
     * @return JSONObject parameters
     */
    public JSONObject getParametersJSON() {
        return mParametersJSON;
    }

    /**
     * Sets parameters as JSONObject
     */
    public void setParametersJSON(JSONObject parameters) {
        this.mParametersJSON = parameters;
    }

    /**
     * Returns either request has parameters or not
     *
     * @return boolean
     */
    public boolean hasParameters() {
        return (mParameters.size() > 0) || (mParametersJSON != null && mParametersJSON.length() > 0);
    }

    /**
     * Returns either request has headers or not
     *
     * @return boolean
     */
    public boolean hasHeaders() {
        return mHeaders.size() > 0;
    }

    /**
     * Returns parameters as URL parameters
     *
     * @return string
     */
    private String getParametersAsURLParameters() {
        log("getParametersAsURLParameters()");

        if (mParameters.size() == 0) {
            return Constants.INVALID_STRING_ID;
        }

        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : mParameters.entrySet()) {
            boolean wasFirst = first;
            if (first) {
                result.append("?");
                first = false;
            } else {
                result.append("&");
            }
            try {
                String encodedKey = URLEncoder.encode(entry.getKey(), "UTF-8");
                String encodedValue = URLEncoder.encode(entry.getValue(), "UTF-8");
                result.append(encodedKey);
                result.append("=");
                result.append(encodedValue);
            } catch (UnsupportedEncodingException uee) {
                log("Unable to add parameter " + entry.getKey() + "=" + entry.getValue() + "; result on start=" + result.toString());
                if (wasFirst) {
                    first = true;
                }
                if (result.length() > 0) {
                    result.deleteCharAt(result.length() - 1);
                }
                log("result on end=" + result.toString());
            }
        }
        log("result=" + result.toString());
        return result.toString();
    }

    public void setShouldOverrideBaseURL(boolean val) {
        this.mShouldOverrideBaseURL = val;
    }

    public boolean getShouldOverrideBaseURL() {
        return this.mShouldOverrideBaseURL;
    }

    /**
     * Logs msg
     *
     * @param msg String
     */
    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
