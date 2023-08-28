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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * All plumbing present, but the 'under each' preference is not exposed to the user yet,
 * because there is no 'position' column for bookshelves.
 * <p>
 * Specialized BooklistGroup representing a {@link Bookshelf} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class BookshelfBooklistGroup
        extends BooklistGroup
        implements UnderEachGroup {

    private static final String[] PREF_KEYS = {
            Style.UnderEach.Bookshelf.getPrefKey()
    };

    /** DomainExpression for displaying the data. */
    @NonNull
    private final DomainExpression displayDomainExpression;
    /** Show a book under each item it is linked to. */
    private boolean underEach;

    /**
     * Constructor.
     */
    BookshelfBooklistGroup() {
        super(BOOKSHELF);
        // Not sorted; we sort on the OB domain as defined in #createGroupKey.
        displayDomainExpression = new DomainExpression(DBDefinitions.DOM_BOOKSHELF_NAME,
                                                       DBDefinitions.TBL_BOOKSHELF,
                                                       Sort.Unsorted);
    }

    @Override
    @NonNull
    public GroupKey createGroupKey(@Id final int id) {
        // We use the foreign ID to create the key-domain.
        // It is NOT used to display the data; instead we use #displayDomainExpression.
        // Neither the key-domain nor the display-domain is sorted;
        // instead we add the OB column, sorted, as a group domain.
        return new GroupKey(id, R.string.lbl_bookshelf, "shelf",
                            new DomainExpression(DBDefinitions.DOM_FK_BOOKSHELF,
                                                 DBDefinitions.TBL_BOOKSHELF.dot(DBKey.PK_ID),
                                                 Sort.Unsorted))
                .addGroupDomain(
                        new DomainExpression(
                                new Domain.Builder(BlgKey.SORT_BOOKSHELF, SqLiteDataType.Text)
                                        .build(),
                                DBDefinitions.TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME),
                                Sort.Asc))
                .addGroupDomain(
                        new DomainExpression(DBDefinitions.DOM_FK_BOOKSHELF,
                                             DBDefinitions.TBL_BOOK_BOOKSHELF,
                                             Sort.Unsorted));
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

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference("psk_style_bookshelf");
        if (category != null) {
            setPreferenceVisibility(category, PREF_KEYS, visible);
        }
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
