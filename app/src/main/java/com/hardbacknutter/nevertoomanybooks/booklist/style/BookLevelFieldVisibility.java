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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Encapsulate the Book fields which can be shown on the Book-list screen
 * as defined <strong>by the current style</strong>.
 * <p>
 * TODO: merge with BaseStyle#bookLevelFieldsOrderBy
 * <p>
 * NEWTHINGS: BookLevelField: add field to FIELDS and optionally to DEFAULT.
 *  Keys must be kept in sync with
 *  {@link StyleDataStore} preference keys
 *  com.hardbacknutter.nevertoomanybooks.booklist.style.BaseStyle BOOK_LEVEL_FIELDS_DEFAULTS
 *  "res/xml/preferences_style_book_details.xml"
 */
public class BookLevelFieldVisibility
        extends FieldVisibility {


    /** The fields which are supported by this class. */
    private static final Set<String> FIELDS = Set.of(
            DBKey.CONDITION_BOOK,
            DBKey.ISBN,
            DBKey.PUBLICATION_DATE,
            DBKey.FIRST_PUBLICATION_DATE,

            DBKey.COVER[0],
            DBKey.EDITION,
            DBKey.FK_AUTHOR,
            DBKey.FK_BOOKSHELF,
            DBKey.FK_PUBLISHER,
            DBKey.FK_SERIES,
            DBKey.FORMAT,
            DBKey.LANGUAGE,
            DBKey.LOANEE_NAME,
            DBKey.LOCATION,
            DBKey.PAGES,
            DBKey.RATING,
            DBKey.READ__BOOL,
            DBKey.SIGNED__BOOL,
            DBKey.TRANSLATION_ORIGINAL_TITLE,
            DBKey.TRANSLATION_ORIGINAL_LANGUAGE,
            DBKey.READ_PROGRESS,

            DBKey.DATE_ADDED__UTC,
            DBKey.DATE_LAST_UPDATED__UTC,
            DBKey.DATE_ACQUIRED
    );

    /** The fields which will be visible by default. */
    public static final Set<String> DEFAULT = Set.of(
            DBKey.COVER[0],
            DBKey.FK_SERIES,
            DBKey.SIGNED__BOOL,
            DBKey.EDITION,
            DBKey.LOANEE_NAME,
            DBKey.READ__BOOL);

    /**
     * Constructor.
     */
    BookLevelFieldVisibility() {
        super(FIELDS, getBitValue(DEFAULT));
    }

    /**
     * Constructor.
     *
     * @param bits the bitmask with the fields
     */
    BookLevelFieldVisibility(final long bits) {
        super(FIELDS, bits);
    }
}
