/*
 * @Copyright 2020 HardBackNutter
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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/** Singleton. */
public final class NightMode {

    /**
     * We're not using the actual {@link AppCompatDelegate} mode constants
     * due to 'day-night' being different depending on the OS version.
     */
    private static final int MODE_NOT_SET = -1;
    private static final int MODE_DAY_NIGHT = 0;
    private static final int MODE_LIGHT = 1;
    private static final int MODE_DARK = 2;

    /** Singleton. */
    private static NightMode sInstance;

    /** Cache the User-specified theme currently in use. '-1' to force an update at App startup. */
    @NightModeId
    private int mCurrentMode = MODE_NOT_SET;

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
     *        // apply the user-preferred Theme before super.onCreate is called.
     *        NightMode.getInstance().apply(this);
     *
     *        super.onCreate(savedInstanceState);
     *    }
     * }
     * </pre>
     *
     * @param context Current context
     *
     * @return the applied mode
     */
    @NightModeId
    public int apply(@NonNull final Context context) {
        // Always read from prefs.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        mCurrentMode = Prefs.getIntListPref(prefs, Prefs.pk_ui_theme, MODE_DAY_NIGHT);

        final int dnMode;
        switch (mCurrentMode) {
            case MODE_NOT_SET:
            case MODE_DAY_NIGHT:
                if (Build.VERSION.SDK_INT >= 29) {
                    dnMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                } else {
                    dnMode = AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
                }
                break;

            case MODE_DARK:
                dnMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;

            case MODE_LIGHT:
            default:
                dnMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(dnMode);
        return mCurrentMode;
    }

    public boolean isChanged(@NonNull final SharedPreferences preferences,
                             @NightModeId final int mode) {
        mCurrentMode = Prefs.getIntListPref(preferences, Prefs.pk_ui_theme, MODE_DAY_NIGHT);
        return mode != mCurrentMode;
    }

    @IntDef({MODE_NOT_SET, MODE_DAY_NIGHT, MODE_DARK, MODE_LIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface NightModeId {

    }
}
