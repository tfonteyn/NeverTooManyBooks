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

    private final MutableLiveData<LiveDataEvent<Book>> mBookLoaded = new MutableLiveData<>();

    private Book mBook;

    /**
     * Pseudo constructor.
     *
     * @param args Bundle with arguments
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void init(@NonNull final Bundle args) {
        if (mBook == null) {
            final long bookId = args.getLong(DBKey.FK_BOOK, 0);
            mBook = Book.from(bookId);
        }
    }

    @NonNull
    public MutableLiveData<LiveDataEvent<Book>> onBookLoaded() {
        return mBookLoaded;
    }

    void reloadBook(final long bookId) {
        mBook = Book.from(bookId);
        mBookLoaded.setValue(new LiveDataEvent<>(mBook));
    }

    void reloadBook() {
        Objects.requireNonNull(mBook, "Book not loaded yet");
        mBook = Book.from(mBook.getId());
        mBookLoaded.setValue(new LiveDataEvent<>(mBook));
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public Book getBook() {
        Objects.requireNonNull(mBook, "Book not loaded yet");
        return mBook;
    }

    /**
     * The book was returned, remove the loanee.
     *
     * <strong>Important:</strong> we're not using {@link #mBookLoaded}.
     * The caller MUST manually update the display and result-data.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteLoan() {
        mBook.remove(DBKey.KEY_LOANEE);
        return ServiceLocator.getInstance().getLoaneeDao().setLoanee(mBook, null);
    }

    /**
     * Toggle the read-status for this book.
     *
     * <strong>Important:</strong> we're not using {@link #mBookLoaded}.
     * The caller MUST manually update the display and result-data.
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    boolean toggleRead() {
        return mBook.toggleRead();
    }

    /**
     * Delete the current book.
     *
     * <strong>Important:</strong> we're not using {@link #mBookLoaded}.
     * The caller MUST manually update the display and result-data.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteBook() {
        if (ServiceLocator.getInstance().getBookDao().delete(mBook)) {
            //noinspection ConstantConditions
            mBook = null;
            return true;
        } else {
            return false;
        }
    }
}
