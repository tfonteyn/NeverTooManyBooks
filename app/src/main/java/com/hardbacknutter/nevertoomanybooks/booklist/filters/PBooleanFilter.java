/*
 * @Copyright 2018-2022 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
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

    public static final int LAYOUT_ID = R.layout.row_edit_bookshelf_filter_boolean;

    @StringRes
    private final int labelResId;
    @ArrayRes
    private final int acEntries;

    @NonNull
    protected final Domain domain;
    @NonNull
    protected final TableDefinition table;
    @NonNull
    private final String dbKey;
    @Nullable
    protected Boolean value;

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
    public boolean isActive(@NonNull final Context context) {
        if (!GlobalFieldVisibility.isUsed(domain.getName())) {
            return false;
        }

        return value != null;
    }

    @NonNull
    @Override
    public String getExpression(@NonNull final Context context) {
        //noinspection ConstantConditions
        return table.dot(domain) + '=' + (value ? 1 : 0);
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
    public void setValue(@Nullable final Boolean value) {
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
        return LAYOUT_ID;
    }

}
