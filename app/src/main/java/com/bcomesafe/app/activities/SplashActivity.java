package com.bcomesafe.app.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import com.bcomesafe.app.Constants;
import com.bcomesafe.app.R;
import com.bcomesafe.app.utils.FontsUtils;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class SplashActivity extends Activity implements View.OnClickListener {

    private static final int RC_PERMISSIONS = 10001;
    private static final String[] PERMISSIONS = {Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION};

    private TextView tvExpl;
    private TextView tvSettings;

    // Auto alarm
    private boolean mAutoAlarm = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        initializeViews();
        setFonts();
        setOnClickListeners();
        Bundle gotExtras = getIntent().getExtras();
        if (gotExtras != null && gotExtras.containsKey(Constants.EXTRA_AUTO_ALARM)) {
            mAutoAlarm = gotExtras.getBoolean(Constants.EXTRA_AUTO_ALARM, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissions()) {
            startAlarmActivity();
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, RC_PERMISSIONS);
        }
    }

    private void initializeViews() {
        tvExpl = findViewById(R.id.tv_splash_explanation);
        tvSettings = findViewById(R.id.tv_splash_settings);
    }

    private void setFonts() {
        tvExpl.setTypeface(FontsUtils.getInstance().getTfLight());
        tvSettings.setTypeface(FontsUtils.getInstance().getTfBold());
    }

    private void setOnClickListeners() {
        tvSettings.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.tv_splash_settings) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (checkPermissions()) {
            startAlarmActivity();
        }
    }

    public boolean checkPermissions() {
        for (String permission : PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startAlarmActivity() {
        Intent splashIntent = new Intent(this, AlarmActivity.class);
        splashIntent.putExtra(Constants.EXTRA_AUTO_ALARM, mAutoAlarm);
        startActivity(splashIntent);
        finish();
    }
}
