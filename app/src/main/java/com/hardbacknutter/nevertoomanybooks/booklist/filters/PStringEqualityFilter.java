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

    @SuppressWarnings("FieldNotUsedInToString")
    private final int mLabelId;
    @NonNull
    private final String mName;
    @Nullable
    private final Supplier<ArrayList<String>> mListSupplier;
    @NonNull
    private final Domain mDomain;
    @NonNull
    private final TableDefinition mTable;
    @Nullable
    private String mValue;
    @Nullable
    private ExtArrayAdapter<String> mAdapter;

    PStringEqualityFilter(@NonNull final String name,
                          @StringRes final int labelId,
                          @NonNull final TableDefinition table,
                          @NonNull final Domain domain,
                          @Nullable final Supplier<ArrayList<String>> listSupplier) {
        mLabelId = labelId;
        mDomain = domain;
        mTable = table;
        mName = name;
        mListSupplier = listSupplier;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        if (!DBKey.isUsed(PreferenceManager.getDefaultSharedPreferences(context),
                          mDomain.getName())) {
            return false;
        }
        return mValue != null;
    }

    @NonNull
    @Override
    public String getExpression(@NonNull final Context context) {
        //noinspection ConstantConditions
        return mTable.dot(mDomain) + "='" + SqlEncode.string(mValue) + '\'';
    }

    @Override
    @NonNull
    public String getPrefName() {
        return mName;
    }

    @Nullable
    @Override
    public String getValueAsString() {
        if (mValue == null || mValue.isEmpty()) {
            return null;
        } else {
            return mValue;
        }
    }

    @Override
    public void setValueAsString(@Nullable final String value) {
        mValue = value;
    }

    @Nullable
    public ExtArrayAdapter<String> getListAdapter(@NonNull final Context context) {
        if (mAdapter == null && mListSupplier != null) {
            mAdapter = new ExtArrayAdapter<>(context, R.layout.popup_dropdown_menu_item,
                                             ExtArrayAdapter.FilterType.Diacritic,
                                             mListSupplier.get());
        }
        return mAdapter;
    }

    @Nullable
    @Override
    public String getValue() {
        return mValue;
    }

    @Override
    public void setValue(@Nullable final String value) {
        mValue = value;
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
        return context.getString(mLabelId);
    }

    @LayoutRes
    @Override
    public int getPrefLayoutId() {
        return LAYOUT_ID;
    }
}
