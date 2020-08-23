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

import android.content.SharedPreferences;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;

/**
 * Base class for a generic Preference stored as a {@code String}.
 * <p>
 * Note that the methods that take a SharedPreferences.Editor do not need to check
 * on uuid or persistence as obviously that is decided by the caller.
 *
 * @param <T> type of the value to store
 */
public abstract class PPrefBase<T>
        implements PPref<T> {

    /**
     * The SharedPreferences references for the BooklistStyle this preference belongs to.
     * This can be the global SharedPreferences if this is the global/default BooklistStyle.
     */
    @NonNull
    protected final SharedPreferences mStylePrefs;

    /**
     * Flag to indicate the value is persisted.
     * (This is used for builtin style properties)
     */
    final boolean mIsPersistent;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    final T mDefaultValue;

    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** in memory value used for non-persistence situations. */
    @Nullable
    T mNonPersistedValue;

    /**
     * Constructor.
     *
     * @param sp           Style preferences reference.
     * @param key          key of preference
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param defValue     in memory default
     */
    PPrefBase(@NonNull final SharedPreferences sp,
              @NonNull final String key,
              final boolean isPersistent,
              @NonNull final T defValue) {
        mStylePrefs = sp;
        mKey = key;
        mIsPersistent = isPersistent;
        mDefaultValue = defValue;
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
    }

    public void set(@Nullable final T value) {
        if (mIsPersistent) {
            final SharedPreferences.Editor ed = mStylePrefs.edit();
            if (value == null) {
                ed.remove(getKey());
            } else {
                set(ed, value);
            }
            ed.apply();
        } else {
            mNonPersistedValue = value;
        }
    }

    /**
     * Set the <strong>value</strong> from the Parcel.
     *
     * @param in parcel to read from
     */
    public void set(@NonNull final Parcel in) {
        //noinspection unchecked
        final T tmp = (T) in.readValue(getClass().getClassLoader());
        if (tmp != null) {
            set(tmp);
        }
    }

    public void writeToParcel(@NonNull final Parcel dest) {
        if (mIsPersistent) {
            // write the actual value, this could be the default if we have no value, but that
            // is what we want for user-defined styles anyhow.
            dest.writeValue(getValue(App.getAppContext()));
        } else {
            // builtin ? write the in-memory value to the parcel
            // do NOT use 'get' as that would return the default if the actual value is not set.
            dest.writeValue(mNonPersistedValue);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "mKey=" + mKey
               + ", style=" + mStylePrefs.getString(BooklistStyle.pk_name, "????")
               + ", type=" + mDefaultValue.getClass().getSimpleName()
               + ", mDefaultValue=`" + mDefaultValue + '`'
               + ", mIsPersistent=" + mIsPersistent
               + ", mNonPersistedValue=`" + mNonPersistedValue + '`'
               + ", value=`" + getValue(App.getAppContext()) + '`';
    }
}
