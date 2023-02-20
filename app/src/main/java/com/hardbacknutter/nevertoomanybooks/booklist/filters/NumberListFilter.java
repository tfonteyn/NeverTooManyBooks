/*
 * @Copyright 2018-2022 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;

/**
 * A non-persistable {@link Filter}.
 * <p>
 * Represents an SQL WHERE clause (column IN (a,b,c,...)
 *
 * @param <T> type of the elements of the 'IN' list.
 */
public class NumberListFilter<T extends Number>
        implements Filter {

    @NonNull
    private final Domain domain;
    @NonNull
    private final TableDefinition table;

    @NonNull
    private final List<T> list;

    public NumberListFilter(@NonNull final TableDefinition table,
                            @NonNull final Domain domain,
                            @NonNull final T id) {
        this.domain = domain;
        this.table = table;
        this.list = List.of(id);
    }

    /**
     * Constructor.
     *
     * @param list of values
     */
    public NumberListFilter(@NonNull final TableDefinition table,
                            @NonNull final Domain domain,
                            @NonNull final List<T> list) {
        this.domain = domain;
        this.table = table;
        this.list = list;
    }

    @Override
    @NonNull
    public String getExpression(@NonNull final Context context) {
        if (list.size() == 1) {
            return '(' + table.dot(domain) + '=' + list.get(0) + ')';
        } else {
            return list.stream()
                       .map(String::valueOf)
                       .collect(Collectors.joining(
                               ",",
                               '(' + table.dot(domain) + " IN (", "))"));
        }
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return !list.isEmpty();
    }
}
