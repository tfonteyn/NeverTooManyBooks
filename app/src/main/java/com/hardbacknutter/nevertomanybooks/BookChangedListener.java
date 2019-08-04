package com.hardbacknutter.nevertomanybooks;

import android.os.Bundle;

import androidx.annotation.Nullable;

/**
 * Allows to be notified of changes made to book(s).
 */
public interface BookChangedListener {

    int AUTHOR = 1;
    int SERIES = 1 << 1;

    int FORMAT = 1 << 2;
    int GENRE = 1 << 3;
    int LANGUAGE = 1 << 4;
    int LOCATION = 1 << 5;
    int PUBLISHER = 1 << 6;

    /** The book was set to read/unread. */
    int BOOK_READ = 1 << 7;

    /** the book was either lend out, or returned. */
    int BOOK_LOANEE = 1 << 8;

    /**
     * not really a field, but we want to be able to return the deleted bookId AND indicate
     * it was deleted.
     */
    int BOOK_WAS_DELETED = 1 << 9;

    /**
     * Called if changes were made.
     *
     * @param bookId        the book that was changed, or 0 if the change was global
     * @param fieldsChanged a bitmask build from the flags
     * @param data          bundle with custom data, can be {@code null}
     */
    void onBookChanged(long bookId,
                       int fieldsChanged,
                       @Nullable Bundle data);
}
