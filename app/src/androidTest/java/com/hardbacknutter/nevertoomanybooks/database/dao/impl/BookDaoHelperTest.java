/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.os.LocaleList;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BookDaoHelperTest
        extends BaseDBTest {

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    public void quickCv() {

        final Book book = new Book();
        book.putLong(DBKey.PK_ID, 1);
        book.setDescription("Six million years ago");
        book.setPages(516);
        book.setLanguage("eng");
        book.setFirstPublicationDate("2008-04-17");
        book.setPublicationDate("2009-06-02");

        final Author author0 = Author.from("Alastair Reynolds");
        author0.setType(Author.TYPE_WRITER);
        final Author author1 = Author.from("Alastair Reynolds");
        book.setAuthors(List.of(author0, author1));

        book.setPublishers(List.of(Publisher.from("Ace"), Publisher.from("Gollancz")));
        book.setTags(List.of(new Tag("Audiobook"),
                             new Tag("Space"),
                             new Tag("Opera")
        ));

        book.setIdentifiers(List.of(new Identifier.Value(Identifier.SID_GOODREADS,
                                                         "18306114")));

        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final BookDaoHelper bookDaoHelper = new BookDaoHelper(userLocales, book, false);
        final ContentValues cv = bookDaoHelper
                .process(context)
                .filterValues(serviceLocator.getDb().getTableInfo(TBL_BOOKS));

        assertEquals(5, cv.size());
        assertTrue(cv.containsKey(DBKey.DESCRIPTION));
        assertTrue(cv.containsKey(DBKey.PAGES));
        assertTrue(cv.containsKey(DBKey.LANGUAGE));
        assertTrue(cv.containsKey(DBKey.FIRST_PUBLICATION_DATE));
        assertTrue(cv.containsKey(DBKey.PUBLICATION_DATE));
    }
}