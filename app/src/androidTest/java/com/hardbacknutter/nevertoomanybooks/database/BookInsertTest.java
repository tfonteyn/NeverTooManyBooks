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
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * a0: b0, b3
 * a1: b0, b1, b4
 * a2: b2, b3, b4
 * <p>
 * a1: t0, t1
 * a2: t2, t3
 * <p>
 * b0: a0, a1 + p0
 * b1: a1 + p1
 * b2: a2 + p2
 * b3: a0, a2 + p1
 * b4: a1, a2 + p1 + p2
 * <p>
 * b4: t0, t1, t2, t3
 *
 * <p>
 * Note we don't follow best practice by starting with a completely empty database.
 * Instead we add 'easy-recognised' names/titles and delete those from the db when starting.
 * Pro: easier to simultaneously do manual testing.
 * Con: cannot test id's (but in a sense this is a 'pro' imho as id's should be unpredictable).
 */
@MediumTest
public class BookInsertTest
        extends BaseSetup {

    /**
     * Create a set of books with authors... and insert the whole lot.
     */
    @Test
    public void inserting()
            throws DaoWriteException, StorageException {
        ArrayList<Long> bookIdList;
        ArrayList<AuthorWork> works;

        assertFalse(serviceLocator.isCollationCaseSensitive());

        final BookDao bookDao = serviceLocator.getBookDao();

        int bookIdx;

        bookIdx = 0;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 0);
        initBookAuthors(bookIdx, 0, 1);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 1;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 1);
        initBookAuthors(bookIdx, 1);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 2;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 2);
        initBookAuthors(bookIdx, 2);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 3;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 1, 3);
        initBookAuthors(bookIdx, 0, 2);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 4;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 1, 2);
        initBookAuthors(bookIdx, 1, 2);
        initBookToc(bookIdx, 2, 1, 0, 3);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);


        // The objects should have been updated with their id
        assertTrue(author[0].getId() > 0);
        assertTrue(author[1].getId() > 0);
        assertTrue(author[2].getId() > 0);
        assertEquals(0, author[3].getId());
        assertEquals(0, author[4].getId());

        assertTrue(publisher[0].getId() > 0);
        assertTrue(publisher[1].getId() > 0);
        assertTrue(publisher[2].getId() > 0);
        assertTrue(publisher[3].getId() > 0);
        assertEquals(0, publisher[4].getId());

        assertEquals(book[0].getId(), bookId[0]);
        assertEquals(book[1].getId(), bookId[1]);
        assertEquals(book[2].getId(), bookId[2]);
        assertEquals(book[3].getId(), bookId[3]);
        assertEquals(book[4].getId(), bookId[4]);

        assertTrue(tocEntry[0].getId() > 0);
        assertTrue(tocEntry[1].getId() > 0);
        assertTrue(tocEntry[2].getId() > 0);
        assertTrue(tocEntry[3].getId() > 0);
        assertEquals(0, tocEntry[4].getId());

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
