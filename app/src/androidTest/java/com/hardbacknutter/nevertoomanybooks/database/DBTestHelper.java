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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * Provides pre-initialised arrays for authors...
 * Has some helper methods to add lists to a book.
 */
class DBTestHelper {

    final Book[] bookArray = new Book[5];
    final long[] bookIdArray = new long[5];
    private final ServiceLocator serviceLocator;
    Author[] authorArray;
    long[] authorIdArray;
    Bookshelf[] bookshelfArray;
    Publisher[] publisherArray;
    long[] publisherIdArray;
    TocEntry[] tocEntryArray;

    DBTestHelper(final ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
        clearDatabaseAndInitArrays();
    }

    private void clearDatabaseAndInitArrays() {
        final SynchronizedDb db = serviceLocator.getDb();
        TestConstants.deleteTocs(db);
        TestConstants.deleteBooks(db);
        TestConstants.deleteAuthors(db);
        TestConstants.deletePublishers(db);
        TestConstants.deleteBookshelves(db);

        initBookshelves();
        initAuthors();
        initPublishers();
        initToc();
    }

    private void initBookshelves() {
        bookshelfArray = new Bookshelf[TestConstants.BOOKSHELF.length];

        final Style defStyle = serviceLocator.getStyles().getDefault();
        bookshelfArray[0] = serviceLocator.getBookshelfDao().getDefault();
        for (int i = 1; i < TestConstants.BOOKSHELF.length; i++) {
            bookshelfArray[i] = new Bookshelf(TestConstants.PUBLISHER[i], defStyle);
        }
    }

    private void initAuthors() {
        authorArray = new Author[TestConstants.AUTHOR_FULL_NAME.length];
        authorIdArray = new long[authorArray.length];

        authorArray[0] = Author.from(TestConstants.AUTHOR_FULL_NAME[0]);

        authorArray[1] = Author.from(TestConstants.AUTHOR_FULL_NAME[1])
                               .setRole(AuthorRole.ARTIST);

        authorArray[2] = Author.from(TestConstants.AUTHOR_FULL_NAME[2])
                               .setRole(AuthorRole.EDITOR);

        authorArray[3] = Author.from(TestConstants.AUTHOR_FULL_NAME[3])
                               .setRole(AuthorRole.COVER_ARTIST | AuthorRole.COVER_INKING);

        authorArray[4] = Author.from(TestConstants.AUTHOR_FULL_NAME[4]);
    }

    private void initPublishers() {
        publisherArray = new Publisher[TestConstants.PUBLISHER.length];
        publisherIdArray = new long[publisherArray.length];
        for (int i = 0; i < TestConstants.PUBLISHER.length; i++) {
            publisherArray[i] = new Publisher(TestConstants.PUBLISHER[i]);
        }
    }

    private void initToc() {
        tocEntryArray = new TocEntry[TestConstants.TOC_TITLE.length];
        // tocs are using random hardcoded author id's
        tocEntryArray[0] = new TocEntry(authorArray[1], TestConstants.TOC_TITLE[0]);
        tocEntryArray[1] = new TocEntry(authorArray[1], TestConstants.TOC_TITLE[1]);
        tocEntryArray[2] = new TocEntry(authorArray[2], TestConstants.TOC_TITLE[2]);
        tocEntryArray[3] = new TocEntry(authorArray[2], TestConstants.TOC_TITLE[3]);
        tocEntryArray[4] = new TocEntry(authorArray[4], TestConstants.TOC_TITLE[4]);
    }

    void initBook(final int bookIdx) {
        bookArray[bookIdx] = new Book();
        bookArray[bookIdx].setStage(EntityStage.Stage.WriteAble);
        bookArray[bookIdx].setTitle(TestConstants.BOOK_TITLE[bookIdx]);
        bookArray[bookIdx].setStage(EntityStage.Stage.Dirty);
        bookArray[bookIdx].setLanguage(TestConstants.lang[bookIdx]);
    }

    void setBookBookshelves(final int bookIdx,
                            @NonNull final int... bookshelfIdx) {
        final Collection<Bookshelf> bookshelfList = new ArrayList<>();
        for (final int idx : bookshelfIdx) {
            bookshelfList.add(bookshelfArray[idx]);
        }
        bookArray[bookIdx].setBookshelves(bookshelfList);
    }

    void setBookPublishers(final int bookIdx,
                           @NonNull final int... publisherIdx) {
        final Collection<Publisher> publisherList = new ArrayList<>();
        for (final int idx : publisherIdx) {
            publisherList.add(publisherArray[idx]);
        }
        bookArray[bookIdx].setPublishers(publisherList);
    }

    void settBookAuthors(final int bookIdx,
                         @NonNull final int... authorIdx) {
        final Collection<Author> authorList = new ArrayList<>();
        for (final int idx : authorIdx) {
            authorList.add(authorArray[idx]);
        }
        bookArray[bookIdx].setAuthors(authorList);
    }

    void setBookTocEntries(final int bookIdx,
                           @NonNull final int... tocIdx) {
        final Collection<TocEntry> tocList = new ArrayList<>();
        for (final int idx : tocIdx) {
            tocList.add(tocEntryArray[idx]);
        }
        bookArray[bookIdx].setToc(tocList);
    }
}
