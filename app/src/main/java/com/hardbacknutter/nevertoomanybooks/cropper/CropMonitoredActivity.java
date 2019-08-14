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
package com.hardbacknutter.nevertoomanybooks.cropper;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;

abstract class CropMonitoredActivity
        extends BaseActivity {

    private final List<LifeCycleListener> mListeners = new ArrayList<>();

    public void addLifeCycleListener(@NonNull final LifeCycleListener listener) {
        if (mListeners.contains(listener)) {
            return;
        }
        mListeners.add(listener);
    }

    public void removeLifeCycleListener(@NonNull final LifeCycleListener listener) {
        mListeners.remove(listener);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityCreated(this);
        }
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

    @Override
    @CallSuper
    protected void onDestroy() {
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityDestroyed(this);
        }
        super.onDestroy();
    }

    @SuppressWarnings({"EmptyMethod", "unused"})
    interface LifeCycleListener {

        void onActivityCreated(@NonNull CropMonitoredActivity activity);

        void onActivityDestroyed(@NonNull CropMonitoredActivity activity);

        void onActivityPaused(@NonNull CropMonitoredActivity activity);

        void onActivityResumed(@NonNull CropMonitoredActivity activity);

        void onActivityStarted(@NonNull CropMonitoredActivity activity);

        void onActivityStopped(@NonNull CropMonitoredActivity activity);
    }

    public static class LifeCycleAdapter
            implements LifeCycleListener {

        public void onActivityCreated(@NonNull final CropMonitoredActivity activity) {
        }

        public void onActivityDestroyed(@NonNull final CropMonitoredActivity activity) {
        }

        public void onActivityPaused(@NonNull final CropMonitoredActivity activity) {
        }

        public void onActivityResumed(@NonNull final CropMonitoredActivity activity) {
        }

        public void onActivityStarted(@NonNull final CropMonitoredActivity activity) {
        }

        public void onActivityStopped(@NonNull final CropMonitoredActivity activity) {
        }
    }
}
