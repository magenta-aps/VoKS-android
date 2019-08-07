/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.requests;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import com.bcomesafe.app.AppContext;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.DefaultParameters;
import com.bcomesafe.app.utils.RemoteLogUtils;
import com.bcomesafe.app.utils.Utils;

@SuppressLint("CommitPrefEdits")
public class RequestManager {

    // Debugging
    private static final String TAG = RequestManager.class.getSimpleName();
    private static final boolean D = false;

    // Next request ID
    private long mNextRequestId = 0L;

    // Shared preferences
    private final SharedPreferences mRequestStore = AppContext.get().getSharedPreferences(Constants.SHARED_PREFERENCES_REQUESTS, Context.MODE_PRIVATE);

    public RequestManager() {
        clearRequestStore();
    }

    @SuppressLint("NewApi")
    public long makeRequest(final Request request) {
        final long requestId = mNextRequestId;
        mNextRequestId++;

        @SuppressLint("StaticFieldLeak")
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                log("makeRequest()");

                URL url = null;

                try {
                    url = new URL(request.getURL());
                } catch (MalformedURLException mue) {
                    // Will be handled later
                }

                log("request URL=" + url);
                log("request method=" + request.getMethod());
                log("request parameters type=" + request.getPostParametersType());

                int statusCode = Constants.HTTP_STATUS_INTERNAL_APP_ERROR;
                String message = Constants.INVALID_STRING_ID;
                String response = Constants.INVALID_STRING_ID;

                if (url != null) {
                    try {
                        HttpURLConnection conn;
                        if (DefaultParameters.SHOULD_USE_SSL) {
                            conn = (HttpsURLConnection) url.openConnection();
                            // Bypass SSL check for developing env
                            // TODO This should be removed when going to production
                            if (D || DefaultParameters.ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV) {
                                SSLSocketFactory sslSocketFactory = Utils.getBypassedSSLSocketFactory(AppContext.get());
                                if (sslSocketFactory != null) {
                                    ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory);
                                }
                            }
                            // END of SSL bypass
                        } else {
                            conn = (HttpURLConnection) url.openConnection();
                        }

                        conn.setUseCaches(false);
                        conn.setReadTimeout(DefaultParameters.REQUEST_READ_TIMEOUT);
                        conn.setConnectTimeout(DefaultParameters.REQUEST_CONNECTION_TIMEOUT);
                        conn.setRequestMethod(request.getMethod());
                        conn.setUseCaches(false);
                        conn.setDoInput(true);
                        conn.setDoOutput(true);

                        // Add headers
                        if (request.hasHeaders()) {
                            log("request has headers");
                            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                                log("adding header:" + header.getKey() + ", value:" + header.getValue());
                                conn.setRequestProperty(header.getKey(), header.getValue());
                            }
                        } else {
                            log("request does not have headers");
                        }

                        // Add parameters if they are not URL parameters
                        if (request.hasParameters()) {
                            if (request.getPostParametersType().equals(Request.POST_PARAMETERS_BODY)) {
                                log("adding parameters to body");
                                OutputStream os = conn.getOutputStream();
                                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                                writer.write(request.getParametersAsString());

                                writer.flush();
                                writer.close();
                                os.close();
                            } else {
                                log("parameters already added to url");
                            }
                        } else {
                            log("request does not have parameters");
                        }

                        statusCode = conn.getResponseCode();
                        message = conn.getResponseMessage();
                        response = Constants.INVALID_STRING_ID;
                        InputStream is = conn.getInputStream();
                        if (is != null) {
                            String line;
                            BufferedReader br = new BufferedReader(new InputStreamReader(is));
                            while ((line = br.readLine()) != null) {
                                response += line + "\n";
                            }
                            is.close();
                            // Store response, status code
                            storeResult(requestId, response, statusCode);
                        } else {
                            storeResult(requestId, Constants.INVALID_STRING_ID, Constants.HTTP_STATUS_INTERNAL_APP_ERROR);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        storeResult(requestId, Constants.INVALID_STRING_ID, Constants.HTTP_STATUS_INTERNAL_APP_ERROR);
                        // Send error broadcast
                        Writer writer = new StringWriter();
                        PrintWriter printWriter = new PrintWriter(writer);
                        e.printStackTrace(printWriter);
                        String stackTrace = writer.toString();
                        sendErrorBroadcast(requestId, statusCode, message, response, stackTrace);
                    }
                } else {
                    storeResult(requestId, Constants.INVALID_STRING_ID, Constants.HTTP_STATUS_INTERNAL_APP_ERROR);
                }
                log("request id=" + requestId);
                log("request message=" + message);
                log("request response=" + response);
                log("request statusCode=" + statusCode);

