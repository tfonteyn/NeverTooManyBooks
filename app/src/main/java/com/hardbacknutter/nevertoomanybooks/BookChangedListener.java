/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Allows to be notified of changes made to book(s).
 */
public interface BookChangedListener {

    /** Author was modified. */
    int AUTHOR = 1;
    /** Series was modified. */
    int SERIES = 1 << 1;
    /** ... */
    int PUBLISHER = 1 << 2;

    int TOC_ENTRY = 1 << 3;

    int FORMAT = 1 << 4;
    int COLOR = 1 << 5;
    int GENRE = 1 << 6;
    int LANGUAGE = 1 << 7;
    int LOCATION = 1 << 8;

    /** A book was set to read/unread. */
    int BOOK_READ = 1 << 9;

    /**
     * A book was either lend out, or returned.
     * <p>
     * When lend out:  data.putString(DBDefinitions.KEY_LOANEE, mLoanee);
     * When returned: data == null
     */
    int BOOK_LOANEE = 1 << 10;

    /** A book was deleted. */
    int BOOK_DELETED = 1 << 11;

    /**
     * Called if changes were made.
     *
     * @param bookId        the book that was changed, or 0 if the change was global
     * @param fieldsChanged a bitmask build from the flags
     * @param data          bundle with custom data, can be {@code null}
     */
    void onChange(long bookId,
                  @Flags int fieldsChanged,
                  @Nullable Bundle data);

    @IntDef(flag = true, value = {AUTHOR, SERIES, PUBLISHER, TOC_ENTRY,
                                  FORMAT, COLOR, GENRE, LANGUAGE, LOCATION,
                                  BOOK_READ, BOOK_LOANEE, BOOK_DELETED})
    @Retention(RetentionPolicy.SOURCE)
    @interface Flags {

    }

    interface Owner {

        /**
         * Call this from {@code onAttachFragment} in the caller.
         *
         * @param listener the object to send the result to.
         */
        void setListener(@NonNull BookChangedListener listener);
    }
}
