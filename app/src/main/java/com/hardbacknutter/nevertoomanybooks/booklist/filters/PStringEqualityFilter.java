/*
 * @Copyright 2018-2024 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

/**
 * A persistable {@link Filter}.
 * <ul>
 * <li>The value is a {@code String}.</li>
 * <li>A {@code null} value indicates an inactive filter.</li>
 * </ul>
 */
public class PStringEqualityFilter
        implements PFilter<String> {

    @StringRes
    private final int labelResId;
    @NonNull
    private final String dbKey;
    @NonNull
    private final TableDefinition table;
    @NonNull
    private final Domain domain;

    @Nullable
    private String value;

    /** The formatter to apply on each line item. */
    @Nullable
    private Function<Context, FieldFormatter<String>> formatterSupplier;
    @Nullable
    private FieldFormatter<String> formatter;

    PStringEqualityFilter(@NonNull final String dbKey,
                          @StringRes final int labelResId,
                          @NonNull final TableDefinition table,
                          @NonNull final Domain domain) {
        this.dbKey = dbKey;
        this.labelResId = labelResId;
        this.table = table;
        this.domain = domain;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        final String dbdKey = domain.getName();
        if (ServiceLocator.getInstance().isFieldEnabled(dbdKey)) {
            return value != null;
        } else {
            return false;
        }
    }

    @NonNull
    @Override
    public String getExpression(@NonNull final Context context) {
        // We want to use the exact string, so do not normalize the value,
        // but we do need to handle single quotes as we are concatenating.
        //noinspection DataFlowIssue
        return table.dot(domain) + "='" + SqlEncode.singleQuotes(value) + '\'';
    }

    @Override
    @NonNull
    public String getDBKey() {
        return dbKey;
    }

    @Nullable
    @Override
    public String getPersistedValue() {
        if (value == null || value.isEmpty()) {
            return null;
        } else {
            return value;
        }
    }

    @Override
    public void setPersistedValue(@Nullable final String value) {
        this.value = value;
    }

    @Nullable
    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(@Nullable final String value) {
        this.value = value;
    }

    /**
     * UI support.
     */
    public void setFormatter(@Nullable final Function<Context, FieldFormatter<String>> supplier) {
        this.formatterSupplier = supplier;
    }

    /**
     * UI support.
     *
     * @param context Current context
     */
    @Nullable
    private FieldFormatter<String> getFormatter(@NonNull final Context context) {
        if (formatterSupplier != null) {
            if (formatter == null) {
                formatter = formatterSupplier.apply(context);
            }
        }
        return formatter;
    }

    @NonNull
    @Override
    public String getValueText(@NonNull final Context context,
                               @Nullable final String value) {
        if (value == null || value.isEmpty()) {
            return context.getString(R.string.bob_empty_field);
        } else {
            final FieldFormatter<String> fmt = getFormatter(context);
            if (fmt != null) {
                return fmt.format(context, value);
            } else {
                return value;
            }
        }
    }

    /**
     * UI support.
     *
     * @param context Current context
     */
    public void setValueText(@NonNull final Context context,
                             @Nullable final String value) {
        if (value == null) {
            setValue(null);
        } else {
            final FieldFormatter<String> fmt = getFormatter(context);
            if (fmt instanceof EditFieldFormatter) {
                setValue(((EditFieldFormatter<String>) fmt).extract(context, value));
            } else {
                setValue(value);
            }
        }
    }

    /**
     * UI support.
     */
    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(labelResId);
    }

    /**
     * UI support.
     */
    @LayoutRes
    @Override
    public int getPrefLayoutId() {
        return R.layout.row_edit_bookshelf_filter_string_equality;
    }
}
