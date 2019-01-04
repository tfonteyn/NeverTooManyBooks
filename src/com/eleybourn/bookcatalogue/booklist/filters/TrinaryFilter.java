package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.properties.ListOfIntegerValuesProperty;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;

/**
 * TrinaryFilter (nullable Boolean) use ListOfIntegerValuesProperty as they need 4 internal values:
 * - true -> 1
 * - false-> 0
 * - null -> -1 -> do not use the filter
 * - use defaults -> which is one of the 3 actual values.
 */
public class TrinaryFilter extends ListOfIntegerValuesProperty implements Filter {
    public static final int FILTER_NOT_USED = -1;
    public static final int FILTER_NO = 0;
    public static final int FILTER_YES = 1;

    @NonNull
    private TableDefinition table;
    @NonNull
    private DomainDefinition domain;

    /**
     * @param list list with options. must be 4 elements, representing
     *             'true', 'false', 'null' and 'use default'
     */
    public TrinaryFilter(@StringRes final int nameResourceId,
                         @NonNull final PropertyGroup group,
                         @NonNull final Integer defaultValue,
                         @NonNull final @Size(4) ItemList<Integer> list) {
        super(nameResourceId, group, defaultValue, list);
    }

    public void setDomain(final @SuppressWarnings("SameParameterValue") @NonNull TableDefinition table,
                          @NonNull final DomainDefinition domain) {
        this.table = table;
        this.domain = domain;
    }

    @Override
    @Nullable
    public String getExpression(@NonNull final String uuid) {
        switch (getResolvedValue()) {
            case FILTER_YES:
                return table.dot(domain) + "=1";
            case FILTER_NO:
                return table.dot(domain) + "=0";
            default:
                return null;
        }
    }
}
