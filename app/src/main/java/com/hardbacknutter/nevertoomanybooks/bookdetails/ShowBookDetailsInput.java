/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.bookdetails;

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public final class ShowBookDetailsInput {

    private static final String TAG = "ShowBookDetailsInput";

    /**
     * Whether {@link ShowBookDetailsFragment} and its related child fragments
     * is running in embedded mode (i.e. inside a frame on the BoB screen) or not.
     * We could (should?) use a boolean resource in "sw800-land" instead.
     */
    private static final String BKEY_EMBEDDED = TAG + ":bd-embedded";
    @IntRange(from = 1)
    private final long bookId;
    @NonNull
    private final Bookshelf bookshelf;
    private final boolean embedded;

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public ShowBookDetailsInput(final long bookId,
                         @NonNull final Bookshelf bookshelf,
                         final boolean embedded) {
        this.bookId = bookId;
        this.bookshelf = bookshelf;
        this.embedded = embedded;
    }

    @NonNull
    static ShowBookDetailsInput fromBundle(@NonNull final Bundle args) {
        @SuppressWarnings("deprecation")
        final Bookshelf bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                                           DBKey.FK_BOOKSHELF);
        final boolean embedded = args.getBoolean(BKEY_EMBEDDED, false);
        final long bookId = args.getLong(DBKey.FK_BOOK, 0);

        return new ShowBookDetailsInput(bookId, bookshelf, embedded);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(3);
        args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);
        args.putBoolean(BKEY_EMBEDDED, embedded);
        args.putLong(DBKey.FK_BOOK, bookId);

        return args;
    }

    long getBookId() {
        return bookId;
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    boolean isEmbedded() {
        return embedded;
    }
}
