/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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

import com.hardbacknutter.nevertoomanybooks.App;

/**
 * An Integer is stored as a String
 * <p>
 * Used for {@link androidx.preference.ListPreference}
 * The Preference uses 'select 1 of many' type and insists on a String.
 */
public class PInteger
        extends PPrefBase<Integer>
        implements PInt {

    /**
     * Constructor. Uses the global setting as the default value, or 0 if none.
     *
     * @param key          key of preference
     * @param uuid         of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     */
    public PInteger(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent) {
        super(key, uuid, isPersistent, App.getListPreference(key, 0));
    }

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if no global default.
     *
     * @param key          key of preference
     * @param uuid         of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param defaultValue in memory default
     */
    public PInteger(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent,
                    @NonNull final Integer defaultValue) {
        super(key, uuid, isPersistent, App.getListPreference(key, defaultValue));
    }

    @NonNull
    @Override
    public Integer get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            // Use a workaround for the real default value not being a String.
            String value = getPrefs().getString(getKey(), null);
            if (value == null || value.isEmpty()) {
                return mDefaultValue;
            }
            return Integer.parseInt(value);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PInteger{" + super.toString()
               + ", value=`" + get() + '`'
               + '}';
    }
}
