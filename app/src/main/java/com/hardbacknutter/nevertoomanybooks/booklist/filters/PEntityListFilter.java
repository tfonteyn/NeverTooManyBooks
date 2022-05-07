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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * an SQL WHERE clause (column IN (a,b,c,...)
 *
 * <ul>
 * <li>The value is a {@code Set<Long>} with the key being the entity id.</li>
 * <li>The Set is never {@code null}.</li>
 * <li>An empty Set indicates an inactive filter.</li>
 * </ul>
 */
public class PEntityListFilter<T extends Entity>
        implements PFilter<Set<Long>> {

    public static final int LAYOUT_ID = R.layout.row_edit_bookshelf_filter_entity_list;

    @SuppressWarnings("FieldNotUsedInToString")
    private final int mLabelId;
    @NonNull
    private final String mName;
    @NonNull
    private final Domain mDomain;
    @NonNull
    private final TableDefinition mTable;
    @NonNull
    private final Supplier<List<T>> mListSupplier;

    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Function<Long, Entity> mEntitySupplier;

    private final Set<Long> mValue = new HashSet<>();

    PEntityListFilter(@NonNull final String name,
                      @StringRes final int labelId,
                      @NonNull final TableDefinition table,
                      @NonNull final Domain domain,
                      @NonNull final Supplier<List<T>> listSupplier,
                      @NonNull final Function<Long, Entity> entitySupplier) {
        mName = name;
        mLabelId = labelId;
        mDomain = domain;
        mTable = table;
        mListSupplier = listSupplier;
        mEntitySupplier = entitySupplier;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        if (!DBKey.isUsed(PreferenceManager.getDefaultSharedPreferences(context),
                          mDomain.getName())) {
            return false;
        }
        return !mValue.isEmpty();
    }

    @Override
    @NonNull
    public String getExpression(@NonNull final Context context) {
        if (mValue.size() == 1) {
            return '(' + mTable.dot(mDomain) + '=' + mValue.toArray()[0] + ')';
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

    @NonNull
    public List<T> getEntities() {
        return mListSupplier.get();
    }

    @NonNull
    @Override
    public Set<Long> getValue() {
        return new HashSet<>(mValue);
    }

    @Override
    public void setValue(@Nullable final Set<Long> value) {
        mValue.clear();
        if (value != null && !value.isEmpty()) {
            mValue.addAll(value);
        }
    }

    @Override
    @NonNull
    public String getValueText(@NonNull final Context context,
                               @Nullable final Set<Long> value) {
        if (value == null || value.isEmpty()) {
            return context.getString(R.string.bob_empty_field);
        } else {
            return value.stream()
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

    @LayoutRes
    @Override
    public int getPrefLayoutId() {
        return LAYOUT_ID;
    }
}
