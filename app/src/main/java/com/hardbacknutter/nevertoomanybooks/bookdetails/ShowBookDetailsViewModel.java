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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ResultIntentOwner;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;

/**
 * Contains ONLY data in the <strong>Fragment</strong> scope.
 */
public class ShowBookDetailsViewModel
        extends ViewModel
        implements ResultIntentOwner {

    /** Accumulate all data that will be send in {@link Activity#setResult}. */
    @NonNull
    private final Intent mResultIntent = new Intent();

    private final MutableLiveData<LiveDataEvent<Book>> mBookUpdate = new MutableLiveData<>();

    private Book mBook;

    /**
     * <ul>
     * <li>{@link DBKey#FK_BOOK}: book id</li>
     * <li>{@link Entity#BKEY_DATA_MODIFIED}: boolean</li>
     * </ul>
     */
    @NonNull
    @Override
    public Intent getResultIntent() {
        // always set the *current* book, so the BoB list can reposition more accurately.
        if (mBook != null) {
            mResultIntent.putExtra(DBKey.FK_BOOK, mBook.getId());
        }
        return mResultIntent;
    }

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
        return mBookUpdate;
    }

    void reloadBook() {
        Objects.requireNonNull(mBook, "Book not loaded yet");
        mBook = Book.from(mBook.getId());
        mBookUpdate.setValue(new LiveDataEvent<>(mBook));
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public Book getBook() {
        Objects.requireNonNull(mBook, "Book not loaded yet");
        return mBook;
    }

    /**
     * Check if this book available in our library; or if it was lend out.
     *
     * @return {@code true} if the book is available for lending.
     */
    boolean isAvailable() {
        return mBook.getLoanee().isEmpty();
    }

    /**
     * The book was returned, remove the loanee.
     */
    void deleteLoan() {
        mBook.remove(DBKey.KEY_LOANEE);
        ServiceLocator.getInstance().getLoaneeDao().setLoanee(mBook, null);
    }

    /**
     * Toggle the read-status for this book.
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean toggleRead() {
        if (mBook.toggleRead()) {
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Delete the current book.
     *
     * @return {@code false} on any failure
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean deleteBook() {
        if (ServiceLocator.getInstance().getBookDao().delete(mBook)) {
            //noinspection ConstantConditions
            mBook = null;
            mResultIntent.putExtra(Entity.BKEY_DATA_MODIFIED, true);
            return true;
        } else {
            return false;
        }
    }
}
