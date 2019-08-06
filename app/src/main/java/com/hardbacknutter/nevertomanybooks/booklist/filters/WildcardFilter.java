/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.booklist.filters;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertomanybooks.database.definitions.TableDefinition;

/**
 * An SQL WHERE clause  (column LIKE '%text%').
 * Note that the LIKE usage means this is case insensitive.
 * <p>
 * FIXME: bad stopgap... use PreparedStatements instead !
 */
public class WildcardFilter
        implements Filter {

    private final TableDefinition mTable;
    private final DomainDefinition mDomain;

    private final String mCriteria;

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
               + "mTable=" + mTable
               + ", mDomain=" + mDomain
               + ", mCriteria=`" + mCriteria + '`'
               + '}';
    }
}
