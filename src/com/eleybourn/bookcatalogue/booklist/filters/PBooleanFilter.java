package com.eleybourn.bookcatalogue.booklist.filters;

import com.eleybourn.bookcatalogue.booklist.filters.Filter;
import com.eleybourn.bookcatalogue.booklist.prefs.PInt;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Used for {@link androidx.preference.ListPreference} mimicking a nullable Boolean
 * and supporting an SQL WHERE clause expression
 *
 * value==1 -> true
 * value==0 -> false
 * value==-1 -> do not use the filter
 */
public class PBooleanFilter extends PInt
    implements Filter {
    private static final int PTrue = 1;
    private static final int PFalse = 0;
    private static final int PNotUsed = -1;

    private final TableDefinition table;
    private final DomainDefinition domain;

    public PBooleanFilter(@StringRes final int key,
                          @SuppressWarnings("SameParameterValue") @NonNull final TableDefinition table,
                          @NonNull final DomainDefinition domain) {
        super(key, PNotUsed);
        this.table = table;
        this.domain = domain;
    }

    public void set(@NonNull final String uuid, final boolean value) {
        set(uuid, value ? PTrue : PFalse);
    }

    @Override
    @Nullable
    public String getExpression(@Nullable final String uuid) {
        if (get(uuid) == PNotUsed) {
            return null;
        }
        return table.dot(domain) + '=' + nonPersistedValue;
    }

    @Override
    public String toString() {
        return "PBooleanFilter{" +
                "table=" + table +
                ", domain=" + domain +
                ", " + super.toString() +
                "}\n";
    }
}
