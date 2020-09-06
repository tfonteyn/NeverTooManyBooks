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

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;

import org.junit.Before;

import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * a0: b0, b3
 * a1: b0, b1, b4
 * a2: b2, b3, b4
 * <p>
 * a1: t0, t1
 * a2: t2, t3
 * <p>
 * b0: a0, a1
 * b1: a1
 * b2: a2
 * b3: a0, a2
 * b4: a1, a2
 * <p>
 * b4: t0, t1, t2, t3
 *
 * <p>
 * Note we don't follow best practice by starting with an empty database.
 * Instead we add 'easy-recognised' names/titles.
 * Pro: easier to simultaneously do manual testing.
 * Con: cannot test id's (but in a sense this is a 'pro' imho as id's should be unpredictable).
 */
public abstract class BaseSetup {

    protected final Author[] author = new Author[5];
    protected final Book[] book = new Book[5];
    protected final TocEntry[] tocEntry = new TocEntry[5];
    protected final long[] bookId = new long[5];

    protected Bookshelf mBookshelf;

    @Before
    public void setup()
            throws DAO.DaoWriteException {

        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (DAO db = new DAO(context, "setup")) {

            db.getSyncDb().delete(TBL_TOC_ENTRIES.getName(),
                                  KEY_TITLE + " LIKE 'TocEntry%'", null);

            db.getSyncDb().delete(TBL_BOOKS.getName(),
                                  KEY_TITLE + " LIKE 'Book%'", null);

            db.getSyncDb().delete(TBL_AUTHORS.getName(),
                                  KEY_AUTHOR_FAMILY_NAME + " LIKE 'Author%'", null);

            ArrayList<Bookshelf> bookshelves;
            ArrayList<Author> authorList;
            ArrayList<TocEntry> tocList;

            mBookshelf = Bookshelf.getBookshelf(context, db, Bookshelf.DEFAULT);
            bookshelves = new ArrayList<>();
            bookshelves.add(mBookshelf);

            // Create, don't insert yet
            author[0] = Author.from("Test0 Author0");
            author[1] = Author.from("Test1 Author1");
            author[2] = Author.from("Test2 Author2");


            book[0] = new Book();
            book[0].putParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY, bookshelves);
            book[0].putString(KEY_TITLE, "Book0");
            book[0].putString(DBDefinitions.KEY_LANGUAGE, "eng");
            authorList = new ArrayList<>();
            authorList.add(author[0]);
            authorList.add(author[1]);
            book[0].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            bookId[0] = db.insert(context, book[0], 0);

            book[1] = new Book();
            book[1].putParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY, bookshelves);
            book[1].putString(KEY_TITLE, "Book1");
            book[1].putString(DBDefinitions.KEY_LANGUAGE, "ger");
            authorList = new ArrayList<>();
            authorList.add(author[1]);
            book[1].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            bookId[1] = db.insert(context, book[1], 0);

            book[2] = new Book();
            book[2].putParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY, bookshelves);
            book[2].putString(KEY_TITLE, "Book2");
            book[2].putString(DBDefinitions.KEY_LANGUAGE, "eng");
            authorList = new ArrayList<>();
            authorList.add(author[2]);
            book[2].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            bookId[2] = db.insert(context, book[2], 0);

            book[3] = new Book();
            book[3].putParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY, bookshelves);
            book[3].putString(KEY_TITLE, "Book3");
            book[3].putString(DBDefinitions.KEY_LANGUAGE, "eng");
            authorList = new ArrayList<>();
            authorList.add(author[0]);
            authorList.add(author[2]);
            book[3].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            bookId[3] = db.insert(context, book[3], 0);

            book[4] = new Book();
            book[4].putParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY, bookshelves);
            book[4].putString(KEY_TITLE, "Book4");
            book[4].putString(DBDefinitions.KEY_LANGUAGE, "eng");
            authorList = new ArrayList<>();
            authorList.add(author[1]);
            authorList.add(author[2]);
            book[4].putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
            tocList = new ArrayList<>();
            tocEntry[0] = new TocEntry(author[1], "TocEntry0", null);
            tocList.add(tocEntry[0]);
            tocEntry[1] = new TocEntry(author[1], "TocEntry1", null);
            tocList.add(tocEntry[1]);
            tocEntry[2] = new TocEntry(author[2], "TocEntry2", null);
            tocList.add(tocEntry[2]);
            tocEntry[3] = new TocEntry(author[2], "TocEntry3", null);
            tocList.add(tocEntry[3]);
            book[4].putParcelableArrayList(Book.BKEY_TOC_ARRAY, tocList);
            bookId[4] = db.insert(context, book[4], 0);
        }
    }

}
