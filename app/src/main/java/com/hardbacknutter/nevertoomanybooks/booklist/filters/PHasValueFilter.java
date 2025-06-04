/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;

/**
 * A persistable {@link Filter}.
 * <ul>
 * <li>Variant of {@link PBooleanFilter} with the expression testing the presence of a value</li>
 * <li>The value is a {@code Boolean}.</li>
 * <li>A {@code null} value indicates an inactive filter.</li>
 * </ul>
 */
public class PHasValueFilter
        extends PBooleanFilter {

    /**
     * Constructor.
     *
     * @param dbKey     the field we're filtering on
     * @param table     the table with the field
     * @param domain    the domain representing the field
     * @param acEntries resource id for the labels array
     */
    PHasValueFilter(@NonNull final String dbKey,
                    @NonNull final TableDefinition table,
                    @NonNull final Domain domain,
                    @ArrayRes final int acEntries) {
        super(dbKey, acEntries, table, domain);
    }

    @NonNull
    @Override
    public String getExpression() {
        final String column = table.dot(domain);
        if (Boolean.TRUE.equals(value)) {
            return '(' + column + " IS NOT NULL AND " + column + "<>'')";
        } else {
            return '(' + column + " IS NULL OR " + column + "='')";
        }
    }
}
