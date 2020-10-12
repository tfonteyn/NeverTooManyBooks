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
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

public class BitmaskFilter
        extends PBitmask
        implements Filter<Integer> {

    /** See {@link com.hardbacknutter.nevertoomanybooks.widgets.BitmaskPreference}. */
    private static final String ACTIVE = ".active";

    @StringRes
    private final int mLabelId;

    @NonNull
    private final TableDefinition mTable;
    @NonNull
    private final String mDomainKey;

    /**
     * Constructor.
     * Default value is {@code 0}, i.e. no bits set.
     *
     * @param sp           Style preferences reference.
     * @param isPersistent {@code true} to have the value persisted.
     *                     {@code false} for in-memory only.
     * @param labelId      string resource id to use as a display label
     * @param key          of the preference
     * @param defValue     in memory default
     * @param mask         valid values bitmask
     * @param table        to use by the expression
     * @param domainKey    to use by the expression
     */
    public BitmaskFilter(final SharedPreferences sp,
                         final boolean isPersistent,
                         @StringRes final int labelId,
                         @NonNull final String key,
                         @NonNull final Integer defValue,
                         @NonNull final Integer mask,
                         @SuppressWarnings("SameParameterValue")
                         @NonNull final TableDefinition table,
                         @NonNull final String domainKey) {
        super(sp, isPersistent, key, defValue, mask);
        mLabelId = labelId;
        mTable = table;
        mDomainKey = domainKey;
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
        return mStylePrefs.getBoolean(getKey() + ACTIVE, false)
               && DBDefinitions.isUsed(mStylePrefs, mDomainKey);
    }

    @Override
    @NonNull
    public String toString() {
        return "BitmaskFilter{"
               + "table=" + mTable.getName()
               + ", mDomainKey=" + mDomainKey
               + ", mLabelId=" + mLabelId
               + ", isActive=" + isActive(App.getAppContext())
               + ", " + super.toString()
               + "}\n";
    }
}
