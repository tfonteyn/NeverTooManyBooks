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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities.tocentry;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

class EditTocEntryInput {

    private static final String TAG = "EditTocEntryInput";
    private static final String BKEY_ANTHOLOGY = TAG + ":anthology";
    private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";
    private static final String BKEY_POSITION = TAG + ":pos";

    @NonNull
    private final TocEntry tocEntry;
    private final int position;

    private final boolean isAnthology;
    @NonNull
    private final String requestKey;
    @Nullable
    private final String bookTitle;

    EditTocEntryInput(@NonNull final String requestKey,
                      @Nullable final String bookTitle,
                      final int position,
                      @NonNull final TocEntry tocEntry,
                      final boolean isAnthology) {
        this.requestKey = requestKey;
        this.bookTitle = bookTitle;
        this.position = position;
        this.tocEntry = tocEntry;
        this.isAnthology = isAnthology;
    }

    @NonNull
    static EditTocEntryInput fromBundle(@NonNull final Bundle args) {
        final String requestKey = Objects.requireNonNull(
                args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                DialogLauncher.BKEY_REQUEST_KEY);

        final String bookTitle = args.getString(DBKey.TITLE);
        final int position = args.getInt(BKEY_POSITION, 0);
        @SuppressWarnings("deprecation")
        final TocEntry tocEntry = Objects.requireNonNull(
                args.getParcelable(BKEY_TOC_ENTRY), BKEY_TOC_ENTRY);
        final boolean isAnthology = args.getBoolean(BKEY_ANTHOLOGY, false);

        return new EditTocEntryInput(requestKey, bookTitle, position, tocEntry, isAnthology);
    }

    @NonNull
    Bundle toBundle() {
        final Bundle args = new Bundle(5);
        args.putString(DialogLauncher.BKEY_REQUEST_KEY, requestKey);
        args.putString(DBKey.TITLE, bookTitle);
        args.putInt(BKEY_POSITION, position);
        args.putParcelable(BKEY_TOC_ENTRY, tocEntry);
        args.putBoolean(BKEY_ANTHOLOGY, isAnthology);

        return args;
    }

    @NonNull
    String getRequestKey() {
        return requestKey;
    }

    @Nullable
    String getBookTitle() {
        return bookTitle;
    }

    int getPosition() {
        return position;
    }

    @NonNull
    TocEntry getTocEntry() {
        return tocEntry;
    }

    boolean isAnthology() {
        return isAnthology;
    }
}
