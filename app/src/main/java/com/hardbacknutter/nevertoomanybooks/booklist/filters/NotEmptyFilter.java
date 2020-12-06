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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

public class NotEmptyFilter
        extends IntStringFilter {

    /**
     * Constructor.
     *
     * @param labelId   string resource id to use as a display label
     * @param key       preference key
     * @param table     to use by the expression
     * @param domainKey to use by the expression
     */
    public NotEmptyFilter(@NonNull final BooklistStyle style,
                          @StringRes final int labelId,
                          @NonNull final String key,
                          @SuppressWarnings("SameParameterValue") @NonNull
                          final TableDefinition table,
                          @NonNull final String domainKey) {
        super(style, labelId, key, table, domainKey);
    }

    @Override
    @Nullable
    public String getExpression(@NonNull final Context context) {
        final Integer value = getValue(context);
        if (!P_NOT_USED.equals(value)) {
            if (value == 0) {
                return "((" + mTable.dot(mDomainKey) + " IS NULL)"
                       + " OR (" + mTable.dot(mDomainKey) + "=''))";
            } else {
                return "((" + mTable.dot(mDomainKey) + " IS NOT NULL)"
                       + " AND (" + mTable.dot(mDomainKey) + "<>''))";
            }
        }
        return null;
    }
}
