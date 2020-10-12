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

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.utils.BitUtils;

/**
 * Used for {@link androidx.preference.MultiSelectListPreference}.
 * <p>
 * We basically want a bitmask/int, but the Preference insists on a {@code Set<String>}
 *
 * @see PInt
 */
public class PBitmask
        extends PPrefBase<Integer>
        implements PInt {

    /** Valid bits. */
    @SuppressWarnings("FieldNotUsedInToString")
    private final int mMask;

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if there is no global default.
     *
     * @param sp           Style preferences reference.
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param key          key of preference
     * @param defValue     in memory default
     * @param mask         valid values bitmask
     */
    public PBitmask(@NonNull final SharedPreferences sp,
                    final boolean isPersistent,
                    @NonNull final String key,
                    @NonNull final Integer defValue,
                    final int mask) {
        super(sp, isPersistent, key, defValue);
        mMask = mask;
    }

    @NonNull
    public Integer getGlobalValue(@NonNull final Context context) {
        final Set<String> value = PreferenceManager.getDefaultSharedPreferences(context)
                                                   .getStringSet(getKey(), null);
        if (value == null || value.isEmpty()) {
            return mDefaultValue;
        }
        return BitUtils.from(value) & mMask;
    }

    /**
     * converts the Integer bitmask and stores it as a {@code Set<String>}.
     */
    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @NonNull final Integer value) {
        ed.putStringSet(getKey(), BitUtils.toStringSet(value & mMask));
    }

    /**
     * Reads a {@code Set<String>} from storage, and converts it to an Integer bitmask.
     *
     * @return bitmask, or the default value
     */
    @NonNull
    @Override
    public Integer getValue(@NonNull final Context context) {
        if (mIsPersistent) {
            final Set<String> value = mStylePrefs.getStringSet(getKey(), null);
            if (value != null) {
                return BitUtils.from(value) & mMask;
            }
            return getGlobalValue(context);
        } else {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PBitmask{" + super.toString()
               + ", value bits=`" + Integer.toBinaryString(getValue(App.getAppContext())) + '`'
               + '}';
    }
}
