/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/** Singleton. */
public final class NightMode {

    /** Singleton. */
    private static NightMode sInstance;

    /**
     * Cache the User-specified theme currently in use.
     * {@link Mode#NotSet} to force an update at App startup.
     */
    @NonNull
    private Mode mCurrentMode = Mode.NotSet;

    /**
     * Constructor. Use {@link #getInstance()}.
     */
    private NightMode() {
    }

    /**
     * Get/create the singleton instance.
     *
     * @return instance
     */
    @NonNull
    public static NightMode getInstance() {
        synchronized (NightMode.class) {
            if (sInstance == null) {
                sInstance = new NightMode();
            }
            return sInstance;
        }
    }

    /**
     * Apply the user's preferred NightMode.
     * <p>
     * The one and only place where this should get called is in {@code Activity.onCreate}
     * <pre>
     * {@code
     *    public void onCreate(@Nullable final Bundle savedInstanceState) {
     *        NightMode.getInstance().apply(this);
     *        super.onCreate(savedInstanceState);
     *    }
     * }
     * </pre>
     *
     * @param context Current context
     *
     * @return the applied mode
     */
    @NonNull
    public Mode apply(@NonNull final Context context) {
        // Always read from prefs.
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        mCurrentMode = Mode.getMode(global);
        mCurrentMode.apply();
        return mCurrentMode;
    }

    public boolean isChanged(@NonNull final SharedPreferences global,
                             @NonNull final Mode mode) {
        mCurrentMode = Mode.getMode(global);
        return mode != mCurrentMode;
    }

    /**
     * We're not using the actual {@link AppCompatDelegate} mode constants
     * due to 'day-night' being different depending on the OS version.
     */
    public enum Mode {
        NotSet(-1),
        DayNight(0),
        Light(1),
        Dark(2);

        private final int value;

        Mode(final int value) {
            this.value = value;
        }

        /**
         * Get the mode to use.
         *
         * @param global Global preferences
         *
         * @return mode
         */
        @NonNull
        public static Mode getMode(@NonNull final SharedPreferences global) {
            final int value = Prefs.getIntListPref(global, Prefs.pk_ui_theme, DayNight.value);
            switch (value) {
                case 2:
                    return Dark;
                case 1:
                    return Light;
                case 0:
                    return DayNight;
                default:
                    return NotSet;
            }
        }

        public void apply() {
            final int dnMode;
            switch (this) {
                case NotSet:
                case DayNight:
                    if (Build.VERSION.SDK_INT >= 29) {
                        dnMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    } else {
                        dnMode = AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
                    }
                    break;

                case Dark:
                    dnMode = AppCompatDelegate.MODE_NIGHT_YES;
                    break;

                case Light:
                default:
                    dnMode = AppCompatDelegate.MODE_NIGHT_NO;
                    break;
            }
            AppCompatDelegate.setDefaultNightMode(dnMode);
        }
    }
}
