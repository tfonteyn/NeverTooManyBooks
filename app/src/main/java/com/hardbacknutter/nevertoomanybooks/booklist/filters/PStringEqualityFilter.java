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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

public class PStringEqualityFilter
        implements PFilter<String> {

    @SuppressWarnings("FieldNotUsedInToString")
    private final int mLabelId;
    @NonNull
    private final String mName;
    @NonNull
    private final Domain mDomain;
    @NonNull
    private final TableDefinition mTable;
    @Nullable
    private String mValue;

    PStringEqualityFilter(@NonNull final String name,
                          @StringRes final int labelId,
                          @NonNull final TableDefinition table,
                          @NonNull final Domain domain) {
        mLabelId = labelId;
        mDomain = domain;
        mTable = table;
        mName = name;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
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

    @Override
    public void setValue(@Nullable final String value) {
        mValue = value;
    }

    @NonNull
    @Override
    public String getValueText(@NonNull final Context context) {
        if (mValue == null || mValue.isEmpty()) {
            return context.getString(R.string.bob_empty_field);
        } else {
            return mValue;
        }
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }
}
