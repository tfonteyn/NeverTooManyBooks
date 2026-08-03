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

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

class EditTocEntryOutput
        implements LauncherOutput {

    private static final String TAG = "EditTocEntryOutput";
    private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";
    private static final String BKEY_POSITION = TAG + ":pos";

    @NonNull
    private final TocEntry tocEntry;
    private final int position;

    EditTocEntryOutput(@NonNull final TocEntry tocEntry,
                       final int position) {
        this.tocEntry = tocEntry;
        this.position = position;
    }

    @NonNull
    static EditTocEntryOutput fromBundle(@NonNull final Bundle args) {
        @SuppressWarnings("deprecation")
        final TocEntry tocEntry = Objects.requireNonNull(args.getParcelable(BKEY_TOC_ENTRY),
                                                         BKEY_TOC_ENTRY);
        final int position = args.getInt(BKEY_POSITION);

        return new EditTocEntryOutput(tocEntry, position);
    }

    @NonNull
    @Override
    public Bundle toBundle() {
        final Bundle args = new Bundle(2);
        args.putParcelable(BKEY_TOC_ENTRY, tocEntry);
        args.putInt(BKEY_POSITION, position);

        return args;
    }

    @NonNull
    public TocEntry getTocEntry() {
        return tocEntry;
    }

    public int getPosition() {
        return position;
    }

    @Override
    @NonNull
    public String toString() {
        return "EditTocEntryOutput{"
               + "tocEntry=" + tocEntry
               + ", position=" + position
               + '}';
    }
}
