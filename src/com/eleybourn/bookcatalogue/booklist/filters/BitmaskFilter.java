package com.eleybourn.bookcatalogue.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.booklist.prefs.PBitmask;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

public class BitmaskFilter
        extends PBitmask
        implements Filter<Integer> {

    /** Convenience definition. */
    private static final Integer P_NOT_USED = 0;

    @StringRes
    private final int mLabelId;

    private final TableDefinition mTable;
    private final DomainDefinition mDomain;

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if no global default.
     *
     * @param key          of the preference
     * @param uuid         the style id
     * @param isPersistent {@code true} to have the value persisted.
     *                     {@code false} for in-memory only.
     */
    public BitmaskFilter(@StringRes final int labelId,
                         @NonNull final String key,
                         @NonNull final String uuid,
                         final boolean isPersistent,
                         @SuppressWarnings("SameParameterValue") @NonNull final TableDefinition table,
                         @NonNull final DomainDefinition domain) {
        super(key, uuid, isPersistent, P_NOT_USED);
        mLabelId = labelId;
        mTable = table;
        mDomain = domain;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }


    /**
     * syntax sugar.
     *
     * @return {@code true} if this filter is active
     */
    @Override
    public boolean isActive() {
        return !P_NOT_USED.equals(get());
    }

    /**
     * @return Filter expression, or {@code null} if not active
     */
    @Override
    @Nullable
    public String getExpression() {
        Integer value = get();
        if (!P_NOT_USED.equals(value)) {
            return mTable.dot(mDomain) + '=' + value;
        }
        return null;
    }

    @Override
    @NonNull
    public String toString() {
        return "BitmaskFilter{"
                + "table=" + mTable
                + ", domain=" + mDomain
                + ", " + super.toString()
                + "}\n";
    }
}
