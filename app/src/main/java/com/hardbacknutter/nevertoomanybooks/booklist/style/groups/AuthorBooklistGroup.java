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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Specialized BooklistGroup representing an {@link Author} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class AuthorBooklistGroup
        extends BooklistGroupImpl
        implements UnderEachGroup {

    private static final GroupPrefs GROUP_PREFS =
            new GroupPrefs("psk_style_author",
                           Style.UnderEach.Author.getPrefKey(),
                           StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE);

    /** DomainExpression for displaying the data. Style dependent. */
    @NonNull
    private final DomainExpression displayDomainExpression;
    /** DomainExpression for sorting the data - depends on the style used. */
    @NonNull
    private final DomainExpression sortingDomainExpression;
    /** Show a book under each item it is linked to. */
    private boolean underEach;
    /** The primary author type the user prefers. */
    @Author.Type
    private int primaryAuthorType = Author.TYPE_UNKNOWN;

    /**
     * Constructor.
     *
     * @param groupKey           of group to create
     * @param showGivenNameFirst whether to <strong>show</strong> the given name
     *                           before (@code true} or after {@code false} the family name
     * @param sortByGivenName    whether to <strong>sort</strong> by the given name
     *                           first (@code true} or by  the family name
     *                           {@code false} first
     */
    AuthorBooklistGroup(@NonNull final GroupKey groupKey,
                        final boolean showGivenNameFirst,
                        final boolean sortByGivenName) {
        super(groupKey);
        // Not sorted
        displayDomainExpression =
                new DomainExpression(DBDefinitions.DOM_AUTHOR_FORMATTED,
                                     AuthorDaoImpl.getDisplayDomainExpression(showGivenNameFirst),
                                     Sort.Unsorted);
        // Sorting depends on user preference
        sortingDomainExpression =
                new DomainExpression(new Domain.Builder(BlgDBKey.SORT_AUTHOR, SqLiteDataType.Text)
                                             .build(),
                                     AuthorDaoImpl.getSortingDomainExpression(sortByGivenName),
                                     Sort.Asc);
    }

    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return displayDomainExpression;
    }

    @Override
    @NonNull
    public List<DomainExpression> getGroupDomainExpressions() {
        // We inject the sortingDomainExpression as first in the list.
        final List<DomainExpression> list = new ArrayList<>();
        list.add(0, sortingDomainExpression);
        list.addAll(super.getGroupDomainExpressions());
        return list;
    }

    @Override
    public boolean isShowBooksUnderEach() {
        return underEach;
    }

    @Override
    public void setShowBooksUnderEach(final boolean value) {
        underEach = value;
    }

    @NonNull
    @Override
    public GroupPrefs getGroupPrefs() {
        return GROUP_PREFS;
    }

    /**
     * Get the type of author we consider the primary author.
     *
     * @return the Author type
     */
    @Author.Type
    public int getPrimaryType() {
        return primaryAuthorType;
    }

    /**
     * Set the type of author we consider the primary author.
     *
     * @param type the Author type
     */
    public void setPrimaryType(@Author.Type final int type) {
        primaryAuthorType = type;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AuthorBooklistGroup that = (AuthorBooklistGroup) o;
        return underEach == that.underEach
               && primaryAuthorType == that.primaryAuthorType
               && displayDomainExpression.equals(that.displayDomainExpression)
               && sortingDomainExpression.equals(that.sortingDomainExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), underEach, displayDomainExpression,
                            sortingDomainExpression, primaryAuthorType);
    }

    @Override
    @NonNull
    public String toString() {
        return "AuthorBooklistGroup{"
               + super.toString()
               + ", displayDomainExpression=" + displayDomainExpression
               + ", sortingDomainExpression=" + sortingDomainExpression
               + ", primaryAuthorType=" + primaryAuthorType
               + ", underEach=" + underEach
               + '}';
    }
}
