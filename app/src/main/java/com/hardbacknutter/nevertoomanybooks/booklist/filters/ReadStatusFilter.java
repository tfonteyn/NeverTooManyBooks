/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.ReadStatus;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * A persistable {@link Filter}.
 * <p>
 * Specific for the {@link ReadStatus} values.
 *
 * <ul>
 * <li>The value is a {@code Set<Long>} with the key being the {@link ReadStatus} id.</li>
 * <li>The Set is never {@code null}.</li>
 * <li>An empty Set indicates an inactive filter.</li>
 * </ul>
 */
public class ReadStatusFilter
        extends PEntityListFilter<ReadStatus> {

    ReadStatusFilter() {
        super(DBKey.READ__BOOL, R.string.lbl_read, DBDefinitions.TBL_BOOKS,
              DBDefinitions.DOM_BOOK_READ, ReadStatus::getAll);
    }

    @NonNull
    @Override
    public String getExpression(@NonNull final Context context) {
        final StringJoiner sj = new StringJoiner(" OR ", "(", ")");

        // Finished reading
        final boolean isRead = value.contains(ReadStatus.Read.getId());
        if (isRead) {
            sj.add(ReadStatus.W_READ);
        }

        // Currently reading
        final boolean isReading = value.contains(ReadStatus.Reading.getId());
        if (isReading) {
            sj.add(ReadStatus.W_READING);
        }

        // Optimization:
        // Books which have been "Read" or "Reading" will never be "Unread",
        // so do NOT add the "Unread" condition in that situation.
        if (!(isRead || isReading)) {
            // Never read
            if (value.contains(ReadStatus.Unread.getId())) {
                sj.add(ReadStatus.W_UNREAD);
            }
        }

        return sj.toString();
    }
}
