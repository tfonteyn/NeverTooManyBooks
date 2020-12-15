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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylePersistenceLayer;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PStringCollection;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;

/**
 * an SQL WHERE clause (column IN (a,b,c,...)
 */
public class IntListFilter
        implements StyleFilter<ArrayList<Integer>>, PStringCollection {

    private static final String ACTIVE = ".active";
    private static final String TAG = "IntListFilter";
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
    private final ArrayList<Integer> mDefaultValue;
    @NonNull
    private final VirtualDomain mVirtualDomain;
    @StringRes
    private final int mLabelId;
    /** in memory value used for non-persistence situations. */
    @Nullable
    private ArrayList<Integer> mNonPersistedValue;
    private boolean mNonPersistedValueIsActive;

    /**
     * Constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param labelId          string resource id to use as a display label
     * @param key              preference key
     * @param virtualDomain    to use by the expression
     */
    IntListFilter(final boolean isPersistent,
                  @Nullable final StylePersistenceLayer persistenceLayer,
                  @StringRes final int labelId,
                  @NonNull final String key,
                  @NonNull final VirtualDomain virtualDomain) {
        if (BuildConfig.DEBUG /* always */) {
            if (isPersistent && persistenceLayer == null) {
                throw new IllegalStateException();
            }
        }
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = key;
        mDefaultValue = new ArrayList<>();

        mLabelId = labelId;
        mVirtualDomain = virtualDomain;
    }

    /**
     * Copy constructor.
     *
     * @param isPersistent     flag
     * @param persistenceLayer Style reference.
     * @param that             to copy from
     */
    private IntListFilter(final boolean isPersistent,
                          @Nullable final StylePersistenceLayer persistenceLayer,
                          @NonNull final IntListFilter that) {
        if (BuildConfig.DEBUG /* always */) {
            if (isPersistent && persistenceLayer == null) {
                throw new IllegalStateException();
            }
        }
        mPersisted = isPersistent;
        mPersistence = persistenceLayer;
        mKey = that.mKey;
        mDefaultValue = new ArrayList<>(that.mDefaultValue);

        mLabelId = that.mLabelId;
        mVirtualDomain = new VirtualDomain(that.mVirtualDomain);

        if (that.mNonPersistedValue != null) {
            mNonPersistedValue = new ArrayList<>(that.mNonPersistedValue);
        } else {
            mNonPersistedValue = null;
        }

        mNonPersistedValueIsActive = that.mNonPersistedValueIsActive;

        if (mPersisted) {
            set(that.getValue());
        }
    }

    @Override
    @NonNull
    public IntListFilter clone(final boolean isPersistent,
                               @Nullable final StylePersistenceLayer persistenceLayer) {
        return new IntListFilter(isPersistent, persistenceLayer, this);
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
    public VirtualDomain getVirtualDomain() {
        return mVirtualDomain;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        if (!DBDefinitions.isUsed(PreferenceManager.getDefaultSharedPreferences(context),
                                  mVirtualDomain.getName())) {
            return false;
        }

        if (mPersisted) {
            //noinspection ConstantConditions
            return mPersistence.getNonGlobalBoolean(mKey + ACTIVE);
        } else {
            return mNonPersistedValueIsActive;
        }
    }

    @Override
    @Nullable
    public String getExpression(@NonNull final Context context) {
        if (isActive(context)) {
            final List<Integer> value = getValue();
            if (!value.isEmpty()) {
                return '(' + mVirtualDomain.getExpression() + " IN ("
                       + value.stream()
                              .map(String::valueOf)
                              .collect(Collectors.joining(","))
                       + "))";
            }
        }
        return null;
    }

    @Override
    public void setStringCollection(@NonNull final Collection<String> values) {
        if (mPersisted) {
            try {
                //noinspection ConstantConditions
                mPersistence.setIntList(mKey, values.stream()
                                                    .map(Integer::parseInt)
                                                    .collect(Collectors.toList()));
            } catch (@NonNull final NumberFormatException e) {
                // should not happen unless we had a bug while previously writing the pref.
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "values=`" + values + '`', e);
                }
                // in which case we bail out gracefully
            }
        } else {
            // Not implemented as this logic branch is never used.
            // Current usage of this method is limited to importing a style, which obviously
            // always will require a persistence layer.
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void set(@Nullable final ArrayList<Integer> value) {
        if (mPersisted) {
            //noinspection ConstantConditions
            mPersistence.setIntList(mKey, value);
        } else {
            if (value != null) {
                mNonPersistedValue = new ArrayList<>(value);
                mNonPersistedValueIsActive = true;
            } else {
                mNonPersistedValue = null;
                mNonPersistedValueIsActive = false;
            }
        }
    }

    @NonNull
    @Override
    public ArrayList<Integer> getValue() {
        if (mPersisted) {
            //noinspection ConstantConditions
            final ArrayList<Integer> value = mPersistence.getIntList(mKey);
            if (value != null) {
                return value;
            }
        } else if (mNonPersistedValue != null) {
            return mNonPersistedValue;
        }

        return mDefaultValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IntListFilter that = (IntListFilter) o;
        return mKey.equals(that.mKey)
               && Objects.equals(mNonPersistedValue, that.mNonPersistedValue)
               && mDefaultValue.equals(that.mDefaultValue)
               && mPersisted == that.mPersisted

               && mLabelId == that.mLabelId
               && mVirtualDomain.equals(that.mVirtualDomain)

               && mNonPersistedValueIsActive == that.mNonPersistedValueIsActive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mNonPersistedValue, mDefaultValue, mPersisted,
                            mLabelId, mVirtualDomain,
                            mNonPersistedValueIsActive);
    }

    @Override
    @NonNull
    public String toString() {
        return "IntListFilter{"
               + "mKey=`" + mKey + '`'
               + ", mNonPersistedValueIsActive=" + mNonPersistedValueIsActive
               + ", mNonPersistedValue=" + mNonPersistedValue
               + ", mDefaultValue=" + mDefaultValue
               + ", mPersisted=" + mPersisted

               + ", mLabelId=`" + App.getAppContext().getString(mLabelId) + '`'
               + ", mVirtualDomain=" + mVirtualDomain
               + '}';
    }
}
