/*
 * @Copyright 2018-2021 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;

/**
 * an SQL WHERE clause (column IN (a,b,c,...)
 *
 * @param <T> type of the elements of the 'IN' list.
 */
public class NumberListFilter<T extends Number>
        implements Filter {

    @NonNull
    private final DomainExpression mDomainExpression;

    @NonNull
    private final List<T> mList;

    /**
     * Constructor.
     *
     * @param domainExpression to use by the expression
     * @param list             of values
     */
    public NumberListFilter(@NonNull final DomainExpression domainExpression,
                            @NonNull final List<T> list) {

        mDomainExpression = domainExpression;
        mList = list;
    }

    @Override
    @NonNull
    public String getExpression(@NonNull final Context context) {
        // micro? optimization...
        if (mList.size() == 1) {
            return '(' + mDomainExpression.getExpression() + '=' + mList.get(0) + ')';
        } else {
            return '(' + mDomainExpression.getExpression() + " IN ("
                   + mList.stream()
                          .map(String::valueOf)
                          .collect(Collectors.joining(","))
                   + "))";
        }
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return true;
    }

    @Override
    @NonNull
    public String toString() {
        return "NumberListFilter{"
               + "mDomainExpression=" + mDomainExpression
               + ", mList=`" + mList + '`'
               + '}';
    }
}
