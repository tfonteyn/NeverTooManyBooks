/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import androidx.annotation.NonNull;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

public class Constants {

    public static final String PREFIX = "Test";

    static final String AUTHOR_FAMILY_NAME = PREFIX + "AuthorFamilyName";
    static final String AUTHOR_GIVEN_NAME = PREFIX + "AuthorGivenName";
    static final String PUBLISHER = PREFIX + "PublisherName";
    static final String BOOK_TITLE = PREFIX + "Title";
    static final String TOC_TITLE = PREFIX + "TocTitle";
    static final String[] COVER = {"0.jpg", "1.jpg"};
    private static final String BOOKSHELF = PREFIX + "BookshelfName";

    // sample external id values
    static final int BOOK_ISFDB_123 = 123;
    static final String BOOK_LCCN_0 = "unused0";

    static String AuthorFullName(final int index) {
        return AUTHOR_GIVEN_NAME + index
               + " "
               + AUTHOR_FAMILY_NAME + index;
    }

    public static void deleteBookshelves(@NonNull final DAO db) {
        db.getSyncDb().delete(TBL_BOOKSHELF.getName(),
                              KEY_BOOKSHELF_NAME
                              + " LIKE '" + BOOKSHELF + "%'", null);
    }

    static void deleteAuthors(@NonNull final DAO db) {
        db.getSyncDb().delete(TBL_AUTHORS.getName(),
                              KEY_AUTHOR_FAMILY_NAME
                              + " LIKE '" + AUTHOR_FAMILY_NAME + "%'", null);
    }

    static void deletePublishers(@NonNull final DAO db) {
        db.getSyncDb().delete(TBL_PUBLISHERS.getName(),
                              KEY_PUBLISHER_NAME
                              + " LIKE '" + PUBLISHER + "%'", null);
    }

    static void deleteTocs(@NonNull final DAO db) {
        db.getSyncDb().delete(TBL_TOC_ENTRIES.getName(),
                              KEY_TITLE
                              + " LIKE '" + TOC_TITLE + "%'", null);
    }

    static void deleteBooks(@NonNull final DAO db) {
        db.getSyncDb().delete(TBL_BOOKS.getName(),
                              KEY_TITLE
                              + " LIKE '" + BOOK_TITLE + "%'", null);
    }
}
