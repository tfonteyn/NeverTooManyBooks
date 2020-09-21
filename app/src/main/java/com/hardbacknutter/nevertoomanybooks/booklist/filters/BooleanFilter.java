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

import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * Used for {@link androidx.preference.ListPreference} mimicking a nullable Boolean
 * and supporting an SQL WHERE clause expression.
 * <ul>
 *      <li>value==1 -> true</li>
 *      <li>value==0 -> false</li>
 *      <li>value==-1 -> do not use the filter</li>
 * </ul>
 * <p>
 * Syntax sugar for {@link IntStringFilter}.
 */
public class BooleanFilter
        extends IntStringFilter {

    /**
     * Constructor.
     *
     * @param sp           Style preferences reference.
     * @param isPersistent {@code true} to have the value persisted.
     *                     {@code false} for in-memory only.
     * @param labelId      string resource id to use as a display label
     * @param key          of the preference
     * @param table        to use by the expression
     * @param domainKey    to use by the expression
     */
    public BooleanFilter(@NonNull final SharedPreferences sp,
                         final boolean isPersistent,
                         @StringRes final int labelId,
                         @NonNull final String key,
                         @SuppressWarnings("SameParameterValue") @NonNull
                         final TableDefinition table,
                         @NonNull final String domainKey) {
        super(sp, isPersistent, labelId, key, table, domainKey);
    }

    @Override
    @Nullable
    public String getExpression(@NonNull final Context context) {
        Integer value = getValue(context);
        if (!P_NOT_USED.equals(value)) {
            return mTable.dot(mDomainKey) + '=' + value;
        }
        return null;
    }

    /**
     * syntax sugar.
     *
     * @param value to set
     */
    public void set(final boolean value) {
        set(value ? 1 : 0);
    }
}
