package com.eleybourn.bookcatalogue.booklist.filters;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

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

    @StringRes
    private final int mLabelId;

    private final TableDefinition mTable;
    private final DomainDefinition mDomain;

    public BooleanFilter(@StringRes final int labelId,
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
    public String getLabel(@NonNull final Resources resources) {
        return resources.getString(mLabelId);
    }

    public void set(final boolean value) {
        set(value ? P_TRUE : P_FALSE);
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
        return "BooleanFilter{"
                + "table=" + mTable
                + ", domain=" + mDomain
                + ", " + super.toString()
                + "}\n";
    }
}
