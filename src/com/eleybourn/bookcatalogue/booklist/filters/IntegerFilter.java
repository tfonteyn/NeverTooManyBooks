package com.eleybourn.bookcatalogue.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.booklist.prefs.PInteger;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;

/**
 * Not directly used for now.
 * Split of from {@link BooleanFilter} making the later a subclass of this one.
 */
public class IntegerFilter
        extends PInteger
        implements Filter<Integer> {

    public static final Integer P_NOT_USED = -1;

    @StringRes
    private final int mLabelId;

    private final TableDefinition mTable;
    private final DomainDefinition mDomain;

    IntegerFilter(@StringRes final int labelId,
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
        return "IntegerFilter{"
                + "table=" + mTable
                + ", domain=" + mDomain
                + ", " + super.toString()
                + "}\n";
    }

}
