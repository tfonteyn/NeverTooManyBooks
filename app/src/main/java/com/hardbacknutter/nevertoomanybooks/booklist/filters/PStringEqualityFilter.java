/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * <ul>
 * <li>The value is a {@code String}.</li>
 * <li>A {@code null} value indicates an inactive filter.</li>
 * </ul>
 */
public class PStringEqualityFilter
        implements PFilter<String> {

    public static final int LAYOUT_ID = R.layout.row_edit_bookshelf_filter_string_equality;

    @StringRes
    private final int labelResId;
    @NonNull
    private final String name;
    @Nullable
    private final Supplier<ArrayList<String>> listSupplier;
    @NonNull
    private final Domain domain;
    @NonNull
    private final TableDefinition table;
    @Nullable
    private String value;

    PStringEqualityFilter(@NonNull final String name,
                          @StringRes final int labelId,
                          @NonNull final TableDefinition table,
                          @NonNull final Domain domain,
                          @Nullable final Supplier<ArrayList<String>> listSupplier) {
        labelResId = labelId;
        this.domain = domain;
        this.table = table;
        this.name = name;
        this.listSupplier = listSupplier;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        if (!DBKey.isUsed(PreferenceManager.getDefaultSharedPreferences(context),
                          domain.getName())) {
            return false;
        }
        return value != null;
    }

    @NonNull
    @Override
    public String getExpression(@NonNull final Context context) {
        //noinspection ConstantConditions
        return table.dot(domain) + "='" + SqlEncode.string(value) + '\'';
    }

    @Override
    @NonNull
    public String getPrefName() {
        return name;
    }

    @Nullable
    @Override
    public String getValueAsString() {
        if (value == null || value.isEmpty()) {
            return null;
        } else {
            return value;
        }
    }

    @Override
    public void setValueAsString(@Nullable final String value) {
        this.value = value;
    }

    @Nullable
    public ExtArrayAdapter<String> createListAdapter(@NonNull final Context context) {
        if (listSupplier != null) {
            return new ExtArrayAdapter<>(context, R.layout.popup_dropdown_menu_item,
                                         ExtArrayAdapter.FilterType.Diacritic,
                                         listSupplier.get());
        }
        return null;
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

    @NonNull
    @Override
    public String getValueText(@NonNull final Context context,
                               @Nullable final String value) {
        if (value == null || value.isEmpty()) {
            return context.getString(R.string.bob_empty_field);
        } else {
            return value;
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
