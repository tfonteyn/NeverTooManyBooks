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
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Sort;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_AUTHOR_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;

/**
 * Specialized BooklistGroup representing an {@link Author} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class AuthorBooklistGroup
        extends AbstractLinkedTableBooklistGroup {

    /** DomainExpression for sorting the data - depends on the style used. */
    @NonNull
    private final DomainExpression sortingDomainExpression;
    /** The primary author type the user prefers. */
    private int primaryAuthorType = Author.TYPE_UNKNOWN;

    /**
     * Constructor.
     *
     * @param style Style reference.
     */
    AuthorBooklistGroup(@NonNull final Style style) {
        super(style, AUTHOR, false);
        sortingDomainExpression = createSortingDomainExpression(style);
    }

    @Override
    @NonNull
    public GroupKey createGroupKey() {
        // We use the foreign ID to create the key domain.
        // We override the display domain in #createDisplayDomainExpression.
        // Sorting is defined in #createSortingDomainExpression
        return new GroupKey(R.string.lbl_author, "a",
                            new DomainExpression(DOM_FK_AUTHOR,
                                                 TBL_AUTHORS.dot(DBKey.PK_ID)))

                .addGroupDomain(
                        // Group by id (we want the id available and there is
                        // a chance two Authors will have the same name)
                        new DomainExpression(DOM_FK_AUTHOR,
                                             TBL_BOOK_AUTHOR.dot(DBKey.FK_AUTHOR)))
                .addGroupDomain(
                        // Group by complete-flag
                        new DomainExpression(DOM_AUTHOR_IS_COMPLETE,
                                             TBL_AUTHORS.dot(DBKey.AUTHOR_IS_COMPLETE)));
    }

    @Override
    @NonNull
    protected DomainExpression createDisplayDomainExpression(@NonNull final Style style) {
        // Not sorted; sort as defined in #createSortingDomainExpression
        return new DomainExpression(DBDefinitions.DOM_AUTHOR_FORMATTED_FAMILY_FIRST,
                                    AuthorDaoImpl.getDisplayDomainExpression(
                                            style.isShowAuthorByGivenName()),
                                    Sort.Unsorted);
    }

    @NonNull
    private DomainExpression createSortingDomainExpression(@NonNull final Style style) {
        // Sorting depends on user preference
        return new DomainExpression(DOM_BL_AUTHOR_SORT,
                                    AuthorDaoImpl.getSortingDomainExpression(
                                            style.isSortAuthorByGivenName()),
                                    Sort.Asc);
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
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(StyleDataStore.PSK_STYLE_AUTHOR);
        if (category != null) {
            final String[] keys = {StyleDataStore.PK_GROUPS_AUTHOR_SHOW_BOOKS_UNDER_EACH,
                                   StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE};
            setPreferenceVisibility(category, keys, visible);
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
        if (!super.equals(o)) {
            return false;
        }
        final AuthorBooklistGroup that = (AuthorBooklistGroup) o;
        return Objects.equals(sortingDomainExpression, that.sortingDomainExpression)
               && primaryAuthorType == that.primaryAuthorType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sortingDomainExpression, primaryAuthorType);
    }

    @Override
    @NonNull
    public String toString() {
        return "AuthorBooklistGroup{"
               + super.toString()
               + ", sortingDomainExpression=" + sortingDomainExpression
               + ", primaryAuthorType=" + primaryAuthorType
               + '}';
    }
}
