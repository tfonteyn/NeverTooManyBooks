/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.search.SearchBookUpdatesViewModel;

public final class UpdateBooksOutput {

    /** The BoB should reposition on this book. */
    private final long repositionToBookId;

    /** We processed a single book. */
    private final long bookModified;

    /** We processed a list of books. This normally means that BoB will need to rebuild. */
    private final boolean listModified;

    /** We processed a single book. */
    private final long lastBookIdProcessed;

    UpdateBooksOutput(final long repositionToBookId,
                      final long bookModified,
                      final long lastBookIdProcessed,
                      final boolean listModified) {
        this.repositionToBookId = repositionToBookId;
        this.bookModified = bookModified;
        this.listModified = listModified;
        this.lastBookIdProcessed = lastBookIdProcessed;
    }

    /**
     * Create the result which {@link UpdateSingleBookContract#parseResult(int, Intent)}
     * and {@link UpdateBooklistContract#parseResult(int, Intent)} will receive.
     *
     * @param dataHolder the bundle from which we'll extract the relevant data.
     *
     * @return Intent
     */
    public static Intent createResult(@NonNull final DataHolder dataHolder) {
        final Intent resultIntent = new Intent();
        resultIntent.putExtra(DBKey.FK_BOOK, dataHolder.getLong(DBKey.FK_BOOK));

        resultIntent.putExtra(SearchBookUpdatesViewModel.BKEY_BOOK_MODIFIED,
                              dataHolder.getLong(SearchBookUpdatesViewModel.BKEY_BOOK_MODIFIED));
        resultIntent.putExtra(SearchBookUpdatesViewModel.BKEY_LIST_MODIFIED,
                              dataHolder.getBoolean(SearchBookUpdatesViewModel.BKEY_LIST_MODIFIED));
        resultIntent.putExtra(SearchBookUpdatesViewModel.BKEY_LAST_BOOK_ID_PROCESSED,
                              dataHolder.getLong(SearchBookUpdatesViewModel
                                                         .BKEY_LAST_BOOK_ID_PROCESSED));
        return resultIntent;
    }

    public long getRepositionToBookId() {
        return repositionToBookId;
    }

    public long getBookModified() {
        return bookModified;
    }

    public boolean isListModified() {
        return listModified;
    }

    public long getLastBookIdProcessed() {
        return lastBookIdProcessed;
    }
}
