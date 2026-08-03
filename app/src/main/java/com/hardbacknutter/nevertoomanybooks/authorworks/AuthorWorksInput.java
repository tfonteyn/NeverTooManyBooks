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

package com.hardbacknutter.nevertoomanybooks.authorworks;

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class AuthorWorksInput {

    private final long authorId;
    @NonNull
    private final Bookshelf bookshelf;

    /**
     * Constructor.
     *
     * @param authorId  the Author to display
     * @param bookshelf the current Bookshelf
     */
    public AuthorWorksInput(@IntRange(from = 1) final long authorId,
                            @NonNull final Bookshelf bookshelf) {
        this.authorId = authorId;
        this.bookshelf = bookshelf;
    }

    @NonNull
    static AuthorWorksInput fromBundle(@NonNull final Bundle args) {
        final long authorId = args.getLong(DBKey.FK_AUTHOR, 0);
        @SuppressWarnings("deprecation")
        final Bookshelf bookshelf = Objects.requireNonNull(
                args.getParcelable(DBKey.FK_BOOKSHELF), DBKey.FK_BOOKSHELF);

        return new AuthorWorksInput(authorId, bookshelf);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putLong(DBKey.FK_AUTHOR, authorId);
        args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);

        return args;
    }

    long getAuthorId() {
        return authorId;
    }

    @NonNull
    Bookshelf getBookshelf() {
        return bookshelf;
    }
}
