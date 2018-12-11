package com.eleybourn.bookcatalogue.booklist.filters;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

public class WildcardFilter implements Filter {

    private TableDefinition table;
    private DomainDefinition domain;

    private String criteria;

    public WildcardFilter(final TableDefinition table, final DomainDefinition domain, final String criteria) {
        this.table = table;
        this.domain = domain;
        this.criteria = criteria;
    }

    @Override
    public String getExpression() {
        return "(" + table.dot(domain) + " LIKE '%" + CatalogueDBAdapter.encodeString(criteria) + "%'" + ")";
    }
}
