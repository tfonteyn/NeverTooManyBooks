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

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.ArrayRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;

/**
 * A persistable {@link Filter}.
 * <ul>
 * <li>The value is a {@code Boolean}.</li>
 * <li>A {@code null} value indicates an inactive filter.</li>
 * </ul>
 */
public class PBooleanFilter
        implements PFilter<Boolean> {

    @NonNull
    protected final TableDefinition table;
    @NonNull
    protected final Domain domain;
    @SuppressWarnings("FieldNotUsedInToString")
    @StringRes
    private final int labelResId;
    @ArrayRes
    private final int acEntries;
    @NonNull
    private final String dbKey;
    @Nullable
    protected Boolean value;

    /**
     * Constructor.
     *
     * @param dbKey      the field we're filtering on
     * @param labelResId label string resource id for the name of the filter as shown to the user
     * @param acEntries  resource id for the labels array
     * @param table      the table with the field
     * @param domain     the domain representing the field
     */
    PBooleanFilter(@NonNull final String dbKey,
                   @StringRes final int labelResId,
                   @ArrayRes final int acEntries,
                   @NonNull final TableDefinition table,
                   @NonNull final Domain domain) {
        this.dbKey = dbKey;
        this.labelResId = labelResId;
        this.acEntries = acEntries;
        this.table = table;
        this.domain = domain;
    }

    @Override
    public boolean isActive() {
        final String dbdKey = domain.getName();
        if (ServiceLocator.getInstance().isFieldEnabled(dbdKey)) {
            return value != null;
        }
        return false;

    }

    @NonNull
    @Override
    public String getExpression() {
        //noinspection DataFlowIssue
        return table.dot(domain) + '=' + (value ? 1 : 0);
    }

    @NonNull
    @Override
    public Optional<TableDefinition> getLeftOuterJoinTable() {
        return Optional.of(table);
    }

    @Override
    @NonNull
    public String getDBKey() {
        return dbKey;
    }

    @NonNull
    @Override
    public String getPersistedValue() {
        return value == null ? "-1" : value ? "1" : "0";
    }

    @Override
    public void setPersistedValue(@Nullable final String value) {
        this.value = value == null ? null : "1".equals(value);
    }

    @SuppressLint("UseValueOf")
    @Nullable
    @Override
    public Boolean getValue() {
        if (value == null) {
            return null;
        } else {
            // ignore Lint warning! we want a COPY of the instance!
            //noinspection BoxingBoxedValue
            return Boolean.valueOf(value);
        }
    }

    @Override
    public void setValue(@NonNull final Context context,
                         @Nullable final Boolean value) {
        this.value = value;
    }

    @Override
    @NonNull
    public String getValueText(@NonNull final Context context,
                               @Nullable final Boolean value) {
        final CharSequence[] textArray = context.getResources().getTextArray(acEntries);
        if (value == null) {
            return textArray[0].toString();
        } else {
            return textArray[value ? 2 : 1].toString();
        }
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(labelResId);
    }

    @LayoutRes
    @Override
    public int getPrefLayoutId() {
        return R.layout.row_edit_bookshelf_filter_boolean;
    }

    @Override
    @NonNull
    public String toString() {
        return "PBooleanFilter{"
               + "dbKey=" + dbKey
               + ", table=" + table.getName()
               + ", domain=" + domain.getName()
               + ", acEntries=" + acEntries
               + ", value=" + value
               + '}';
    }
}
