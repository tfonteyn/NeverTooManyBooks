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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * an SQL WHERE clause (column IN (a,b,c,...)
 */
public class PEntityListFilter
        implements PFilter<List<Long>> {

    @SuppressWarnings("FieldNotUsedInToString")
    private final int mLabelId;
    @NonNull
    private final String mName;
    @NonNull
    private final Domain mDomain;
    @NonNull
    private final TableDefinition mTable;
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Function<Long, Entity> mEntitySupplier;

    private final List<Long> mValue = new ArrayList<>();

    PEntityListFilter(@NonNull final String name,
                      @StringRes final int labelId,
                      @NonNull final TableDefinition table,
                      @NonNull final Domain domain,
                      @NonNull final Function<Long, Entity> entitySupplier) {
        mName = name;
        mLabelId = labelId;
        mDomain = domain;
        mTable = table;
        mEntitySupplier = entitySupplier;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        return !mValue.isEmpty();
    }

    @Override
    @NonNull
    public String getExpression(@NonNull final Context context) {
        if (mValue.size() == 1) {
            return '(' + mTable.dot(mDomain) + '=' + mValue.get(0) + ')';
        } else {
            return mValue.stream()
                         .map(String::valueOf)
                         .collect(Collectors.joining(
                                 ",",
                                 '(' + mTable.dot(mDomain) + " IN ("
                                 , "))"));
        }
    }

    @Override
    @NonNull
    public String getPrefName() {
        return mName;
    }

    @Nullable
    @Override
    public String getValueAsString() {
        return mValue.stream()
                     .map(String::valueOf)
                     .collect(Collectors.joining(","));
    }

    @Override
    public void setValueAsString(@Nullable final String csvString) {
        mValue.clear();
        if (csvString != null && !csvString.isEmpty()) {
            mValue.addAll(Arrays.stream(csvString.split(","))
                                .map(Long::parseLong)
                                .collect(Collectors.toList()));
        }
    }

    @Override
    public void setValue(@Nullable final List<Long> value) {
        mValue.clear();
        if (value != null && !value.isEmpty()) {
            mValue.addAll(value);
        }
    }

    @Override
    @NonNull
    public String getValueText(@NonNull final Context context) {
        if (mValue.isEmpty()) {
            return context.getString(R.string.bob_empty_field);
        } else {
            return mValue.stream()
                         .map(mEntitySupplier)
                         .map(entity -> entity.getLabel(context))
                         .collect(Collectors.joining("; "));
        }
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return context.getString(mLabelId);
    }
}
