package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.utils.StringList;

import java.util.List;

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
            @NonNull
            @Override
            public T decode(@NonNull final String element) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public String encode(@NonNull final T element) {
                return element.toString();
            }
        }).encode(list);
    }

    @Override
    @NonNull
    public String getExpression(@Nullable final String uuid) {
        return '(' + mTable.dot(mDomain) + " IN (" + mCriteria + "))";
    }
}
