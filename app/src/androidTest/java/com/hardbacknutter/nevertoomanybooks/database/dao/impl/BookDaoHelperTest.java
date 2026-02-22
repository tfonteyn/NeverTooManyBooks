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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.ContentValues;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.TableInfo;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookDaoHelperTest
        extends BaseDBTest {

    private TableInfo tableInfo;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        tableInfo = serviceLocator.getDb().getTableInfo(DBDefinitions.TBL_BOOKS);
    }

    @Test
    void contentValues() {
        final List<Locale> userLocales = List.of(Locale.US);

        final Book book = new Book();
        book.putLong(DBKey.PK_ID, 1);
        book.setDescription("Six million years ago");
        book.setPages(516);
        book.setLanguage("eng");
        book.setFirstPublicationDate("2008-04-17");
        book.setPublicationDate("2009-06-02");

        final Author author0 = Author.from("Alastair Reynolds");
        author0.setRole(AuthorRole.WRITER);
        final Author author1 = Author.from("Alastair Reynolds");
        book.setAuthors(List.of(author0, author1));

        book.setPublishers(List.of(Publisher.from("Ace"), Publisher.from("Gollancz")));
        book.setTags(List.of(new Tag("Audiobook"),
                             new Tag("Space"),
                             new Tag("Opera")
        ));

        book.setIdentifiers(List.of(new Identifier.Value(Identifier.SID_GOODREADS,
                                                         "18306114")));

        final BookDaoHelper bookDaoHelper = new BookDaoHelper(tableInfo, userLocales);
        final ContentValues cv = bookDaoHelper.process(context, book, false);

        assertEquals(5, cv.size());
        assertTrue(cv.containsKey(DBKey.DESCRIPTION));
        assertTrue(cv.containsKey(DBKey.PAGES));
        assertTrue(cv.containsKey(DBKey.LANGUAGE));
        assertTrue(cv.containsKey(DBKey.FIRST_PUBLICATION_DATE));
        assertTrue(cv.containsKey(DBKey.PUBLICATION_DATE));
    }
}