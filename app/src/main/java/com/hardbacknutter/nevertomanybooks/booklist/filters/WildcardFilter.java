package com.hardbacknutter.nevertomanybooks.booklist.filters;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertomanybooks.database.definitions.TableDefinition;

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
