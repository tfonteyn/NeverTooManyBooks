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

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@MediumTest
@SuppressWarnings("MissingJavadoc")
public class AuthorTest
        extends BaseSetup {

    private static final String RENAMED_FAMILY_NAME = "RenamedFamily";
    private static final String RENAMED_GIVEN_NAMES = "RenamedGiven";

    private Locale bookLocale;
    private AuthorDao authorDao;

    @Before
    public void setup()
            throws IOException, StorageException, DaoWriteException {
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

        authorIdArray[0] = authorDao.insert(context, authorArray[0], bookLocale);
        assertTrue(authorIdArray[0] > 0);

        authorArray[0] = authorDao.findById(authorIdArray[0]).orElseThrow();
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME[0], authorArray[0].getFamilyName());
        assertEquals(TestConstants.AUTHOR_GIVEN_NAME[0], authorArray[0].getGivenNames());
        assertFalse(authorArray[0].isComplete());

        authorArray[0].setComplete(true);
        authorDao.update(context, authorArray[0], bookLocale);

        authorArray[0] = authorDao.findById(authorIdArray[0]).orElseThrow();
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME[0], authorArray[0].getFamilyName());
        assertEquals(TestConstants.AUTHOR_GIVEN_NAME[0], authorArray[0].getGivenNames());
        assertTrue(authorArray[0].isComplete());

        final boolean updateOk = authorDao.delete(context, authorArray[0]);
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

        final List<Long> bookIdList;

        Author existingAuthor;
        final Author tmpAuthor;

        // rename an author
        authorIdArray[0] = authorDao.insert(context, authorArray[0], bookLocale);
        assertTrue(authorIdArray[0] > 0);
        // UPDATE in the database
        // run 'fixId' -> must keep same id
        // No changes to anything else
        authorArray[0].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        authorDao.update(context, authorArray[0], bookLocale);
        assertEquals(authorArray[0].getId(), authorIdArray[0]);
        authorDao.fixId(context, authorArray[0], bookLocale);
        assertEquals(authorArray[0].getId(), authorIdArray[0]);

        // rename an Author to another EXISTING name
        authorIdArray[1] = authorDao.insert(context, authorArray[1], bookLocale);
        assertTrue(authorIdArray[1] > 0);
        // Do NOT update the database.
        //  run 'fixId' -> id in memory will change;
        // No changes to anything else
        authorArray[1].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        authorDao.fixId(context, authorArray[1], bookLocale);
        // should have become author[0]
        assertEquals(authorArray[0].getId(), authorArray[1].getId());
        // original should still be there with original name
        tmpAuthor = authorDao.findById(authorIdArray[1]).orElseThrow();
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME[1], tmpAuthor.getFamilyName());

        // rename an Author to another EXISTING name and MERGE books
        authorIdArray[2] = authorDao.insert(context, authorArray[2], bookLocale);
        assertTrue(authorIdArray[2] > 0);

        final BookDao bookDao = serviceLocator.getBookDao();
        int bookIdx;
        // add book 0,1,4 to author 2
        bookIdx = 0;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 0);
        settBookAuthors(bookIdx, 2);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 1;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 0);
        settBookAuthors(bookIdx, 2);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 4;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 0);
        settBookAuthors(bookIdx, 2);
        setBookTocEntries(bookIdx, 2, 1, 0, 3);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        authorArray[2].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        existingAuthor = authorDao.findByName(context, authorArray[2], bookLocale).orElseThrow();

        authorDao.moveBooks(context, authorArray[2], existingAuthor);
        // - the renamed author[2] will have been deleted
        assertEquals(0, authorArray[2].getId());
        // find the author[2] again...
        existingAuthor = authorDao.findByName(context, authorArray[2], bookLocale).orElseThrow();
        // should be recognized as author[0]
        assertEquals(authorArray[0].getId(), existingAuthor.getId());

        // - all books of author[2] will now belong to author[0]
        bookIdList = authorDao.getBookIds(authorArray[0].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookIdArray[0], (long) bookIdList.get(0));
        assertEquals(bookIdArray[1], (long) bookIdList.get(1));
        assertEquals(bookIdArray[4], (long) bookIdList.get(2));
    }

    @Test
    public void renameAuthorWithTocs()
            throws DaoWriteException, StorageException {

        final List<Long> bookIdList;
        final List<AuthorWork> works;

        Author existingAuthor;

        // rename an author
        authorIdArray[1] = authorDao.insert(context, authorArray[1], bookLocale);
        assertTrue(authorIdArray[1] > 0);
        // UPDATE in the database
        // run 'fixId' -> must keep same id
        // No changes to anything else
        authorArray[1].setName(RENAMED_FAMILY_NAME + "_b", RENAMED_GIVEN_NAMES + "_b");

        authorDao.update(context, authorArray[1], bookLocale);
        assertEquals(authorArray[1].getId(), authorIdArray[1]);
        authorDao.fixId(context, authorArray[1], bookLocale);
        assertEquals(authorArray[1].getId(), authorIdArray[1]);

        // rename an Author to another EXISTING name and MERGE books
        authorIdArray[2] = authorDao.insert(context, authorArray[2], bookLocale);
        assertTrue(authorIdArray[2] > 0);

        final BookDao bookDao = serviceLocator.getBookDao();
        int bookIdx;
        // add book 0,1,4 to author 2
        bookIdx = 0;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 0);
        settBookAuthors(bookIdx, 2);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 1;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 0);
        settBookAuthors(bookIdx, 2);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);
        bookIdx = 4;
        initBook(bookIdx);
        setBookBookshelves(bookIdx, 0);
        setBookPublishers(bookIdx, 0);
        settBookAuthors(bookIdx, 2);
        setBookTocEntries(bookIdx, 2, 1, 0, 3);
        bookIdArray[bookIdx] = bookDao.insert(context, bookArray[bookIdx], Set.of());
        bookArray[bookIdx].setStage(EntityStage.Stage.Clean);

        authorArray[2].setName(RENAMED_FAMILY_NAME + "_b", RENAMED_GIVEN_NAMES + "_b");

        existingAuthor = authorDao.findByName(context, authorArray[2], bookLocale).orElseThrow();
        authorDao.moveBooks(context, authorArray[2], existingAuthor);
        // - the renamed author[2] will have been deleted
        assertEquals(0, authorArray[2].getId());
        // find the author[2] again...
        existingAuthor = authorDao.findByName(context, authorArray[2], bookLocale).orElseThrow();
        // should be recognized as author[1]
        assertEquals(authorArray[1].getId(), existingAuthor.getId());

        // - all books of author[2] will now belong to author[1]
        bookIdList = authorDao.getBookIds(authorArray[1].getId());
        assertEquals(3, bookIdList.size());
        assertEquals(bookIdArray[0], (long) bookIdList.get(0));
        assertEquals(bookIdArray[1], (long) bookIdList.get(1));
        assertEquals(bookIdArray[4], (long) bookIdList.get(2));

        // - all tocs of author[2] will now belong to author[1]
        works = authorDao.getAuthorWorks(authorArray[1], bookshelfArray[0].getId(),
                                         true, false, null);
        assertEquals(4, works.size());
        assertEquals(tocEntryArray[0].getId(), works.get(0).getId());
        assertEquals(tocEntryArray[1].getId(), works.get(1).getId());
        assertEquals(tocEntryArray[2].getId(), works.get(2).getId());
        assertEquals(tocEntryArray[3].getId(), works.get(3).getId());
    }

    @Test
    public void realAuthor()
            throws DaoWriteException {

        int aIdx;
        Author resolved;

        aIdx = 0;
        authorIdArray[aIdx] = authorDao.insert(context, authorArray[aIdx], bookLocale);
        assertTrue(authorIdArray[aIdx] > 0);

        aIdx = 1;
        authorIdArray[aIdx] = authorDao.insert(context, authorArray[aIdx], bookLocale);
        assertTrue(authorIdArray[aIdx] > 0);

        // Author 2 is a pseudonym for Author 0
        aIdx = 2;
        resolved = authorArray[aIdx].setRealAuthor(authorArray[0]);
        assertEquals(authorArray[0], resolved);
        authorIdArray[aIdx] = authorDao.insert(context, authorArray[aIdx], bookLocale);
        assertTrue(authorIdArray[aIdx] > 0);

        // Author 3 is a pseudonym for Author 1
        aIdx = 3;
        resolved = authorArray[aIdx].setRealAuthor(authorArray[1]);
        assertEquals(authorArray[1], resolved);
        authorIdArray[aIdx] = authorDao.insert(context, authorArray[aIdx], bookLocale);
        assertTrue(authorIdArray[aIdx] > 0);

        aIdx = 4;
        authorIdArray[aIdx] = authorDao.insert(context, authorArray[aIdx], bookLocale);
        assertTrue(authorIdArray[aIdx] > 0);

        reload();

        // do a simple test of the realAuthor so we know further tests use the correct start-data
        assertNull(authorArray[0].getRealAuthor());
        assertNull(authorArray[1].getRealAuthor());
        assertEquals(authorArray[0], authorArray[2].getRealAuthor());
        assertEquals(authorArray[1], authorArray[3].getRealAuthor());
        assertNull(authorArray[4].getRealAuthor());


        // remove the realAuthor from author 2
        aIdx = 2;
        resolved = authorArray[aIdx].setRealAuthor(null);
        assertNull(resolved);
        authorDao.update(context, authorArray[aIdx], bookLocale);
        reload();

        assertNull(authorArray[0].getRealAuthor());
        assertNull(authorArray[1].getRealAuthor());
        assertNull(authorArray[2].getRealAuthor());
        assertEquals(authorArray[1], authorArray[3].getRealAuthor());
        assertNull(authorArray[4].getRealAuthor());


        // add a realAuthor 0 to author 1
        // this will cascade and make 3 point to 0 as well
        aIdx = 1;
        resolved = authorArray[aIdx].setRealAuthor(authorArray[0]);
        assertEquals(authorArray[0], resolved);
        authorDao.update(context, authorArray[aIdx], bookLocale);
        reload();

        assertNull(authorArray[0].getRealAuthor());
        assertEquals(authorArray[0], authorArray[1].getRealAuthor());
        assertNull(authorArray[2].getRealAuthor());
        assertEquals(authorArray[0], authorArray[3].getRealAuthor());
        assertNull(authorArray[4].getRealAuthor());

        // add the same realAuthor 0 to author 2
        aIdx = 2;
        resolved = authorArray[aIdx].setRealAuthor(authorArray[0]);
        assertEquals(authorArray[0], resolved);
        authorDao.update(context, authorArray[aIdx], bookLocale);
        reload();

        assertNull(authorArray[0].getRealAuthor());
        assertEquals(authorArray[0], authorArray[1].getRealAuthor());
        assertEquals(authorArray[0], authorArray[2].getRealAuthor());
        assertEquals(authorArray[0], authorArray[3].getRealAuthor());
        assertNull(authorArray[4].getRealAuthor());

        // modify realAuthor from author 3, now point to 4
        aIdx = 3;
        resolved = authorArray[aIdx].setRealAuthor(authorArray[4]);
        assertEquals(authorArray[4], resolved);
        authorDao.update(context, authorArray[aIdx], bookLocale);
        reload();

        assertNull(authorArray[0].getRealAuthor());
        assertEquals(authorArray[0], authorArray[1].getRealAuthor());
        assertEquals(authorArray[0], authorArray[2].getRealAuthor());
        assertEquals(authorArray[4], authorArray[3].getRealAuthor());
        assertNull(authorArray[4].getRealAuthor());

        // try a 1:1 circular; the author should end up having no realAuthor set
        aIdx = 4;
        resolved = authorArray[aIdx].setRealAuthor(authorArray[4]);
        assertNull(resolved);
        authorDao.update(context, authorArray[aIdx], bookLocale);
        reload();

        assertNull(authorArray[0].getRealAuthor());
        assertEquals(authorArray[0], authorArray[1].getRealAuthor());
        assertEquals(authorArray[0], authorArray[2].getRealAuthor());
        assertEquals(authorArray[4], authorArray[3].getRealAuthor());
        assertNull(authorArray[4].getRealAuthor());

        // try a linked reference: a1 -> a3 -> a4
        aIdx = 1;
        resolved = authorArray[aIdx].setRealAuthor(authorArray[3]);
        assertEquals(authorArray[4], resolved);
        authorDao.update(context, authorArray[aIdx], bookLocale);
        reload();

        assertNull(authorArray[0].getRealAuthor());
        assertEquals(authorArray[4], authorArray[1].getRealAuthor());
        assertEquals(authorArray[0], authorArray[2].getRealAuthor());
        assertEquals(authorArray[4], authorArray[3].getRealAuthor());
        assertNull(authorArray[4].getRealAuthor());

        // try a circular linked reference: a0 -> a2 -> a0
        aIdx = 0;
        resolved = authorArray[aIdx].setRealAuthor(authorArray[2]);
        assertNull(resolved);
        authorDao.update(context, authorArray[aIdx], bookLocale);
        reload();

        assertNull(authorArray[0].getRealAuthor());
        assertEquals(authorArray[4], authorArray[1].getRealAuthor());
        assertEquals(authorArray[0], authorArray[2].getRealAuthor());
        assertEquals(authorArray[4], authorArray[3].getRealAuthor());
        assertNull(authorArray[4].getRealAuthor());
    }

    private void reload() {
        for (int i = 0; i < TestConstants.AUTHOR_FULL_NAME.length; i++) {
            authorArray[i] = Author.from(TestConstants.AUTHOR_FULL_NAME[i]);
            authorArray[i] = authorDao.findByName(context, authorArray[i], bookLocale)
                                      .orElseThrow();
            assertEquals(authorIdArray[i], authorArray[i].getId());
        }
    }
}
