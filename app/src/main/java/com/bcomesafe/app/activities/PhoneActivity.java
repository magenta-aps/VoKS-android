package com.bcomesafe.app.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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
import com.bcomesafe.app.requests.DeletePhoneRequest;
import com.bcomesafe.app.requests.UpdateDeviceRequest;
import com.bcomesafe.app.utils.FontsUtils;
import com.bcomesafe.app.utils.RemoteLogUtils;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class PhoneActivity extends Activity implements View.OnClickListener {

    private static final boolean D = false;
    private static final String TAG = "PhoneActivity";

    private TextView tvPhoneExplanation;
    private View vPhone;
    private EditText etPhone;
    private TextView tvDelete;
    private TextView tvSkip;
    private TextView tvSubmit;

    private long mDeleteRequestId = Constants.INVALID_LONG_ID;
    private long mSkipRequestId = Constants.INVALID_LONG_ID;
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
                if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mDeleteRequestId) {
                    handleRequestResult(mDeleteRequestId);
                } else if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mSkipRequestId) {
                    handleRequestResult(mSkipRequestId);
                } else if (intent.getLongExtra(Constants.REQUEST_ID_EXTRA, Constants.INVALID_LONG_ID) == mSubmitRequestId) {
                    handleRequestResult(mSubmitRequestId);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone);
        initializeViews();
        setFonts();
        setOnClickListeners();
        setPhone();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check existing requests results if needed
        if (mDeleteRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mDeleteRequestId)) {
            handleRequestResult(mDeleteRequestId);
        }
        if (mSkipRequestId != Constants.INVALID_LONG_ID && AppContext.get().getRequestManager().isRequestEnded(mSkipRequestId)) {
            handleRequestResult(mSkipRequestId);
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
        tvPhoneExplanation = findViewById(R.id.tv_phone_explanation);
        vPhone = findViewById(R.id.v_phone_delete);
        etPhone = findViewById(R.id.et_phone);
        tvDelete = findViewById(R.id.tv_phone_delete);
        tvSkip = findViewById(R.id.tv_phone_skip);
        tvSubmit = findViewById(R.id.tv_phone_submit);
    }

    private void setFonts() {
        tvPhoneExplanation.setTypeface(FontsUtils.getInstance().getTfLight());
        etPhone.setTypeface(FontsUtils.getInstance().getTfLight());
        tvDelete.setTypeface(FontsUtils.getInstance().getTfBold());
        tvSkip.setTypeface(FontsUtils.getInstance().getTfBold());
        tvSubmit.setTypeface(FontsUtils.getInstance().getTfBold());
    }

    private void setOnClickListeners() {
        tvDelete.setOnClickListener(this);
        tvSkip.setOnClickListener(this);
        tvSubmit.setOnClickListener(this);
    }

    private void setPhone() {
        String phone = AppUser.get().getPhone();
        etPhone.setText(phone);
        if (phone != null && !phone.isEmpty()) {
            vPhone.setVisibility(View.VISIBLE);
            tvDelete.setVisibility(View.VISIBLE);
        } else {
            vPhone.setVisibility(View.GONE);
            tvDelete.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.tv_phone_delete) {
            showDeletePhoneDialog();
        } else if (view.getId() == R.id.tv_phone_skip) {
            skipPhone();
        } else if (view.getId() == R.id.tv_phone_submit) {
            submitPhone();
        }
    }

    private void updateContentEnabled(boolean isEnabled) {
        tvDelete.setEnabled(isEnabled);
        tvSkip.setEnabled(isEnabled);
        tvSubmit.setEnabled(isEnabled);
        etPhone.setEnabled(isEnabled);
    }

    private void showDeletePhoneDialog() {
        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle(getString(R.string.delete_phone_dialog_title))
                .setMessage(getString(R.string.delete_phone_dialog_message))
                .setPositiveButton(getString(R.string.delete_phone_dialog_confirm), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (dialogInterface != null) {
                            dialogInterface.dismiss();
                        }
                        deletePhone();
                    }
                })
                .setNegativeButton(getString(R.string.delete_phone_dialog_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (dialogInterface != null) {
                            dialogInterface.dismiss();
                        }
                    }
                })
                .show();
    }

    private void deletePhone() {
        updateContentEnabled(false);
        mDeleteRequestId = AppContext.get().getRequestManager().makeRequest(
                new DeletePhoneRequest(AppUser.get().getDeviceUID())
        );
    }

    private void skipPhone() {
        updateContentEnabled(false);
        mSkipRequestId = AppContext.get().getRequestManager().makeRequest(
                new UpdateDeviceRequest(AppUser.get().getDeviceUID(),
                        null,
                        null,
                        Constants.REQUEST_RESPONSE_VALUE_TRUE,
                        null
                )
        );
    }

    private void submitPhone() {
        String phone = etPhone.getText().toString().trim();
        if (!phone.isEmpty()) {
            updateContentEnabled(false);
            mSubmitRequestId = AppContext.get().getRequestManager().makeRequest(
                    new UpdateDeviceRequest(AppUser.get().getDeviceUID(),
                            phone,
                            null,
                            null,
                            null
                    )
            );
        } else {
            Toast.makeText(this, getString(R.string.error_phone_empty), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleRequestResult(long requestId) {
        log("handleRequestResult()");
        boolean isSkipOrDelete = requestId == mSkipRequestId || requestId == mDeleteRequestId;

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

        updateContentEnabled(true);
        if (success) {
            if (isSkipOrDelete) {
                AppUser.get().setPhone(null);
                AppUser.get().setNeedPhone(false);
                AppUser.get().setPhoneConfirmed(true);
            } else {
                AppUser.get().setPhone(etPhone.getText().toString().trim());
                AppUser.get().setNeedPhone(false);
                AppUser.get().setPhoneConfirmed(false);
            }
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, getString(R.string.check_internet_connection), Toast.LENGTH_SHORT).show();
        }

        if (requestId == mDeleteRequestId) {
            mDeleteRequestId = Constants.INVALID_LONG_ID;
        } else if (requestId == mSkipRequestId) {
            mSkipRequestId = Constants.INVALID_LONG_ID;
        } else if (requestId == mSubmitRequestId) {
            mSubmitRequestId = Constants.INVALID_LONG_ID;
        }
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
