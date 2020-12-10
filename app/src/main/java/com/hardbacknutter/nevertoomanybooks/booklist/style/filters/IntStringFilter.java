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

/**
 * An Integer stored as a String
 * <p>
 * Used for {@link androidx.preference.ListPreference}
 * The Preference uses 'select 1 of many' type and insists on a String.
 * <p>
 * The default value is {@link #P_NOT_USED}.
 * For now, it cannot be changed, but the logic is implemented so
 * only a new constructor would need to be added.
 */
abstract class IntStringFilter
        implements StyleFilter<Integer>, PInt {

    private static final Integer P_NOT_USED = -1;
    @NonNull
    final TableDefinition mTable;
    @NonNull
    final String mDomainKey;
    @StringRes
    private final int mLabelId;
    /** The {@link StylePersistenceLayer} to use. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final StylePersistenceLayer mPersistence;
    /** preference key. */
    @NonNull
    private final String mKey;
    @NonNull
    private final Integer mDefaultValue;
    /** Flag indicating we should use the persistence store, or use {@link #mNonPersistedValue}. */
    private final boolean mPersisted;
    /** in memory value used for non-persistence situations. */
    @Nullable
    private Integer mNonPersistedValue;

    /**
     * Constructor.
     * Default value is {@code P_NOT_USED}.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param labelId          string resource id to use as a display label
     * @param key              preference key
     * @param table            to use by the expression
     * @param domainKey        to use by the expression
     */
    IntStringFilter(final boolean isPersistent,
                    @NonNull final StylePersistenceLayer persistenceLayer,
                    @StringRes final int labelId,
                    @NonNull final String key,
                    @SuppressWarnings("SameParameterValue") @NonNull final TableDefinition table,
                    @NonNull final String domainKey) {
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = key;
        mDefaultValue = P_NOT_USED;

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
    IntStringFilter(final boolean isPersistent,
                    @NonNull final StylePersistenceLayer persistenceLayer,
                    @NonNull final IntStringFilter that) {
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = that.mKey;
        mDefaultValue = that.mDefaultValue;

        mLabelId = that.mLabelId;
        mTable = that.mTable;
        mDomainKey = that.mDomainKey;

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

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return !P_NOT_USED.equals(getValue())
               && DBDefinitions.isUsed(PreferenceManager.getDefaultSharedPreferences(context),
                                       mDomainKey);
    }

    @Override
    public void set(@Nullable final Integer value) {
        if (mPersisted) {
            mPersistence.setStringedInt(mKey, value);
        } else {
            mNonPersistedValue = value;
        }
    }

    @NonNull
    @Override
    public Integer getValue() {
        if (mPersisted) {
            final Integer value = mPersistence.getStringedInt(mKey);
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
        final IntStringFilter that = (IntStringFilter) o;
        return mLabelId == that.mLabelId
               && mPersisted == that.mPersisted
               && mTable.equals(that.mTable)
               && mDomainKey.equals(that.mDomainKey)
               && mKey.equals(that.mKey)
               && mDefaultValue.equals(that.mDefaultValue)
               && Objects.equals(mNonPersistedValue, that.mNonPersistedValue);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(mTable, mDomainKey, mLabelId, mPersistence, mKey, mDefaultValue, mPersisted,
                      mNonPersistedValue);
    }

    @Override
    @NonNull
    public String toString() {
        return "IntStringFilter{"
               + "mKey=`" + mKey + '`'
               + ", mDefaultValue=" + mDefaultValue
               + ", mPersisted=" + mPersisted
               + ", mNonPersistedValue=" + mNonPersistedValue

               + ", mLabelId=`" + App.getAppContext().getString(mLabelId) + '`'
               + ", mTable=" + mTable
               + ", mDomainKey='" + mDomainKey + '\''
               + '}';
    }
}
