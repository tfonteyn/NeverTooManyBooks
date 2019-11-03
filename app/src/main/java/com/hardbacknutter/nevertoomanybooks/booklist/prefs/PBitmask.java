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
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Used for {@link androidx.preference.MultiSelectListPreference}.
 * <p>
 * We basically want a bitmask/int, but the Preference insists on a {@code Set<String>}
 */
public class PBitmask
        extends PPrefBase<Integer>
        implements PInt {

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if no global default.
     *
     * @param key          key of preference
     * @param uuid         of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param defaultValue in memory default
     */
    public PBitmask(@NonNull final String key,
                    @NonNull final String uuid,
                    final boolean isPersistent,
                    final int defaultValue) {
        super(key, uuid, isPersistent, getMultiSelectListPreference(key, defaultValue));
    }

    /**
     * {@link MultiSelectListPreference} store the selected value as a StringSet.
     * But they are really Integer values. Hence this transmogrification....
     *
     * @param key      The name of the preference to retrieve.
     * @param defValue Value to return if this preference does not exist.
     *
     * @return int (stored as StringSet) global preference
     */
    private static Integer getMultiSelectListPreference(@NonNull final String key,
                                                        final int defValue) {
        Set<String> value = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                             .getStringSet(key, null);
        if (value == null || value.isEmpty()) {
            return defValue;
        }
        return Prefs.toInteger(value);
    }

    /**
     * converts the Integer bitmask and stores it as a {@code Set<String>}.
     */
    @Override
    public void set(@Nullable final Integer value) {
        if (!mIsPersistent) {
            mNonPersistedValue = value;
        } else if (value == null) {
            remove();
        } else {
            getPrefs().edit().putStringSet(getKey(), Prefs.toStringSet(value)).apply();
        }
    }

    /**
     * converts the Integer bitmask and stores it as a {@code Set<String>}.
     */
    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @Nullable final Integer value) {
        if (value == null) {
            ed.remove(getKey());
        } else {
            ed.putStringSet(getKey(), Prefs.toStringSet(value));
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PBitmask{" + super.toString()
               + ",value=`" + Prefs.toStringSet(get()) + '`'
               + '}';
    }

    /**
     * Reads a {@code Set<String>} from storage, and converts it to an Integer bitmask.
     */
    @NonNull
    @Override
    public Integer get() {
        if (!mIsPersistent) {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        } else {
            Set<String> value = getPrefs().getStringSet(getKey(), null);
            if (value == null) {
                // not present, fallback to global.
                value = getGlobal().getStringSet(getKey(), null);
                if (value == null || value.isEmpty()) {
                    return mDefaultValue;
                }
            } else if (value.isEmpty()) {
                return 0;
            }
            return Prefs.toInteger(value);
        }
    }
}
