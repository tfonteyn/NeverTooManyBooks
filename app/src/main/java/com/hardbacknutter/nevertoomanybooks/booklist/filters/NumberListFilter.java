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

import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * an SQL WHERE clause (column IN (a,b,c,...)
 */
public class NumberListFilter
        implements Filter {

    @NonNull
    private final TableDefinition mTable;
    @NonNull
    private final String mDomainKey;

    /** CSV list of (escaped) values. */
    @NonNull
    private final String mCriteria;

    /**
     * Constructor.
     *
     * @param table     to use by the expression
     * @param domainKey to use by the expression
     * @param list      of values
     * @param <T>       type the elements of the 'IN' list.
     */
    public <T extends Number> NumberListFilter(@NonNull final TableDefinition table,
                                               @NonNull final String domainKey,
                                               @NonNull final List<T> list) {
        mTable = table;
        mDomainKey = domainKey;
        mCriteria = list.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * Copy constructor.
     *
     * @param filter to copy from
     */
    public NumberListFilter(@NonNull final NumberListFilter filter) {
        mTable = filter.mTable;
        mDomainKey = filter.mDomainKey;
        mCriteria = filter.mCriteria;
    }

    @Override
    @NonNull
    public String getExpression(@NonNull final Context context) {
        return '(' + mTable.dot(mDomainKey) + " IN (" + mCriteria + "))";
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return true;
    }

    @Override
    @NonNull
    public String toString() {
        return "NumberListFilter{"
               + "mTable=" + mTable.getName()
               + ", mDomain=" + mDomainKey
               + ", mCriteria=`" + mCriteria + '`'
               + '}';
    }
}
