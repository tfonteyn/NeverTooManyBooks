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
package com.hardbacknutter.nevertoomanybooks.booklist.style.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInt;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

public class BitmaskFilter
        implements StyleFilter<Integer>, PInt {

    /** See {@link com.hardbacknutter.nevertoomanybooks.widgets.BitmaskPreference}. */
    private static final String ACTIVE = ".active";

    @NonNull
    private final TableDefinition mTable;
    @NonNull
    private final String mDomainKey;
    @StringRes
    private final int mLabelId;
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
    /** Flag indicating we should use the persistence store, or use {@link #mNonPersistedValue}. */
    private final boolean mPersisted;

    /** Valid bits. */
    private final int mMask;

    /** in memory value used for non-persistence situations. */
    @Nullable
    private Integer mNonPersistedValue;
    private boolean mNonPersistedValueIsActive;

    /**
     * Constructor.
     * Default value is {@code 0}, i.e. no bits set.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param labelId          string resource id to use as a display label
     * @param key              preference key
     * @param table            to use by the expression
     * @param domainKey        to use by the expression
     * @param mask             valid values bitmask
     */
    BitmaskFilter(final boolean isPersistent,
                  @NonNull final StylePersistenceLayer persistenceLayer,
                  @StringRes final int labelId,
                  @NonNull final String key,
                  @SuppressWarnings("SameParameterValue")
                  @NonNull final TableDefinition table,
                  @NonNull final String domainKey,
                  @NonNull final Integer mask) {
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = key;
        mDefaultValue = 0;

        mMask = mask;

        mLabelId = labelId;
        mTable = table;
        mDomainKey = domainKey;
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param that             to copy from
     */
    BitmaskFilter(final boolean isPersistent,
                  @NonNull final StylePersistenceLayer persistenceLayer,
                  @NonNull final BitmaskFilter that) {
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = that.mKey;
        mDefaultValue = that.mDefaultValue;

        mMask = that.mMask;

        mLabelId = that.mLabelId;
        mTable = that.mTable;
        mDomainKey = that.mDomainKey;

        mNonPersistedValue = that.mNonPersistedValue;
        mNonPersistedValueIsActive = that.mNonPersistedValueIsActive;

        if (mPersisted) {
            set(that.getValue());
        }
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        if (!DBDefinitions.isUsed(PreferenceManager.getDefaultSharedPreferences(context),
                                  mDomainKey)) {
            return false;
        }

        if (mPersisted) {
            return mPersistence.getNonGlobalBoolean(mKey + ACTIVE);
        } else {
            return mNonPersistedValueIsActive;
        }
    }

    /**
     * If the bitmask has <strong>at least one bit set</strong>,
     * the filter looks for values having <strong>those</strong> bits set;
     * other bits being ignored.
     * If the bitmask is {@code == 0}, the filter looks for values {@code == 0} only.
     *
     * @return filter SQL expression, or {@code null} if not active.
     */
    @Override
    @Nullable
    public String getExpression(@NonNull final Context context) {
        if (isActive(context)) {
            final int value = getValue();
            if (value > 0) {
                return "((" + mTable.dot(mDomainKey) + " & " + value + ") <> 0)";
            } else {
                return "(" + mTable.dot(mDomainKey) + "=0)";
            }
        }
        return null;
    }

    @Override
    public void set(@Nullable final Integer value) {

        if (mPersisted) {
            if (value != null) {
                mPersistence.setBitmask(mKey, value & mMask);
            } else {
                mPersistence.setBitmask(mKey, null);
            }
        } else {
            if (value != null) {
                mNonPersistedValue = value & mMask;
                mNonPersistedValueIsActive = true;
            } else {
                mNonPersistedValue = null;
                mNonPersistedValueIsActive = false;
            }
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
        final BitmaskFilter that = (BitmaskFilter) o;
        return mLabelId == that.mLabelId
               && mMask == that.mMask
               && mPersisted == that.mPersisted
               && mTable.equals(that.mTable)
               && mDomainKey.equals(that.mDomainKey)
               && mKey.equals(that.mKey)
               && mDefaultValue.equals(that.mDefaultValue)
               && Objects.equals(mNonPersistedValue, that.mNonPersistedValue)
               && mNonPersistedValueIsActive == that.mNonPersistedValueIsActive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mLabelId, mTable, mDomainKey, mPersistence, mKey, mDefaultValue, mMask,
                            mPersisted, mNonPersistedValue, mNonPersistedValueIsActive);
    }

    @Override
    @NonNull
    public String toString() {
        return "BitmaskFilter{"
               + "mKey='" + mKey + '\''
               + ", mDefaultValue=" + mDefaultValue + "=" + Integer.toBinaryString(mDefaultValue)
               + ", mPersisted=" + mPersisted
               + ", mNonPersistedValue=" + mNonPersistedValue
               + (mNonPersistedValue != null ? "=" + Integer.toBinaryString(mNonPersistedValue)
                                             : "")
               + ", mNonPersistedValueIsActive=" + mNonPersistedValueIsActive
               + ", mMask=" + mMask + "=" + Integer.toBinaryString(mMask)

               + ", mLabelId=`" + App.getAppContext().getString(mLabelId) + '`'
               + ", mTable=" + mTable
               + ", mDomainKey='" + mDomainKey + '\''
               + '}';
    }
}
