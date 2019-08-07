package com.bcomesafe.app.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
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

public class PhoneConfirmActivity extends Activity implements View.OnClickListener {

    private static final boolean D = false;
    private static final String TAG = "PhoneConfirmActivity";

    private TextView tvPhoneConfirmExplanation;
    private TextView tvPhoneConfirmExplanation2;
    private EditText etPhoneToken;
    private TextView tvResend;
    private TextView tvCancel;
    private TextView tvSubmit;

    private long mResendRequestId = Constants.INVALID_LONG_ID;
    private long mCancelRequestId = Constants.INVALID_LONG_ID;
    private long mSubmitRequestId = Constants.INVALID_LONG_ID;

    /**
     * Broadcast receiver for request completed actions
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("onReceive()");
            if (intent != null && intent.getAction() != null && intent.getAction().equals(Constants.REQUEST_COMPLETED_ACTION)) {
                log("onReceive() request completed action");
                if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mResendRequestId) {
                    handleResendRequestResult(mResendRequestId);
                } else if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mCancelRequestId) {
                    handleRequestResult(mCancelRequestId);
                } else if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mSubmitRequestId) {
                    handleRequestResult(mSubmitRequestId);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_confirm);
        initializeViews();
        setFonts();
        setOnClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check existing requests results if needed
        if (mResendRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mResendRequestId)) {
            handleResendRequestResult(mResendRequestId);
        }
        if (mCancelRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mCancelRequestId)) {
            handleRequestResult(mCancelRequestId);
        }
        if (mSubmitRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mSubmitRequestId)) {
            handleRequestResult(mSubmitRequestId);
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
        tvPhoneConfirmExplanation = findViewById(R.id.tv_phone_confirm_explanation);
        tvPhoneConfirmExplanation2 = findViewById(R.id.tv_phone_confirm_explanation_2);
        etPhoneToken = findViewById(R.id.et_phone_confirm);
        tvResend = findViewById(R.id.tv_phone_confirm_resend);
        tvCancel = findViewById(R.id.tv_phone_confirm_cancel);
        tvSubmit = findViewById(R.id.tv_phone_confirm_submit);
    }

    private void setFonts() {
        tvPhoneConfirmExplanation.setTypeface(FontsUtils.getInstance().getTfLight());
        tvPhoneConfirmExplanation2.setTypeface(FontsUtils.getInstance().getTfLight());
        etPhoneToken.setTypeface(FontsUtils.getInstance().getTfLight());
        tvResend.setTypeface(FontsUtils.getInstance().getTfBold());
        tvCancel.setTypeface(FontsUtils.getInstance().getTfBold());
        tvSubmit.setTypeface(FontsUtils.getInstance().getTfBold());
    }

    private void setOnClickListeners() {
        tvResend.setOnClickListener(this);
        tvCancel.setOnClickListener(this);
        tvSubmit.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.tv_phone_confirm_resend) {
            resend();
        } else if (view.getId() == R.id.tv_phone_confirm_cancel) {
            cancel();
        } else if (view.getId() == R.id.tv_phone_confirm_submit) {
            submit();
        }
    }

    private void updateContentEnabled(boolean isEnabled) {
        tvResend.setEnabled(isEnabled);
        tvCancel.setEnabled(isEnabled);
        tvSubmit.setEnabled(isEnabled);
        etPhoneToken.setEnabled(isEnabled);
    }

    private void resend() {
        if (mResendRequestId == Constants.INVALID_LONG_ID) {
            updateContentEnabled(false);
            mResendRequestId = AppContext.get().getRequestManager().makeRequest(
                    new UpdateDeviceRequest(AppUser.get().getDeviceUID(),
                            AppUser.get().getPhone(),
                            null,
                            null,
                            null
                    )
            );
        }
    }

    private void cancel() {
        updateContentEnabled(false);
        mCancelRequestId = AppContext.get().getRequestManager().makeRequest(
                new UpdateDeviceRequest(AppUser.get().getDeviceUID(),
                        null,
                        null,
                        Constants.REQUEST_RESPONSE_VALUE_TRUE,
                        null
                )
        );
    }

    private void submit() {
        String phoneToken = etPhoneToken.getText().toString().trim();
        if (!phoneToken.isEmpty()) {
            updateContentEnabled(false);
            mSubmitRequestId = AppContext.get().getRequestManager().makeRequest(
                    new UpdateDeviceRequest(AppUser.get().getDeviceUID(),
                            null,
                            null,
                            null,
                            phoneToken
                    )
            );
        } else {
            Toast.makeText(this, getString(R.string.error_phone_token_empty), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleResendRequestResult(long requestId) {
        log("handleResendRequestResult()");

        boolean success = isRequestSuccess(requestId);

        updateContentEnabled(true);
        if (success) {
            Toast.makeText(this, getString(R.string.phone_confirm_resend_success), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.check_internet_connection), Toast.LENGTH_SHORT).show();
        }

        mResendRequestId = Constants.INVALID_LONG_ID;
    }

    private void handleRequestResult(long requestId) {
        log("handleRequestResult()");
        boolean isCancel = requestId == mCancelRequestId;
        boolean success = isRequestSuccess(requestId);
        updateContentEnabled(true);
        if (success) {
            if (isCancel) {
                AppUser.get().setPhone(null);
                AppUser.get().setNeedPhone(false);
                AppUser.get().setPhoneConfirmed(true);
            } else {
                AppUser.get().setNeedPhone(false);
                AppUser.get().setPhoneConfirmed(true);
            }
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, getString(R.string.check_internet_connection), Toast.LENGTH_SHORT).show();
        }

        if (isCancel) {
            mCancelRequestId = Constants.INVALID_LONG_ID;
        } else {
            mSubmitRequestId = Constants.INVALID_LONG_ID;
        }
    }

    private boolean isRequestSuccess(long requestId) {
        boolean success = false;
        int httpStatusCode = AppContext.get().getRequestManager().getRequestStatusCode(requestId);
        log("HTTP status code:" + httpStatusCode);
        if (httpStatusCode == Constants.HTTP_STATUS_CODE_OK) {
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
        return success;
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
