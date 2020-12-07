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
 * Used for {@link androidx.preference.EditTextPreference}.
 */
public class PString
        implements PPref<String> {

    /** The {@link ListStyle} this preference belongs to. */
    @NonNull
    private final ListStyle mStyle;

    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    private final String mDefaultValue;
    /** in memory value used for non-persistence situations. */
    @Nullable
    private String mNonPersistedValue;

    /**
     * Constructor. Uses the global setting as the default value,
     * or {@code ""} if there is no global default.
     *
     * @param style Style reference.
     * @param key   preference key
     */
    public PString(@NonNull final ListStyle style,
                   @NonNull final String key) {
        mStyle = style;
        mKey = key;
        mDefaultValue = "";
    }

    /**
     * Copy constructor.
     *
     * @param style   Style reference.
     * @param pString to copy from
     */
    public PString(@NonNull final ListStyle style,
                   @NonNull final PString pString) {
        mStyle = style;
        mKey = pString.mKey;
        mDefaultValue = pString.mDefaultValue;

        mNonPersistedValue = pString.mNonPersistedValue;
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public void set(@Nullable final String value) {
        if (mStyle.isUserDefined()) {
            mStyle.getSettings().setString(getKey(), value);
        } else {
            mNonPersistedValue = value;
        }
    }

    @NonNull
    @Override
    public String getValue(@NonNull final Context context) {
        if (mStyle.isUserDefined()) {
            final String value = mStyle.getSettings().getString(context, getKey());
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
        final String tmp = (String) in.readValue(getClass().getClassLoader());
        if (tmp != null) {
            set(tmp);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "PString{"
               + "mStyle=" + mStyle.getUuid()
               + ", mKey=`" + mKey + '`'
               + ", mDefaultValue=`" + mDefaultValue + '`'
               + ", mNonPersistedValue=`" + mNonPersistedValue + '`'
               + ", value=`" + getValue(App.getAppContext()) + '`'
               + '}';
    }
}
