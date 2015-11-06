/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app;

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.bcomesafe.app.activities.AlarmActivity;
import com.bcomesafe.app.widgets.SquareButton;

import com.bcomesafe.app.R;

public class AlarmActivityTest extends ActivityInstrumentationTestCase2<AlarmActivity> {

    private static final String TAG = "AlarmActivity";
    private static final int ALARM_ACTIVITY_TIMEOUT = 30; // Seconds

    public AlarmActivityTest() {
        super(AlarmActivity.class);
    }

    private AlarmActivity mActivity;
    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Thread.sleep(10000L);
        mActivity = getActivity();
        mContext = getInstrumentation().getTargetContext().getApplicationContext();
        // Clear everything in the SharedPreferences
        AppUser.get().clearUserData();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mActivity = null;
        mContext = null;
    }

//    @LargeTest
//    public void test() {
//        testA_Preconditions();
//        testB_AlarmActivityViews();
//        testC_AlarmActivity();
//    }

    @SmallTest
    public void testA_Preconditions() {
        Log.d(TAG, "-----------------------------------------------------------------------------");
        Log.d(TAG, "testPreconditions start");
        assertNotNull("AlarmActivity is null", mActivity);
        assertNotNull("Context is null", mContext);
        assertFalse("Device is already registered to shelter", AppUser.get().getIsRegisteredOnShelter());

        CountDownLatch signal = mActivity.getSignal();
        try {
            if (!signal.await(ALARM_ACTIVITY_TIMEOUT, TimeUnit.SECONDS)) {
                Log.d(TAG, "testPreconditions check internet connection for application");
                Log.d(TAG, "testPreconditions end");
                Log.d(TAG, "-----------------------------------------------------------------------------");
                fail("Timed out waiting for the activity to complete actions, check internet connection for application");
            }
        } catch (InterruptedException ie) {
            Log.d(TAG, "testPreconditions check internet connection for application");
            Log.d(TAG, "testPreconditions end");
            Log.d(TAG, "-----------------------------------------------------------------------------");
            fail("Timed out waiting for the activity to complete actions, check internet connection for application");
        }

        Log.d(TAG, "testPreconditions end");
        Log.d(TAG, "-----------------------------------------------------------------------------");
    }

    @SmallTest
    public void testB_AlarmActivityViews() {
        Log.d(TAG, "-----------------------------------------------------------------------------");
        Log.d(TAG, "testAlarmActivityViews start");
        LinearLayout llAlarm = (LinearLayout) mActivity.findViewById(R.id.ll_start_alarm);
        assertNotNull("llAlarm is null", llAlarm);

        SquareButton sbStartAlarm = (SquareButton) mActivity.findViewById(R.id.sb_start_alarm);
        assertNotNull("sbStartAlarm is null", sbStartAlarm);

        RelativeLayout rlCancelAlarm = (RelativeLayout) mActivity.findViewById(R.id.rl_cancel_alarm);
        assertNotNull("rlCancelAlarm is null", rlCancelAlarm);

        TextView tvCancelAlarm = (TextView) mActivity.findViewById(R.id.tv_cancel_alarm);
        assertNotNull("tvCancelAlarm is null", tvCancelAlarm);

        RelativeLayout rlMessage = (RelativeLayout) mActivity.findViewById(R.id.rl_message);
        assertNotNull("rlMessage is null", rlMessage);

        TextView tvMessage = (TextView) mActivity.findViewById(R.id.tv_message);
        assertNotNull("tvMessage is null", tvMessage);

        CountDownLatch signal = mActivity.getSignal();
        try {
            if (!signal.await(ALARM_ACTIVITY_TIMEOUT, TimeUnit.SECONDS)) {
                Log.d(TAG, "testPreconditions check internet connection for application");
                Log.d(TAG, "testPreconditions end");
                Log.d(TAG, "-----------------------------------------------------------------------------");
                fail("Timed out waiting for the activity to complete actions, check internet connection for application");
            }
        } catch (InterruptedException ie) {
            Log.d(TAG, "testPreconditions check internet connection for application");
            Log.d(TAG, "testPreconditions end");
            Log.d(TAG, "-----------------------------------------------------------------------------");
            fail("Timed out waiting for the activity to complete actions, check internet connection for application");
        }

        Log.d(TAG, "testAlarmActivityViews end");
        Log.d(TAG, "-----------------------------------------------------------------------------");
    }

    @LargeTest
    public void testC_AlarmActivity() {
        Log.d(TAG, "-----------------------------------------------------------------------------");
        Log.d(TAG, "testAlarmActivity start");

        // Wait for worker async task and registration to finish
        Log.d(TAG, "Waiting for activity to finish actions");
        CountDownLatch signal = mActivity.getSignal();
        try {
            if (!signal.await(ALARM_ACTIVITY_TIMEOUT, TimeUnit.SECONDS)) {
                Log.d(TAG, "testAlarmActivity check internet connection for application");
                Log.d(TAG, "testAlarmActivity end");
                Log.d(TAG, "-----------------------------------------------------------------------------");
                fail("Timed out waiting for the activity to complete actions, check internet connection for application");
            }
        } catch (InterruptedException ie) {
            Log.d(TAG, "testAlarmActivity check internet connection for application");
            Log.d(TAG, "testAlarmActivity end");
            Log.d(TAG, "-----------------------------------------------------------------------------");
            fail("Timed out waiting for the activity to complete actions, check internet connection for application");
        }

        // After that mSignalMessage has to be null
        assertNull("Signal message not null:" + mActivity.getSignalMessage(), mActivity.getSignalMessage());
        // and there has to be these values in shared preferences under name Constants.SHARED_PREFERENCES_APP_USER
        // getIsRegisteredOnShelter() == true
        assertTrue("Device is not registered on shelter", AppUser.get().getIsRegisteredOnShelter());

        // Check GCM registration id
        // try {
        //    MethodAccessor<String> getRegistrationIdPrivate = new MethodAccessor<String>("getRegistrationId", mActivity, new Class[]{Context.class});
        //    String registrationId = getRegistrationIdPrivate.invoke(mContext);
        //    Log.d(TAG, "registrationId=" + registrationId);
        //    assertNotNull("Registration id is not null", registrationId);
        //} catch (Exception e) {
        //    Log.d(TAG, "Method not found getRegistrationId");
        //    fail("Method not found getRegistrationId");
        //}

        Log.d(TAG, "testAlarmActivity end");
        Log.d(TAG, "-----------------------------------------------------------------------------");
        /*
        MethodAccessor<String> stringPrivateValue = new MethodAccessor<String>("getTextPrivate", mActivity, new Class[0]);
        assertEquals("THIS IS A TEST PRIVATE", stringPrivateValue.invoke());
         */
    }
}
