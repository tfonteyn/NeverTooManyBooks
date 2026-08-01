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

package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public class EditBookshelvesOutput {

    private static final String BKEY_MODIFIED = "modified";

    private final long selectedBookshelfId;
    private final boolean modified;

    /**
     * Constructor.
     *
     * @param selectedBookshelfId id, or {@code 0} for none
     * @param modified            was anything at all modified
     */
    public EditBookshelvesOutput(final long selectedBookshelfId,
                                 final boolean modified) {
        this.selectedBookshelfId = selectedBookshelfId;
        this.modified = modified;
    }

    @NonNull
    static EditBookshelvesOutput fromBundle(@NonNull final Bundle result) {
        final long id = result.getLong(DBKey.FK_BOOKSHELF, 0);
        final boolean modified = result.getBoolean(BKEY_MODIFIED, false);

        return new EditBookshelvesOutput(id, modified);
    }

    @NonNull
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putLong(DBKey.FK_BOOKSHELF, selectedBookshelfId);
        args.putBoolean(BKEY_MODIFIED, modified);

        return args;
    }

    public long getSelectedBookshelfId() {
        return selectedBookshelfId;
    }

    /**
     * Was anything at all modified.
     *
     * @return flag
     */
    public boolean isModified() {
        return modified;
    }
}
