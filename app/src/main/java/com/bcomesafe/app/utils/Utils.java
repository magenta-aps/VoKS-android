/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import com.bcomesafe.app.AppContext;
import com.bcomesafe.app.AppUser;
import com.bcomesafe.app.Constants;
import com.bcomesafe.app.DefaultParameters;

/**
 * Utils
 */
public class Utils {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = Utils.class.getSimpleName();

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    public static int getCurrentAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            log("Unable to get current app version");
            return Constants.INVALID_INT_ID;
        }
    }

    /**
     * @return Current OS version
     */
    @SuppressWarnings("SameReturnValue")
    public static int getCurrentOsVersion() {
        return android.os.Build.VERSION.SDK_INT;
    }

    /**
     * Checks if device is network connected only by Wi-Fi
     *
     * @return boolean true/false
     */
    @SuppressWarnings("unused")
    public static boolean isWiFiConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isAvailable() && (networkInfo.getType() == ConnectivityManager.TYPE_WIFI);
    }

    /**
     * Checks if host is reachable
     *
     * @param context          - context
     * @param urlString        - URL address
     * @param timeoutInSeconds - timeout in seconds
     * @return boolean
     */
    @SuppressWarnings("SameParameterValue")
    public static boolean isURLReachable(Context context, String urlString, int timeoutInSeconds) {
        boolean reachable = false;

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(urlString);

                if (DefaultParameters.SHOULD_USE_SSL) {
                    conn = (HttpsURLConnection) url.openConnection();
                    // Bypass SSL check for developing env
                    if (DefaultParameters.ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV) {
                        SSLSocketFactory sslSocketFactory = getBypassedSSLSocketFactory(context);
                        if (sslSocketFactory != null) {
                            ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory);
                        }
                    }
                    // END of SSL bypass
                } else {
                    conn = (HttpURLConnection) url.openConnection();
                }

                conn.setUseCaches(false);

                conn.setConnectTimeout(timeoutInSeconds * 1000);
                conn.connect();
                log("ResponseCode=" + conn.getResponseCode());
                if (conn.getResponseCode() == Constants.HTTP_STATUS_CODE_OK) {
                    log("URL=" + urlString + " is reachable");
                    reachable = true;
                }
            } catch (Exception e) {
                if (D) {
                    Writer writer = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(writer);
                    e.printStackTrace(printWriter);
                    String stackTrace = writer.toString();
                    log("StackTrace:" + stackTrace);
                }
                reachable = false;
            }

            if (conn != null) {
                conn.disconnect();
            }
        } else {
            reachable = false;
        }

        log("URL=" + urlString + " is reachable == " + reachable);
        return reachable;
    }

    /**
     * Mutes sounds
     */
    @SuppressWarnings("deprecation")
    public static void muteSounds(Context context, boolean ignoreChanges) {
        log("muteSounds()");
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (ignoreChanges) {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            } else {
                AppUser.get().setUserRingerMode(audioManager.getRingerMode());
                if (AppUser.get().getUserRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                }

                AppUser.get().setUserStreamMusicVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
                if (AppUser.get().getUserStreamMusicVolume() != 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                }
            }
        } else {
            if (ignoreChanges) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
                try {
                    audioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                    audioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION, AudioManager.VIBRATE_SETTING_OFF);
                } catch (Exception e) {
                    // Nothing to do
                }
            } else {
                AppUser.get().setUserStreamAlarmVolume(audioManager.getStreamVolume(AudioManager.STREAM_ALARM));
                if (AppUser.get().getUserStreamAlarmVolume() != 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
                }

                AppUser.get().setUserStreamMusicVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
                if (AppUser.get().getUserStreamMusicVolume() != 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                }

                AppUser.get().setUserStreamNotificationVolume(audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION));
                if (AppUser.get().getUserStreamNotificationVolume() != 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
                }

                AppUser.get().setUserStreamSystemVolume(audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM));
                if (AppUser.get().getUserStreamSystemVolume() != 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
                }

                AppUser.get().setUserStreamRingVolume(audioManager.getStreamVolume(AudioManager.STREAM_RING));
                if (AppUser.get().getUserStreamRingVolume() != 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
                }

                log("muteSounds, user settings:" + AppUser.get().getUserStreamAlarmVolume() + ";"
                        + AppUser.get().getUserStreamMusicVolume() + ";"
                        + AppUser.get().getUserStreamNotificationVolume() + ";"
                        + AppUser.get().getUserStreamSystemVolume() + ";"
                        + AppUser.get().getUserStreamRingVolume());

                try {
                    AppUser.get().setUserVibrateTypeRinger(audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER));
                    if (AppUser.get().getUserVibrateTypeRinger() != 0) {
                        audioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                    }

                    AppUser.get().setUserVibrateTypeNotification(audioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION));
                    if (AppUser.get().getUserVibrateTypeNotification() != 0) {
                        audioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION, AudioManager.VIBRATE_SETTING_OFF);
                    }
                } catch (Exception e) {
                    // Nothing to do
                    e.printStackTrace();
                }
            }
        }
        //audioManager.setStreamVolume(AudioManager.STREAM_DTMF, 0, 0);
        //audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0);
        //AppUser.get().setUserRingerMode(audioManager.getRingerMode());
        //if (AppUser.get().getUserRingerMode() != AudioManager.RINGER_MODE_SILENT) {
        //    audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        //}
        log("muteSounds() end");
    }

    /**
     * Unmutes sounds
     */
    @SuppressWarnings("deprecation")
    public static void unmuteSounds(Context context) {
        log("unmuteSounds()");
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            log("unmuteSounds() pre lollipop");
            audioManager.setRingerMode(AppUser.get().getUserRingerMode());
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, AppUser.get().getUserStreamMusicVolume(), 0);
        } else {
            log("unmuteSounds() lollipop user settings:" + AppUser.get().getUserStreamAlarmVolume() + ";"
                    + AppUser.get().getUserStreamMusicVolume() + ";"
                    + AppUser.get().getUserStreamNotificationVolume() + ";"
                    + AppUser.get().getUserStreamSystemVolume() + ";"
                    + AppUser.get().getUserStreamRingVolume());
            if (AppUser.get().getUserStreamAlarmVolume() != 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, AppUser.get().getUserStreamAlarmVolume(), 0);
            }
            if (AppUser.get().getUserStreamMusicVolume() != 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, AppUser.get().getUserStreamMusicVolume(), 0);
            }
            if (AppUser.get().getUserStreamNotificationVolume() != 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, AppUser.get().getUserStreamNotificationVolume(), 0);
            }
            if (AppUser.get().getUserStreamSystemVolume() != 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, AppUser.get().getUserStreamSystemVolume(), 0);
            }
            if (AppUser.get().getUserStreamRingVolume() != 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, AppUser.get().getUserStreamRingVolume(), 0);
            }
            try {
                audioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AppUser.get().getUserVibrateTypeRinger());
                audioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION, AppUser.get().getUserVibrateTypeRinger());
            } catch (Exception e) {
                // Nothing to do
                e.printStackTrace();
            }
            // if (AppUser.get().getUserRingerMode() != Constants.INVALID_INT_ID && AppUser.get().getUserRingerMode() != audioManager.getRingerMode()) {
            //    audioManager.setRingerMode(AppUser.get().getUserRingerMode());
            //}
        }
        log("unmuteSounds() end");
    }

    public static boolean doesCameraExist(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    public static boolean doesMicrophoneExist(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }

    @SuppressWarnings("deprecation")
    public static boolean isCameraAvailable() {
        // TODO think about deprecation
        Camera camera = null;
        try {
            camera = Camera.open();
        } catch (Exception e) {
            log("camera is in use");
            return false;
        }

        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
        }
        log("camera is not in use");
        return true;
    }

    public static boolean isMicrophoneAvailable(Context context) {
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setOutputFile(new File(context.getCacheDir(), "MediaUtil#micAvailTestFile").getAbsolutePath());
        boolean available = true;
        try {
            recorder.prepare();
            recorder.start();
        } catch (Exception exception) {
            available = false;
        }
        recorder.release();
        // NOTE other version
        // log("MICROPHONE AVAILABLE=" + available);
        // int sampleRateInHz = 8000;//8000 44100, 22050 and 11025
        // int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        // int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        // int bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        // AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRateInHz, channelConfig, audioFormat, bufferSize);
        // if (audioRecord.getRecordingState() == 3) {
        //     available = false;
        // }
        // log("MICROPHONE AVAILABLE=" + available);
        if (available) {
            log("microphone is not in use");
        } else {
            log("microphone is in use");
        }
        return available;
    }

    /**
     * Bypasses SSL certificate validation for connection
     *
     * @param context Context
     */
    public static SSLSocketFactory getBypassedSSLSocketFactory(Context context) {
        if (DefaultParameters.ENVIRONMENT_ID == Constants.ENVIRONMENT_DEV && DefaultParameters.SHOULD_USE_SSL) {
            if (context == null) {
                context = AppContext.get().getApplicationContext();
            }

            try {
                // Load CAs from an InputStream
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                // NOTE ba.crt - DEV, ba2.crt - UAT
                InputStream caInput = context.getAssets().open("ba.crt");

                //noinspection ConstantConditions
                if (caInput != null) {
                    Certificate ca;
                    //noinspection TryFinallyCanBeTryWithResources
                    try {
                        ca = cf.generateCertificate(caInput);
                        log("ca=" + ((X509Certificate) ca).getSubjectDN());
                    } finally {
                        caInput.close();
                    }

                    // Create a KeyStore containing our trusted CAs
                    String keyStoreType = KeyStore.getDefaultType();
                    KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                    keyStore.load(null, null);
                    keyStore.setCertificateEntry("ca", ca);

                    // Create a TrustManager that trusts the CAs in our KeyStore
                    String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                    tmf.init(keyStore);

                    // Create an SSLContext that uses our TrustManager
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, tmf.getTrustManagers(), null);

                    // Tell the URLConnection to use a SocketFactory from our SSLContext
                    // connection.setSSLSocketFactory(sslContext.getSocketFactory());
                    return sslContext.getSocketFactory();
                } else {
                    log("Unable to verify SSL certificate for debugging env");
                    return null;
                }
            } catch (Exception e) {
                log("Unable to verify SSL certificate for debugging env");
                return null;
            }
        } else {
            log("SSL bypass is turned off");
            return null;
        }
    }

    /**
     * Returns current language code
     *
     * @param context context
     * @return String
     */
    public static String getCurrentLanguageCode(Context context) {
        return isCurrentLanguageNorwegian(context) ? Constants.LANGUAGE_CODE_NO : Constants.LANGUAGE_CODE_EN;
    }

    /**
     * Returns current configuration locale as string
     *
     * @param context Context
     * @return String current configuration locale
     */
    private static String getCurrentLocale(Context context) {
        log("getCurrentLocale()");
        if (context == null) {
            return null;
        }
        log("getCurrentLocale() currentLocale=" + context.getResources().getConfiguration().locale.toString());
        return context.getResources().getConfiguration().locale.toString();
    }

    /**
     * Returns if current language is Norwegian
     *
     * @param context Context
     * @return Boolean
     */
    private static Boolean isCurrentLanguageNorwegian(Context context) {
        log("isCurrentLanguageNorwegian()");
        if (context == null) {
            return true;
        }
        String currentLocale = getCurrentLocale(context);
        if (currentLocale == null) {
            return true;
        }
        log("isCurrentLanguageNorwegian() currentLocale=" + currentLocale);
        log("isCurrentLanguageNorwegian()=" + currentLocale.toLowerCase(Locale.getDefault()).contains("no"));
        return currentLocale.toLowerCase(Locale.getDefault()).contains("no");
    }

    public static String createCheckUrl(String url) {
        try {
            String scheme = "";
            if (url.contains("http://")) {
                scheme = "http://";
            } else if (url.contains("https://")) {
                scheme = "https://";
            } else {
                scheme = "http://";
            }

            url = url.replace("http://", "").replace("https://", "");
            String parts[] = url.split("/");
            url = parts[0];
            String combinedUrl = scheme + url + DefaultParameters.DEFAULT_CHECK_URL_END;
            return combinedUrl;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}