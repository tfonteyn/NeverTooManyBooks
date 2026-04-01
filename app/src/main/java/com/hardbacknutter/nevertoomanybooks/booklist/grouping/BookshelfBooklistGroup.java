/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * All plumbing present, but the 'under each' preference is not exposed to the user yet,
 * because there is no 'position' column for bookshelves.
 * <p>
 * Specialized BooklistGroup representing a {@link Bookshelf} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customised display domain
 */
class BookshelfBooklistGroup
        extends BooklistGroupImpl
        implements UnderEachGroup {

    private static final GroupSettings GROUP_PREFS =
            new GroupSettings(StyleDataStore.PSK_STYLE_BOOKSHELF,
                              Style.UnderEach.Bookshelf.getPrefKey());

    /** DomainExpression for displaying the data. */
    @NonNull
    private final DomainExpression displayDomainExpression;
    /** Show a book under each item it is linked to. */
    private boolean underEach;

    /**
     * Constructor.
     *
     * @param groupKey of group to create
     */
    BookshelfBooklistGroup(@NonNull final GroupKey groupKey) {
        super(groupKey);
        // Not sorted; we sort on the name domain as defined in GroupKeyFactory#create
        // This is "replacing" the foreign-key domain; it's NOT duplicating the
        // group/sort domain from the GroupKey
        displayDomainExpression = new DomainExpression(DBDefinitions.DOM_BOOKSHELF_NAME,
                                                       DBDefinitions.TBL_BOOKSHELF,
                                                       Sort.Unsorted);
    }

    @Override
    @NonNull
    public DomainExpression getDisplayDomainExpression() {
        return displayDomainExpression;
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
    public GroupSettings getGroupSettings() {
        return GROUP_PREFS;
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
        final BookshelfBooklistGroup that = (BookshelfBooklistGroup) o;
        return underEach == that.underEach
               && displayDomainExpression.equals(that.displayDomainExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), underEach, displayDomainExpression);
    }

    @Override
    @NonNull
    public String toString() {
        return "BookshelfBooklistGroup{"
               + super.toString()
               + ", displayDomainExpression=" + displayDomainExpression
               + ", underEach=" + underEach
               + '}';
    }
}
