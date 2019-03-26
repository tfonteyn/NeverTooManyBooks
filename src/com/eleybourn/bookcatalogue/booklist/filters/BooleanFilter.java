package com.eleybourn.bookcatalogue.booklist.filters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.booklist.prefs.PInteger;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

/**
 * Used for {@link androidx.preference.ListPreference} mimicking a nullable Boolean
 * and supporting an SQL WHERE clause expression.
 * <p>
 * value==1 -> true
 * value==0 -> false
 * value==-1 -> do not use the filter
 */
public class BooleanFilter
        extends PInteger
        implements Filter {

    private static final Integer P_TRUE = 1;
    private static final Integer P_FALSE = 0;
    private static final Integer P_NOT_USED = -1;

    private final TableDefinition mTable;
    private final DomainDefinition mDomain;

    public BooleanFilter(@NonNull final String key,
                         @NonNull final String uuid,
                         @SuppressWarnings("SameParameterValue") @NonNull final TableDefinition table,
                         @NonNull final DomainDefinition domain) {
        super(key, uuid, P_NOT_USED);
        mTable = table;
        mDomain = domain;
    }

    public void set(final boolean value) {
        set(value ? P_TRUE : P_FALSE);
    }

    /**
     * syntax sugar.
     *
     * @return <tt>true</tt> if this filter is active
     */
    public boolean isActive() {
        return P_NOT_USED.equals(get());
    }

    /**
     * @return Filter expression, or null if not active
     */
    @Override
    @Nullable
    public String getExpression(@Nullable final String uuid) {
        Integer value = get();
        if (P_NOT_USED.equals(value)) {
            return null;
        }
        return mTable.dot(mDomain) + '=' + value;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooleanFilter{"
                + "table=" + mTable
                + ", domain=" + mDomain
                + ", " + super.toString()
                + "}\n";
    }
}
