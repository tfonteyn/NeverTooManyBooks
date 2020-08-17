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
package com.hardbacknutter.nevertoomanybooks.booklist.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * An Integer stored as a String
 * <p>
 * Used for {@link androidx.preference.ListPreference}
 * The Preference uses 'select 1 of many' type and insists on a String.
 *
 * @see PInt
 */
public class PIntString
        extends PPrefBase<Integer>
        implements PInt {

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if there is no global default.
     *
     * @param sp           Style preferences reference.
     * @param key          key of preference
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param defValue     in memory default
     */
    public PIntString(@NonNull final SharedPreferences sp,
                      @NonNull final String key,
                      final boolean isPersistent,
                      @NonNull final Integer defValue) {
        super(sp, key, isPersistent, defValue);
    }

    @NonNull
    public Integer getGlobalValue(@NonNull final Context context) {
        final String value = PreferenceManager.getDefaultSharedPreferences(context)
                                              .getString(getKey(), null);
        if (value != null && !value.isEmpty()) {
            return Integer.parseInt(value);
        }
        return mDefaultValue;
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @NonNull final Integer value) {
        ed.putString(getKey(), String.valueOf(value));
    }

    @NonNull
    @Override
    public Integer getValue(@NonNull final Context context) {
        if (mIsPersistent) {
            // reminder: {@link androidx.preference.ListPreference} is stored as a String
            final String value = mStylePrefs.getString(getKey(), null);
            if (value != null && !value.isEmpty()) {
                return Integer.parseInt(value);
            }
            return getGlobalValue(context);
        } else {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        }
    }
}
