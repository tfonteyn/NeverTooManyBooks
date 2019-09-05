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

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.utils.StringList;

/**
 * an SQL WHERE clause (column IN (a,b,c,...).
 *
 * @param <T> type the elements of the 'IN' list.
 */
public class ListOfValuesFilter<T>
        implements Filter {

    @NonNull
    private final TableDefinition mTable;
    @NonNull
    private final DomainDefinition mDomain;

    /** CSV list of (escaped) values. */
    @NonNull
    private final String mCriteria;

    public ListOfValuesFilter(@NonNull final TableDefinition table,
                              @NonNull final DomainDefinition domain,
                              @NonNull final List<T> list) {
        mTable = table;
        mDomain = domain;

        mCriteria = new StringList<>(new StringList.Factory<T>() {
            @Override
            public char getListSeparator() {
                return ',';
            }

            @NonNull
            @Override
            public T decode(@NonNull final String element) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public String encode(@NonNull final T obj) {
                return obj.toString();
            }
        }).encodeList(list);
    }

    @Override
    @NonNull
    public String getExpression() {
        return '(' + mTable.dot(mDomain) + " IN (" + mCriteria + "))";
    }

    @Override
    @NonNull
    public String toString() {
        return "ListOfValuesFilter{"
               + "mTable=" + mTable.getName()
               + ", mDomain=" + mDomain
               + ", mCriteria=`" + mCriteria + '`'
               + '}';
    }
}
