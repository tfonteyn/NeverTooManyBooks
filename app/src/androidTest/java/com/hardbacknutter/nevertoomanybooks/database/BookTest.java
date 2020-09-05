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

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * b0: a0, a1
 * b1: a1
 * b2: a2
 * b3: a0, a2
 * b4: a1, a2
 * <p>
 * Note we don't follow best practice by starting with an empty database.
 * Instead we add 'easy-recognised' names/titles.
 * Pro: easier to simultaneously do manual testing.
 * Con: cannot test id's (but in a sense this is a 'pro' imho as id's should be unpredictable).
 */
@SmallTest
public class BookTest {

    private final Author[] author = new Author[5];
    private final Book[] book = new Book[5];
    private final long[] bookId = new long[5];

    @Before
    public void setup()
            throws DAO.DaoWriteException {

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "clean")) {

            db.getSyncDb().delete(TBL_BOOKS.getName(),
                                  KEY_TITLE + " LIKE 'Book%'", null);

            db.getSyncDb().delete(TBL_AUTHORS.getName(),
                                  KEY_AUTHOR_FAMILY_NAME + " LIKE 'Author%'", null);

            ArrayList<Author> authorList;

            // Create, don't insert yet
            author[0] = Author.from("Test0 Author0");
            author[1] = Author.from("Test1 Author1");
            author[2] = Author.from("Test2 Author2");


            book[0] = new Book();
            book[0].putString(KEY_TITLE, "Book0");
            book[0].putString(DBDefinitions.KEY_LANGUAGE, "eng");
            authorList = new ArrayList<>();
            authorList.add(author[0]);
            authorList.add(author[1]);
            book[0].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            bookId[0] = db.insert(context, book[0], 0);

            book[1] = new Book();
            book[1].putString(KEY_TITLE, "Book1");
            book[1].putString(DBDefinitions.KEY_LANGUAGE, "ger");
            authorList = new ArrayList<>();
            authorList.add(author[1]);
            book[1].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            bookId[1] = db.insert(context, book[1], 0);

            book[2] = new Book();
            book[2].putString(KEY_TITLE, "Book2");
            book[2].putString(DBDefinitions.KEY_LANGUAGE, "eng");
            authorList = new ArrayList<>();
            authorList.add(author[2]);
            book[2].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            bookId[2] = db.insert(context, book[2], 0);

            book[3] = new Book();
            book[3].putString(KEY_TITLE, "Book3");
            book[3].putString(DBDefinitions.KEY_LANGUAGE, "eng");
            authorList = new ArrayList<>();
            authorList.add(author[0]);
            authorList.add(author[2]);
            book[3].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            bookId[3] = db.insert(context, book[3], 0);

            book[4] = new Book();
            book[4].putString(KEY_TITLE, "Book4");
            book[4].putString(DBDefinitions.KEY_LANGUAGE, "eng");
            authorList = new ArrayList<>();
            authorList.add(author[1]);
            authorList.add(author[2]);
            book[4].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            bookId[4] = db.insert(context, book[4], 0);
        }
    }

    @Test
    public void book()
            throws DAO.DaoWriteException {

        ArrayList<Long> bookIdList;

        long idBefore;
        long existingId;
        Author tmpAuthor;

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "book")) {

            // The objects should have been updated with their id
            assertTrue(author[0].getId() > 0);
            assertTrue(author[1].getId() > 0);
            assertTrue(author[2].getId() > 0);

            assertEquals(book[0].getId(), bookId[0]);
            assertEquals(book[1].getId(), bookId[1]);
            assertEquals(book[2].getId(), bookId[2]);
            assertEquals(book[3].getId(), bookId[3]);
            assertEquals(book[4].getId(), bookId[4]);

            // a0 is present in b0, b3
            bookIdList = db.getBookIdsByAuthor(author[0].getId());
            assertEquals(2, bookIdList.size());
            assertEquals(bookId[0], (long) bookIdList.get(0));
            assertEquals(bookId[3], (long) bookIdList.get(1));

            // a1 is present in b0, b1, b4
            bookIdList = db.getBookIdsByAuthor(author[1].getId());
            assertEquals(3, bookIdList.size());
            assertEquals(bookId[0], (long) bookIdList.get(0));
            assertEquals(bookId[1], (long) bookIdList.get(1));
            assertEquals(bookId[4], (long) bookIdList.get(2));

            // a2 is present in b2, b3, b4
            bookIdList = db.getBookIdsByAuthor(author[2].getId());
            assertEquals(3, bookIdList.size());
            assertEquals(bookId[2], (long) bookIdList.get(0));
            assertEquals(bookId[3], (long) bookIdList.get(1));
            assertEquals(bookId[4], (long) bookIdList.get(2));


            // rename an author; must keep same id; no changes to anything else
            idBefore = author[0].getId();
            author[0].setName("Author0ren", "FirstName");
            db.update(context, author[0]);
            assertEquals(author[0].getId(), idBefore);
            author[0].fixId(context, db, false, Locale.getDefault());
            assertEquals(author[0].getId(), idBefore);

            // rename an Author to another EXISTING name; id in memory will change;
            // no changes to anything else
            idBefore = author[1].getId();
            author[1].setName("Author0ren", "FirstName");
            author[1].fixId(context, db, false, Locale.getDefault());
            // should have become author[0]
            assertEquals(author[0].getId(), author[1].getId());
            // original should still be there with original name
            tmpAuthor = db.getAuthor(idBefore);
            assertNotNull(tmpAuthor);
            assertEquals("Author1", tmpAuthor.getFamilyName());

            // rename an Author to another EXISTING name and MERGE books
            author[2].setName("Author0ren", "FirstName");
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
}
