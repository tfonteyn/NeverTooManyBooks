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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public final class NightModeUtils {

    /**
     * We're not using the actual {@link AppCompatDelegate} mode constants
     * due to 'day-night' being different depending on the OS version.
     */
    private static final int NIGHT_MODE_IS_NOT_SET = -1;
    private static final int NIGHT_MODE_IS_DAY_NIGHT = 0;
    private static final int NIGHT_MODE_IS_LIGHT = 1;
    private static final int NIGHT_MODE_IS_DARK = 2;

    /** Cache the User-specified theme currently in use. '-1' to force an update at App startup. */
    @NightModeId
    private static int sCurrentMode = NIGHT_MODE_IS_NOT_SET;

    private NightModeUtils() {
    }

    /**
     * Apply the user's preferred NightMode.
     * <p>
     * The one and only place where this should get called is in {@code Activity.onCreate}
     * <pre>
     * {@code
     *    public void onCreate(@Nullable final Bundle savedInstanceState) {
     *        // apply the user-preferred Theme before super.onCreate is called.
     *        applyNightMode(this);
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
    public static int applyNightMode(@NonNull final Context context) {
        // Always read from prefs.
        sCurrentMode = Prefs.getListPreference(context, Prefs.pk_ui_theme,
                                               NIGHT_MODE_IS_DAY_NIGHT);
        final int dnMode;
        switch (sCurrentMode) {
            case NIGHT_MODE_IS_NOT_SET:
            case NIGHT_MODE_IS_DAY_NIGHT:
                if (Build.VERSION.SDK_INT >= 29) {
                    dnMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                } else {
                    dnMode = AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
                }
                break;

            case NIGHT_MODE_IS_DARK:
                dnMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;

            case NIGHT_MODE_IS_LIGHT:
            default:
                dnMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(dnMode);
        return sCurrentMode;
    }

    public static boolean isChanged(@NonNull final Context context,
                                    @NightModeId final int mode) {
        sCurrentMode = Prefs.getListPreference(context, Prefs.pk_ui_theme,
                                               NIGHT_MODE_IS_DAY_NIGHT);
        return mode != sCurrentMode;
    }

    @IntDef({NIGHT_MODE_IS_NOT_SET,
             NIGHT_MODE_IS_DAY_NIGHT,
             NIGHT_MODE_IS_DARK,
             NIGHT_MODE_IS_LIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface NightModeId {

    }
}
