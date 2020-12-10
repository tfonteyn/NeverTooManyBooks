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
 * Used for {@link androidx.preference.MultiSelectListPreference}.
 * <p>
 * We basically want a bitmask/int, but the Preference insists on a {@code Set<String>}
 *
 * @see PInt
 */
public class PBitmask
        implements PPref<Integer>, PInt {

    /** The {@link StylePersistenceLayer} to use. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final StylePersistenceLayer mPersistence;

    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    private final Integer mDefaultValue;
    /** Valid bits. */
    private final int mMask;
    /** Flag indicating we should use the persistence store, or use {@link #mNonPersistedValue}. */
    private final boolean mPersisted;
    /** in memory value used for non-persistence situations. */
    @Nullable
    private Integer mNonPersistedValue;

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if there is no global default.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param key              key of preference
     * @param defValue         default value
     * @param mask             valid values bitmask
     */
    public PBitmask(final boolean isPersistent,
                    @NonNull final StylePersistenceLayer persistenceLayer,
                    @NonNull final String key,
                    final int defValue,
                    final int mask) {
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = key;
        mDefaultValue = defValue;
        mMask = mask;
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param that             to copy from
     */
    public PBitmask(final boolean isPersistent,
                    @NonNull final StylePersistenceLayer persistenceLayer,
                    @NonNull final PBitmask that) {
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = that.mKey;
        mDefaultValue = that.mDefaultValue;
        mNonPersistedValue = that.mNonPersistedValue;

        mMask = that.mMask;

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
    public void set(@Nullable final Integer value) {
        final Integer maskedValue = value != null ? value & mMask : null;
        if (mPersisted) {
            mPersistence.setBitmask(mKey, maskedValue);
        } else {
            mNonPersistedValue = maskedValue;
        }
    }

    @NonNull
    @Override
    public Integer getValue() {
        if (mPersisted) {
            final Integer value = mPersistence.getBitmask(mKey);
            if (value != null) {
                return value & mMask;
            }
        } else if (mNonPersistedValue != null) {
            return mNonPersistedValue & mMask;
        }

        return mDefaultValue & mMask;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PBitmask pBitmask = (PBitmask) o;
        return mMask == pBitmask.mMask
               && mPersisted == pBitmask.mPersisted
               && mKey.equals(pBitmask.mKey)
               && mDefaultValue.equals(pBitmask.mDefaultValue)
               && Objects.equals(mNonPersistedValue, pBitmask.mNonPersistedValue);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(mPersistence, mKey, mDefaultValue, mMask, mPersisted, mNonPersistedValue);
    }

    @Override
    @NonNull
    public String toString() {
        return "PBitmask{"
               + "mKey='" + mKey + '\''
               + ", mDefaultValue=" + mDefaultValue + "=" + Integer.toBinaryString(mDefaultValue)
               + ", mPersisted=" + mPersisted
               + ", mNonPersistedValue=" + mNonPersistedValue
               + (mNonPersistedValue != null ? "=" + Integer.toBinaryString(mNonPersistedValue)
                                             : "")
               + ", mMask=" + mMask + "=" + Integer.toBinaryString(mMask)
               + '}';
    }
}
