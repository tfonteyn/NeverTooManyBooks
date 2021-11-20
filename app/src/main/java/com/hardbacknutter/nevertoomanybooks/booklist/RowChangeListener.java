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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.os.Bundle;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Allows to be notified of changes made.
 * The bit number are not stored and can be changed.
 * <p>
 * If a book id is passed back, it should be available
 * in {@code data.getLong(DBDefinitions.KEY_FK_BOOK)}.
 */
public interface RowChangeListener
        extends FragmentResultListener {

    String REQUEST_KEY = "rk:RowChangeListener";

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

    /* private. */ String ITEM_ID = "item";
    /* private. */ String CHANGE = "change";

    /**
     * Notify changes where made.
     *
     * @param requestKey for use with the FragmentResultListener
     * @param change     what changed
     * @param id         the item being modified,
     *                   or {@code 0} for a global change or for an books-table inline item
     */
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @Change final int change,
                          final long id) {
        final Bundle result = new Bundle(2);
        result.putInt(CHANGE, change);
        result.putLong(ITEM_ID, id);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    @Override
    default void onFragmentResult(@NonNull final String requestKey,
                                  @NonNull final Bundle result) {
        onChange(result.getInt(CHANGE),
                 result.getLong(ITEM_ID));
    }

    /**
     * Called if changes were made.
     *
     * @param change what changed
     * @param id     the item being modified, or {@code 0} for a global change
     */
    void onChange(@Change int change,
                  @IntRange(from = 0) long id);

    @IntDef({AUTHOR, SERIES, PUBLISHER, BOOKSHELF, TOC_ENTRY,
             FORMAT, COLOR, GENRE, LANGUAGE, LOCATION})
    @Retention(RetentionPolicy.SOURCE)
    @interface Change {

    }

    @IntDef({BOOK_READ, BOOK_LOANEE, BOOK_DELETED})
    @Retention(RetentionPolicy.SOURCE)
    @interface BookChange {

    }
}
