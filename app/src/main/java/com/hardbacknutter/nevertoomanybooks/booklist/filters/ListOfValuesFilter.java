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
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.utils.StringList;

/**
 * an SQL WHERE clause (column IN (a,b,c,...).
 *
 * @param <T> type the elements of the 'IN' list.
 */
public class ListOfValuesFilter<T>
        implements Filter<String> {

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
     * @param table  to use by the expression
     * @param domainKey to use by the expression
     * @param list   of values
     */
    public ListOfValuesFilter(@NonNull final TableDefinition table,
                              @NonNull final String domainKey,
                              @NonNull final Iterable<T> list) {
        mTable = table;
        mDomainKey = domainKey;

        mCriteria = new StringList<>(new StringList.Factory<T>() {
            @Override
            public char getElementSeparator() {
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
    public String getExpression(@NonNull final Context context) {
        return '(' + mTable.dot(mDomainKey) + " IN (" + mCriteria + "))";
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return DBDefinitions.isUsed(prefs, mDomainKey);
    }

    @Override
    @NonNull
    public String getValue(@NonNull final Context context) {
        return mCriteria;
    }

    @Override
    @NonNull
    public String toString() {
        return "ListOfValuesFilter{"
               + "mTable=" + mTable.getName()
               + ", mDomain=" + mDomainKey
               + ", mCriteria=`" + mCriteria + '`'
               + '}';
    }
}
