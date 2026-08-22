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
package com.hardbacknutter.nevertoomanybooks.database;

import java.util.List;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookRepository;
import com.hardbacknutter.nevertoomanybooks.database.dao.PublisherDao;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class BookInsertTest
        extends BaseDBTest {

    private DBTestHelper h;
    private BookRepository bookRepository;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
        h = new DBTestHelper(serviceLocator);

        bookRepository = new BookRepository(context);
    }

    /**
     * Create a set of books with authors... and insert the lot.
     */
    @Test
    void inserting()
            throws DaoWriteException, StorageException {
        List<Long> bookIdList;
        List<AuthorWork> works;

        assertFalse(serviceLocator.getDb().isCollationCaseSensitive());

        int bookIdx;

        bookIdx = 0;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 0);
        h.settBookAuthors(bookIdx, 0, 1);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 1;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 1);
        h.settBookAuthors(bookIdx, 1);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 2;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 2);
        h.settBookAuthors(bookIdx, 2);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 3;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 1, 3);
        h.settBookAuthors(bookIdx, 0, 2);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        bookIdx = 4;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 1, 2);
        h.settBookAuthors(bookIdx, 1, 2);
        h.setBookTocEntries(bookIdx, 2, 1, 0, 3);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);


        // The objects should have been updated with their id
        assertTrue(h.authorArray[0].getId() > 0);
        assertTrue(h.authorArray[1].getId() > 0);
        assertTrue(h.authorArray[2].getId() > 0);
        assertEquals(0, h.authorArray[3].getId());
        assertEquals(0, h.authorArray[4].getId());

        assertTrue(h.publisherArray[0].getId() > 0);
        assertTrue(h.publisherArray[1].getId() > 0);
        assertTrue(h.publisherArray[2].getId() > 0);
        assertTrue(h.publisherArray[3].getId() > 0);
        assertEquals(0, h.publisherArray[4].getId());

        assertEquals(h.bookArray[0].getId(), h.bookIdArray[0]);
        assertEquals(h.bookArray[1].getId(), h.bookIdArray[1]);
        assertEquals(h.bookArray[2].getId(), h.bookIdArray[2]);
        assertEquals(h.bookArray[3].getId(), h.bookIdArray[3]);
        assertEquals(h.bookArray[4].getId(), h.bookIdArray[4]);

        assertTrue(h.tocEntryArray[0].getId() > 0);
        assertTrue(h.tocEntryArray[1].getId() > 0);
        assertTrue(h.tocEntryArray[2].getId() > 0);
        assertTrue(h.tocEntryArray[3].getId() > 0);
        assertEquals(0, h.tocEntryArray[4].getId());

        final AuthorDao authorDao = serviceLocator.getAuthorDao();
        final PublisherDao publisherDao = serviceLocator.getPublisherDao();

        // a0 is present in b0, b3
        bookIdList = authorDao.getBookIds(h.authorArray[0].getId());
        assertEquals(2, bookIdList.size());
        assertEquals(h.bookIdArray[0], (long) bookIdList.get(0));
        assertEquals(h.bookIdArray[3], (long) bookIdList.get(1));

        // a1 is present in b0, b1, b4
        bookIdList = authorDao.getBookIds(h.authorArray[1].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(h.bookIdArray[0], (long) bookIdList.get(0));
        assertEquals(h.bookIdArray[1], (long) bookIdList.get(1));
        assertEquals(h.bookIdArray[4], (long) bookIdList.get(2));

        // a2 is present in b2, b3, b4
        bookIdList = authorDao.getBookIds(h.authorArray[2].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(h.bookIdArray[2], (long) bookIdList.get(0));
        assertEquals(h.bookIdArray[3], (long) bookIdList.get(1));
        assertEquals(h.bookIdArray[4], (long) bookIdList.get(2));

        // p0 is present in b0
        bookIdList = publisherDao.getBookIds(h.publisherArray[0].getId());
        assertEquals(1, bookIdList.size());
        assertEquals(h.bookIdArray[0], (long) bookIdList.get(0));

        // p1 is present in b1, b3, b4
        bookIdList = publisherDao.getBookIds(h.publisherArray[1].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(h.bookIdArray[1], (long) bookIdList.get(0));
        assertEquals(h.bookIdArray[3], (long) bookIdList.get(1));
        assertEquals(h.bookIdArray[4], (long) bookIdList.get(2));

        // p2 is present in b2, b4
        bookIdList = publisherDao.getBookIds(h.publisherArray[2].getId());
        assertEquals(2, bookIdList.size());
        assertEquals(h.bookIdArray[2], (long) bookIdList.get(0));
        assertEquals(h.bookIdArray[4], (long) bookIdList.get(1));


        works = authorDao.getAuthorWorks(h.authorArray[1], h.bookshelfArray[0].getId(),
                                         true, false, null);
        assertEquals(2, works.size());
        works = authorDao.getAuthorWorks(h.authorArray[2], h.bookshelfArray[0].getId(),
                                         true, false, null);
        assertEquals(2, works.size());

        works = authorDao.getAuthorWorks(h.authorArray[1], h.bookshelfArray[0].getId(),
                                         true, true, null);
        assertEquals(5, works.size());
        works = authorDao.getAuthorWorks(h.authorArray[2], h.bookshelfArray[0].getId(),
                                         true, true, null);
        assertEquals(5, works.size());

        works = authorDao.getAuthorWorks(h.authorArray[1], h.bookshelfArray[0].getId(),
                                         false, true, null);
        assertEquals(3, works.size());
        works = authorDao.getAuthorWorks(h.authorArray[2], h.bookshelfArray[0].getId(),
                                         false, true, null);
        assertEquals(3, works.size());
    }
}
