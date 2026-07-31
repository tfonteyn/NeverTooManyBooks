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

package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class EditBookInput {
    final long bookId;
    @Nullable
    final Book book;

    @NonNull
    final String styleUuid;

    /**
     * Add/Edit a <strong>new</strong> book, typically data as retrieved after an
     * internet search, or a copy of an existing book.
     * <p>
     * This is meant for book(data) <strong>without</strong> an {@code id}.
     *
     * @param book  data
     * @param style to use
     */
    public EditBookInput(@NonNull final Book book,
                         @NonNull final Style style) {
        this.bookId = 0;
        this.book = book;
        this.styleUuid = style.getUuid();
    }

    /**
     * Edit an <strong>existing</strong> book.
     *
     * @param bookId of the book; can be {@code 0} for a new empty book.
     * @param style  to use
     */
    public EditBookInput(@IntRange(from = 0) final long bookId,
                         @NonNull final Style style) {
        this.bookId = bookId;
        this.book = null;
        this.styleUuid = style.getUuid();
    }

    private EditBookInput(@IntRange(from = 0) final long bookId,
                          @Nullable final Book book,
                          @NonNull final String styleUuid) {
        this.bookId = bookId;
        this.book = book;
        this.styleUuid = styleUuid;
    }

    @NonNull
    public static EditBookInput fromBundle(@NonNull final Bundle args) {
        @SuppressWarnings("deprecation")
        final Book bookFromArguments = args.getParcelable(Book.BKEY_BOOK_DATA);
        final long bookId = args.getLong(DBKey.FK_BOOK, 0);
        final String styleUuid = Objects.requireNonNull(args.getString(Style.BKEY_UUID));

        return new EditBookInput(bookId, bookFromArguments, styleUuid);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putString(Style.BKEY_UUID, styleUuid);
        if (book != null) {
            args.putParcelable(Book.BKEY_BOOK_DATA, book);
        } else {
            args.putLong(DBKey.FK_BOOK, bookId);
        }

        return args;
    }

    @IntRange(from = 0)
    public long getBookId() {
        return bookId;
    }

    @Nullable
    public Book getBook() {
        return book;
    }

    @NonNull
    public String getStyleUuid() {
        return styleUuid;
    }
}
