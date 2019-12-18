/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.booklist.prefs;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.App;

/**
 * An Integer stored as a String
 * <p>
 * Used for {@link androidx.preference.ListPreference}
 * The Preference uses 'select 1 of many' type and insists on a String.
 */
public class PIntString
        extends PPrefBase<Integer>
        implements PInt {

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if there is no global default.
     *
     * @param key          key of preference
     * @param uuid         UUID of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param defValue     in memory default
     */
    public PIntString(@NonNull final String key,
                      @NonNull final String uuid,
                      final boolean isPersistent,
                      @NonNull final Integer defValue) {
        super(key, uuid, isPersistent, getListPreference(key, defValue));
    }

    /**
     * {@link ListPreference} stores the selected value as a String.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @param key      The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist.
     *
     * @return int (stored as String) global preference
     */
    public static int getListPreference(@NonNull final String key,
                                        final int defValue) {
        String value = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                        .getString(key, null);
        if (value == null || value.isEmpty()) {
            return defValue;
        }
        return Integer.parseInt(value);
    }

    @NonNull
    @Override
    public Integer get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            // reminder: Integer is stored as a String
            String value = getPrefs().getString(getKey(), null);
            if (value == null) {
                // not present, fallback to global/default
                value = getGlobal().getString(getKey(), null);
                if (value == null || value.isEmpty()) {
                    return mDefaultValue;
                }
            } else if (value.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(value);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PIntString{" + super.toString()
               + ", value=`" + get() + '`'
               + '}';
    }
}
