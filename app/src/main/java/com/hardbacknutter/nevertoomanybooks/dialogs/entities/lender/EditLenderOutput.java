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
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

public class EditLenderOutput
        implements LauncherOutput {
    @IntRange(from = 1)
    private final long bookId;
    @Nullable
    private final String loanee;

    /**
     * Constructor.
     *
     * @param bookId the id of the lent book
     * @param loanee the name of the loanee,
     *               or {@code null} / {@code ""} for a returned book
     */
    EditLenderOutput(final long bookId,
                     @Nullable final String loanee) {
        this.bookId = bookId;
        this.loanee = loanee;
    }

    @NonNull
    static EditLenderOutput fromBundle(final Bundle result) {
        final long bookId = result.getLong(DBKey.FK_BOOK);
        final String loanee = result.getString(DBKey.LOANEE_NAME);

        return new EditLenderOutput(bookId, loanee);
    }

    @NonNull
    @Override
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putLong(DBKey.FK_BOOK, bookId);
        if (loanee != null) {
            args.putString(DBKey.LOANEE_NAME, loanee);
        }

        return args;
    }

    long getBookId() {
        return bookId;
    }

    @Nullable
    String getLoanee() {
        return loanee;
    }

    @Override
    @NonNull
    public String toString() {
        return "EditLenderOutput{"
               + "bookId=" + bookId
               + ", loanee='" + loanee + '\''
               + '}';
    }
}
