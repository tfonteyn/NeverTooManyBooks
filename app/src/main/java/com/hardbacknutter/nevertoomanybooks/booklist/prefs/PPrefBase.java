/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.App;

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

    /** Flag to indicate the value is persisted. */
    final boolean mIsPersistent;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    final T mDefaultValue;

    /**
     * Copy of the style uuid this Preference belongs to.
     * Convenience only and not locally preserved.
     * Must be set in the constructor.
     * <p>
     * When set to the empty string, the global preferences will be used.
     */
    @NonNull
    private final String mUuid;

    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** in memory value used for non-persistence situations. */
    @Nullable
    T mNonPersistedValue;

    /**
     * Constructor.
     *
     * @param key          key of preference
     * @param uuid         UUID of the style
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param defValue     in memory default
     */
    PPrefBase(@NonNull final String key,
              @NonNull final String uuid,
              final boolean isPersistent,
              @NonNull final T defValue) {
        mKey = key;
        mUuid = uuid;
        mIsPersistent = isPersistent;
        mDefaultValue = defValue;
    }

    /**
     * Get the style preferences, or if the UUID is not set, the global preferences.
     *
     * @param context Current context
     *
     * @return preferences
     */
    @NonNull
    protected SharedPreferences getPrefs(@NonNull final Context context) {
        if (!mUuid.isEmpty()) {
            return context.getSharedPreferences(mUuid, Context.MODE_PRIVATE);
        } else {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public void set(@Nullable final T value) {
        if (!mIsPersistent) {
            mNonPersistedValue = value;

        } else {
            SharedPreferences.Editor ed = getPrefs(App.getAppContext()).edit();
            if (value == null) {
                ed.remove(getKey());
            } else {
                set(ed, value);
            }
            ed.apply();
        }
    }


    /**
     * Set the <strong>value</strong> from the Parcel.
     *
     * @param in parcel to read from
     */
    public void set(@NonNull final Parcel in) {
        //noinspection unchecked
        T tmp = (T) in.readValue(getClass().getClassLoader());
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
               + ", mUuid=" + mUuid
               + ", type=" + mDefaultValue.getClass().getSimpleName()
               + ", defaultValue=`" + mDefaultValue + '`'
               + ", ssPersistent=" + mIsPersistent
               + ", nonPersistedValue=`" + mNonPersistedValue + '`'
               + ", value=`" + getValue(App.getAppContext()) + '`';
    }
}
