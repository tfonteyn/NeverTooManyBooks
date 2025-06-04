/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.util.Pair;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.EditFieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;

/**
 * A persistable {@link Filter}.
 * <ul>
 * <li>The value is a {@code String}.</li>
 * <li>A {@code null} value indicates an inactive filter.</li>
 * <li>Persisted in the database as a {@code String}.</li>
 * </ul>
 */
public class PStringEqualityFilter
        implements PFilter<String> {

    @NonNull
    private final String dbKey;
    @NonNull
    private final TableDefinition table;
    @NonNull
    private final Domain domain;
    @Nullable
    private final Pair<String, String> join;

    private boolean wildcards;

    @Nullable
    private String value;

    /** The formatter to apply on each line item. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private Function<Context, FieldFormatter<String>> formatterSupplier;
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private FieldFormatter<String> formatter;

    /**
     * Constructor.
     *
     * @param dbKey  the field we're filtering on
     * @param table  the table with the field
     * @param domain the domain representing the field
     */
    PStringEqualityFilter(@NonNull final String dbKey,
                          @NonNull final TableDefinition table,
                          @NonNull final Domain domain) {
        this.dbKey = dbKey;
        this.table = table;
        this.domain = domain;

        if (table == TBL_BOOKS) {
            join = null;
        } else {
            join = new Pair<>(table.getName(), TBL_BOOKS.leftOuterJoin(table));
        }
    }

    /**
     * Constructor.
     *
     * @param dbKey          the field we're filtering on
     * @param table          the table with the field
     * @param domain         the domain representing the field
     * @param joinExpression custom JOIN expression, see {@link Filter#getJoinExpression()}
     */
    PStringEqualityFilter(@NonNull final String dbKey,
                          @NonNull final TableDefinition table,
                          @NonNull final Domain domain,
                          @NonNull final Pair<String, String> joinExpression) {
        this.dbKey = dbKey;
        this.table = table;
        this.domain = domain;

        join = joinExpression;
    }

    /**
     * Use wildcards to search. The Expression will be surrounded with '%'.
     * The user can embed '%' and '?' as wanted.
     * Note that enabling this also enables case-insensitive searches.
     * <p>
     * TODO: maybe allow the user to use '*' instead of '%'  but handle that in UI code
     *
     * @param wildcards flag
     *
     * @return {@code this} for chaining
     */
    @SuppressWarnings("SameParameterValue")
    @NonNull
    PStringEqualityFilter setWildcards(final boolean wildcards) {
        this.wildcards = wildcards;
        return this;
    }

    @Override
    public boolean isActive() {
        if (ServiceLocator.getInstance().isFieldEnabled(domain.getName())) {
            return value != null;
        } else {
            return false;
        }
    }

    @NonNull
    @Override
    public String getExpression() {
        // We want to use the exact string, so do not normalize the value,
        // but we do need to handle single quotes as we are concatenating.
        //noinspection DataFlowIssue
        final String s = SqlEncode.singleQuotes(value);

        // Yes, this is a security risk. We ARE aware that concatenation with a user-entered
        // value should never be done. Given the nature of this app, oh well...
        // ... if a user deliberately wants to destroy their data, let them :)
        if (wildcards) {
            return table.dot(domain) + " LIKE '%" + s + "%'";
        } else {
            return table.dot(domain) + "='" + s + '\'';
        }
    }

    @NonNull
    @Override
    public Optional<Pair<String, String>> getJoinExpression() {
        return join == null ? Optional.empty() : Optional.of(join);
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

    /**
     * Set the typed value.
     *
     * @param context   Current context
     * @param valueText to set; if there is a formatter set, an attempt
     *                  will be made to extract the value,
     *                  otherwise it's set as-is.
     */
    @Override
    public void setValue(@NonNull final Context context,
                         @Nullable final String valueText) {
        if (valueText == null) {
            this.value = null;
        } else {
            final FieldFormatter<String> fmt = getFormatter(context);
            if (fmt instanceof EditFieldFormatter) {
                this.value = ((EditFieldFormatter<String>) fmt).extract(context, valueText);
            } else {
                this.value = valueText;
            }
        }
    }

    /**
     * UI support.
     *
     * @param supplier optional formatter supplier
     *
     * @return {@code this} for chaining
     */
    @NonNull
    public PStringEqualityFilter setFormatter(
            @Nullable final Function<Context, FieldFormatter<String>> supplier) {
        this.formatterSupplier = supplier;
        return this;
    }

    /**
     * UI support.
     *
     * @param context Current context
     *
     * @return the optional formatter
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

    @Nullable
    @Override
    public CharSequence getValueText(@NonNull final Context context,
                                     @Nullable final String value) {
        if (value == null || value.isBlank()) {
            return null;
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
     */
    @LayoutRes
    @Override
    public int getPrefLayoutId() {
        return R.layout.row_edit_bookshelf_filter_string_equality;
    }

    @Override
    @NonNull
    public String toString() {
        return "PStringEqualityFilter{"
               + "dbKey=" + dbKey
               + ", table=" + table.getName()
               + ", domain=" + domain.getName()
               + ", wildcards=" + wildcards
               + ", join=" + join
               + ", value='" + value + '\''
               + '}';
    }
}
