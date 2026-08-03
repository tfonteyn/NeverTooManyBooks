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

import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;

public class LoaneeFilter
        implements Filter {

    private static final String LOAN_FILTER =
            "EXISTS(SELECT NULL FROM " + TBL_BOOK_LOANEE.as()
            + " WHERE " + TBL_BOOK_LOANEE.dot(DBKey.LOANEE_NAME) + "='%1$s'"
            + " AND " + TBL_BOOK_LOANEE.fkMatch(TBL_BOOKS) + ')';

    @NonNull
    private final String loanee;

    /**
     * Constructor.
     *
     * @param loanee the exact name to filter on
     */
    public LoaneeFilter(@NonNull final String loanee) {
        this.loanee = loanee;
    }

    @Nullable
    @Override
    public String getExpression() {
        // We want to use the exact string, so do not normalise the value,
        // but we do need to handle single quotes as we are concatenating.
        return String.format(LOAN_FILTER, SqlEncode.singleQuotes(loanee));
    }

    @NonNull
    @Override
    public Optional<Pair<String, String>> getJoinExpression() {
        return Optional.of(new Pair<>(TBL_BOOK_LOANEE.getName(),
                                      TBL_BOOKS.leftOuterJoin(TBL_BOOK_LOANEE)));
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    @NonNull
    public String toString() {
        return "LoaneeFilter{"
               + "loanee=`" + loanee + '`'
               + '}';
    }
}
