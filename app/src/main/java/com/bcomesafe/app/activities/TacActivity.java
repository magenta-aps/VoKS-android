package com.bcomesafe.app.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.bcomesafe.app.AppContext;
import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.R;
import com.bcomesafe.app.requests.UpdateDeviceRequest;
import com.bcomesafe.app.utils.FontsUtils;
import com.bcomesafe.app.utils.RemoteLogUtils;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class TacActivity extends Activity implements View.OnClickListener {

    private static final boolean D = false;
    private static final String TAG = "TacActivity";

    private TextView tvTac;
    private TextView tvTacDecline;
    private TextView tvTacAccept;

    private long mTacDeclineRequestId = Constants.INVALID_LONG_ID;
    private long mTacAcceptRequestId = Constants.INVALID_LONG_ID;

    /**
     * Broadcast receiver for request completed actions
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("onReceive()");
            if (intent != null && intent.getAction() != null && intent.getAction().equals(Constants.REQUEST_COMPLETED_ACTION)) {
                log("onReceive() request completed action");
                if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mTacDeclineRequestId) {
                    handleTacRequestResult(mTacDeclineRequestId);
                } else if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mTacAcceptRequestId) {
                    handleTacRequestResult(mTacAcceptRequestId);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tac);
        initializeViews();
        setFonts();
        setOnClickListeners();
        setTacText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check existing requests results if needed
        if (mTacDeclineRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mTacDeclineRequestId)) {
            handleTacRequestResult(mTacDeclineRequestId);
        }
        if (mTacAcceptRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mTacAcceptRequestId)) {
            handleTacRequestResult(mTacAcceptRequestId);
        }
        // Register broadcast receiver for completed requests
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, new IntentFilter(Constants.REQUEST_COMPLETED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister broadcast receiver for completed requests
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            // Nothing to do
        }
    }

    private void initializeViews() {
        tvTac = findViewById(R.id.tv_tac);
        tvTacDecline = findViewById(R.id.tv_tac_decline);
        tvTacAccept = findViewById(R.id.tv_tac_accept);
    }

    private void setFonts() {
        tvTac.setTypeface(FontsUtils.getInstance().getTfLight());
        tvTacDecline.setTypeface(FontsUtils.getInstance().getTfBold());
        tvTacAccept.setTypeface(FontsUtils.getInstance().getTfBold());
    }

    private void setOnClickListeners() {
        tvTacDecline.setOnClickListener(this);
        tvTacAccept.setOnClickListener(this);
    }

    private void setTacText() {
        String tacText = AppUser.get().getTacText();
        if (tacText != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tvTac.setText(Html.fromHtml(tacText, Html.FROM_HTML_MODE_LEGACY));
            } else {
                //noinspection Deprecation
                tvTac.setText(Html.fromHtml(tacText));
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.tv_tac_decline) {
            declineTac();
        } else if (view.getId() == R.id.tv_tac_accept) {
            acceptTac();
        }
    }

    private void updateButtons(boolean clickable) {
        tvTacDecline.setEnabled(clickable);
        tvTacAccept.setEnabled(clickable);
    }

    private void declineTac() {
        updateButtons(false);
        mTacDeclineRequestId = AppContext.get().getRequestManager().makeRequest(
                new UpdateDeviceRequest(AppUser.get().getDeviceUID(),
                        null,
                        Constants.REQUEST_RESPONSE_VALUE_FALSE,
                        null,
                        null
                )
        );
    }

    private void acceptTac() {
        updateButtons(false);
        mTacAcceptRequestId = AppContext.get().getRequestManager().makeRequest(
                new UpdateDeviceRequest(AppUser.get().getDeviceUID(),
                        null,
                        Constants.REQUEST_RESPONSE_VALUE_TRUE,
                        null,
                        null
                )
        );
    }

    private void handleTacRequestResult(long requestId) {
        log("handleTacRequestResult()");
        boolean isDecline = requestId == mTacDeclineRequestId;

        // Check request HTTP status code
        int httpStatusCode = AppContext.get().getRequestManager().getRequestStatusCode(requestId);
        log("HTTP status code:" + httpStatusCode);

        switch (httpStatusCode) {
            case Constants.HTTP_STATUS_INTERNAL_APP_ERROR:
            case Constants.HTTP_STATUS_BAD_REQUEST:
            case Constants.HTTP_STATUS_CODE_INTERNAL_SERVER_ERROR:
            case Constants.HTTP_STATUS_CODE_NOT_AVAILABLE:
            case Constants.HTTP_STATUS_CODE_NOT_FOUND:
                log("Got request error");
                break;
            case Constants.HTTP_STATUS_CODE_OK:
            case Constants.HTTP_STATUS_CODE_REDIRECT_1:
            case Constants.HTTP_STATUS_CODE_REDIRECT_2:
                // Continue
                break;
        }

        boolean success = false;
        // If HTTP status is OK
        if (httpStatusCode == Constants.HTTP_STATUS_CODE_OK) {
            // Parse result
            String response = AppContext.get().getRequestManager().getRequestResult(requestId);
            AppContext.get().getRequestManager().clearRequestData(requestId);
            log("Got response:" + response);

            JSONObject responseJson = null;
            try {
                responseJson = new JSONObject(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (responseJson != null) {
                if (!responseJson.isNull(Constants.REQUEST_RESPONSE_PARAM_SUCCESS)) {
                    try {
                        success = responseJson.getBoolean(Constants.REQUEST_RESPONSE_PARAM_SUCCESS);
                    } catch (JSONException e) {
                        if (D) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    log("Response does not have " + Constants.REQUEST_RESPONSE_PARAM_SUCCESS);
                }
            } else {
                log("Unable to parse response JSONObject");
            }
        }

        updateButtons(true);
        if (success) {
            if (!isDecline) {
                AppUser.get().setNeedTac(false);
            } else {
                AppUser.get().setNeedTac(true);
            }
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, getString(R.string.check_internet_connection), Toast.LENGTH_SHORT).show();
        }

        if (isDecline) {
            mTacDeclineRequestId = Constants.INVALID_LONG_ID;
        } else {
            mTacAcceptRequestId = Constants.INVALID_LONG_ID;
        }
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
