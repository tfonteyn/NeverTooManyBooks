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
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookRepository;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorTest
        extends BaseDBTest {

    private static final String RENAMED_FAMILY_NAME = "RenamedFamily";
    private static final String RENAMED_GIVEN_NAMES = "RenamedGiven";

    private Locale bookLocale;
    private AuthorDao authorDao;
    private BookRepository bookRepository;

    private DBTestHelper h;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        bookLocale = Locale.getDefault();
        authorDao = serviceLocator.getAuthorDao();

        bookRepository = new BookRepository(context);

        h = new DBTestHelper(serviceLocator);
    }

    /**
     * Very basic test of insert/update/delete an Author.
     */
    @Test
    void crud()
            throws DaoWriteException {

        h.authorIdArray[0] = authorDao.insert(context, h.authorArray[0], bookLocale);
        assertTrue(h.authorIdArray[0] > 0);

        h.authorArray[0] = authorDao.findById(h.authorIdArray[0]).orElseThrow();
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME[0], h.authorArray[0].getFamilyName());
        assertEquals(TestConstants.AUTHOR_GIVEN_NAME[0], h.authorArray[0].getGivenNames());
        assertFalse(h.authorArray[0].isComplete());

        h.authorArray[0].setComplete(true);
        authorDao.update(context, h.authorArray[0], bookLocale);

        h.authorArray[0] = authorDao.findById(h.authorIdArray[0]).orElseThrow();
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME[0], h.authorArray[0].getFamilyName());
        assertEquals(TestConstants.AUTHOR_GIVEN_NAME[0], h.authorArray[0].getGivenNames());
        assertTrue(h.authorArray[0].isComplete());

        final boolean updateOk = authorDao.delete(context, h.authorArray[0]);
        assertTrue(updateOk);
    }

    /**
     * - rename an Author and update the database
     * - rename an Author in memory only
     * - rename an Author and merge books
     * - rename an Author and merge books and toc-entries
     */
    @Test
    void renameAuthor()
            throws DaoWriteException {

        final List<Long> bookIdList;

        Author existingAuthor;
        final Author tmpAuthor;

        // rename an author
        h.authorIdArray[0] = authorDao.insert(context, h.authorArray[0], bookLocale);
        assertTrue(h.authorIdArray[0] > 0);
        // UPDATE in the database
        // run 'fixId' -> must keep same id
        // No changes to anything else
        h.authorArray[0].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        authorDao.update(context, h.authorArray[0], bookLocale);
        assertEquals(h.authorArray[0].getId(), h.authorIdArray[0]);
        authorDao.fixId(context, h.authorArray[0], bookLocale);
        assertEquals(h.authorArray[0].getId(), h.authorIdArray[0]);

        // rename an Author to another EXISTING name
        h.authorIdArray[1] = authorDao.insert(context, h.authorArray[1], bookLocale);
        assertTrue(h.authorIdArray[1] > 0);
        // Do NOT update the database.
        //  run 'fixId' -> id in memory will change;
        // No changes to anything else
        h.authorArray[1].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        authorDao.fixId(context, h.authorArray[1], bookLocale);
        // should have become author[0]
        assertEquals(h.authorArray[0].getId(), h.authorArray[1].getId());
        // original should still be there with original name
        tmpAuthor = authorDao.findById(h.authorIdArray[1]).orElseThrow();
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME[1], tmpAuthor.getFamilyName());

        // rename an Author to another EXISTING name and MERGE books
        h.authorIdArray[2] = authorDao.insert(context, h.authorArray[2], bookLocale);
        assertTrue(h.authorIdArray[2] > 0);

        int bookIdx;
        // add book 0,1,4 to author 2
        bookIdx = 0;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 0);
        h.settBookAuthors(bookIdx, 2);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 1;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 0);
        h.settBookAuthors(bookIdx, 2);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 4;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 0);
        h.settBookAuthors(bookIdx, 2);
        h.setBookTocEntries(bookIdx, 2, 1, 0, 3);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        h.authorArray[2].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        existingAuthor = authorDao.findByName(context, h.authorArray[2], bookLocale).orElseThrow();

        authorDao.moveBooks(context, h.authorArray[2], existingAuthor);
        // - the renamed author[2] will have been deleted
        assertEquals(0, h.authorArray[2].getId());
        // find the author[2] again...
        existingAuthor = authorDao.findByName(context, h.authorArray[2], bookLocale).orElseThrow();
        // should be recognised as author[0]
        assertEquals(h.authorArray[0].getId(), existingAuthor.getId());

        // - all books of author[2] will now belong to author[0]
        bookIdList = authorDao.getBookIds(h.authorArray[0].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(h.bookIdArray[0], (long) bookIdList.get(0));
        assertEquals(h.bookIdArray[1], (long) bookIdList.get(1));
        assertEquals(h.bookIdArray[4], (long) bookIdList.get(2));
    }

    @Test
    void renameAuthorWithTocs()
            throws DaoWriteException {

        final List<Long> bookIdList;
        final List<AuthorWork> works;

        Author existingAuthor;

        // rename an author
        h.authorIdArray[1] = authorDao.insert(context, h.authorArray[1], bookLocale);
        assertTrue(h.authorIdArray[1] > 0);
        // UPDATE in the database
        // run 'fixId' -> must keep same id
        // No changes to anything else
        h.authorArray[1].setName(RENAMED_FAMILY_NAME + "_b", RENAMED_GIVEN_NAMES + "_b");

        authorDao.update(context, h.authorArray[1], bookLocale);
        assertEquals(h.authorArray[1].getId(), h.authorIdArray[1]);
        authorDao.fixId(context, h.authorArray[1], bookLocale);
        assertEquals(h.authorArray[1].getId(), h.authorIdArray[1]);

        // rename an Author to another EXISTING name and MERGE books
        h.authorIdArray[2] = authorDao.insert(context, h.authorArray[2], bookLocale);
        assertTrue(h.authorIdArray[2] > 0);

        int bookIdx;
        // add book 0,1,4 to author 2
        bookIdx = 0;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 0);
        h.settBookAuthors(bookIdx, 2);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 1;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 0);
        h.settBookAuthors(bookIdx, 2);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 4;
        h.initBook(bookIdx);
        h.setBookBookshelves(bookIdx, 0);
        h.setBookPublishers(bookIdx, 0);
        h.settBookAuthors(bookIdx, 2);
        h.setBookTocEntries(bookIdx, 2, 1, 0, 3);
        h.bookIdArray[bookIdx] = bookRepository.insert(context, h.bookArray[bookIdx], Set.of());
        h.bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        h.authorArray[2].setName(RENAMED_FAMILY_NAME + "_b", RENAMED_GIVEN_NAMES + "_b");

        existingAuthor = authorDao.findByName(context, h.authorArray[2], bookLocale).orElseThrow();
        authorDao.moveBooks(context, h.authorArray[2], existingAuthor);
        // - the renamed author[2] will have been deleted
        assertEquals(0, h.authorArray[2].getId());
        // find the author[2] again...
        existingAuthor = authorDao.findByName(context, h.authorArray[2], bookLocale).orElseThrow();
        // should be recognised as author[1]
        assertEquals(h.authorArray[1].getId(), existingAuthor.getId());

        // - all books of author[2] will now belong to author[1]
        bookIdList = authorDao.getBookIds(h.authorArray[1].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(h.bookIdArray[0], (long) bookIdList.get(0));
        assertEquals(h.bookIdArray[1], (long) bookIdList.get(1));
        assertEquals(h.bookIdArray[4], (long) bookIdList.get(2));

        // - all tocs of author[2] will now belong to author[1]
        works = authorDao.getAuthorWorks(h.authorArray[1], h.bookshelfArray[0].getId(),
                                         true, false, null);
        assertEquals(4, works.size());
        assertEquals(h.tocEntryArray[0].getId(), works.get(0).getId());
        assertEquals(h.tocEntryArray[1].getId(), works.get(1).getId());
        assertEquals(h.tocEntryArray[2].getId(), works.get(2).getId());
        assertEquals(h.tocEntryArray[3].getId(), works.get(3).getId());
    }

    @Test
    void realAuthor()
            throws DaoWriteException {

        int aIdx;
        Author resolved;

        aIdx = 0;
        h.authorIdArray[aIdx] = authorDao.insert(context, h.authorArray[aIdx], bookLocale);
        assertTrue(h.authorIdArray[aIdx] > 0);

        aIdx = 1;
        h.authorIdArray[aIdx] = authorDao.insert(context, h.authorArray[aIdx], bookLocale);
        assertTrue(h.authorIdArray[aIdx] > 0);

        // Author 2 is a pseudonym for Author 0
        aIdx = 2;
        resolved = h.authorArray[aIdx].setRealAuthor(h.authorArray[0]);
        assertEquals(h.authorArray[0], resolved);
        h.authorIdArray[aIdx] = authorDao.insert(context, h.authorArray[aIdx], bookLocale);
        assertTrue(h.authorIdArray[aIdx] > 0);

        // Author 3 is a pseudonym for Author 1
        aIdx = 3;
        resolved = h.authorArray[aIdx].setRealAuthor(h.authorArray[1]);
        assertEquals(h.authorArray[1], resolved);
        h.authorIdArray[aIdx] = authorDao.insert(context, h.authorArray[aIdx], bookLocale);
        assertTrue(h.authorIdArray[aIdx] > 0);

        aIdx = 4;
        h.authorIdArray[aIdx] = authorDao.insert(context, h.authorArray[aIdx], bookLocale);
        assertTrue(h.authorIdArray[aIdx] > 0);

        reload();

        // do a simple test of the realAuthor so we know further tests use the correct start-data
        assertNull(h.authorArray[0].getRealAuthor());
        assertNull(h.authorArray[1].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[2].getRealAuthor());
        assertEquals(h.authorArray[1], h.authorArray[3].getRealAuthor());
        assertNull(h.authorArray[4].getRealAuthor());


        // remove the realAuthor from author 2
        aIdx = 2;
        resolved = h.authorArray[aIdx].setRealAuthor(null);
        assertNull(resolved);
        authorDao.update(context, h.authorArray[aIdx], bookLocale);
        reload();

        assertNull(h.authorArray[0].getRealAuthor());
        assertNull(h.authorArray[1].getRealAuthor());
        assertNull(h.authorArray[2].getRealAuthor());
        assertEquals(h.authorArray[1], h.authorArray[3].getRealAuthor());
        assertNull(h.authorArray[4].getRealAuthor());


        // add a realAuthor 0 to author 1
        // this will cascade and make 3 point to 0 as well
        aIdx = 1;
        resolved = h.authorArray[aIdx].setRealAuthor(h.authorArray[0]);
        assertEquals(h.authorArray[0], resolved);
        authorDao.update(context, h.authorArray[aIdx], bookLocale);
        reload();

        assertNull(h.authorArray[0].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[1].getRealAuthor());
        assertNull(h.authorArray[2].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[3].getRealAuthor());
        assertNull(h.authorArray[4].getRealAuthor());

        // add the same realAuthor 0 to author 2
        aIdx = 2;
        resolved = h.authorArray[aIdx].setRealAuthor(h.authorArray[0]);
        assertEquals(h.authorArray[0], resolved);
        authorDao.update(context, h.authorArray[aIdx], bookLocale);
        reload();

        assertNull(h.authorArray[0].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[1].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[2].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[3].getRealAuthor());
        assertNull(h.authorArray[4].getRealAuthor());

        // modify realAuthor from author 3, now point to 4
        aIdx = 3;
        resolved = h.authorArray[aIdx].setRealAuthor(h.authorArray[4]);
        assertEquals(h.authorArray[4], resolved);
        authorDao.update(context, h.authorArray[aIdx], bookLocale);
        reload();

        assertNull(h.authorArray[0].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[1].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[2].getRealAuthor());
        assertEquals(h.authorArray[4], h.authorArray[3].getRealAuthor());
        assertNull(h.authorArray[4].getRealAuthor());

        // try a 1:1 circular; the author should end up having no realAuthor set
        aIdx = 4;
        resolved = h.authorArray[aIdx].setRealAuthor(h.authorArray[4]);
        assertNull(resolved);
        authorDao.update(context, h.authorArray[aIdx], bookLocale);
        reload();

        assertNull(h.authorArray[0].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[1].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[2].getRealAuthor());
        assertEquals(h.authorArray[4], h.authorArray[3].getRealAuthor());
        assertNull(h.authorArray[4].getRealAuthor());

        // try a linked reference: a1 -> a3 -> a4
        aIdx = 1;
        resolved = h.authorArray[aIdx].setRealAuthor(h.authorArray[3]);
        assertEquals(h.authorArray[4], resolved);
        authorDao.update(context, h.authorArray[aIdx], bookLocale);
        reload();

        assertNull(h.authorArray[0].getRealAuthor());
        assertEquals(h.authorArray[4], h.authorArray[1].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[2].getRealAuthor());
        assertEquals(h.authorArray[4], h.authorArray[3].getRealAuthor());
        assertNull(h.authorArray[4].getRealAuthor());

        // try a circular linked reference: a0 -> a2 -> a0
        aIdx = 0;
        resolved = h.authorArray[aIdx].setRealAuthor(h.authorArray[2]);
        assertNull(resolved);
        authorDao.update(context, h.authorArray[aIdx], bookLocale);
        reload();

        assertNull(h.authorArray[0].getRealAuthor());
        assertEquals(h.authorArray[4], h.authorArray[1].getRealAuthor());
        assertEquals(h.authorArray[0], h.authorArray[2].getRealAuthor());
        assertEquals(h.authorArray[4], h.authorArray[3].getRealAuthor());
        assertNull(h.authorArray[4].getRealAuthor());
    }

    private void reload() {
        for (int i = 0; i < TestConstants.AUTHOR_FULL_NAME.length; i++) {
            h.authorArray[i] = Author.from(TestConstants.AUTHOR_FULL_NAME[i]);
            h.authorArray[i] = authorDao.findByName(context, h.authorArray[i], bookLocale)
                                        .orElseThrow();
            assertEquals(h.authorIdArray[i], h.authorArray[i].getId());
        }
    }
}
