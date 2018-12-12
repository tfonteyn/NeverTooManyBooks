/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.cropper;

import android.os.Bundle;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Tracker;

import java.util.ArrayList;
import java.util.List;

abstract class CropMonitoredActivity extends BaseActivity {

    private final List<LifeCycleListener> mListeners = new ArrayList<>();

    public void addLifeCycleListener(final @NonNull LifeCycleListener listener) {
        if (mListeners.contains(listener))
            return;
        mListeners.add(listener);
    }

    public void removeLifeCycleListener(final @NonNull LifeCycleListener listener) {
        mListeners.remove(listener);
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityCreated(this);
        }
        Tracker.exitOnCreate(this);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityDestroyed(this);
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

    @Override
    @CallSuper
    protected void onStart() {
        super.onStart();
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityStarted(this);
        }
    }

    @Override
    @CallSuper
    protected void onStop() {
        super.onStop();
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityStopped(this);
        }
    }

    @SuppressWarnings({"EmptyMethod", "unused"})
    interface LifeCycleListener {
        void onActivityCreated(final @NonNull CropMonitoredActivity activity);
        void onActivityDestroyed(final @NonNull CropMonitoredActivity activity);
        void onActivityPaused(final @NonNull CropMonitoredActivity activity);
        void onActivityResumed(final @NonNull CropMonitoredActivity activity);
        void onActivityStarted(final @NonNull CropMonitoredActivity activity);
        void onActivityStopped(final @NonNull CropMonitoredActivity activity);
    }

    public static class LifeCycleAdapter implements LifeCycleListener {
        public void onActivityCreated(final @NonNull CropMonitoredActivity activity) {
        }
        public void onActivityDestroyed(final @NonNull CropMonitoredActivity activity) {
        }
        public void onActivityPaused(final @NonNull CropMonitoredActivity activity) {
        }
        public void onActivityResumed(final @NonNull CropMonitoredActivity activity) {
        }
        public void onActivityStarted(final @NonNull CropMonitoredActivity activity) {
        }
        public void onActivityStopped(final @NonNull CropMonitoredActivity activity) {
        }
    }
}