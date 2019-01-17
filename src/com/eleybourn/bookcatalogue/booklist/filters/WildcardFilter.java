package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

/**
 * an SQL WHERE clause  (column LIKE '%text%').
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
    public String getExpression(@Nullable final String uuid) {
        return '(' + mTable.dot(mDomain)
                + " LIKE '%" + DBA.encodeString(mCriteria) + "%'" + ')';
    }
}
