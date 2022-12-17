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

import android.content.Context;

import androidx.test.filters.MediumTest;

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@MediumTest
public class AuthorTest
        extends BaseSetup {

    private static final String RENAMED_FAMILY_NAME = TestConstants.AUTHOR_FAMILY_NAME + "Renamed";
    private static final String RENAMED_GIVEN_NAMES = TestConstants.AUTHOR_GIVEN_NAME + "Renamed";

    /**
     * Very basic test of insert/update/delete an Author.
     */
    @Test
    public void crud()
            throws DaoWriteException {

        final Context context = serviceLocator.getLocalizedAppContext();
        final Locale bookLocale = Locale.getDefault();

        final AuthorDao authorDao = serviceLocator.getAuthorDao();

        author[0] = Author.from(TestConstants.AuthorFullName(0));
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
            throws DaoWriteException {

        ArrayList<Long> bookIdList;

        long idBefore;
        long existingId;
        final Author tmpAuthor;

        final Context context = serviceLocator.getLocalizedAppContext();
        final Locale bookLocale = Locale.getDefault();
        final AuthorDao authorDao = serviceLocator.getAuthorDao();

        // rename an author
        // UPDATE in the database
        // run 'fixId' -> must keep same id
        // No changes to anything else
        author[0].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        idBefore = author[0].getId();
        authorDao.update(context, author[0], bookLocale);
        assertEquals(author[0].getId(), idBefore);
        authorDao.fixId(context, author[0], false, bookLocale);
        assertEquals(author[0].getId(), idBefore);

        // rename an Author to another EXISTING name
        // Do NOT update the database.
        //  run 'fixId' -> id in memory will change;
        // No changes to anything else
        author[1].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        idBefore = author[1].getId();
        authorDao.fixId(context, author[1], false, bookLocale);
        // should have become author[0]
        assertEquals(author[0].getId(), author[1].getId());
        // original should still be there with original name
        tmpAuthor = authorDao.getById(idBefore);
        assertNotNull(tmpAuthor);
        assertEquals(TestConstants.AUTHOR_FAMILY_NAME + "1", tmpAuthor.getFamilyName());

        // rename an Author to another EXISTING name and MERGE books
        author[2].setName(RENAMED_FAMILY_NAME + "_a", RENAMED_GIVEN_NAMES + "_a");

        existingId = authorDao.find(context, author[2], false, bookLocale);
        final Author destination = authorDao.getById(existingId);
        assertNotNull(destination);

        authorDao.moveBooks(context, author[2], destination);
        // - the renamed author[2] will have been deleted
        assertEquals(0, author[2].getId());
        // find the author[2] again...
        existingId = authorDao.find(context, author[2], false, bookLocale);
        // should be recognized as author[0]
        assertEquals(author[0].getId(), existingId);

        // - all books of author[2] will now belong to author[0]
        bookIdList = authorDao.getBookIds(author[0].getId());
        assertEquals(4, bookIdList.size());
        assertEquals(bookId[0], (long) bookIdList.get(0));
        assertEquals(bookId[2], (long) bookIdList.get(1));
        assertEquals(bookId[3], (long) bookIdList.get(2));
        assertEquals(bookId[4], (long) bookIdList.get(3));
    }

    @Test
    public void renameAuthorWithTocs()
            throws DaoWriteException {

        final ArrayList<Long> bookIdList;
        final ArrayList<AuthorWork> works;

        final long idBefore;
        long existingId;

        final Context context = serviceLocator.getLocalizedAppContext();
        final Locale bookLocale = Locale.getDefault();
        final AuthorDao authorDao = serviceLocator.getAuthorDao();

        // rename an author
        // UPDATE in the database
        // run 'fixId' -> must keep same id
        // No changes to anything else
        author[1].setName(RENAMED_FAMILY_NAME + "_b", RENAMED_GIVEN_NAMES + "_b");

        idBefore = author[1].getId();
        authorDao.update(context, author[1], bookLocale);
        assertEquals(author[1].getId(), idBefore);
        authorDao.fixId(context, author[1], false, bookLocale);
        assertEquals(author[1].getId(), idBefore);

        // rename an Author to another EXISTING name and MERGE books
        author[2].setName(RENAMED_FAMILY_NAME + "_b", RENAMED_GIVEN_NAMES + "_b");

        existingId = authorDao.find(context, author[2], false, bookLocale);
        final Author destination = authorDao.getById(existingId);
        assertNotNull(destination);
        authorDao.moveBooks(context, author[2], destination);
        // - the renamed author[2] will have been deleted
        assertEquals(0, author[2].getId());
        // find the author[2] again...
        existingId = authorDao.find(context, author[2], false, bookLocale);
        // should be recognized as author[1]
        assertEquals(author[1].getId(), existingId);

        // - all books of author[2] will now belong to author[1]
        bookIdList = authorDao.getBookIds(author[1].getId());
        assertEquals(5, bookIdList.size());
        assertEquals(bookId[0], (long) bookIdList.get(0));
        assertEquals(bookId[1], (long) bookIdList.get(1));
        assertEquals(bookId[2], (long) bookIdList.get(2));
        assertEquals(bookId[3], (long) bookIdList.get(3));
        assertEquals(bookId[4], (long) bookIdList.get(4));

        // - all tocs of author[2] will now belong to author[1]
        works = authorDao.getAuthorWorks(author[1], bookshelf[0].getId(), true, false, null);
        assertEquals(4, works.size());
        assertEquals(tocEntry[0].getId(), works.get(0).getId());
        assertEquals(tocEntry[1].getId(), works.get(1).getId());
        assertEquals(tocEntry[2].getId(), works.get(2).getId());
        assertEquals(tocEntry[3].getId(), works.get(3).getId());
    }
}
