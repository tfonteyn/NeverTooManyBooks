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
package com.hardbacknutter.nevertoomanybooks.database;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

final class TestConstants {

    static final String[] lang = {"eng", "ger", "eng", "nld", "eng",};

    // sample external id values
    static final int[] BOOK_ISFDB = {101, 102, 103, 104, 105};

    private static final String PREFIX = "Test";

    static final String[] AUTHOR_FAMILY_NAME = {
            PREFIX + "AuthorFamilyName0",
            PREFIX + "AuthorFamilyName1",
            PREFIX + "AuthorFamilyName2",
            PREFIX + "AuthorFamilyName3",
            PREFIX + "AuthorFamilyName4",
    };
    static final String[] AUTHOR_GIVEN_NAME = {
            PREFIX + "AuthorGivenName0",
            PREFIX + "AuthorGivenName1",
            PREFIX + "AuthorGivenName2",
            PREFIX + "AuthorGivenName3",
            PREFIX + "AuthorGivenName4",
    };

    static final String[] AUTHOR_FULL_NAME = {
            AUTHOR_GIVEN_NAME[0] + " " + AUTHOR_FAMILY_NAME[0],
            AUTHOR_GIVEN_NAME[1] + " " + AUTHOR_FAMILY_NAME[1],
            AUTHOR_GIVEN_NAME[2] + " " + AUTHOR_FAMILY_NAME[2],
            AUTHOR_GIVEN_NAME[3] + " " + AUTHOR_FAMILY_NAME[3],
            AUTHOR_GIVEN_NAME[4] + " " + AUTHOR_FAMILY_NAME[4],
    };

    static final String[] BOOKSHELF = {
            PREFIX + "BookshelfName0",
            PREFIX + "BookshelfName1",
            PREFIX + "BookshelfName2",
            PREFIX + "BookshelfName3",
            PREFIX + "BookshelfName4"
    };

    static final String[] PUBLISHER = {
            PREFIX + "PublisherName0",
            PREFIX + "PublisherName1",
            PREFIX + "PublisherName2",
            PREFIX + "PublisherName3",
            PREFIX + "PublisherName4"
    };
    static final String[] TOC_TITLE = {
            PREFIX + "TocTitle0",
            PREFIX + "TocTitle1",
            PREFIX + "TocTitle2",
            PREFIX + "TocTitle3",
            PREFIX + "TocTitle4"
    };

    static final String[] BOOK_TITLE = {
            PREFIX + "Title0",
            PREFIX + "Title1",
            PREFIX + "Title2",
            PREFIX + "Title3",
            PREFIX + "Title4"};

    static final String[] BOOK_LCCN = {
            PREFIX + "Unused0",
            PREFIX + "Unused1",
            PREFIX + "Unused2",
            PREFIX + "Unused3",
            PREFIX + "Unused4"
    };

    private TestConstants() {
    }

    static void deleteBookshelves(@NonNull final SynchronizedDb db) {
        final String list = Arrays.stream(BOOKSHELF)
                                  .map(n -> "'" + n + "'")
                                  .collect(Collectors.joining(","));
        db.delete(TBL_BOOKSHELF.getName(), DBKey.BOOKSHELF_NAME
                                           + " IN (" + list + ")", null);
    }

    static void deleteAuthors(@NonNull final SynchronizedDb db) {
        final String list = Arrays.stream(AUTHOR_FAMILY_NAME)
                                  .map(n -> "'" + n + "'")
                                  .collect(Collectors.joining(","));
        db.delete(TBL_AUTHORS.getName(), DBKey.AUTHOR_FAMILY_NAME
                                         + " IN (" + list + ")", null);
    }

    static void deletePublishers(@NonNull final SynchronizedDb db) {
        final String list = Arrays.stream(PUBLISHER)
                                  .map(n -> "'" + n + "'")
                                  .collect(Collectors.joining(","));
        db.delete(TBL_PUBLISHERS.getName(), DBKey.PUBLISHER_NAME
                                            + " IN (" + list + ")", null);
    }

    static void deleteTocs(@NonNull final SynchronizedDb db) {
        final String list = Arrays.stream(TOC_TITLE)
                                  .map(n -> "'" + n + "'")
                                  .collect(Collectors.joining(","));
        db.delete(TBL_TOC_ENTRIES.getName(), DBKey.TITLE
                                             + " IN (" + list + ")", null);
    }

    static void deleteBooks(@NonNull final SynchronizedDb db) {
        final String list = Arrays.stream(BOOK_TITLE)
                                  .map(n -> "'" + n + "'")
                                  .collect(Collectors.joining(","));
        db.delete(TBL_BOOKS.getName(), DBKey.TITLE
                                       + " IN (" + list + ")", null);
    }
}
