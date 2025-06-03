/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

@SuppressWarnings("WeakerAccess")
public class TocViewModel
        extends ViewModel {

    private final MutableLiveData<Long> onReloadBook = new MutableLiveData<>();

    /** The book to display. */
    private Book book;

    /**
     * The list of TOC entries we're displaying.
     * This is a copy of {@link Book#getToc()} but cast to {@link AuthorWork} items.
     */
    @NonNull
    private final List<AuthorWork> works = new ArrayList<>();

    /** Whether the fragment is running in embedded mode. */
    private boolean embedded;

    /**
     * Pseudo constructor.
     * <p>
     * In full-screen mode, we get all we need from the arguments.
     * In embedded mode, we don't use any arguments,
     * but rely on {@link #reloadBook(Book)} being called.
     *
     * @param args Bundle with arguments
     */
    void init(@NonNull final Bundle args) {
        if (works.isEmpty()) {
            embedded = args.getBoolean(TocFragment.BKEY_EMBEDDED, false);

            final long bookId = args.getLong(DBKey.FK_BOOK, 0);
            if (bookId == 0) {
                throw new IllegalArgumentException("No bookId?");
            }

            book = Book.from(bookId);
            works.addAll(book.getToc());
        }
    }

    /**
     * Are we running in embedded mode.
     *
     * @return flag
     */
    boolean isEmbedded() {
        return embedded;
    }

    @NonNull
    MutableLiveData<Long> onReloadBook() {
        return onReloadBook;
    }

    void reloadBook() {
        reloadBook(Book.from(this.book.getId()));
    }

    void reloadBook(@NonNull final Book book) {
        // All fragments in the ViewPager might/will be called,
        // If we're in embedded mode, we always need to reload.
        // Otherwise only continue with the reload if the incoming data is OUR book.
        if (!embedded && book.getId() != this.book.getId()) {
            return;
        }

        this.book = book;

        works.clear();
        works.addAll(this.book.getToc());

        onReloadBook.setValue(this.book.getId());
    }

    @NonNull
    Book getBook() {
        return book;
    }

    @NonNull
    List<Author> getAuthors() {
        return book.getAuthors();
    }

    @NonNull
    List<AuthorWork> getWorks() {
        return works;
    }

    @NonNull
    String getScreenSubtitle() {
        if (BuildConfig.DEBUG /* always */) {
            return "[" + book.getId() + "] " + book.getTitle();
        } else {
            return book.getTitle();
        }
    }
}
