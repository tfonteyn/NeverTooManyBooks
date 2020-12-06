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
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

public class BitmaskFilter
        implements Filter<Integer>, PPref<Integer>, PInt {

    /** See {@link com.hardbacknutter.nevertoomanybooks.widgets.BitmaskPreference}. */
    private static final String ACTIVE = ".active";

    @StringRes
    private final int mLabelId;

    @NonNull
    private final TableDefinition mTable;
    @NonNull
    private final String mDomainKey;
    /** The {@link BooklistStyle} this preference belongs to. */
    @NonNull
    private final BooklistStyle mStyle;

    /** in-memory default to use when value==null, or when the backend does not contain the key. */
    @NonNull
    private final Integer mDefaultValue;

    /** key for the Preference. */
    @NonNull
    private final String mKey;
    /** Valid bits. */
    private final int mMask;
    /** in memory value used for non-persistence situations. */
    @Nullable
    private Integer mNonPersistedValue;


    /**
     * Constructor.
     * Default value is {@code 0}, i.e. no bits set.
     *
     * @param style     Style reference
     * @param labelId   string resource id to use as a display label
     * @param key       preference key
     * @param defValue  in memory default
     * @param mask      valid values bitmask
     * @param table     to use by the expression
     * @param domainKey to use by the expression
     */
    public BitmaskFilter(@NonNull final BooklistStyle style,
                         @StringRes final int labelId,
                         @NonNull final String key,
                         @NonNull final Integer defValue,
                         @NonNull final Integer mask,
                         @SuppressWarnings("SameParameterValue")
                         @NonNull final TableDefinition table,
                         @NonNull final String domainKey) {
        mStyle = style;
        mKey = key;
        mDefaultValue = defValue;
        mMask = mask;

        mLabelId = labelId;
        mTable = table;
        mDomainKey = domainKey;
    }

    @NonNull
    @Override
    public String getKey() {
        return mKey;
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
            final int value = getValue(context);
            if (value > 0) {
                return "((" + mTable.dot(mDomainKey) + " & " + value + ") <> 0)";
            } else {
                return "(" + mTable.dot(mDomainKey) + "=0)";
            }
        }
        return null;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return mStyle.getSettings().getBoolean(getKey() + ACTIVE)
               && DBDefinitions.isUsed(PreferenceManager.getDefaultSharedPreferences(context),
                                       mDomainKey);
    }

    @Override
    public void set(@Nullable final Integer value) {
        if (mStyle.isUserDefined()) {
            mStyle.getSettings().setBitmask(getKey(), mMask, value);
        } else {
            mNonPersistedValue = value;
        }
    }

    @NonNull
    @Override
    public Integer getValue(@NonNull final Context context) {
        if (mStyle.isUserDefined()) {
            final Integer value = mStyle.getSettings().getBitmask(context, getKey(), mMask);
            if (value != null) {
                return value;
            }
        } else if (mNonPersistedValue != null) {
            return mNonPersistedValue;
        }

        return mDefaultValue;
    }

    public void writeToParcel(@NonNull final Parcel dest) {
        if (mStyle.isUserDefined()) {
            dest.writeValue(getValue(App.getAppContext()));
        } else {
            // Write the in-memory value to the parcel.
            // Do NOT use 'get' as that would return the default if the actual value is not set.
            dest.writeValue(mNonPersistedValue);
        }
    }

    public void set(@NonNull final Parcel in) {
        final Integer tmp = (Integer) in.readValue(getClass().getClassLoader());
        if (tmp != null) {
            set(tmp);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "BitmaskFilter{"
               + "mStyle=" + mStyle.getUuid()
               + ", mKey='" + mKey + '\''
               + ", mDefaultValue=" + mDefaultValue
               + "=" + Integer.toBinaryString(mDefaultValue)
               + ", mMask=" + mMask
               + "=" + Integer.toBinaryString(mMask)
               + ", mNonPersistedValue=" + mNonPersistedValue
               + (mNonPersistedValue != null ? "=" + Integer.toBinaryString(mNonPersistedValue)
                                             : "")
               + ", mLabelId=" + mLabelId
               + ", mTable=" + mTable
               + ", mDomainKey='" + mDomainKey + '\''
               + ", isActive=" + isActive(App.getAppContext())
               + ", expression=`" + getExpression(App.getAppContext()) + '\''
               + '}';
    }
}
