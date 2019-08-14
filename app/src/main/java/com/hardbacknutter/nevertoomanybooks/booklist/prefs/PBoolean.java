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

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.App;

/**
 * A Boolean is stored as a Boolean.
 * <p>
 * Used for {@link androidx.preference.SwitchPreference}
 */
public class PBoolean
        extends PPrefBase<Boolean> {

    /**
     * Constructor. Uses the global setting as the default value, or false if none.
     *
     * @param key          key of preference
     * @param uuid         of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     */
    public PBoolean(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent) {
        super(key, uuid, isPersistent, App.getPrefBoolean(key, false));
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
    public PBoolean(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent,
                    @NonNull final Boolean defaultValue) {
        super(key, uuid, isPersistent, App.getPrefBoolean(key, defaultValue));
    }

    @Override
    public void set(@Nullable final Boolean value) {
        if (!mIsPersistent) {
            mNonPersistedValue = value;
        } else if (value == null) {
            remove();
        } else {
            getPrefs().edit().putBoolean(getKey(), value).apply();
        }
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final Boolean value) {
        if (value == null) {
            ed.remove(getKey());
        } else {
            ed.putBoolean(getKey(), value);
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
    public Boolean get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            return getPrefs().getBoolean(getKey(), mDefaultValue);
        }
    }

    /**
     * syntax sugar...
     */
    public boolean isTrue() {
        return get();
    }

    /**
     * syntax sugar...
     */
    public boolean isFalse() {
        return !get();
    }
}
