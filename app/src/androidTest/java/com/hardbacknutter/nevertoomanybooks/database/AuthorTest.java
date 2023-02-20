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

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@MediumTest
public class AuthorTest
        extends BaseSetup {

    private static final String RENAMED_FAMILY_NAME = TestConstants.AUTHOR_FAMILY_NAME + "Renamed";
    private static final String RENAMED_GIVEN_NAMES = TestConstants.AUTHOR_GIVEN_NAME + "Renamed";

    private Locale bookLocale;
    private AuthorDao authorDao;

    @Override
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup();

        bookLocale = Locale.getDefault();
        authorDao = serviceLocator.getAuthorDao();

    }

    /**
     * Very basic test of insert/update/delete an Author.
     */
    @Test
    public void crud()
            throws DaoWriteException {

        authorId[0] = authorDao.insert(context, author[0], bookLocale);
        assertTrue(authorId[0] > 0);

        author[0] = authorDao.getById(authorId[0]);
        assertNotNull(author[0]);
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME + "0", author[0].getFamilyName());
        assertEquals(TestConstants.AUTHOR_GIVEN_NAME + "0", author[0].getGivenNames());
        assertFalse(author[0].isComplete());

        author[0].setComplete(true);
        authorDao.update(context, author[0], bookLocale);

        author[0] = authorDao.getById(authorId[0]);
        assertNotNull(author[0]);
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME + "0", author[0].getFamilyName());
        assertEquals(TestConstants.AUTHOR_GIVEN_NAME + "0", author[0].getGivenNames());
        assertTrue(author[0].isComplete());

        final boolean updateOk = authorDao.delete(context, author[0]);
        assertTrue(updateOk);
    }

    /**
     * - rename an Author and update the database
     * - rename an Author in memory only
     * - rename an Author and merge books
     * - rename an Author and merge books and toc-entries
     */
    @Test
    public void renameAuthor()
            throws DaoWriteException, StorageException {

        ArrayList<Long> bookIdList;

        Author existingAuthor;
        final Author tmpAuthor;

        // rename an author
        authorId[0] = authorDao.insert(context, author[0], bookLocale);
        assertTrue(authorId[0] > 0);
        // UPDATE in the database
        // run 'fixId' -> must keep same id
        // No changes to anything else
        author[0].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        authorDao.update(context, author[0], bookLocale);
        assertEquals(author[0].getId(), authorId[0]);
        authorDao.fixId(context, author[0], false, bookLocale);
        assertEquals(author[0].getId(), authorId[0]);

        // rename an Author to another EXISTING name
        authorId[1] = authorDao.insert(context, author[1], bookLocale);
        assertTrue(authorId[1] > 0);
        // Do NOT update the database.
        //  run 'fixId' -> id in memory will change;
        // No changes to anything else
        author[1].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        authorDao.fixId(context, author[1], false, bookLocale);
        // should have become author[0]
        assertEquals(author[0].getId(), author[1].getId());
        // original should still be there with original name
        tmpAuthor = authorDao.getById(authorId[1]);
        assertNotNull(tmpAuthor);
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME + "1", tmpAuthor.getFamilyName());

        // rename an Author to another EXISTING name and MERGE books
        authorId[2] = authorDao.insert(context, author[2], bookLocale);
        assertTrue(authorId[2] > 0);

        final BookDao bookDao = serviceLocator.getBookDao();
        int bookIdx;
        // add book 0,1,4 to author 2
        bookIdx = 0;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 0);
        initBookAuthors(bookIdx, 2);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 1;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 0);
        initBookAuthors(bookIdx, 2);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 4;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 0);
        initBookAuthors(bookIdx, 2);
        initBookToc(bookIdx, 2, 1, 0, 3);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);

        author[2].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        existingAuthor = authorDao.findByName(context, author[2], false, bookLocale);
        assertNotNull(existingAuthor);

        authorDao.moveBooks(context, author[2], existingAuthor);
        // - the renamed author[2] will have been deleted
        assertEquals(0, author[2].getId());
        // find the author[2] again...
        existingAuthor = authorDao.findByName(context, author[2], false, bookLocale);
        assertNotNull(existingAuthor);
        // should be recognized as author[0]
        assertEquals(author[0].getId(), existingAuthor.getId());

        // - all books of author[2] will now belong to author[0]
        bookIdList = authorDao.getBookIds(author[0].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookId[0], (long) bookIdList.get(0));
        assertEquals(bookId[1], (long) bookIdList.get(1));
        assertEquals(bookId[4], (long) bookIdList.get(2));
    }

    @Test
    public void renameAuthorWithTocs()
            throws DaoWriteException, StorageException {

        final ArrayList<Long> bookIdList;
        final ArrayList<AuthorWork> works;

        Author existingAuthor;

        // rename an author
        authorId[1] = authorDao.insert(context, author[1], bookLocale);
        assertTrue(authorId[1] > 0);
        // UPDATE in the database
        // run 'fixId' -> must keep same id
        // No changes to anything else
        author[1].setName(RENAMED_FAMILY_NAME + "_b", RENAMED_GIVEN_NAMES + "_b");

        authorDao.update(context, author[1], bookLocale);
        assertEquals(author[1].getId(), authorId[1]);
        authorDao.fixId(context, author[1], false, bookLocale);
        assertEquals(author[1].getId(), authorId[1]);

        // rename an Author to another EXISTING name and MERGE books
        authorId[2] = authorDao.insert(context, author[2], bookLocale);
        assertTrue(authorId[2] > 0);

        final BookDao bookDao = serviceLocator.getBookDao();
        int bookIdx;
        // add book 0,1,4 to author 2
        bookIdx = 0;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 0);
        initBookAuthors(bookIdx, 2);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 1;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 0);
        initBookAuthors(bookIdx, 2);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 4;
        initBook(bookIdx);
        initBookBookshelves(bookIdx, 0);
        initBookPublishers(bookIdx, 0);
        initBookAuthors(bookIdx, 2);
        initBookToc(bookIdx, 2, 1, 0, 3);
        bookId[bookIdx] = bookDao.insert(context, book[bookIdx]);
        book[bookIdx].setStage(EntityStage.Stage.Clean);

        author[2].setName(RENAMED_FAMILY_NAME + "_b", RENAMED_GIVEN_NAMES + "_b");

        existingAuthor = authorDao.findByName(context, author[2], false, bookLocale);
        assertNotNull(existingAuthor);
        authorDao.moveBooks(context, author[2], existingAuthor);
        // - the renamed author[2] will have been deleted
        assertEquals(0, author[2].getId());
        // find the author[2] again...
        existingAuthor = authorDao.findByName(context, author[2], false, bookLocale);
        assertNotNull(existingAuthor);
        // should be recognized as author[1]
        assertEquals(author[1].getId(), existingAuthor.getId());

        // - all books of author[2] will now belong to author[1]
        bookIdList = authorDao.getBookIds(author[1].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookId[0], (long) bookIdList.get(0));
        assertEquals(bookId[1], (long) bookIdList.get(1));
        assertEquals(bookId[4], (long) bookIdList.get(2));

        // - all tocs of author[2] will now belong to author[1]
        works = authorDao.getAuthorWorks(author[1], bookshelf[0].getId(), true, false, null);
        assertEquals(4, works.size());
        assertEquals(tocEntry[0].getId(), works.get(0).getId());
        assertEquals(tocEntry[1].getId(), works.get(1).getId());
        assertEquals(tocEntry[2].getId(), works.get(2).getId());
        assertEquals(tocEntry[3].getId(), works.get(3).getId());
    }

    @Test
    public void realAuthor()
            throws DaoWriteException {

        int aIdx;
        Author resolved;

        aIdx = 0;
        authorId[aIdx] = authorDao.insert(context, author[aIdx], bookLocale);
        assertTrue(authorId[aIdx] > 0);

        aIdx = 1;
        authorId[aIdx] = authorDao.insert(context, author[aIdx], bookLocale);
        assertTrue(authorId[aIdx] > 0);

        // Author 2 is a pseudonym for Author 0
        aIdx = 2;
        resolved = author[aIdx].setRealAuthor(author[0]);
        assertEquals(author[0], resolved);
        authorId[aIdx] = authorDao.insert(context, author[aIdx], bookLocale);
        assertTrue(authorId[aIdx] > 0);

        // Author 3 is a pseudonym for Author 1
        aIdx = 3;
        resolved = author[aIdx].setRealAuthor(author[1]);
        assertEquals(author[1], resolved);
        authorId[aIdx] = authorDao.insert(context, author[aIdx], bookLocale);
        assertTrue(authorId[aIdx] > 0);

        aIdx = 4;
        authorId[aIdx] = authorDao.insert(context, author[aIdx], bookLocale);
        assertTrue(authorId[aIdx] > 0);

        reload();

        // do a simple test of the realAuthor so we know further tests use the correct start-data
        assertNull(author[0].getRealAuthor());
        assertNull(author[1].getRealAuthor());
        assertEquals(author[0], author[2].getRealAuthor());
        assertEquals(author[1], author[3].getRealAuthor());
        assertNull(author[4].getRealAuthor());


        // remove the realAuthor from author 2
        aIdx = 2;
        resolved = author[aIdx].setRealAuthor(null);
        assertNull(resolved);
        authorDao.update(context, author[aIdx], bookLocale);
        reload();

        assertNull(author[0].getRealAuthor());
        assertNull(author[1].getRealAuthor());
        assertNull(author[2].getRealAuthor());
        assertEquals(author[1], author[3].getRealAuthor());
        assertNull(author[4].getRealAuthor());


        // add a realAuthor 0 to author 1
        // this will cascade and make 3 point to 0 as well
        aIdx = 1;
        resolved = author[aIdx].setRealAuthor(author[0]);
        assertEquals(author[0], resolved);
        authorDao.update(context, author[aIdx], bookLocale);
        reload();

        assertNull(author[0].getRealAuthor());
        assertEquals(author[0], author[1].getRealAuthor());
        assertNull(author[2].getRealAuthor());
        assertEquals(author[0], author[3].getRealAuthor());
        assertNull(author[4].getRealAuthor());

        // add the same realAuthor 0 to author 2
        aIdx = 2;
        resolved = author[aIdx].setRealAuthor(author[0]);
        assertEquals(author[0], resolved);
        authorDao.update(context, author[aIdx], bookLocale);
        reload();

        assertNull(author[0].getRealAuthor());
        assertEquals(author[0], author[1].getRealAuthor());
        assertEquals(author[0], author[2].getRealAuthor());
        assertEquals(author[0], author[3].getRealAuthor());
        assertNull(author[4].getRealAuthor());

        // modify realAuthor from author 3, now point to 4
        aIdx = 3;
        resolved = author[aIdx].setRealAuthor(author[4]);
        assertEquals(author[4], resolved);
        authorDao.update(context, author[aIdx], bookLocale);
        reload();

        assertNull(author[0].getRealAuthor());
        assertEquals(author[0], author[1].getRealAuthor());
        assertEquals(author[0], author[2].getRealAuthor());
        assertEquals(author[4], author[3].getRealAuthor());
        assertNull(author[4].getRealAuthor());

        // try a 1:1 circular; the author should end up having no realAuthor set
        aIdx = 4;
        resolved = author[aIdx].setRealAuthor(author[4]);
        assertNull(resolved);
        authorDao.update(context, author[aIdx], bookLocale);
        reload();

        assertNull(author[0].getRealAuthor());
        assertEquals(author[0], author[1].getRealAuthor());
        assertEquals(author[0], author[2].getRealAuthor());
        assertEquals(author[4], author[3].getRealAuthor());
        assertNull(author[4].getRealAuthor());

        // try a linked reference: a1 -> a3 -> a4
        aIdx = 1;
        resolved = author[aIdx].setRealAuthor(author[3]);
        assertEquals(author[4], resolved);
        authorDao.update(context, author[aIdx], bookLocale);
        reload();

        assertNull(author[0].getRealAuthor());
        assertEquals(author[4], author[1].getRealAuthor());
        assertEquals(author[0], author[2].getRealAuthor());
        assertEquals(author[4], author[3].getRealAuthor());
        assertNull(author[4].getRealAuthor());

        // try a circular linked reference: a0 -> a2 -> a0
        aIdx = 0;
        resolved = author[aIdx].setRealAuthor(author[2]);
        assertNull(resolved);
        authorDao.update(context, author[aIdx], bookLocale);
        reload();

        assertNull(author[0].getRealAuthor());
        assertEquals(author[4], author[1].getRealAuthor());
        assertEquals(author[0], author[2].getRealAuthor());
        assertEquals(author[4], author[3].getRealAuthor());
        assertNull(author[4].getRealAuthor());
    }

    private void reload() {
        for (int i = 0; i <= 4; i++) {
            author[i] = Author.from(TestConstants.AuthorFullName(i));
            author[i] = authorDao.findByName(context, author[i], false, bookLocale);
            assertNotNull(author[i]);
            assertEquals(authorId[i], author[i].getId());
        }
    }
}























