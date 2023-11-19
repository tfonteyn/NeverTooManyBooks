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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;


/**
 * Provides pre-initialised arrays for authors...
 * Has some helper methods to add lists to a book.
 */
public abstract class BaseSetup
        extends BaseDBTest {

    protected final String[] lang = {"eng", "ger", "eng", "nld", "eng",};

    protected final Bookshelf[] bookshelf = new Bookshelf[5];
    protected final long[] bookshelfId = new long[5];

    protected final Author[] author = new Author[5];
    protected final long[] authorId = new long[5];
    protected final Publisher[] publisher = new Publisher[5];
    protected final long[] publisherId = new long[5];

    protected final TocEntry[] tocEntry = new TocEntry[5];
    protected final long[] tocEntryId = new long[5];

    protected final Book[] book = new Book[5];
    protected final long[] bookId = new long[5];

    @CallSuper
    public void setup(@NonNull final String localeCode)
            throws DaoWriteException, StorageException {
        super.setup(localeCode);

        clearDatabase();

        initBookshelves();
        initAuthors();
        initPublishers();
        // tocs are using hardcoded author id's
        initToc();
    }

    private void clearDatabase() {
        final SynchronizedDb db = serviceLocator.getDb();
        TestConstants.deleteTocs(db);
        TestConstants.deleteBooks(db);
        TestConstants.deleteAuthors(db);
        TestConstants.deletePublishers(db);
        TestConstants.deleteBookshelves(db);
    }

    protected void initBook(final int bookIdx) {
        book[bookIdx] = new Book();
        book[bookIdx].setStage(EntityStage.Stage.WriteAble);
        book[bookIdx].putString(DBKey.TITLE, TestConstants.BOOK_TITLE + bookIdx);
        book[bookIdx].setStage(EntityStage.Stage.Dirty);
        book[bookIdx].putString(DBKey.LANGUAGE, lang[bookIdx]);
    }

    protected void initBookBookshelves(final int bookIdx,
                                       @NonNull final int... bookshelfIdx) {
        final List<Bookshelf> bookshelfList = new ArrayList<>();
        for (final int idx : bookshelfIdx) {
            bookshelfList.add(bookshelf[idx]);
        }
        book[bookIdx].setBookshelves(bookshelfList);
    }

    protected void initBookPublishers(final int bookIdx,
                                      @NonNull final int... publisherIdx) {
        final List<Publisher> publisherList = new ArrayList<>();
        for (final int idx : publisherIdx) {
            publisherList.add(publisher[idx]);
        }
        book[bookIdx].setPublishers(publisherList);
    }

    protected void initBookAuthors(final int bookIdx,
                                   @NonNull final int... authorIdx) {
        final List<Author> authorList = new ArrayList<>();
        for (final int idx : authorIdx) {
            authorList.add(author[idx]);
        }
        book[bookIdx].setAuthors(authorList);
    }

    protected void initBookToc(final int bookIdx,
                               @NonNull final int... tocIdx) {
        final List<TocEntry> tocList = new ArrayList<>();
        for (final int idx : tocIdx) {
            tocList.add(tocEntry[idx]);
        }
        book[bookIdx].setToc(tocList);
    }

    private void initBookshelves() {
        final Style defStyle = serviceLocator.getStyles().getDefault();
        bookshelf[0] = serviceLocator.getBookshelfDao()
                                     .getBookshelf(context, Bookshelf.HARD_DEFAULT)
                                     .orElseThrow();
        bookshelf[1] = new Bookshelf(TestConstants.BOOKSHELF + "1", defStyle);
        bookshelf[2] = new Bookshelf(TestConstants.BOOKSHELF + "2", defStyle);
        bookshelf[3] = new Bookshelf(TestConstants.BOOKSHELF + "3", defStyle);
        bookshelf[4] = new Bookshelf(TestConstants.BOOKSHELF + "4", defStyle);
    }

    private void initPublishers() {
        publisher[0] = Publisher.from(TestConstants.PUBLISHER + "0");
        publisher[1] = Publisher.from(TestConstants.PUBLISHER + "1");
        publisher[2] = Publisher.from(TestConstants.PUBLISHER + "2");
        publisher[3] = Publisher.from(TestConstants.PUBLISHER + "3");
        publisher[4] = Publisher.from(TestConstants.PUBLISHER + "4");
    }

    private void initAuthors() {
        author[0] = Author.from(TestConstants.AuthorFullName(0));
        author[1] = Author.from(TestConstants.AuthorFullName(1));
        author[1].setType(Author.TYPE_ARTIST);
        author[2] = Author.from(TestConstants.AuthorFullName(2));
        author[2].setType(Author.TYPE_EDITOR);
        author[3] = Author.from(TestConstants.AuthorFullName(3));
        author[2].setType(Author.TYPE_COVER_ARTIST | Author.TYPE_COVER_INKING);
        author[4] = Author.from(TestConstants.AuthorFullName(4));
    }

    private void initToc() {
        // tocs are using hardcoded author id's
        tocEntry[0] = new TocEntry(author[1], TestConstants.TOC_TITLE + "0");
        tocEntry[1] = new TocEntry(author[1], TestConstants.TOC_TITLE + "1");
        tocEntry[2] = new TocEntry(author[2], TestConstants.TOC_TITLE + "2");
        tocEntry[3] = new TocEntry(author[2], TestConstants.TOC_TITLE + "3");
        tocEntry[4] = new TocEntry(author[4], TestConstants.TOC_TITLE + "4");
    }
}
