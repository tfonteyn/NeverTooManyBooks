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

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.App;

/**
 * An Integer stored as an Integer.
 * <p>
 * Used for {@link androidx.preference.SeekBarPreference}
 */
public class PInteger
        extends PPrefBase<Integer>
        implements PInt {

    /**
     * Constructor. Uses the global setting as the default value,
     * or {@code 0} if there is no global default.
     *
     * @param key          key of preference
     * @param uuid         of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     */
    public PInteger(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent) {
        super(key, uuid, isPersistent, getPrefInteger(key, 0));
    }

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if there is no global default.
     *
     * @param key          key of preference
     * @param uuid         of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param defValue     in memory default
     */
    public PInteger(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent,
                    @NonNull final Integer defValue) {
        super(key, uuid, isPersistent, getPrefInteger(key, defValue));
    }

    /**
     * Get a global preference int.
     *
     * @param key      The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist.
     *
     * @return the preference value
     */
    private static int getPrefInteger(@NonNull final String key,
                                      final int defValue) {
        return PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                .getInt(key, defValue);
    }

    @Override
    public void set(@Nullable final Integer value) {
        if (!mIsPersistent) {
            mNonPersistedValue = value;
        } else if (value == null) {
            remove();
        } else {
            getPrefs().edit().putInt(getKey(), value).apply();
        }
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final Integer value) {
        if (value == null) {
            ed.remove(getKey());
        } else {
            ed.putInt(getKey(), value);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PBoolean{" + super.toString()
               + ", value=`" + get() + '`'
               + '}';
    }

    @NonNull
    @Override
    public Integer get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            // value is a primitive, never null
            if (getPrefs().contains(getKey())) {
                return getPrefs().getInt(getKey(), mDefaultValue);
            } else {
                // not present, fallback to global.
                return getGlobal().getInt(getKey(), mDefaultValue);
            }
        }
    }
}
