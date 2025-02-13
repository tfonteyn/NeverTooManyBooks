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

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * A persistable {@link Filter}.
 * <p>
 * Represents an SQL WHERE clause (column IN (a,b,c,...)
 *
 * <ul>
 * <li>The value is a {@code Set<Long>} with the key being the entity id.</li>
 * <li>The Set is never {@code null}.</li>
 * <li>An empty Set indicates an inactive filter.</li>
 * </ul>
 *
 * <strong>IMPORTANT</strong>: there <strong>may</strong> be invalid/deleted ids
 * in this set. An example is a filter based on DBKey.TAG when a tag gets deleted.
 * The code is annotated when needed.
 * See the BookshelfDao validation methods where the cleanup is done.
 *
 * @param <T> type of Entity value.
 */
public class PEntityListFilter<T extends Entity>
        implements PFilter<Set<Long>> {

    protected final Set<Long> value = new HashSet<>();
    @SuppressWarnings("FieldNotUsedInToString")
    @StringRes
    private final int labelResId;
    @NonNull
    private final String dbKey;
    @NonNull
    private final TableDefinition table;
    @NonNull
    private final Domain domain;
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Supplier<List<T>> listSupplier;
    @Nullable
    private Map<Long, Entity> entityMap;

    /**
     * Constructor.
     *
     * @param dbKey        the field we're filtering on
     * @param labelResId   label string resource id for the name of the filter as shown to the user
     * @param table        the table with the field
     * @param domain       the domain representing the field
     * @param listSupplier a supplier of <strong>all</strong> possible values.
     *                     Typically {@code () -> dao.getAll()} or similar
     */
    PEntityListFilter(@NonNull final String dbKey,
                      @StringRes final int labelResId,
                      @NonNull final TableDefinition table,
                      @NonNull final Domain domain,
                      @NonNull final Supplier<List<T>> listSupplier) {
        this.dbKey = dbKey;
        this.labelResId = labelResId;
        this.table = table;
        this.domain = domain;
        this.listSupplier = listSupplier;
    }

    @Override
    public boolean isActive() {
        final String dbdKey = domain.getName();
        if (ServiceLocator.getInstance().isFieldEnabled(dbdKey)) {
            return !value.isEmpty();
        }
        return false;
    }

    @Override
    @NonNull
    public String getExpression() {
        if (value.size() == 1) {
            return '(' + table.dot(domain) + '=' + value.toArray()[0] + ')';
        } else {
            // deleted ids MAY be included, but have no effect
            return value.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(
                                ",",
                                '(' + table.dot(domain) + " IN (",
                                "))"));
        }
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

    @Nullable
    @Override
    public String getPersistedValue() {
        // deleted ids will be included
        return value.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
    }

    @Override
    public void setPersistedValue(@Nullable final String csvString) {
        value.clear();
        if (csvString != null && !csvString.isEmpty()) {
            // deleted ids will be included
            value.addAll(Arrays.stream(csvString.split(","))
                               .map(Long::parseLong)
                               .collect(Collectors.toList()));
        }
    }

    /**
     * Get the list of <strong>all</strong> possible values.
     *
     * @return list
     */
    @NonNull
    public List<T> getEntities() {
        return listSupplier.get();
    }

    @NonNull
    @Override
    public Set<Long> getValue() {
        // deleted ids will be included
        return new HashSet<>(value);
    }

    @Override
    public void setValue(@NonNull final Context context,
                         @Nullable final Set<Long> value) {
        this.value.clear();
        if (value != null && !value.isEmpty()) {
            // deleted ids will be included
            this.value.addAll(value);
        }
    }

    @Override
    @Nullable
    public String getValueText(@NonNull final Context context,
                               @Nullable final Set<Long> value) {
        if (value == null || value.isEmpty()) {
            return null;
        } else {
            if (entityMap == null) {
                entityMap = listSupplier
                        .get()
                        .stream()
                        .collect(Collectors.toMap(Entity::getId, entity -> entity));
            }
            //noinspection DataFlowIssue
            return value.stream()
                        .map(entityMap::get)
                        // deleted ids will be filtered out
                        // If there was only a single and deleted id,
                        // the result returned will be the empty String.
                        .filter(Objects::nonNull)
                        .map(entity -> entity.getLabel(context))
                        .collect(Collectors.joining("; "));
        }
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return context.getString(labelResId);
    }

    @LayoutRes
    @Override
    public int getPrefLayoutId() {
        return R.layout.row_edit_bookshelf_filter_entity_list;
    }

    @Override
    @NonNull
    public String toString() {
        return "PEntityListFilter{"
               + "dbKey=" + dbKey
               + ", table=" + table.getName()
               + ", domain=" + domain.getName()
               + ", value=" + value
               + ", entityMap=" + entityMap
               + '}';
    }
}
