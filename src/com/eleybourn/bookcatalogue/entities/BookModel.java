package com.eleybourn.bookcatalogue.entities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;

/**
 * This is the (obvious) replacement of the homegrown BookManager in previous commits.
 *
 * Used by the set of fragments that allow viewing and editing a Book.
 *
 * Holds the {@link Book} and whether it's dirty or not.
 */
public class BookModel
        extends ViewModel {

    /** Flag to indicate we're dirty. */
    private boolean mIsDirty;
    private Book book;

    /**
     * Conditional constructor.
     * If we already have been initialized, return silently.
     * Otherwise use the passed data to construct a Book.
     */
    public void init(@Nullable final Bundle args,
                     @NonNull final DBA db) {
        if (book == null) {
            if (args != null) {
                // load the book data
                Bundle bookData = args.getBundle(UniqueId.BKEY_BOOK_DATA);
                if (bookData != null) {
                    // if we have a populated bundle, e.g. after an internet search, use that.
                    setBook(new Book(bookData));
                } else {
                    // otherwise, check if we have an id, e.g. user clicked on a book in a list.
                    long bookId = args.getLong(DBDefinitions.KEY_ID, 0);
                    // If the id is valid, load from database.
                    // or if it's 0, create a new 'empty' book. Because paranoia.
                    setBook(new Book(bookId, db));
                }
            } else {
                // no args, we want a new book (e.g. user wants to add one manually).
                setBook(new Book());
            }
        }
    }

    /**
     * @return {@code true} if our data was changed.
     */
    public boolean isDirty() {
        return mIsDirty;
    }

    /**
     * @param isDirty set to {@code true} if our data was changed.
     */
    public void setDirty(final boolean isDirty) {
        mIsDirty = isDirty;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(@NonNull final Book book) {
        this.book = book;
    }
}
