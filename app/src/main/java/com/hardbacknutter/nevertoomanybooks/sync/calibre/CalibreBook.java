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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * These are the field names the Calibre Content Server uses/expects in its AJAX API.
 */
final class CalibreBook {

    static final String USER_METADATA = "user_metadata";


    static final String ID = "application_id";
    static final String UUID = "uuid";
    static final String TITLE = "title";
    static final String DESCRIPTION = "comments";
    static final String LAST_MODIFIED = "last_modified";
    static final String RATING = "rating";

    static final String SERIES = "series";
    static final String SERIES_INDEX = "series_index";

    static final String PUBLISHER = "publisher";

    static final String LANGUAGES_ARRAY = "languages";
    static final String AUTHOR_ARRAY = "authors";

    static final String IDENTIFIERS = "identifiers";
    /** The URL when reading; Base64 encoded image when writing. */
    static final String COVER = "cover";

    static final String DATE_PUBLISHED = "pubdate";
    static final String EBOOK_FORMAT = "main_format";

    private CalibreBook() {
    }

    /**
     * Check if the given book has all the Calibre keys needed.
     *
     * @param book to check
     *
     * @return {@code true} is this book <strong>is</strong> a CalibreBook
     */
    static boolean isCalibreBook(@NonNull final Book book) {
        return book.contains(DBKey.CALIBRE_BOOK_ID)
               && !book.getString(DBKey.CALIBRE_BOOK_UUID).isEmpty()
               && !book.getString(DBKey.CALIBRE_BOOK_MAIN_FORMAT).isEmpty()
               && book.contains(DBKey.FK_CALIBRE_LIBRARY);
    }
}
