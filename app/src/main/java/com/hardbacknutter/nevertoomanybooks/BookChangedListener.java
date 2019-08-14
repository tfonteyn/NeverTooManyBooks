/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks;

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
