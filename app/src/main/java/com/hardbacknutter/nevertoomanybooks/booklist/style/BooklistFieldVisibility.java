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
 * as defined by the current style.
 */
public class BooklistFieldVisibility
        extends FieldVisibility {

    public static final long DEFAULT = getBitValue(Set.of(
            DBKey.COVER[0],
            DBKey.FK_SERIES));

    private static final Set<String> KEYS = Set.of(
            DBKey.COVER[0],
            DBKey.COVER[1],
            DBKey.FK_AUTHOR,
            DBKey.FK_SERIES,
            DBKey.FK_PUBLISHER,
            DBKey.FK_BOOKSHELF,

            DBKey.BOOK_CONDITION,
            DBKey.BOOK_ISBN,
            DBKey.BOOK_PUBLICATION__DATE,
            DBKey.FORMAT,
            DBKey.LANGUAGE,
            DBKey.LOCATION,
            DBKey.RATING);

    /**
     * Constructor.
     */
    BooklistFieldVisibility() {
        super(KEYS, DEFAULT);
    }
}
