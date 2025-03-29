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
package com.hardbacknutter.nevertoomanybooks.booklist.grouping;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.adapter.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

/**
 * Represents a level in the booklist hierarchy.
 * <p>
 * There is a one-to-one mapping with a {@link GroupKey},
 * the latter providing a lightweight (static final) object without user preferences.
 * <p>
 * How to add a new Group:
 * <ol>
 *      <li>add a constant to {@link BooklistGroup}
 *          and update {@link BooklistGroup#GROUP_KEY_MAX}</li>
 *      <li>add the constant to the {@link BooklistGroup.Id} list</li>
 *      <li>if necessary add new domain to {@link DBDefinitions}</li>
 *      <li>add to the switch() in {@link GroupKey}#create,
 *          creating key/sort domains as needed</li>
 *      <li>If needed, create a class extending BooklistGroupImpl and
 *          add it to {@link BooklistGroup#newInstance(int, Style)}</li>
 *      <li>Optionally modify {@link BooklistAdapter#onCreateViewHolder};
 *          If it is just a string field it can use a {@link BooklistAdapter}.GenericStringHolder
 *          otherwise add a new holder</li>
 * </ol>
 *
 * This basic implementation wraps the {@link GroupKey} and keeps tracks of
 * the accumulated domain (i.e. the level it represents).
 * Child classes can customize the display/sort domains with user preferences.
 */
class BooklistGroupImpl
        implements BooklistGroup {

    /** The underlying group key object. */
    @NonNull
    private final GroupKey groupKey;
    /**
     * The domains represented by this group.
     * Set at <strong>runtime</strong> by the BooklistBuilder
     * based on current group <strong>and its outer groups</strong>
     */
    @Nullable
    private List<Domain> accumulatedDomains;

    /**
     * Constructor.
     *
     * @param groupKey of group to create
     */
    BooklistGroupImpl(@NonNull final GroupKey groupKey) {
        this.groupKey = groupKey;
    }

    @Override
    @BooklistGroup.Id
    public int getId() {
        return groupKey.getId();
    }

    @NonNull
    @Override
    public GroupKey getGroupKey() {
        return groupKey;
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return groupKey.getLabel(context);
    }

    @Override
    @NonNull
    public String getNodeKeyExpression() {
        return groupKey.getNodeKeyExpression();
    }

    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return groupKey.getKeyDomainExpression();
    }

    @Override
    @NonNull
    public List<DomainExpression> getGroupDomainExpressions() {
        return groupKey.getGroupDomainExpressions();
    }

    @Override
    @NonNull
    public List<DomainExpression> getBaseDomainExpressions() {
        return groupKey.getBaseDomainExpressions();
    }

    @Override
    @NonNull
    public List<Domain> getAccumulatedDomains() {
        return Objects.requireNonNull(accumulatedDomains);
    }

    @Override
    public void setAccumulatedDomains(@NonNull final List<Domain> accumulatedDomains) {
        this.accumulatedDomains = accumulatedDomains;
    }

    @Nullable
    @Override
    public GroupPrefs getGroupPrefs() {
        return null;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BooklistGroupImpl that = (BooklistGroupImpl) o;
        return Objects.equals(groupKey, that.groupKey)
               && Objects.equals(accumulatedDomains, that.accumulatedDomains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupKey, accumulatedDomains);
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistGroup{"
               + "groupKey=" + groupKey
               + ", accumulatedDomains=" + accumulatedDomains
               + '}';
    }

}

