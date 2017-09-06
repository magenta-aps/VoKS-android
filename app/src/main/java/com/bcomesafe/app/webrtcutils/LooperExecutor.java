/*
 * libjingle
 * Copyright 2015 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * BComeSafe, http://bcomesafe.com
 * Copyright 2015 Magenta ApS, http://magenta.dk
 * Licensed under MPL 2.0, https://www.mozilla.org/MPL/2.0/
 * Developed in co-op with Baltic Amadeus, http://baltic-amadeus.lt
 */

package com.bcomesafe.app.webrtcutils;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.concurrent.Executor;

import com.bcomesafe.app.utils.RemoteLogUtils;

/**
 * Looper based executor class.
 */
public class LooperExecutor extends Thread implements Executor {

    // Debugging
    private static final boolean D = false;
    private static final String TAG = LooperExecutor.class.getSimpleName();

    // Object used to signal that looper thread has started and Handler instance
    // associated with looper thread has been allocated.
    private final Object looperStartedEvent = new Object();
    private Handler handler = null;
    private boolean running = false;
    private long threadId;

    @Override
    public void run() {
        Looper.prepare();
        synchronized (looperStartedEvent) {
            log("Looper thread started.");
            handler = new Handler();
            threadId = Thread.currentThread().getId();
            looperStartedEvent.notify();
        }
        Looper.loop();
    }

    public synchronized void requestStart() {
        if (running) {
            return;
        }
        running = true;
        handler = null;
        start();
        // Wait for Handler allocation.
        synchronized (looperStartedEvent) {
            while (handler == null) {
                try {
                    looperStartedEvent.wait();
                } catch (InterruptedException e) {
                    log("Can not start looper thread");
                    running = false;
                }
            }
        }
    }

    @TargetApi(18)
    public synchronized void requestStop() {
        if (!running) {
            return;
        }
        running = false;
        handler.post(new Runnable() {
            @Override
            public void run() {
                // TODO this should be tested on API <18
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    //noinspection ConstantConditions
                    Looper.myLooper().quitSafely();
                } else {
                    //noinspection ConstantConditions
                    Looper.myLooper().quit();
                }
                log("Looper thread finished.");
            }
        });
    }

    // Checks if current thread is a looper thread.
    public boolean checkOnLooperThread() {
        return (Thread.currentThread().getId() == threadId);
    }

    @Override
    public synchronized void execute(@NonNull final Runnable runnable) {
        if (!running) {
            log("Running looper executor without calling requestStart()");
            return;
        }
        if (Thread.currentThread().getId() == threadId) {
            runnable.run();
        } else {
            handler.post(runnable);
        }
    }

    private void log(String msg) {
        if (D) {
            Log.e(TAG, msg);
            RemoteLogUtils.getInstance().put(TAG, msg, System.currentTimeMillis());
        }
    }
}
