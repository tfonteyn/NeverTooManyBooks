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
 * Used for {@link androidx.preference.SwitchPreference}.
 */
public class PBoolean
        extends PPrefBase<Boolean> {

    /**
     * Constructor. Uses the global setting as the default value,
     * or {@code false} if there is no global default.
     *
     * @param sp           Style preferences reference.
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param key          key of preference
     */
    public PBoolean(@NonNull final SharedPreferences sp,
                    final boolean isPersistent,
                    @NonNull final String key) {
        super(sp, isPersistent, key, false);
    }

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if there is no global default.
     *
     * @param sp           Style preferences reference.
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param key          key of preference
     * @param defValue     in memory default
     */
    public PBoolean(final SharedPreferences sp,
                    final boolean isPersistent,
                    @NonNull final String key,
                    @NonNull final Boolean defValue) {
        super(sp, isPersistent, key, defValue);
    }

    @NonNull
    public Boolean getGlobalValue(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(getKey(), mDefaultValue);
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @NonNull final Boolean value) {
        ed.putBoolean(getKey(), value);
    }

    @NonNull
    @Override
    public Boolean getValue(@NonNull final Context context) {
        if (mIsPersistent) {
            // reminder: it's a primitive so we must test on contains first
            if (mStylePrefs.contains(getKey())) {
                return mStylePrefs.getBoolean(getKey(), mDefaultValue);
            }
            return getGlobalValue(context);
        } else {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        }
    }

    public boolean isTrue(@NonNull final Context context) {
        return getValue(context);
    }
}
