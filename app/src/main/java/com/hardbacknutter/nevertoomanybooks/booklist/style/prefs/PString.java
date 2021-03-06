/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;

/**
 * Used for {@link androidx.preference.EditTextPreference}.
 */
public class PString
        implements PPref<String> {

    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** Flag indicating we should use the persistence store, or use {@link #mNonPersistedValue}. */
    private final boolean mPersisted;
    /** The {@link StylePersistenceLayer} to use. Must be NonNull if {@link #mPersisted} == true. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private final StylePersistenceLayer mPersistenceLayer;
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
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param key              preference key
     */
    public PString(final boolean isPersistent,
                   @Nullable final StylePersistenceLayer persistenceLayer,
                   @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            if (isPersistent && persistenceLayer == null) {
                throw new IllegalStateException();
            }
        }
        mPersisted = isPersistent;
        mPersistenceLayer = persistenceLayer;
        mKey = key;
        mDefaultValue = "";
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param that             to copy from
     */
    public PString(final boolean isPersistent,
                   @Nullable final StylePersistenceLayer persistenceLayer,
                   @NonNull final PString that) {
        if (BuildConfig.DEBUG /* always */) {
            if (isPersistent && persistenceLayer == null) {
                throw new IllegalStateException();
            }
        }
        mPersisted = isPersistent;
        mPersistenceLayer = persistenceLayer;
        mKey = that.mKey;
        mDefaultValue = that.mDefaultValue;

        mNonPersistedValue = that.mNonPersistedValue;

        if (mPersisted) {
            set(that.getValue());
        }
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public void set(@Nullable final String value) {
        if (mPersisted) {
            //noinspection ConstantConditions
            mPersistenceLayer.setString(getKey(), value);
        } else {
            mNonPersistedValue = value;
        }
    }

    @NonNull
    @Override
    public String getValue() {
        if (mPersisted) {
            //noinspection ConstantConditions
            final String value = mPersistenceLayer.getString(getKey());
            if (value != null) {
                return value;
            }
        } else if (mNonPersistedValue != null) {
            return mNonPersistedValue;
        }

        return mDefaultValue;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PString that = (PString) o;
        // mPersisted is NOT part of the values to compare!
        return mKey.equals(that.mKey)
               && Objects.equals(mNonPersistedValue, that.mNonPersistedValue)
               && mDefaultValue.equals(that.mDefaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mNonPersistedValue, mDefaultValue);
    }

    @Override
    @NonNull
    public String toString() {
        return "PString{"
               + "mKey=`" + mKey + '`'
               + ", mNonPersistedValue=" + mNonPersistedValue
               + ", mDefaultValue=" + mDefaultValue
               + ", mPersisted=" + mPersisted
               + '}';
    }
}
