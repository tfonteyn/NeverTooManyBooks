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

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Encapsulate the Book fields which can be shown on the Book-list screen
 * as defined <strong>by the current style</strong>.
 * <p>
 * URGENT: merge with BaseStyle#bookLevelFieldsOrderBy
 * <p>
 * Keys must be kept in sync with {@link StyleDataStore} preference keys
 * and "res/xml/preferences_style_book_details.xml".
 */
public class BookLevelFieldVisibility
        extends FieldVisibility {

    /** The {@link DBKey}s of the fields which will be visible by default. */
    public static final long DEFAULT = getBitValue(Set.of(
            DBKey.COVER[0],
            DBKey.FK_SERIES,
            DBKey.SIGNED__BOOL,
            DBKey.EDITION__BITMASK,
            DBKey.LOANEE_NAME));

    /**
     * The {@link DBKey}s of the fields which are supported by this class
     * and their column names (domains) in the
     * {@link com.hardbacknutter.nevertoomanybooks.booklist.Booklist} table.
     * Keep in sync with {@link BaseStyle}#BOOK_LEVEL_FIELDS_DEFAULTS.
     */
    private static final Map<String, String> DB_KEY_WITH_DOMAIN_NAME = Map.ofEntries(
            Map.entry(DBKey.COVER[0], DBKey.BOOK_UUID),
            Map.entry(DBKey.COVER[1], DBKey.BOOK_UUID),

            Map.entry(DBKey.FK_AUTHOR, DBKey.AUTHOR_FORMATTED),
            Map.entry(DBKey.FK_SERIES, DBKey.SERIES_TITLE),
            Map.entry(DBKey.FK_PUBLISHER, DBKey.PUBLISHER_NAME),
            Map.entry(DBKey.FK_BOOKSHELF, DBKey.BOOKSHELF_NAME_CSV),

            Map.entry(DBKey.TITLE_ORIGINAL_LANG, DBKey.TITLE_ORIGINAL_LANG),
            Map.entry(DBKey.BOOK_CONDITION, DBKey.BOOK_CONDITION),
            Map.entry(DBKey.BOOK_ISBN, DBKey.BOOK_ISBN),
            Map.entry(DBKey.BOOK_PUBLICATION__DATE, DBKey.BOOK_PUBLICATION__DATE),
            Map.entry(DBKey.FORMAT, DBKey.FORMAT),
            Map.entry(DBKey.LANGUAGE, DBKey.LANGUAGE),
            Map.entry(DBKey.LOCATION, DBKey.LOCATION),
            Map.entry(DBKey.RATING, DBKey.RATING),
            Map.entry(DBKey.PAGE_COUNT, DBKey.PAGE_COUNT),

            Map.entry(DBKey.SIGNED__BOOL, DBKey.SIGNED__BOOL),
            Map.entry(DBKey.EDITION__BITMASK, DBKey.EDITION__BITMASK),
            Map.entry(DBKey.LOANEE_NAME, DBKey.LOANEE_NAME)
    );

    /**
     * Constructor.
     */
    BookLevelFieldVisibility() {
        super(DB_KEY_WITH_DOMAIN_NAME.keySet(), DEFAULT);
    }

    @NonNull
    public static String getDomain(@NonNull final String dbKey) {
        return Objects.requireNonNull(DB_KEY_WITH_DOMAIN_NAME.get(dbKey),
                                      () -> "DB_KEY_TO_DOMAIN_NAME is missing key: " + dbKey);
    }
}
