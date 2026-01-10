/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;

/**
 * An SQL WHERE clause  (column LIKE '%text%').
 * Note that the LIKE usage means this is case-insensitive.
 * <p>
 * Yes, this is a security risk. We ARE aware that concatenation with a user-entered
 * value should never be done. Given the nature of this app, oh well...
 * ... if a user deliberately wants to destroy their data, let them :)
 */
public class WildcardFilter
        implements Filter {

    @NonNull
    private final TableDefinition table;
    @NonNull
    private final Domain domain;
    @Nullable
    private final Pair<String, String> join;

    @NonNull
    private final String criteria;

    /**
     * Constructor.
     *
     * @param table    the table with the field
     * @param domain   the domain representing the field
     * @param criteria to use by the expression
     */
    public WildcardFilter(@NonNull final TableDefinition table,
                          @NonNull final Domain domain,
                          @NonNull final String criteria) {
        this.domain = domain;
        this.table = table;
        this.criteria = criteria;

        if (table == TBL_BOOKS) {
            join = null;
        } else {
            join = new Pair<>(table.getName(), TBL_BOOKS.leftOuterJoin(table));
        }
    }

    @Override
    @NonNull
    public String getExpression() {
        // We want to use the exact string, so do not normalise the value,
        // but we do need to handle single quotes as we are concatenating.
        return table.dot(domain) + " LIKE '%" + SqlEncode.singleQuotes(criteria) + "%'";
    }

    @NonNull
    @Override
    public Optional<Pair<String, String>> getJoinExpression() {
        return join == null ? Optional.empty() : Optional.of(join);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    @NonNull
    public String toString() {
        return "WildcardFilter{"
               + "table=" + table.getName()
               + ", domain=" + domain.getName()
               + ", join=" + join
               + ", criteria='" + criteria + '\''
               + '}';
    }
}
