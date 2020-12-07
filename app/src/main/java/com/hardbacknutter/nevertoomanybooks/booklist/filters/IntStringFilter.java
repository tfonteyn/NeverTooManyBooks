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
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PInt;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * An Integer stored as a String
 * <p>
 * Used for {@link androidx.preference.ListPreference}
 * The Preference uses 'select 1 of many' type and insists on a String.
 *
 * @see PInt
 */
abstract class IntStringFilter
        implements Filter<Integer>, PPref<Integer>, PInt {

    static final Integer P_NOT_USED = -1;

    @StringRes
    private final int mLabelId;
    @NonNull
    final TableDefinition mTable;
    @NonNull
    final String mDomainKey;

    /** The {@link ListStyle} this preference belongs to. */
    @NonNull
    private final ListStyle mStyle;
    /** preference key. */
    @NonNull
    private final String mKey;
    @NonNull
    private final Integer mDefaultValue;
    /** in memory value used for non-persistence situations. */
    @Nullable
    private Integer mNonPersistedValue;

    /**
     * Constructor.
     * Default value is {@code P_NOT_USED}.
     *
     * @param style     Style preferences reference.
     * @param labelId   string resource id to use as a display label
     * @param key       preference key
     * @param table     to use by the expression
     * @param domainKey to use by the expression
     */
    IntStringFilter(@NonNull final ListStyle style,
                    @StringRes final int labelId,
                    @NonNull final String key,
                    @SuppressWarnings("SameParameterValue") @NonNull final TableDefinition table,
                    @NonNull final String domainKey) {
        mStyle = style;
        mKey = key;
        mDefaultValue = P_NOT_USED;

        mLabelId = labelId;
        mTable = table;
        mDomainKey = domainKey;
    }

    /**
     * Copy constructor.
     *
     * @param filter to copy from
     */
    IntStringFilter(@NonNull final ListStyle style,
                    @NonNull final IntStringFilter filter) {
        mStyle = style;
        mKey = filter.mKey;
        mDefaultValue = filter.mDefaultValue;

        mLabelId = filter.mLabelId;
        mTable = filter.mTable;
        mDomainKey = filter.mDomainKey;

        mNonPersistedValue = filter.mNonPersistedValue;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return !mDefaultValue.equals(getValue(context))
               && DBDefinitions.isUsed(PreferenceManager.getDefaultSharedPreferences(context),
                                       mDomainKey);
    }

    @NonNull
    @Override
    public Integer getValue(@NonNull final Context context) {
        if (mStyle.isUserDefined()) {
            final Integer value = mStyle.getSettings().getStringedInt(context, mKey);
            if (value != null) {
                return value;
            }
        } else if (mNonPersistedValue != null) {
            return mNonPersistedValue;
        }

        return mDefaultValue;
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public void set(@Nullable final Integer value) {
        if (mStyle.isUserDefined()) {
            mStyle.getSettings().setStringedInt(mKey, value);
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
        final Integer tmp = (Integer) in.readValue(getClass().getClassLoader());
        if (tmp != null) {
            set(tmp);
        }
    }

    public void writeToParcel(@NonNull final Parcel dest) {
        if (mStyle.isUserDefined()) {
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
        return "IntStringFilter{"
               + "mStyle=" + mStyle.getUuid()
               + ", mKey=`" + mKey + '`'
               + ", mDefaultValue=" + mDefaultValue
               + ", mNonPersistedValue=" + mNonPersistedValue

               + ", mLabelId=" + mLabelId
               + ", mTable=" + mTable
               + ", mDomainKey='" + mDomainKey + '\''
               + ", isActive=" + isActive(App.getAppContext())
               + ", expression=`" + getExpression(App.getAppContext()) + '\''

               + ", value=" + getValue(App.getAppContext())
               + '}';
    }
}
