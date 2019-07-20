package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

/**
 * Used for {@link androidx.preference.ListPreference} mimicking a nullable Boolean
 * and supporting an SQL WHERE clause expression.
 * <ul>
 * <li>value==1 -> true</li>
 * <li>value==0 -> false</li>
 * <li>value==-1 -> do not use the filter</li>
 * </ul>
 *
 * Syntax sugar for {@link IntegerFilter}.
 */
public class BooleanFilter
        extends IntegerFilter {

    public static final Integer P_TRUE = 1;
    public static final Integer P_FALSE = 0;

    public BooleanFilter(@StringRes final int labelId,
                         @NonNull final String key,
                         @NonNull final String uuid,
                         final boolean isPersistent,
                         @SuppressWarnings("SameParameterValue") @NonNull final TableDefinition table,
                         @NonNull final DomainDefinition domain) {
        super(labelId, key, uuid, isPersistent, table, domain);
    }

    public void set(final boolean value) {
        set(value ? P_TRUE : P_FALSE);
    }
}
