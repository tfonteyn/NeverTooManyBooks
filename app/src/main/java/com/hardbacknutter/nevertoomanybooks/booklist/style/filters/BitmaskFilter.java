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
import com.hardbacknutter.nevertoomanybooks.widgets.TriStateMultiSelectListPreference;

public class BitmaskFilter
        implements StyleFilter<Integer>, PInt {

    /** See {@link TriStateMultiSelectListPreference}. */
    private static final String ACTIVE = ".active";

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
    @NonNull
    private final DomainExpression mDomainExpression;
    @StringRes
    private final int mLabelId;

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
     * @param domainExpression to use by the expression
     * @param mask             valid values bitmask
     */
    BitmaskFilter(final boolean isPersistent,
                  @Nullable final StylePersistenceLayer persistenceLayer,
                  @StringRes final int labelId,
                  @NonNull final String key,
                  @NonNull final DomainExpression domainExpression,
                  @NonNull final Integer mask) {
        if (BuildConfig.DEBUG /* always */) {
            if (isPersistent && persistenceLayer == null) {
                throw new IllegalStateException();
            }
        }
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = key;
        mDefaultValue = 0;

        mLabelId = labelId;
        mDomainExpression = domainExpression;

        mMask = mask;
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param that             to copy from
     */
    private BitmaskFilter(final boolean isPersistent,
                          @Nullable final StylePersistenceLayer persistenceLayer,
                          @NonNull final BitmaskFilter that) {
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

        mMask = that.mMask;

        mNonPersistedValue = that.mNonPersistedValue;
        mNonPersistedValueIsActive = that.mNonPersistedValueIsActive;

        if (mPersisted) {
            set(that.getValue());
        }
    }

    @Override
    @NonNull
    public BitmaskFilter clone(final boolean isPersistent,
                               @Nullable final StylePersistenceLayer persistenceLayer) {
        return new BitmaskFilter(isPersistent, persistenceLayer, this);
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

    @NonNull
    @Override
    public DomainExpression getDomainExpression() {
        return mDomainExpression;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        if (!DBDefinitions.isUsed(PreferenceManager.getDefaultSharedPreferences(context),
                                  mDomainExpression.getName())) {
            return false;
        }

        if (mPersisted) {
            //noinspection ConstantConditions
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
                return "((" + mDomainExpression.getExpression() + " & " + value + ") <> 0)";
            } else {
                return "(" + mDomainExpression.getExpression() + "=0)";
            }
        }
        return null;
    }


    @Override
    public void set(@Nullable final Integer value) {
        if (mPersisted) {
            if (value != null) {
                //noinspection ConstantConditions
                mPersistence.setBitmask(mKey, value & mMask);
            } else {
                //noinspection ConstantConditions
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
            //noinspection ConstantConditions
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
        return mKey.equals(that.mKey)
               && Objects.equals(mNonPersistedValue, that.mNonPersistedValue)
               && mDefaultValue.equals(that.mDefaultValue)
               && mPersisted == that.mPersisted

               && mLabelId == that.mLabelId
               && mDomainExpression.equals(that.mDomainExpression)

               && mNonPersistedValueIsActive == that.mNonPersistedValueIsActive
               && mMask == that.mMask;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mNonPersistedValue, mDefaultValue, mPersisted,
                            mLabelId, mDomainExpression,
                            mNonPersistedValueIsActive,
                            mMask);
    }

    @Override
    @NonNull
    public String toString() {
        return "BitmaskFilter{"
               + "mKey=`" + mKey + '`'
               + ", mNonPersistedValueIsActive=" + mNonPersistedValueIsActive
               + ", mNonPersistedValue=" + mNonPersistedValue
               + (mNonPersistedValue != null ? "=" + Integer.toBinaryString(mNonPersistedValue)
                                             : "")
               + ", mDefaultValue=" + mDefaultValue + "=" + Integer.toBinaryString(mDefaultValue)
               + ", mPersisted=" + mPersisted

               + ", mLabelId=`" + App.getAppContext().getString(mLabelId) + '`'
               + ", mDomainExpression=" + mDomainExpression

               + ", mMask=" + mMask + "=" + Integer.toBinaryString(mMask)
               + '}';
    }
}