                // Send broadcast
                Intent intent = new Intent(Constants.REQUEST_COMPLETED_ACTION);
                intent.putExtra(Constants.REQUEST_ID_EXTRA, requestId);
                LocalBroadcastManager.getInstance(AppContext.get()).sendBroadcast(intent);
                // If status code not OK
                if (statusCode != Constants.HTTP_STATUS_CODE_OK && statusCode != Constants.HTTP_STATUS_INTERNAL_APP_ERROR) {
                    // Send error broadcast
                    sendErrorBroadcast(requestId, statusCode, message, response, Constants.INVALID_STRING_ID);
                }
                return null;
            }

            /**
             * Sends error broadcast
             *
             * @param requestId long
             * @param statusCode int
             * @param message string
             * @param response string
             * @param stackTrace string
             */
            private void sendErrorBroadcast(long requestId, int statusCode, String message, String response, String stackTrace) {
                log("sendErrorBroadcast()");
                Intent intent = new Intent(Constants.REQUEST_ERROR_ACTION);
                intent.putExtra(Constants.REQUEST_ID_EXTRA, requestId);
                intent.putExtra(Constants.REQUEST_STATUS_CODE_EXTRA, statusCode);
                intent.putExtra(Constants.REQUEST_MESSAGE_EXTRA, message);
                intent.putExtra(Constants.REQUEST_RESPONSE_EXTRA, response);
                intent.putExtra(Constants.REQUEST_STACK_TRACE_EXTRA, stackTrace);
                LocalBroadcastManager.getInstance(AppContext.get()).sendBroadcast(intent);
            }

            /**
             * Stores request result
             * @param requestId - request id
             * @param response - response String
             * @param statusCode - status code int
             */
            private void storeResult(long requestId, String response, int statusCode) {
                log("storeResult()");
                mRequestStore.edit().putString("request_response_" + requestId, response).commit();
                mRequestStore.edit().putInt("request_status_code_" + requestId, statusCode).commit();
            }
        };

        if (Build.VERSION.SDK_INT >= 11 && request.getExecuteOnExecutor()) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
        }
        return requestId;
    }

    /**
     * Gets request result
     *
     * @param requestId long
     * @return request result as string
     */
    public String getRequestResult(long requestId) {
        return mRequestStore.getString("request_response_" + requestId, "");
    }

    /**
     * Gets request status code
     *
     * @param requestId long
     * @return request status code as int
     */
    public Integer getRequestStatusCode(long requestId) {
        return mRequestStore.getInt("request_status_code_" + requestId, Constants.INVALID_INT_ID);
    }

    /**
     * Returns boolean if request ended
     *
     * @param requestId long
     * @return boolean
     */
    public boolean isRequestEnded(long requestId) {
        return mRequestStore.contains("request_status_code_" + requestId) && mRequestStore.contains("request_response_" + requestId);
    }

    /**
     * Returns boolean if request was successful
     *
     * @param requestId long
     * @return boolean
     */
    @SuppressWarnings("unused")
    public boolean wasSuccessful(long requestId) {
        return (mRequestStore.getInt("request_status_code_" + requestId, Constants.INVALID_INT_ID) == Constants.HTTP_STATUS_CODE_OK);
    }

    /**
     * Clears request store
     */
    public void clearRequestStore() {
        mRequestStore.edit().clear().commit();
    }

    /**
     * Clears request data by request id
     */
    public void clearRequestData(long requestId) {
        log("clearRequestData() requestId:" + requestId);
        mRequestStore.edit().remove("request_response_" + requestId).commit();
        mRequestStore.edit().remove("request_status_code_" + requestId).commit();
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