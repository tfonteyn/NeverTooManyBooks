/*
 * @Copyright 2018-2022 HardBackNutter
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
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Sort;
import com.hardbacknutter.nevertoomanybooks.database.definitions.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Specialized BooklistGroup representing an {@link Author} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class AuthorBooklistGroup
        extends BooklistGroup
        implements UnderEachGroup {

    private static final String[] PREF_KEYS = {
            StyleDataStore.PK_GROUPS_AUTHOR_SHOW_BOOKS_UNDER_EACH,
            StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE};

    /** DomainExpression for displaying the data. Style dependent. */
    @NonNull
    private final DomainExpression displayDomainExpression;
    /** DomainExpression for sorting the data - depends on the style used. */
    @NonNull
    private final DomainExpression sortingDomainExpression;
    /** Show a book under each item it is linked to. */
    private boolean underEach;
    /** The primary author type the user prefers. */
    private int primaryAuthorType = Author.TYPE_UNKNOWN;

    /**
     * Constructor.
     *
     * @param style Style reference.
     */
    AuthorBooklistGroup(@NonNull final Style style) {
        super(AUTHOR);
        // Not sorted
        displayDomainExpression =
                new DomainExpression(DBDefinitions.DOM_AUTHOR_FORMATTED_FAMILY_FIRST,
                                     AuthorDaoImpl.getDisplayDomainExpression(
                                             style.isShowAuthorByGivenName()),
                                     Sort.Unsorted);
        // Sorting depends on user preference
        sortingDomainExpression =
                new DomainExpression(new Domain.Builder(BlgKey.SORT_AUTHOR, SqLiteDataType.Text)
                                             .build(),
                                     AuthorDaoImpl.getSortingDomainExpression(
                                             style.isSortAuthorByGivenName()),
                                     Sort.Asc);
    }

    @Override
    @NonNull
    public GroupKey createGroupKey(@Id final int id) {
        // We use the foreign ID to create the key-domain.
        // It is NOT used to display the data; instead we use #displayDomainExpression.
        // Neither the key-domain nor the display-domain is sorted;
        // Sorting is done with #sortingDomainExpression
        return new GroupKey(id, R.string.lbl_author, "a",
                            new DomainExpression(DBDefinitions.DOM_FK_AUTHOR,
                                                 DBDefinitions.TBL_AUTHORS.dot(DBKey.PK_ID),
                                                 Sort.Unsorted))
                .addGroupDomain(
                        new DomainExpression(DBDefinitions.DOM_FK_AUTHOR,
                                             DBDefinitions.TBL_BOOK_AUTHOR))

                // Extra data we need:
                .addGroupDomain(
                        new DomainExpression(DBDefinitions.DOM_AUTHOR_IS_COMPLETE,
                                             DBDefinitions.TBL_AUTHORS))
                .addGroupDomain(
                        new DomainExpression(DBDefinitions.DOM_AUTHOR_PSEUDONYM,
                                             DBDefinitions.TBL_AUTHOR_PSEUDONYMS));
    }

    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return displayDomainExpression;
    }

    @Override
    @NonNull
    public ArrayList<DomainExpression> getGroupDomainExpressions() {
        // We inject the sortingDomainExpression as first in the list.
        final ArrayList<DomainExpression> list = new ArrayList<>();
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

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(StyleDataStore.PSK_STYLE_AUTHOR);
        if (category != null) {
            setPreferenceVisibility(category, PREF_KEYS, visible);
        }
    }

    /**
     * Get this preference.
     *
     * @return the type of author we consider the primary author
     */
    @Author.Type
    public int getPrimaryType() {
        return primaryAuthorType;
    }

    public void setPrimaryType(final int value) {
        primaryAuthorType = value;
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
