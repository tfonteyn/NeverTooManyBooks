package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

/**
 * An SQL WHERE clause  (column LIKE '%text%').
 * Note that the LIKE usage means this is case insensitive.
 *
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
        return '(' + mTable.dot(mDomain) + " LIKE '%" + DBA.encodeString(mCriteria) + "%'" + ')';
    }
}
