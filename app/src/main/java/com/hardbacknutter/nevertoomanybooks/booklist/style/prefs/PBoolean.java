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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;

/**
 * Used for {@link androidx.preference.SwitchPreference}.
 */
public class PBoolean
        implements PPref<Boolean> {

    /** The {@link StylePersistenceLayer} to use. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final StylePersistenceLayer mPersistence;

    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    private final Boolean mDefaultValue;
    /** Flag indicating we should use the persistence store, or use {@link #mNonPersistedValue}. */
    private final boolean mPersisted;
    /** in memory value used for non-persistence situations. */
    @Nullable
    private Boolean mNonPersistedValue;

    /**
     * Constructor. Uses the global setting as the default value,
     * or {@code false} if there is no global default.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param key              preference key
     */
    public PBoolean(final boolean isPersistent,
                    @NonNull final StylePersistenceLayer persistenceLayer,
                    @NonNull final String key) {
        this(isPersistent, persistenceLayer, key, false);
    }

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if there is no global default.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param key              preference key
     * @param defValue         default value
     */
    public PBoolean(final boolean isPersistent,
                    @NonNull final StylePersistenceLayer persistenceLayer,
                    @NonNull final String key,
                    final boolean defValue) {
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = key;
        mDefaultValue = defValue;
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param that             to copy from
     */
    public PBoolean(final boolean isPersistent,
                    @NonNull final StylePersistenceLayer persistenceLayer,
                    @NonNull final PBoolean that) {
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
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
    public void set(@Nullable final Boolean value) {
        if (mPersisted) {
            mPersistence.setBoolean(getKey(), value);
        } else {
            mNonPersistedValue = value;
        }
    }

    @NonNull
    @Override
    public Boolean getValue() {
        if (mPersisted) {
            final Boolean value = mPersistence.getBoolean(getKey());
            if (value != null) {
                return value;
            }
        } else if (mNonPersistedValue != null) {
            return mNonPersistedValue;
        }

        return mDefaultValue;
    }

    public boolean isTrue() {
        return getValue();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PBoolean pBoolean = (PBoolean) o;
        return mPersisted == pBoolean.mPersisted
               && mKey.equals(pBoolean.mKey)
               && mDefaultValue.equals(pBoolean.mDefaultValue)
               && Objects.equals(mNonPersistedValue, pBoolean.mNonPersistedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPersistence, mKey, mDefaultValue, mPersisted, mNonPersistedValue);
    }

    @Override
    @NonNull
    public String toString() {
        return "PBoolean{"
               + "mKey=`" + mKey + '`'
               + ", mDefaultValue=" + mDefaultValue
               + ", mPersisted=" + mPersisted
               + ", mNonPersistedValue=" + mNonPersistedValue
               + '}';
    }
}
