package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

/**
 * an SQL WHERE clause  (column LIKE '%text%')
 * <p>
 * FIXME: bad stopgap... use PreparedStatements instead !
 */
public class WildcardFilter
        implements Filter {

    private final TableDefinition table;
    private final DomainDefinition domain;

    private final String criteria;

    public WildcardFilter(@NonNull final TableDefinition table,
                          @NonNull final DomainDefinition domain,
                          @NonNull final String criteria) {
        this.table = table;
        this.domain = domain;
        this.criteria = criteria;
    }

    @Override
    public String getExpression(@Nullable final String uuid) {
        return '(' + table.dot(domain) +
                " LIKE '%" + CatalogueDBAdapter.encodeString(criteria) + "%'" + ')';
    }
}
