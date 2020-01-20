/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */

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
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;

abstract class CropMonitoredActivity
        extends BaseActivity {

    private final Collection<LifeCycleListener> mListeners = new ArrayList<>();

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
