/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.os.Bundle;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

/**
 * Allows to be notified of changes made.
 * The bit number are not stored and can be changed.
 * <p>
 * If a book id is passed back, it should be available
 * in {@code data.getLong(DBDefinitions.KEY_FK_BOOK)}.
 */
public interface ChangeListener
        extends FragmentResultListener {

    String REQUEST_KEY = "rk:ChangeListener";

    // Note that BOOK is missing here.
    // It's implied if a bookId is passed back, or if the context makes it clear anyhow.
    int AUTHOR = 1;
    int SERIES = 1 << 1;
    int PUBLISHER = 1 << 2;
    int BOOKSHELF = 1 << 3;
    int TOC_ENTRY = 1 << 4;

    int FORMAT = 1 << 5;
    int COLOR = 1 << 6;
    int GENRE = 1 << 7;
    int LANGUAGE = 1 << 8;
    int LOCATION = 1 << 9;

    /** A book was set to read/unread. */
    int BOOK_READ = 1 << 10;

    /**
     * A book was either lend out, or returned.
     * <p>
     * When lend out:  data.putString(DBDefinitions.KEY_LOANEE, mLoanee);
     * When returned: data == null
     */
    int BOOK_LOANEE = 1 << 11;

    /** A book was deleted. */
    int BOOK_DELETED = 1 << 12;

    /* private. */ String FLAGS = "flags";

    /**
     * Notify changes where made.
     *
     * @param requestKey for use with the FragmentResultListener
     * @param flags      flags
     */
    static void update(@NonNull final Fragment fragment,
                       @NonNull final String requestKey,
                       @Flags final int flags) {
        final Bundle result = new Bundle(1);
        result.putInt(FLAGS, flags);
        // Currently not adding DBDefinitions.KEY_FK_BOOK as it's always 0
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    @Override
    default void onFragmentResult(@NonNull final String requestKey,
                                  @NonNull final Bundle result) {
        onChange(result.getLong(DBDefinitions.KEY_FK_BOOK), result.getInt(FLAGS));
    }

    /**
     * Called if changes were made.
     *
     * @param bookId the book id being modified, or {@code 0} for a global change
     * @param flags  bitmask
     */
    void onChange(long bookId,
                  @Flags int flags);

    @IntDef(flag = true, value = {AUTHOR, SERIES, PUBLISHER, BOOKSHELF, TOC_ENTRY,
                                  FORMAT, COLOR, GENRE, LANGUAGE, LOCATION,
                                  BOOK_READ, BOOK_LOANEE, BOOK_DELETED})
    @Retention(RetentionPolicy.SOURCE)
    @interface Flags {

    }
}
