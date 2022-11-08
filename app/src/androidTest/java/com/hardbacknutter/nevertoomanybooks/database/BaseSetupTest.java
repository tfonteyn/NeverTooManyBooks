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
package com.hardbacknutter.nevertoomanybooks.database;

import androidx.test.filters.MediumTest;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test the test database as setup by {@link BaseSetup}.
 */
@MediumTest
public class BaseSetupTest
        extends BaseSetup {

    @Test
    public void base() {
        ArrayList<Long> bookIdList;
        ArrayList<AuthorWork> works;

        assertFalse(serviceLocator.isCollationCaseSensitive());

        // The objects should have been updated with their id
        assertTrue(author[0].getId() > 0);
        assertTrue(author[1].getId() > 0);
        assertTrue(author[2].getId() > 0);

        assertTrue(publisher[0].getId() > 0);
        assertTrue(publisher[1].getId() > 0);
        assertTrue(publisher[2].getId() > 0);

        assertEquals(book[0].getId(), bookId[0]);
        assertEquals(book[1].getId(), bookId[1]);
        assertEquals(book[2].getId(), bookId[2]);
        assertEquals(book[3].getId(), bookId[3]);
        assertEquals(book[4].getId(), bookId[4]);

        assertTrue(tocEntry[0].getId() > 0);
        assertTrue(tocEntry[1].getId() > 0);
        assertTrue(tocEntry[2].getId() > 0);
        assertTrue(tocEntry[3].getId() > 0);

        final AuthorDao authorDao = serviceLocator.getAuthorDao();
        final PublisherDao publisherDao = serviceLocator.getPublisherDao();

        // a0 is present in b0, b3
        bookIdList = authorDao.getBookIds(author[0].getId());
        assertEquals(2, bookIdList.size());
        assertEquals(bookId[0], (long) bookIdList.get(0));
        assertEquals(bookId[3], (long) bookIdList.get(1));

        // a1 is present in b0, b1, b4
        bookIdList = authorDao.getBookIds(author[1].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookId[0], (long) bookIdList.get(0));
        assertEquals(bookId[1], (long) bookIdList.get(1));
        assertEquals(bookId[4], (long) bookIdList.get(2));

        // a2 is present in b2, b3, b4
        bookIdList = authorDao.getBookIds(author[2].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookId[2], (long) bookIdList.get(0));
        assertEquals(bookId[3], (long) bookIdList.get(1));
        assertEquals(bookId[4], (long) bookIdList.get(2));

        // p0 is present in b0
        bookIdList = publisherDao.getBookIds(publisher[0].getId());
        assertEquals(1, bookIdList.size());
        assertEquals(bookId[0], (long) bookIdList.get(0));

        // p1 is present in b1, b3, b4
        bookIdList = publisherDao.getBookIds(publisher[1].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookId[1], (long) bookIdList.get(0));
        assertEquals(bookId[3], (long) bookIdList.get(1));
        assertEquals(bookId[4], (long) bookIdList.get(2));

        // p2 is present in b2, b4
        bookIdList = publisherDao.getBookIds(publisher[2].getId());
        assertEquals(2, bookIdList.size());
        assertEquals(bookId[2], (long) bookIdList.get(0));
        assertEquals(bookId[4], (long) bookIdList.get(1));


        works = authorDao.getAuthorWorks(author[1], bookshelf[0].getId(), true, false, null);
        assertEquals(2, works.size());
        works = authorDao.getAuthorWorks(author[2], bookshelf[0].getId(), true, false, null);
        assertEquals(2, works.size());

        works = authorDao.getAuthorWorks(author[1], bookshelf[0].getId(), true, true, null);
        assertEquals(5, works.size());
        works = authorDao.getAuthorWorks(author[2], bookshelf[0].getId(), true, true, null);
        assertEquals(5, works.size());

        works = authorDao.getAuthorWorks(author[1], bookshelf[0].getId(), false, true, null);
        assertEquals(3, works.size());
        works = authorDao.getAuthorWorks(author[2], bookshelf[0].getId(), false, true, null);
        assertEquals(3, works.size());
    }
}
