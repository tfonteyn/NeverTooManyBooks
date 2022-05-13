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
package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;

/**
 * Contains ONLY data in the <strong>Fragment</strong> scope.
 */
public class ShowBookDetailsViewModel
        extends ViewModel {

    private final MutableLiveData<LiveDataEvent<Book>> onBookLoaded = new MutableLiveData<>();

    private Book book;

    /**
     * Pseudo constructor.
     *
     * @param args Bundle with arguments
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void init(@NonNull final Bundle args) {
        if (book == null) {
            loadBook(args.getLong(DBKey.FK_BOOK, 0));
        }
    }

    @NonNull
    public MutableLiveData<LiveDataEvent<Book>> onBookLoaded() {
        return onBookLoaded;
    }

    void loadBook(final long bookId) {
        book = Book.from(bookId);
        onBookLoaded.setValue(new LiveDataEvent<>(book));
    }

    void reloadBook() {
        Objects.requireNonNull(book, "Book not loaded yet");
        loadBook(book.getId());
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public Book getBook() {
        Objects.requireNonNull(book, "Book not loaded yet");
        return book;
    }

    /**
     * The book was returned, remove the loanee.
     *
     * <strong>Important:</strong> we're not using {@link #onBookLoaded}.
     * The caller MUST manually update the display and result-data.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteLoan() {
        book.remove(DBKey.LOANEE_NAME);
        return ServiceLocator.getInstance().getLoaneeDao().setLoanee(book, null);
    }

    /**
     * Delete the current book.
     *
     * <strong>Important:</strong> we're not using {@link #onBookLoaded}.
     * The caller MUST manually update the display and result-data.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteBook() {
        if (ServiceLocator.getInstance().getBookDao().delete(book)) {
            //noinspection ConstantConditions
            book = null;
            return true;
        } else {
            return false;
        }
    }
}
