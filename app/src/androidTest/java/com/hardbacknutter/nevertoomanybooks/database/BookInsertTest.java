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

import androidx.test.filters.MediumTest;

import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;

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
@SuppressWarnings("MissingJavadoc")
public class BookInsertTest
        extends BaseSetup {

    /**
     * Create a set of books with authors... and insert the whole lot.
     */
    @Test
    public void inserting()
            throws DaoWriteException, StorageException {
        List<Long> bookIdList;
        List<AuthorWork> works;

        assertFalse(serviceLocator.getDb().isCollationCaseSensitive());

        final BookDao bookDao = serviceLocator.getBookDao();

        int bookIdx;

        bookIdx = 0;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 0);
        settBookAuthors(bookIdx, 0, 1);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 1;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 1);
        settBookAuthors(bookIdx, 1);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 2;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 2);
        settBookAuthors(bookIdx, 2);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 3;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 1, 3);
        settBookAuthors(bookIdx, 0, 2);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 4;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 1, 2);
        settBookAuthors(bookIdx, 1, 2);
        setBookTocEntries(bookIdx, 2, 1, 0, 3);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);


        // The objects should have been updated with their id
        assertTrue(authorArray[0].getId() > 0);
        assertTrue(authorArray[1].getId() > 0);
        assertTrue(authorArray[2].getId() > 0);
        assertEquals(0, authorArray[3].getId());
        assertEquals(0, authorArray[4].getId());

        assertTrue(publisherArray[0].getId() > 0);
        assertTrue(publisherArray[1].getId() > 0);
        assertTrue(publisherArray[2].getId() > 0);
        assertTrue(publisherArray[3].getId() > 0);
        assertEquals(0, publisherArray[4].getId());

        assertEquals(bookArray[0].getId(), bookIdArray[0]);
        assertEquals(bookArray[1].getId(), bookIdArray[1]);
        assertEquals(bookArray[2].getId(), bookIdArray[2]);
        assertEquals(bookArray[3].getId(), bookIdArray[3]);
        assertEquals(bookArray[4].getId(), bookIdArray[4]);

        assertTrue(tocEntryArray[0].getId() > 0);
        assertTrue(tocEntryArray[1].getId() > 0);
        assertTrue(tocEntryArray[2].getId() > 0);
        assertTrue(tocEntryArray[3].getId() > 0);
        assertEquals(0, tocEntryArray[4].getId());

        final AuthorDao authorDao = serviceLocator.getAuthorDao();
        final PublisherDao publisherDao = serviceLocator.getPublisherDao();

        // a0 is present in b0, b3
        bookIdList = authorDao.getBookIds(authorArray[0].getId());
        assertEquals(2, bookIdList.size());
        assertEquals(bookIdArray[0], (long) bookIdList.get(0));
        assertEquals(bookIdArray[3], (long) bookIdList.get(1));

        // a1 is present in b0, b1, b4
        bookIdList = authorDao.getBookIds(authorArray[1].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookIdArray[0], (long) bookIdList.get(0));
        assertEquals(bookIdArray[1], (long) bookIdList.get(1));
        assertEquals(bookIdArray[4], (long) bookIdList.get(2));

        // a2 is present in b2, b3, b4
        bookIdList = authorDao.getBookIds(authorArray[2].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookIdArray[2], (long) bookIdList.get(0));
        assertEquals(bookIdArray[3], (long) bookIdList.get(1));
        assertEquals(bookIdArray[4], (long) bookIdList.get(2));

        // p0 is present in b0
        bookIdList = publisherDao.getBookIds(publisherArray[0].getId());
        assertEquals(1, bookIdList.size());
        assertEquals(bookIdArray[0], (long) bookIdList.get(0));

        // p1 is present in b1, b3, b4
        bookIdList = publisherDao.getBookIds(publisherArray[1].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookIdArray[1], (long) bookIdList.get(0));
        assertEquals(bookIdArray[3], (long) bookIdList.get(1));
        assertEquals(bookIdArray[4], (long) bookIdList.get(2));

        // p2 is present in b2, b4
        bookIdList = publisherDao.getBookIds(publisherArray[2].getId());
        assertEquals(2, bookIdList.size());
        assertEquals(bookIdArray[2], (long) bookIdList.get(0));
        assertEquals(bookIdArray[4], (long) bookIdList.get(1));


        works = authorDao.getAuthorWorks(authorArray[1], bookshelfArray[0].getId(),
                                         true, false, null);
        assertEquals(2, works.size());
        works = authorDao.getAuthorWorks(authorArray[2], bookshelfArray[0].getId(),
                                         true, false, null);
        assertEquals(2, works.size());

        works = authorDao.getAuthorWorks(authorArray[1], bookshelfArray[0].getId(),
                                         true, true, null);
        assertEquals(5, works.size());
        works = authorDao.getAuthorWorks(authorArray[2], bookshelfArray[0].getId(),
                                         true, true, null);
        assertEquals(5, works.size());

        works = authorDao.getAuthorWorks(authorArray[1], bookshelfArray[0].getId(),
                                         false, true, null);
        assertEquals(3, works.size());
        works = authorDao.getAuthorWorks(authorArray[2], bookshelfArray[0].getId(),
                                         false, true, null);
        assertEquals(3, works.size());
    }
}
