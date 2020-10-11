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
import androidx.annotation.Nullable;
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
public interface ChangeListener
        extends FragmentResultListener {

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

    /* private. */ String CHANGES = "changes";
    /* private. */ String DATA = "data";

    /**
     * Notify changes where made.
     *
     * @param changes flags
     * @param data    (optional) data bundle with details
     */
    static void update(@NonNull final Fragment fragment,
                       @NonNull final String requestKey,
                       @Changes int changes,
                       @Nullable final Bundle data) {
        final Bundle result = new Bundle(2);
        result.putInt(CHANGES, changes);
        result.putBundle(DATA, data);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    @Override
    default void onFragmentResult(@NonNull final String requestKey,
                                  @NonNull final Bundle result) {
        onChange(result.getInt(CHANGES), result.getBundle(DATA));
    }

    /**
     * Called if changes were made.
     *
     * @param changes a bitmask build from the flags
     * @param data    bundle with custom data, can be {@code null}
     */
    void onChange(@Changes int changes,
                  @Nullable Bundle data);

    @IntDef(flag = true, value = {AUTHOR, SERIES, PUBLISHER, BOOKSHELF, TOC_ENTRY,
                                  FORMAT, COLOR, GENRE, LANGUAGE, LOCATION,
                                  BOOK_READ, BOOK_LOANEE, BOOK_DELETED})
    @Retention(RetentionPolicy.SOURCE)
    @interface Changes {

    }
}
