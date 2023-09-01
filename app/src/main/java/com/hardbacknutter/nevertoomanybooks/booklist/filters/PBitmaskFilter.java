/*
 * @Copyright 2018-2023 HardBackNutter
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;

/**
 * A persistable {@link Filter}.
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

    /** The layout id; also used as row type. */
    public static final int LAYOUT_ID = R.layout.row_edit_bookshelf_filter_bitmask;

    @StringRes
    private final int labelResId;
    @NonNull
    private final String dbKey;
    @NonNull
    private final Domain domain;
    @NonNull
    private final TableDefinition table;
    @NonNull
    private final Supplier<Map<Integer, Integer>> mapSupplier;

    @Nullable
    private Set<Integer> value;

    PBitmaskFilter(@NonNull final String dbKey,
                   @StringRes final int labelResId,
                   @NonNull final TableDefinition table,
                   @NonNull final Domain domain,
                   @NonNull final Supplier<Map<Integer, Integer>> mapSupplier) {
        this.dbKey = dbKey;
        this.labelResId = labelResId;
        this.domain = domain;
        this.table = table;
        this.mapSupplier = mapSupplier;
    }

    @Override
    public boolean isActive(@NonNull final Context context) {
        final String dbdKey = domain.getName();
        if (ServiceLocator.getInstance().getGlobalFieldVisibility()
                          .isVisible(dbdKey).orElse(true)) {
            return value != null;
        }
        return false;

    }

    @NonNull
    @Override
    public String getExpression(@NonNull final Context context) {
        //noinspection DataFlowIssue
        if (value.isEmpty()) {
            return "(" + table.dot(domain) + "=0)";
        } else {
            return "((" + table.dot(domain) + " & " + getPersistedValue() + ")<>0)";
        }
    }

    @Override
    @NonNull
    public String getDBKey() {
        return dbKey;
    }

    @NonNull
    public Map<Integer, String> getBitsAndLabels(@NonNull final Context context) {
        final Map<Integer, String> result = new LinkedHashMap<>();
        mapSupplier.get().forEach((key, lblId) -> result.put(key, context.getString(lblId)));
        return result;
    }

    @Nullable
    @Override
    public String getPersistedValue() {
        if (value == null) {
            return null;
        } else {
            return String.valueOf(value.stream()
                                       .mapToInt(i -> i)
                                       .reduce(0, (a, b) -> a | b));
        }
    }

    @Override
    public void setPersistedValue(@Nullable final String value) {
        if (value == null || value.isEmpty()) {
            this.value = null;
        } else {
            try {
                int tmp = (int) NumberParser.toLong(value);

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
            return new HashSet<>(value);
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
            return mapSupplier.get()
                              .entrySet()
                              .stream()
                              .filter(entry -> value.contains(entry.getKey()))
                              .map(Map.Entry::getValue)
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
