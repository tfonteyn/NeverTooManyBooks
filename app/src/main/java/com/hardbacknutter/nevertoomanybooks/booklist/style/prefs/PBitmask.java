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
package com.hardbacknutter.nevertoomanybooks.booklist.style.prefs;

import android.content.Context;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;

/**
 * Used for {@link androidx.preference.MultiSelectListPreference}.
 * <p>
 * We basically want a bitmask/int, but the Preference insists on a {@code Set<String>}
 *
 * @see PInt
 */
public class PBitmask
        implements PPref<Integer>, PInt {

    /** The {@link ListStyle} this preference belongs to. */
    @NonNull
    private final ListStyle mStyle;

    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    private final Integer mDefaultValue;
    /** Valid bits. */
    private final int mMask;
    /** in memory value used for non-persistence situations. */
    @Nullable
    private Integer mNonPersistedValue;

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if there is no global default.
     *
     * @param style    Style reference.
     * @param key      key of preference
     * @param defValue default value
     * @param mask     valid values bitmask
     */
    public PBitmask(@NonNull final ListStyle style,
                    @NonNull final String key,
                    final int defValue,
                    final int mask) {
        mStyle = style;
        mKey = key;
        mDefaultValue = defValue;
        mMask = mask;
    }

    /**
     * Copy constructor.
     *
     * @param style    Style reference.
     * @param pBitmask to copy from
     */
    public PBitmask(@NonNull final ListStyle style,
                    @NonNull final PBitmask pBitmask) {
        mStyle = style;
        mKey = pBitmask.mKey;
        mDefaultValue = pBitmask.mDefaultValue;

        mMask = pBitmask.mMask;

        mNonPersistedValue = pBitmask.mNonPersistedValue;
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public void set(@Nullable final Integer value) {
        if (mStyle.isUserDefined()) {
            mStyle.getSettings().setBitmask(getKey(), mMask, value);
        } else {
            mNonPersistedValue = value;
        }
    }

    @NonNull
    @Override
    public Integer getValue(@NonNull final Context context) {
        if (mStyle.isUserDefined()) {
            final Integer value = mStyle.getSettings().getBitmask(context, getKey(), mMask);
            if (value != null) {
                return value;
            }
        } else if (mNonPersistedValue != null) {
            return mNonPersistedValue;
        }

        return mDefaultValue;
    }

    public void writeToParcel(@NonNull final Parcel dest) {
        if (mStyle.isUserDefined()) {
            dest.writeValue(getValue(App.getAppContext()));
        } else {
            // Write the in-memory value to the parcel.
            // Do NOT use 'get' as that would return the default if the actual value is not set.
            dest.writeValue(mNonPersistedValue);
        }
    }

    public void set(@NonNull final Parcel in) {
        final Integer tmp = (Integer) in.readValue(getClass().getClassLoader());
        if (tmp != null) {
            set(tmp);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PBitmask{"
               + "mStyle=" + mStyle.getUuid()
               + ", mKey='" + mKey + '\''
               + ", mDefaultValue=" + mDefaultValue
               + "=" + Integer.toBinaryString(mDefaultValue)
               + ", mMask=" + mMask
               + "=" + Integer.toBinaryString(mMask)
               + ", mNonPersistedValue=" + mNonPersistedValue
               + (mNonPersistedValue != null ? "=" + Integer.toBinaryString(mNonPersistedValue)
                                             : "")
               + ", value=`" + getValue(App.getAppContext()) + '`'
               + "=" + Integer.toBinaryString(getValue(App.getAppContext()))
               + '}';
    }
}
