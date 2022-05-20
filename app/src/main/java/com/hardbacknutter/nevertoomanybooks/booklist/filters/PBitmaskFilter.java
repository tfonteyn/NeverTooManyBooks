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

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * <ul>
 * <li>The value is a {@code Set<Integer>} with the key being a single bit.</li>
 * <li>A {@code null} value indicates an inactive filter.</li>
 * <li>An empty Set represents a bitmask value of {@code 0} which in turn means
 *     that the filter limits the results to <strong>ONLY</strong> rows with
 *     the value {@code 0}.</li>
 * <li>If the value has <strong>at least one bit set</strong>
 *      (i.e. at least one element in the Set),
 *      the filter looks for values having <strong>those</strong> bits set;
 *      other bits are <strong>ignored</strong></li>
 * </ul>
 * Note 1: we store ONE bit in an Integer - memory waste is huge! But considering we only
 * have something like 8 bits maximum... not a real problem for now.<br/>
 * Note 2: maybe we should wrap a BitSet is a custom (real) Set.
 */
public class PBitmaskFilter
        implements PFilter<Set<Integer>> {

    public static final int LAYOUT_ID = R.layout.row_edit_bookshelf_filter_bitmask;

    @StringRes
    private final int labelResId;
    @NonNull
    private final String name;
    @NonNull
    private final Domain domain;
    @NonNull
    private final TableDefinition table;
    // key: int with a single bit set; value: a string resId with the user label
    @NonNull
    private final Supplier<Map<Integer, Integer>> bitsAndLabels;

    @Nullable
    private Set<Integer> value;

    PBitmaskFilter(@NonNull final String name,
                   @StringRes final int labelResId,
                   @NonNull final TableDefinition table,
                   @NonNull final Domain domain,
                   @NonNull final Supplier<Map<Integer, Integer>> bitsAndLabelSupplier) {
        this.name = name;
        this.labelResId = labelResId;
        this.domain = domain;
        this.table = table;
        bitsAndLabels = bitsAndLabelSupplier;
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
        if (value.isEmpty()) {
            return "(" + table.dot(domain) + "=0)";
        } else {
            return "((" + table.dot(domain) + " & " + getValueAsString() + ")<>0)";
        }
    }

    @Override
    @NonNull
    public String getPrefName() {
        return name;
    }

    @NonNull
    public Map<Integer, Integer> getBitsAndLabels() {
        return Collections.unmodifiableMap(bitsAndLabels.get());
    }

    @Nullable
    @Override
    public String getValueAsString() {
        if (value == null) {
            return null;
        } else {
            return String.valueOf(value.stream()
                                       .mapToInt(i -> i)
                                       .reduce(0, (a, b) -> a | b));
        }
    }

    @Override
    public void setValueAsString(@Nullable final String value) {
        if (value == null || value.isEmpty()) {
            this.value = null;
        } else {
            try {
                int tmp = (int) ParseUtils.toLong(value);

                this.value = new HashSet<>();
                int bit = 1;
                while (tmp != 0) {
                    if ((tmp & 1) == 1) {
                        this.value.add(bit);
                    }
                    bit *= 2;
                    // unsigned shift
                    tmp = tmp >>> 1;
                }
            } catch (@NonNull final NumberFormatException e) {
                this.value = null;
            }
        }
    }

    @Nullable
    @Override
    public Set<Integer> getValue() {
        if (value == null) {
            return null;
        } else {
            return Set.copyOf(value);
        }
    }

    @Override
    public void setValue(@Nullable final Set<Integer> value) {
        this.value = value;
    }

    @Override
    @NonNull
    public String getValueText(@NonNull final Context context,
                               @Nullable final Set<Integer> value) {
        if (value == null) {
            return context.getString(R.string.bob_empty_field);
        } else if (value.isEmpty()) {
            return context.getString(R.string.btn_all_books);
        } else {
            final Map<Integer, Integer> blMap = bitsAndLabels.get();
            //noinspection ConstantConditions
            return blMap.keySet()
                        .stream()
                        .filter(value::contains)
                        .map(blMap::get)
                        .map(context::getString)
                        .collect(Collectors.joining("; "));
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
