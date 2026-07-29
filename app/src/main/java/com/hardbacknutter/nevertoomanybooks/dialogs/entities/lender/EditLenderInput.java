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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.lender;

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

class EditLenderInput {

    @NonNull
    private final String requestKey;
    private final long bookId;
    @NonNull
    private final String bookTitle;

    EditLenderInput(@NonNull final String requestKey,
                    @IntRange(from = 1) final long bookId,
                    @NonNull final String bookTitle) {
        this.requestKey = requestKey;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
    }

    @NonNull
    static EditLenderInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);
        final long bookId = args.getLong(DBKey.FK_BOOK, 0);
        final String bookTitle = Objects.requireNonNull(args.getString(DBKey.TITLE), DBKey.TITLE);

        return new EditLenderInput(requestKey, bookId, bookTitle);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(3);
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putLong(DBKey.FK_BOOK, bookId);
        args.putString(DBKey.TITLE, bookTitle);

        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    long getBookId() {
        return bookId;
    }

    @NonNull
    String getBookTitle() {
        return bookTitle;
    }
}
