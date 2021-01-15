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
package com.hardbacknutter.nevertoomanybooks.booklist.style.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInt;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;

/**
 * An Integer stored as a String
 * <p>
 * Used for {@link androidx.preference.ListPreference}
 * The Preference uses 'select 1 of many' type and insists on a String.
 * <p>
 * The default value cannot by {@code null} so we use {@link #P_NOT_USED} instead.
 * For now, it cannot be changed, but the logic is implemented so
 * only a new constructor would need to be added.
 */
abstract class IntStringFilter
        implements StyleFilter<Integer>, PInt {

    private static final Integer P_NOT_USED = -1;

    @NonNull
    final DomainExpression mDomainExpression;
    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** Flag indicating we should use the persistence store, or use {@link #mNonPersistedValue}. */
    private final boolean mPersisted;
    /** The {@link StylePersistenceLayer} to use. Must be NonNull if {@link #mPersisted} == true. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private final StylePersistenceLayer mPersistence;
    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    private final Integer mDefaultValue;
    @StringRes
    private final int mLabelId;

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
     * @param domainExpression to use by the expression
     */
    IntStringFilter(final boolean isPersistent,
                    @Nullable final StylePersistenceLayer persistenceLayer,
                    @StringRes final int labelId,
                    @NonNull final String key,
                    @NonNull final DomainExpression domainExpression) {
        if (BuildConfig.DEBUG /* always */) {
            if (isPersistent && persistenceLayer == null) {
                throw new IllegalStateException();
            }
        }
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = key;
        mDefaultValue = P_NOT_USED;
        mNonPersistedValue = P_NOT_USED;

        mLabelId = labelId;
        mDomainExpression = domainExpression;
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param that             to copy from
     */
    IntStringFilter(final boolean isPersistent,
                    @Nullable final StylePersistenceLayer persistenceLayer,
                    @NonNull final IntStringFilter that) {
        if (BuildConfig.DEBUG /* always */) {
            if (isPersistent && persistenceLayer == null) {
                throw new IllegalStateException();
            }
        }
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = that.mKey;
        mDefaultValue = that.mDefaultValue;

        mLabelId = that.mLabelId;
        mDomainExpression = new DomainExpression(that.mDomainExpression);

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
                                       mDomainExpression.getName());
    }

    @NonNull
    @Override
    public DomainExpression getDomainExpression() {
        return mDomainExpression;
    }

    @Override
    public void set(@Nullable final Integer value) {
        if (mPersisted) {
            //noinspection ConstantConditions
            mPersistence.setStringedInt(mKey, value);
        } else {
            mNonPersistedValue = value;
        }
    }

    @NonNull
    @Override
    public Integer getValue() {
        if (mPersisted) {
            //noinspection ConstantConditions
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
        return mKey.equals(that.mKey)
               && Objects.equals(mNonPersistedValue, that.mNonPersistedValue)
               && mDefaultValue.equals(that.mDefaultValue)
               && mPersisted == that.mPersisted

               && mLabelId == that.mLabelId
               && mDomainExpression.equals(that.mDomainExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mNonPersistedValue, mDefaultValue, mPersisted,
                            mLabelId, mDomainExpression);
    }

    @Override
    @NonNull
    public String toString() {
        return "IntStringFilter{"
               + "mKey=`" + mKey + '`'
               + ", mNonPersistedValue=" + mNonPersistedValue
               + ", mDefaultValue=" + mDefaultValue
               + ", mPersisted=" + mPersisted

               + ", mLabelId=`" + App.getAppContext().getString(mLabelId) + '`'
               + ", mDomainExpression=" + mDomainExpression
               + '}';
    }
}
