package com.eleybourn.bookcatalogue;

import android.os.Bundle;

import androidx.annotation.Nullable;

/**
 * Allows to be notified of changes made to book(s).
 */
public interface BookChangedListener {

    int FLAG_AUTHOR = 1;
    int FLAG_SERIES = 1 << 1;

    int FLAG_FORMAT = 1 << 2;
    int FLAG_GENRE = 1 << 3;
    int FLAG_LANGUAGE = 1 << 4;
    int FLAG_LOCATION = 1 << 5;
    int FLAG_PUBLISHER = 1 << 6;

    int FLAG_BOOK_READ = 1 << 7;
    int FLAG_BOOK_LOANEE = 1 << 8;

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
