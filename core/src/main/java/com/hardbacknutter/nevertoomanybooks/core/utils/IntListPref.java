/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.core.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

public final class IntListPref {
    private IntListPref() {
    }

    /**
     * {@link ListPreference} stores the selected value as a String.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @param context  Current context
     * @param key      The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist,
     *                 or if the stored value is somehow invalid
     *
     * @return int (stored as String) global preference
     */
    public static int getInt(@NonNull final Context context,
                             @NonNull final String key,
                             final int defValue) {
        final String value = PreferenceManager.getDefaultSharedPreferences(context)
                                              .getString(key, null);
        if (value == null || value.isEmpty()) {
            return defValue;
        }

        // we should never have an invalid setting in the prefs... flw
        try {
            return Integer.parseInt(value);
        } catch (@NonNull final NumberFormatException ignore) {
            return defValue;
        }
    }
}
