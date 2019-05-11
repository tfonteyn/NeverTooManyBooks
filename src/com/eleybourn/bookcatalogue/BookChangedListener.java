package com.eleybourn.bookcatalogue;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.debug.MustImplementException;

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
     * Convenience method. Try in order:
     * <ul>
     * <li>getTargetFragment()</li>
     * <li>getParentFragment()</li>
     * <li>getActivity()</li>
     * </ul>
     */
    static void onBookChanged(@NonNull final Fragment sourceFragment,
                              final long bookId,
                              final int fieldsChanged,
                              @Nullable final Bundle data) {

        if (sourceFragment.getTargetFragment() instanceof BookChangedListener) {
            ((BookChangedListener) sourceFragment.getTargetFragment())
                    .onBookChanged(bookId, fieldsChanged, data);
        } else if (sourceFragment.getParentFragment() instanceof BookChangedListener) {
            ((BookChangedListener) sourceFragment.getParentFragment())
                    .onBookChanged(bookId, fieldsChanged, data);
        } else if (sourceFragment.getActivity() instanceof BookChangedListener) {
            ((BookChangedListener) sourceFragment.getActivity())
                    .onBookChanged(bookId, fieldsChanged, data);
        } else {
            throw new MustImplementException(BookChangedListener.class);
        }
    }

    /**
     * Called if changes were made.
     *
     * @param bookId        the book that was changed, or 0 if the change was global
     * @param fieldsChanged a bitmask build from the flags of {@link BookChangedListener}
     * @param data          bundle with custom data, can be {@code null}
     */
    void onBookChanged(long bookId,
                       int fieldsChanged,
                       @Nullable Bundle data);
}
