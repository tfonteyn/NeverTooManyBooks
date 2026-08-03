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

import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

class ShowTocInput {

    private static final String TAG = "ShowTocInput";

    private static final String BKEY_EMBEDDED = TAG + ":toc-embedded";

    private final long bookId;
    private final boolean embedded;
    @NonNull
    private final Bookshelf bookshelf;

    ShowTocInput(final long bookId,
                 final boolean embedded,
                 @NonNull final Bookshelf bookshelf) {
        this.bookId = bookId;
        this.embedded = embedded;
        this.bookshelf = bookshelf;
    }

    @NonNull
    static ShowTocInput fromBundle(@NonNull final Bundle args) {
        @SuppressWarnings("deprecation")
        final Bookshelf bookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                                           DBKey.FK_BOOKSHELF);
        final boolean embedded = args.getBoolean(ShowTocInput.BKEY_EMBEDDED, false);
        final long bookId = args.getLong(DBKey.FK_BOOK, 0);

        return new ShowTocInput(bookId, embedded, bookshelf);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(3);
        args.putBoolean(BKEY_EMBEDDED, embedded);
        args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);
        args.putLong(DBKey.FK_BOOK, bookId);

        return args;
    }

    long getBookId() {
        return bookId;
    }

    boolean isEmbedded() {
        return embedded;
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }

    @Override
    @NonNull
    public String toString() {
        return "ShowTocInput{"
               + "bookId=" + bookId
               + ", embedded=" + embedded
               + ", bookshelf=" + bookshelf
               + '}';
    }
}
