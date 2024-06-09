/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.utils.theme;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.DynamicColors;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;

/**
 * Adapted from the excellent article
 * <a href="https://medium.com/revolut/customising-android-app-ui-with-themes-a251e42b1451">
 * Customising Android app UI with themes</a> which all Android devs should read.
 * <p>
 * Setup the style:
 * <pre>
 *     {@code
 *          <style name="Theme.App" parent="Theme.Material3.DayNight.NoActionBar">
 *              ...
 *              <item name="dynamicColorThemeOverlay">
 *                  @style/ThemeOverlay.Material3.DynamicColors.[Light/Dark]
 *              </item>
 *          </style>
 *     }
 * </pre>
 * <p>
 * And call {@link #init(Application)} from {@link Application#onCreate()}.
 * Provide preference settings as used in this class.
 * <p>
 * Note we do not check for Android 12 as the minimal version supported.
 * The intention is to add custom themes as well.
 */
public final class ThemeColorController {

    /**
     * The {@link android.content.res.Resources.Theme} preference.
     * <p>
     * {@code int}
     */
    public static final String PK_UI_THEME_COLOR = "ui.theme.color";

    /** {@link #PK_UI_THEME_COLOR} value - the original NeverTooManyBooks blue-grey theme. */
    private static final int PK_UI_THEME_COLOR_BLUE_GREY = 0;
    /** {@link #PK_UI_THEME_COLOR} value - the Dynamic-Colors theme available on Android 12+ */
    private static final int PK_UI_THEME_COLOR_DYNAMIC = 1;

    private static final ActivityCallbacks activityCallbacks = new ActivityCallbacks();
    @SuppressWarnings("RedundantFieldInitialization")
    private static boolean initialized = false;

    private ThemeColorController() {
    }

    /**
     * Init support for custom themes and dynamic colors.
     * <p>
     * Must be called from {@link Application#onCreate()}.
     *
     * @param app the app
     */
    public static void init(@NonNull final Application app) {
        if (!initialized) {
            initialized = true;
            app.registerActivityLifecycleCallbacks(activityCallbacks);
        }
    }

    /**
     * Get the indexed color preference.
     *
     * @param context Current context
     *
     * @return one of the {@link #PK_UI_THEME_COLOR_BLUE_GREY} etc values.
     */
    public static int getSetting(@NonNull final Context context) {
        return IntListPref.getInt(context, PK_UI_THEME_COLOR, 0);
    }

    /**
     * To be called when the user changes the preference.
     */
    public static void recreate() {
        activityCallbacks.recreate();
    }

    private static class ActivityCallbacks
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

            switch (getSetting(activity)) {
                case PK_UI_THEME_COLOR_DYNAMIC:
                    DynamicColors.applyToActivityIfAvailable(activity);
                    break;

//                case Prefs.PK_UI_COLOR_THEME_ANOTHER_ONE:
//                    activity.getTheme().applyStyle(R.style.ThemeOverlay_Another_Theme, true);
//                    break;

                case PK_UI_THEME_COLOR_BLUE_GREY:
                default:
                    //do nothing, it will use the activity default theme
                    break;

            }
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
