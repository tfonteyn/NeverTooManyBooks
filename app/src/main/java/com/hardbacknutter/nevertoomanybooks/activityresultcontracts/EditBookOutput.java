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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public final class EditBookOutput {

    private static final String TAG = "EditBookOutput";

    private static final String BKEY_MODIFIED = TAG + ":m";
    /** The BoB should reposition on this book. */
    public final long repositionToBookId;

    /** SOMETHING was modified. This normally means that BoB will need to rebuild. */
    public final boolean modified;

    private EditBookOutput(final long repositionToBookId,
                           final boolean modified) {
        this.repositionToBookId = repositionToBookId;
        this.modified = modified;
    }

    /**
     * Create the result which {@link ActivityResultContract#parseResult(int, Intent)} will receive.
     *
     * @return Intent
     */
    @NonNull
    public static Intent createResult(final long bookId,
                                      final boolean modified) {
        return new Intent().putExtra(DBKey.FK_BOOK, bookId)
                           .putExtra(BKEY_MODIFIED, modified);
    }

    public static void toBundle(@NonNull final EditBookOutput input,
                                @NonNull final Bundle output) {
        output.putLong(DBKey.FK_BOOK, input.repositionToBookId);
        output.putBoolean(BKEY_MODIFIED, input.modified);
    }

    public static EditBookOutput parseResult(@NonNull final Intent intent) {
        final long repositionToBookId =
                intent.getLongExtra(DBKey.FK_BOOK, 0);
        final boolean modified =
                intent.getBooleanExtra(BKEY_MODIFIED, false);

        return new EditBookOutput(repositionToBookId, modified);
    }

}
