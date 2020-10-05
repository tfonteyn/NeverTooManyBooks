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
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntString;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

public abstract class IntStringFilter
        extends PIntString
        implements Filter<Integer> {

    static final Integer P_NOT_USED = -1;
    @NonNull
    final TableDefinition mTable;
    @NonNull
    final String mDomainKey;
    @StringRes
    private final int mLabelId;

    /**
     * Constructor.
     * Default value is {@code P_NOT_USED}.
     *
     * @param sp           Style preferences reference.
     * @param isPersistent {@code true} to have the value persisted.
     *                     {@code false} for in-memory only.
     * @param labelId      string resource id to use as a display label
     * @param key          of the preference
     * @param table        to use by the expression
     * @param domainKey    to use by the expression
     */
    IntStringFilter(@NonNull final SharedPreferences sp,
                    final boolean isPersistent,
                    @StringRes final int labelId,
                    @NonNull final String key,
                    @SuppressWarnings("SameParameterValue") @NonNull final TableDefinition table,
                    @NonNull final String domainKey) {
        super(sp, isPersistent, key, P_NOT_USED);
        mLabelId = labelId;
        mTable = table;
        mDomainKey = domainKey;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return !P_NOT_USED.equals(getValue(context))
               && DBDefinitions.isUsed(mStylePrefs, mDomainKey);
    }

    @Override
    @NonNull
    public String toString() {
        return "IntStringFilter{"
               + "mTable=" + mTable.getName()
               + ", mDomainKey=" + mDomainKey
               + ", mLabelId=" + mLabelId
               + ", " + super.toString()
               + "}\n";
    }

}
