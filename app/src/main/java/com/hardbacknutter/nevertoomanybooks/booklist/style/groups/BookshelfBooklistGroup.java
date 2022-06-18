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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;

/**
 * All plumbing present, but the 'under each' preference is not exposed to the user yet,
 * because there is no 'position' column for bookshelves.
 * <p>
 * <p>
 * Specialized BooklistGroup representing a {@link Bookshelf} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class BookshelfBooklistGroup
        extends AbstractLinkedTableBooklistGroup {

    public static final boolean DEFAULT_SHOW_BOOKS_UNDER_EACH = false;

    /** For sorting. */
    private static final Domain DOM_SORTING;

    static {
        DOM_SORTING = new Domain.Builder(DBKey.BL_BOOKSHELF_SORT, ColumnInfo.TYPE_TEXT)
                .build();
    }

    /**
     * Constructor.
     *
     * @param style Style reference.
     */
    BookshelfBooklistGroup(@NonNull final Style style) {
        super(style, BOOKSHELF);

        underEach = DEFAULT_SHOW_BOOKS_UNDER_EACH;
    }

    /**
     * Copy constructor.
     *
     * @param style        Style reference.
     * @param group        to copy from
     */
    BookshelfBooklistGroup(@NonNull final Style style,
                           @NonNull final BookshelfBooklistGroup group) {
        super(style, group);
    }

    @Override
    @NonNull
    public GroupKey createGroupKey() {
        // We use the foreign ID to create the key.
        // We override the display domain in #createDisplayDomainExpression.
        return new GroupKey(R.string.lbl_bookshelf, "shelf",
                            new DomainExpression(DOM_FK_BOOKSHELF,
                                                 TBL_BOOKSHELF.dot(DBKey.PK_ID)))
                .addGroupDomain(
                        // We do not sort on the key domain but add the OB column instead
                        new DomainExpression(DOM_SORTING,
                                             TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME),
                                             DomainExpression.SORT_ASC))
                .addGroupDomain(
                        // Group by id (we want the id available)
                        new DomainExpression(DOM_FK_BOOKSHELF,
                                             TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOKSHELF)));
    }

    @Override
    @NonNull
    protected DomainExpression createDisplayDomainExpression(@NonNull final Style style) {
        // Not sorted; we sort on the OB domain as defined in #createGroupKey.
        return new DomainExpression(DOM_BOOKSHELF_NAME,
                                    TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME));
    }

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(
                StyleDataStore.PSK_STYLE_BOOKSHELF);
        if (category != null) {
            final String[] keys = {StyleDataStore.PK_GROUPS_BOOKSHELF_SHOW_BOOKS_UNDER_EACH};
            setPreferenceVisibility(category, keys, visible);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "BookshelfBooklistGroup{"
               + super.toString()
               + '}';
    }
}
