package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.utils.StringList;

import java.util.List;

/**
 * an SQL WHERE clause  (column IN (a,b,c,...)
 *
 * @param <T> type the elements of the 'IN' list.
 */
public class ListOfValuesFilter<T>
        implements Filter {

    @NonNull
    private final TableDefinition table;
    @NonNull
    private final DomainDefinition domain;

    @NonNull
    private final String criteria;

    public ListOfValuesFilter(@NonNull final TableDefinition table,
                              @NonNull final DomainDefinition domain,
                              @NonNull final List<T> list) {
        this.table = table;
        this.domain = domain;

        StringList<T> au = new StringList<>();
        criteria = au.encode(list);
    }

    @Override
    @NonNull
    public String getExpression(@NonNull final String uuid) {
        return '(' + table.dot(domain) + " IN (" + criteria + "))";
    }
}
