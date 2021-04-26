/*
 * @Copyright 2018-2021 HardBackNutter
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

import java.util.ArrayList;

import org.junit.Before;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

import static com.hardbacknutter.nevertoomanybooks.database.Constants.AuthorFullName;
import static com.hardbacknutter.nevertoomanybooks.database.Constants.BOOK_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.Constants.TOC_TITLE;

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
 * Note we don't follow best practice by starting with an empty database.
 * Instead we add 'easy-recognised' names/titles.
 * Pro: easier to simultaneously do manual testing.
 * Con: cannot test id's (but in a sense this is a 'pro' imho as id's should be unpredictable).
 */
public abstract class BaseSetup
        extends BaseDBTest {

    protected final Bookshelf[] bookshelf = new Bookshelf[5];
    protected final long[] bookshelfId = new long[5];
    protected final ArrayList<Bookshelf> bookshelfList = new ArrayList<>();

    protected final Author[] author = new Author[5];
    protected final long[] authorId = new long[5];
    protected final ArrayList<Author> authorList = new ArrayList<>();

    protected final Publisher[] publisher = new Publisher[5];
    protected final long[] publisherId = new long[5];
    protected final ArrayList<Publisher> publisherList = new ArrayList<>();

    protected final TocEntry[] tocEntry = new TocEntry[5];
    protected final long[] tocEntryId = new long[5];
    protected final ArrayList<TocEntry> tocList = new ArrayList<>();

    protected final Book[] book = new Book[5];
    protected final long[] bookId = new long[5];

    @Before
    public void setup()
            throws DaoWriteException {
        super.setup();

        final Context context = ServiceLocator.getLocalizedAppContext();

        final SynchronizedDb db = ServiceLocator.getDb();
        Constants.deleteTocs(db);
        Constants.deleteBooks(db);
        Constants.deleteAuthors(db);
        Constants.deletePublishers(db);

        final BookDao bookDao = ServiceLocator.getInstance().getBookDao();
        // all books will sit on the same shelf for now
        //Constants.deleteBookshelves(db);
        bookshelf[0] = Bookshelf.getBookshelf(context, Bookshelf.DEFAULT);

        // Create, don't insert yet
        author[0] = Author.from(AuthorFullName(0));
        author[1] = Author.from(AuthorFullName(1));
        author[2] = Author.from(AuthorFullName(2));
        author[3] = Author.from(AuthorFullName(3));
        author[4] = Author.from(AuthorFullName(4));

        // Create, don't insert yet
        publisher[0] = Publisher.from(Constants.PUBLISHER + "0");
        publisher[1] = Publisher.from(Constants.PUBLISHER + "1");
        publisher[2] = Publisher.from(Constants.PUBLISHER + "2");
        publisher[3] = Publisher.from(Constants.PUBLISHER + "3");
        publisher[4] = Publisher.from(Constants.PUBLISHER + "4");


        book[0] = new Book();
        book[0].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[0].putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, bookshelfList);
        book[0].setStage(EntityStage.Stage.Dirty);

        book[0].putString(DBKey.KEY_TITLE, BOOK_TITLE + "0");
        book[0].putString(DBKey.KEY_LANGUAGE, "eng");
        authorList.clear();
        authorList.add(author[0]);
        authorList.add(author[1]);
        book[0].putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
        publisherList.clear();
        publisherList.add(publisher[0]);
        book[0].putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);
        bookId[0] = bookDao.insert(context, book[0], 0);
        book[0].setStage(EntityStage.Stage.Clean);

        book[1] = new Book();
        book[1].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[1].putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, bookshelfList);
        book[1].setStage(EntityStage.Stage.Dirty);
        book[1].putString(DBKey.KEY_TITLE, BOOK_TITLE + "1");
        book[1].putString(DBKey.KEY_LANGUAGE, "ger");
        authorList.clear();
        authorList.add(author[1]);
        book[1].putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
        publisherList.clear();
        publisherList.add(publisher[1]);
        book[1].putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);
        bookId[1] = bookDao.insert(context, book[1], 0);
        book[1].setStage(EntityStage.Stage.Clean);

        book[2] = new Book();
        book[2].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[2].putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, bookshelfList);
        book[2].setStage(EntityStage.Stage.Dirty);
        book[2].putString(DBKey.KEY_TITLE, BOOK_TITLE + "2");
        book[2].putString(DBKey.KEY_LANGUAGE, "eng");
        authorList.clear();
        authorList.add(author[2]);
        book[2].putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
        publisherList.clear();
        publisherList.add(publisher[2]);
        book[2].putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);
        bookId[2] = bookDao.insert(context, book[2], 0);
        book[2].setStage(EntityStage.Stage.Clean);

        book[3] = new Book();
        book[3].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[3].putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, bookshelfList);
        book[3].setStage(EntityStage.Stage.Dirty);
        book[3].putString(DBKey.KEY_TITLE, BOOK_TITLE + "3");
        book[3].putString(DBKey.KEY_LANGUAGE, "eng");
        authorList.clear();
        authorList.add(author[0]);
        authorList.add(author[2]);
        book[3].putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
        publisherList.clear();
        publisherList.add(publisher[1]);
        book[3].putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);
        bookId[3] = bookDao.insert(context, book[3], 0);
        book[3].setStage(EntityStage.Stage.Clean);

        book[4] = new Book();
        book[4].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[4].putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, bookshelfList);
        book[4].setStage(EntityStage.Stage.Dirty);
        book[4].putString(DBKey.KEY_TITLE, BOOK_TITLE + "4");
        book[4].putString(DBKey.KEY_LANGUAGE, "eng");
        authorList.clear();
        authorList.add(author[1]);
        authorList.add(author[2]);
        book[4].putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
        publisherList.clear();
        publisherList.add(publisher[1]);
        publisherList.add(publisher[2]);
        book[4].putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);
        tocList.clear();
        tocEntry[0] = new TocEntry(author[1], TOC_TITLE + "0", null);
        tocList.add(tocEntry[0]);
        tocEntry[1] = new TocEntry(author[1], TOC_TITLE + "1", null);
        tocList.add(tocEntry[1]);
        tocEntry[2] = new TocEntry(author[2], TOC_TITLE + "2", null);
        tocList.add(tocEntry[2]);
        tocEntry[3] = new TocEntry(author[2], TOC_TITLE + "3", null);
        tocList.add(tocEntry[3]);
        book[4].putParcelableArrayList(Book.BKEY_TOC_LIST, tocList);
        bookId[4] = bookDao.insert(context, book[4], 0);
        book[4].setStage(EntityStage.Stage.Clean);
    }
}
