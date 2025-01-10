/*
 * @Copyright 2018-2025 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A simple {@link Application.ActivityLifecycleCallbacks} to allow easy
 * restarting all current Activities when a relevant preference/setting is changed.
 * Examples:
 * - user changes Dark/Light
 * - import of an archive loads new preferences
 * <p>
 * During testing we've seen:
 * <pre>
 *     android.os.StrictMode$InstanceCountViolation:
 *     class com.hardbacknutter.nevertoomanybooks.FragmentHostActivity; instances=2; limit=1
 *     at android.os.StrictMode.setClassInstanceLimit(StrictMode.java:1)
 * </pre>
 * <p>
 * This seems to be a false positive:
 * <a href="https://stackoverflow.com/questions/5956132/android-strictmode-instancecountviolation">
 * stackoverflow</a>
 */
public final class ActivityRestarter {

    @SuppressWarnings("StaticVariableMayNotBeInitialized")
    private static ActivityCallbacks INSTANCE;

    private ActivityRestarter() {
    }

    /**
     * Must be called from {@link Application#onCreate()}.
     *
     * @param app the app
     */
    public static void init(@NonNull final Application app) {
        synchronized (ActivityRestarter.class) {
            if (INSTANCE == null) {
                INSTANCE = new ActivityCallbacks();
                app.registerActivityLifecycleCallbacks(INSTANCE);
            }
        }
    }

    /**
     * To be called when the user changes the preference.
     */
    public static void recreate() {
        //noinspection StaticVariableUsedBeforeInitialization
        INSTANCE.recreate();
    }

    private static final class ActivityCallbacks
            implements Application.ActivityLifecycleCallbacks {

        private final Set<WeakReference<Activity>> activities = new HashSet<>();

        void recreate() {
            activities.stream()
                      .map(Reference::get)
                      .filter(Objects::nonNull)
                      .forEach(Activity::recreate);
        }

        @Override
        public void onActivityCreated(@NonNull final Activity activity,
                                      @Nullable final Bundle savedInstanceState) {

            // paranoia... remove dead references
            activities.stream()
                      .filter(ref -> ref.get() == null)
                      .forEach(activities::remove);

            activities.add(new WeakReference<>(activity));
        }

        @Override
        public void onActivityDestroyed(@NonNull final Activity activity) {
            activities.stream()
                      .filter(ref -> activity.equals(ref.get()))
                      .findAny()
                      .ifPresent(activities::remove);
        }

        @Override
        public void onActivityStarted(@NonNull final Activity activity) {

        }

        @Override
        public void onActivityResumed(@NonNull final Activity activity) {

        }

        @Override
        public void onActivityPaused(@NonNull final Activity activity) {

        }

        @Override
        public void onActivityStopped(@NonNull final Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(@NonNull final Activity activity,
                                                @NonNull final Bundle outState) {

        }
    }
}
