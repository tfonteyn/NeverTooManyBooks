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
 * Used for {@link androidx.preference.EditTextPreference}.
 */
public class PString
        extends PPrefBase<String> {

    /**
     * Constructor. Uses the global setting as the default value,
     * or {@code ""} if there is no global default.
     *
     * @param sp           Style preferences reference.
     * @param key          key of preference
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     */
    public PString(@NonNull final SharedPreferences sp,
                   @NonNull final String key,
                   final boolean isPersistent) {
        super(sp, key, isPersistent, "");
    }

    @NonNull
    public String getGlobalValue(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(getKey(), mDefaultValue);
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @NonNull final String value) {
        ed.putString(getKey(), value);
    }

    @NonNull
    @Override
    public String getValue(@NonNull final Context context) {
        if (mIsPersistent) {
            String value = mStylePrefs.getString(getKey(), null);
            if (value != null && !value.isEmpty()) {
                return value;
            }
            return getGlobalValue(context);
        } else {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        }
    }
}
