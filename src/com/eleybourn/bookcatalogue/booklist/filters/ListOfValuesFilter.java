package com.eleybourn.bookcatalogue.booklist.filters;

import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.utils.StringList;

import java.util.List;

public class ListOfValuesFilter<T> implements Filter {

    private TableDefinition table;
    private DomainDefinition domain;

    private String criteria;

    public ListOfValuesFilter(final TableDefinition table, final DomainDefinition domain, final List<T> list) {
        this.table = table;
        this.domain = domain;

        StringList<T> au = new StringList<>();
        criteria = au.encode(list);
    }

    @Override
    public String getExpression() {
        return "(" + table.dot(domain) + " IN (" + criteria + "))";
    }
}
