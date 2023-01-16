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

import androidx.annotation.NonNull;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.junit.Before;

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
            throws DaoWriteException, StorageException {
        super.setup();

        final Context context = serviceLocator.getLocalizedAppContext();

        final SynchronizedDb db = serviceLocator.getDb();
        TestConstants.deleteTocs(db);
        TestConstants.deleteBooks(db);
        TestConstants.deleteAuthors(db);
        TestConstants.deletePublishers(db);

        final BookDao bookDao = serviceLocator.getBookDao();
        // all books will sit on the same shelf for now
        //TestConstants.deleteBookshelves(db);
        bookshelf[0] = Bookshelf.getBookshelf(context, Bookshelf.DEFAULT);

        // Create, don't insert yet
        author[0] = Author.from(TestConstants.AuthorFullName(0));
        author[1] = Author.from(TestConstants.AuthorFullName(1));
        author[2] = Author.from(TestConstants.AuthorFullName(2));
        author[3] = Author.from(TestConstants.AuthorFullName(3));
        author[4] = Author.from(TestConstants.AuthorFullName(4));

        // Create, don't insert yet
        publisher[0] = Publisher.from(TestConstants.PUBLISHER + "0");
        publisher[1] = Publisher.from(TestConstants.PUBLISHER + "1");
        publisher[2] = Publisher.from(TestConstants.PUBLISHER + "2");
        publisher[3] = Publisher.from(TestConstants.PUBLISHER + "3");
        publisher[4] = Publisher.from(TestConstants.PUBLISHER + "4");


        book[0] = new Book();
        book[0].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[0].setBookshelves(bookshelfList);
        book[0].setStage(EntityStage.Stage.Dirty);

        book[0].putString(DBKey.TITLE, TestConstants.BOOK_TITLE + "0");
        book[0].putString(DBKey.LANGUAGE, "eng");
        authorList.clear();
        // author[0] has no type
        authorList.add(author[0]);
        author[1].setType(Author.TYPE_ARTIST);
        authorList.add(author[1]);
        book[0].setAuthors(authorList);
        publisherList.clear();
        publisherList.add(publisher[0]);
        book[0].setPublishers(publisherList);
        bookId[0] = bookDao.insert(context, book[0]);
        book[0].setStage(EntityStage.Stage.Clean);

        book[1] = new Book();
        book[1].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[1].setBookshelves(bookshelfList);
        book[1].setStage(EntityStage.Stage.Dirty);
        book[1].putString(DBKey.TITLE, TestConstants.BOOK_TITLE + "1");
        book[1].putString(DBKey.LANGUAGE, "ger");
        authorList.clear();
        authorList.add(author[1]);
        book[1].setAuthors(authorList);
        publisherList.clear();
        publisherList.add(publisher[1]);
        book[1].setPublishers(publisherList);
        bookId[1] = bookDao.insert(context, book[1]);
        book[1].setStage(EntityStage.Stage.Clean);

        book[2] = new Book();
        book[2].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[2].setBookshelves(bookshelfList);
        book[2].setStage(EntityStage.Stage.Dirty);
        book[2].putString(DBKey.TITLE, TestConstants.BOOK_TITLE + "2");
        book[2].putString(DBKey.LANGUAGE, "eng");
        authorList.clear();
        authorList.add(author[2]);
        book[2].setAuthors(authorList);
        publisherList.clear();
        publisherList.add(publisher[2]);
        book[2].setPublishers(publisherList);
        bookId[2] = bookDao.insert(context, book[2]);
        book[2].setStage(EntityStage.Stage.Clean);

        book[3] = new Book();
        book[3].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[3].setBookshelves(bookshelfList);
        book[3].setStage(EntityStage.Stage.Dirty);
        book[3].putString(DBKey.TITLE, TestConstants.BOOK_TITLE + "3");
        book[3].putString(DBKey.LANGUAGE, "eng");
        authorList.clear();
        // author[0] has no type
        authorList.add(author[0]);
        author[2].setType(Author.TYPE_EDITOR);
        authorList.add(author[2]);
        book[3].setAuthors(authorList);
        publisherList.clear();
        publisherList.add(publisher[1]);
        book[3].setPublishers(publisherList);
        bookId[3] = bookDao.insert(context, book[3]);
        book[3].setStage(EntityStage.Stage.Clean);

        book[4] = new Book();
        book[4].setStage(EntityStage.Stage.WriteAble);
        bookshelfList.clear();
        bookshelfList.add(bookshelf[0]);
        book[4].setBookshelves(bookshelfList);
        book[4].setStage(EntityStage.Stage.Dirty);
        book[4].putString(DBKey.TITLE, TestConstants.BOOK_TITLE + "4");
        book[4].putString(DBKey.LANGUAGE, "eng");
        authorList.clear();
        author[1].setType(Author.TYPE_COLORIST);
        authorList.add(author[1]);
        author[2].setType(Author.TYPE_EDITOR);
        authorList.add(author[2]);
        book[4].setAuthors(authorList);
        publisherList.clear();
        publisherList.add(publisher[1]);
        publisherList.add(publisher[2]);
        book[4].setPublishers(publisherList);
        tocList.clear();
        tocEntry[0] = new TocEntry(author[1], TestConstants.TOC_TITLE + "0");
        tocList.add(tocEntry[0]);
        tocEntry[1] = new TocEntry(author[1], TestConstants.TOC_TITLE + "1");
        tocList.add(tocEntry[1]);
        tocEntry[2] = new TocEntry(author[2], TestConstants.TOC_TITLE + "2");
        tocList.add(tocEntry[2]);
        tocEntry[3] = new TocEntry(author[2], TestConstants.TOC_TITLE + "3");
        tocList.add(tocEntry[3]);
        book[4].setToc(tocList);
        bookId[4] = bookDao.insert(context, book[4]);
        book[4].setStage(EntityStage.Stage.Clean);
    }
}
