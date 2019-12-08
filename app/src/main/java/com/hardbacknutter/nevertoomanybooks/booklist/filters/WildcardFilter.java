/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * An SQL WHERE clause  (column LIKE '%text%').
 * Note that the LIKE usage means this is case insensitive.
 * <p>
 * TODO: bad stopgap... use PreparedStatements instead !
 */
public class WildcardFilter
        implements Filter {

    private final TableDefinition mTable;
    private final DomainDefinition mDomain;

    private final String mCriteria;

    /**
     * Constructor.
     *
     * @param table    to use by the expression
     * @param domain   to use by the expression
     * @param criteria to use by the expression
     */
    public WildcardFilter(@NonNull final TableDefinition table,
                          @NonNull final DomainDefinition domain,
                          @NonNull final String criteria) {
        mTable = table;
        mDomain = domain;
        mCriteria = criteria;
    }

    @Override
    @NonNull
    public String getExpression() {
        return '(' + mTable.dot(mDomain) + " LIKE '%" + DAO.encodeString(mCriteria) + "%'" + ')';
    }

    @Override
    @NonNull
    public String toString() {
        return "WildcardFilter{"
               + "mTable=" + mTable.getName()
               + ", mDomain=" + mDomain
               + ", mCriteria=`" + mCriteria + '`'
               + '}';
    }
}
