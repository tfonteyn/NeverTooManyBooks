package com.eleybourn.bookcatalogue.booklist.filters;

import com.eleybourn.bookcatalogue.booklist.prefs.PInteger;
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
public class BooleanFilter
    extends PInteger
    implements Filter {

    private static final int PTrue = 1;
    private static final int PFalse = 0;
    private static final int PNotUsed = -1;

    private final TableDefinition table;
    private final DomainDefinition domain;

    public BooleanFilter(@StringRes final int key,
                         @Nullable final String uuid,
                         @SuppressWarnings("SameParameterValue") @NonNull final TableDefinition table,
                         @NonNull final DomainDefinition domain) {
        super(key, uuid, PNotUsed);
        this.table = table;
        this.domain = domain;
    }

    public void set(final boolean value) {
        set(value ? PTrue : PFalse);
    }

    /**
     * syntax sugar
     *
     * @return <tt>true</tt> if this filter is active
     */
    public boolean isActive() {
        return (get() == PNotUsed);
    }

    /**
     * @return Filter expression, or null if not active
     */
    @Override
    @Nullable
    public String getExpression(@Nullable final String uuid) {
        if (get() == PNotUsed) {
            return null;
        }
        return table.dot(domain) + '=' + nonPersistedValue;
    }

    @Override
    public String toString() {
        return "BooleanFilter{" +
            "table=" + table +
            ", domain=" + domain +
            ", " + super.toString() +
            "}\n";
    }
}
