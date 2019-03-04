package com.eleybourn.bookcatalogue;

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

    int BOOK_READ = 1 << 7;
    int BOOK_LOANEE = 1 << 8;

    /**
     * Called if changes were made.
     *
     * @param bookId        the book that was changed, or 0 if the change was global
     * @param fieldsChanged a bitmask build from the flags of {@link BookChangedListener}
     * @param data          bundle with custom data, can be null
     */
    void onBookChanged(long bookId,
                       int fieldsChanged,
                       @Nullable Bundle data);
}
