/*
 * @Copyright 2018-2023 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;

/**
 * An SQL WHERE clause  (column LIKE '%text%').
 * Note that the LIKE usage means this is case insensitive.
 * <p>
 * If we ever use this class... sql concat with user-entered strings is a security issue.
 * MUST use PreparedStatements instead !
 */
public class WildcardFilter
        implements Filter {

    @NonNull
    private final Domain domain;
    @NonNull
    private final TableDefinition table;

    @NonNull
    private final String criteria;

    /**
     * Constructor.
     *
     * @param criteria to use by the expression
     */
    public WildcardFilter(@NonNull final TableDefinition table,
                          @NonNull final Domain domain,
                          @NonNull final String criteria) {
        this.domain = domain;
        this.table = table;
        this.criteria = criteria;
    }

    @Override
    @NonNull
    public String getExpression(@NonNull final Context context) {
        // We want to use the exact string, so do not normalize the value,
        // but we do need to handle single quotes as we are concatenating.
        return '(' + table.dot(domain)
               + " LIKE '%" + SqlEncode.singleQuotes(criteria) + "%'"
               + ')';
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return true;
    }
}
