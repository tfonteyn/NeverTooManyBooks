/*
 * @Copyright 2020 HardBackNutter
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

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.Locale;

import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * - rename an Author and update the database
 * - rename an Author in memory only
 * - rename an Author and merge books
 * - rename an Author and merge books and toc-entries
 */
@SmallTest
public class AuthorRenameTest
        extends BaseSetup {

    @Test
    public void renameAuthor()
            throws DAO.DaoWriteException {

        ArrayList<Long> bookIdList;

        long idBefore;
        long existingId;
        Author tmpAuthor;

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "renameAuthor")) {

            // rename an author
            // UPDATE in the database
            // run 'fixId' -> must keep same id
            // No changes to anything else
            idBefore = author[0].getId();
            author[0].setName("Author0ren", "gn0");
            db.update(context, author[0]);
            assertEquals(author[0].getId(), idBefore);
            author[0].fixId(context, db, false, Locale.getDefault());
            assertEquals(author[0].getId(), idBefore);

            // rename an Author to another EXISTING name
            // Do NOT update the database.
            //  run 'fixId' -> id in memory will change;
            // No changes to anything else
            idBefore = author[1].getId();
            author[1].setName("Author0ren", "gn0");
            author[1].fixId(context, db, false, Locale.getDefault());
            // should have become author[0]
            assertEquals(author[0].getId(), author[1].getId());
            // original should still be there with original name
            tmpAuthor = db.getAuthor(idBefore);
            assertNotNull(tmpAuthor);
            assertEquals("Author1", tmpAuthor.getFamilyName());

            // rename an Author to another EXISTING name and MERGE books
            author[2].setName("Author0ren", "gn0");
            existingId = db.getAuthorId(context, author[2], false, Locale.getDefault());
            db.merge(context, author[2], existingId);
            // - the renamed author[2] will have been deleted
            assertEquals(0, author[2].getId());
            // find the author[2] again...
            existingId = db.getAuthorId(context, author[2], false, Locale.getDefault());
            // should be recognized as author[0]
            assertEquals(author[0].getId(), existingId);

            // - all books of author[2] will now belong to author[0]
            bookIdList = db.getBookIdsByAuthor(author[0].getId());
            assertEquals(4, bookIdList.size());
            assertEquals(bookId[0], (long) bookIdList.get(0));
            assertEquals(bookId[2], (long) bookIdList.get(1));
            assertEquals(bookId[3], (long) bookIdList.get(2));
            assertEquals(bookId[4], (long) bookIdList.get(3));
        }
    }

    @Test
    public void renameAuthorWithTocs()
            throws DAO.DaoWriteException {

        ArrayList<Long> bookIdList;
        ArrayList<AuthorWork> works;

        long idBefore;
        long existingId;

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "renameAuthorWithTocs")) {

            // rename an author
            // UPDATE in the database
            // run 'fixId' -> must keep same id
            // No changes to anything else
            idBefore = author[1].getId();
            author[1].setName("Author1ren", "gn1");
            db.update(context, author[1]);
            assertEquals(author[1].getId(), idBefore);
            author[1].fixId(context, db, false, Locale.getDefault());
            assertEquals(author[1].getId(), idBefore);

            // rename an Author to another EXISTING name and MERGE books
            author[2].setName("Author1ren", "gn1");
            existingId = db.getAuthorId(context, author[2], false, Locale.getDefault());
            db.merge(context, author[2], existingId);
            // - the renamed author[2] will have been deleted
            assertEquals(0, author[2].getId());
            // find the author[2] again...
            existingId = db.getAuthorId(context, author[2], false, Locale.getDefault());
            // should be recognized as author[1]
            assertEquals(author[1].getId(), existingId);

            // - all books of author[2] will now belong to author[1]
            bookIdList = db.getBookIdsByAuthor(author[1].getId());
            assertEquals(5, bookIdList.size());
            assertEquals(bookId[0], (long) bookIdList.get(0));
            assertEquals(bookId[1], (long) bookIdList.get(1));
            assertEquals(bookId[2], (long) bookIdList.get(2));
            assertEquals(bookId[3], (long) bookIdList.get(3));
            assertEquals(bookId[4], (long) bookIdList.get(4));

            // - all tocs of author[2] will now belong to author[1]
            works = db.getAuthorWorks(author[1], mBookshelf.getId(), true, false);
            assertEquals(4, works.size());
            assertEquals(tocEntry[0].getId(), (long) works.get(0).getId());
            assertEquals(tocEntry[1].getId(), (long) works.get(1).getId());
            assertEquals(tocEntry[2].getId(), (long) works.get(2).getId());
            assertEquals(tocEntry[3].getId(), (long) works.get(3).getId());
        }
    }
}
