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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Encapsulate the Book fields which can be shown on the Book-list screen
 * as defined <strong>by the current style</strong>.
 * <p>
 * TODO: merge with BaseStyle#bookLevelFieldsOrderBy
 * <p>
 * Keys must be kept in sync with {@link StyleDataStore} preference keys
 * and "res/xml/preferences_style_book_details.xml".
 */
public class BookLevelFieldVisibility
        extends FieldVisibility {


    /** The fields which are supported by this class. */
    private static final Set<String> FIELDS = Set.of(
            DBKey.BOOK_CONDITION,
            DBKey.BOOK_ISBN,
            DBKey.BOOK_PUBLICATION__DATE,
            DBKey.COVER[0],
            DBKey.EDITION__BITMASK,
            DBKey.FK_AUTHOR,
            DBKey.FK_BOOKSHELF,
            DBKey.FK_PUBLISHER,
            DBKey.FK_SERIES,
            DBKey.FORMAT,
            DBKey.LANGUAGE,
            DBKey.LOANEE_NAME,
            DBKey.LOCATION,
            DBKey.PAGE_COUNT,
            DBKey.RATING,
            DBKey.SIGNED__BOOL,
            DBKey.TITLE_ORIGINAL_LANG
    );

    /** The fields which will be visible by default. */
    private static final Set<String> DEFAULT = Set.of(
            DBKey.COVER[0],
            DBKey.FK_SERIES,
            DBKey.SIGNED__BOOL,
            DBKey.EDITION__BITMASK,
            DBKey.LOANEE_NAME);

    /**
     * Constructor.
     */
    BookLevelFieldVisibility() {
        super(FIELDS, getDefault());
    }

    /**
     * Constructor.
     *
     * @param bits the bitmask with the fields
     */
    BookLevelFieldVisibility(final long bits) {
        super(FIELDS, bits);
    }

    public static long getDefault() {
        return getBitValue(DEFAULT);
    }
}
