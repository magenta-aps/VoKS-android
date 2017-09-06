/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app;

import android.test.ActivityUnitTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.Button;

import com.bcomesafe.app.activities.MainActivity;

@SuppressWarnings("unused")
public class MainActivityTest extends ActivityUnitTestCase<MainActivity> {

    private MainActivity activity;

    public MainActivityTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        activity = getActivity();
    }

    @SmallTest
    public void testButtonViewNotNull(){
        Button button = (Button) activity.findViewById(R.id.b_chat_call);
        assertNotNull(button);
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}