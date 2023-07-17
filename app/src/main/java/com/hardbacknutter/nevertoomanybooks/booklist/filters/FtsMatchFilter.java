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

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;

public class FtsMatchFilter
        implements Filter {

    @NonNull
    private final String matchClause;

    /**
     * Constructor.
     *
     * @param matchClause the string to use for the {@code MATCH}
     */
    public FtsMatchFilter(@NonNull final String matchClause) {
        this.matchClause = matchClause;
    }

    @NonNull
    @Override
    public String getExpression(@NonNull final Context context) {
        return '(' + TBL_BOOKS.dot(DBKey.PK_ID) + " IN ("
               // fetch the ID's only
               + "SELECT " + DBKey.FTS_BOOK_ID
               + " FROM " + TBL_FTS_BOOKS.getName()
               + " WHERE " + TBL_FTS_BOOKS.getName()
               + " MATCH '" + matchClause + "')"
               + ')';
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return !matchClause.isEmpty();
    }
}
