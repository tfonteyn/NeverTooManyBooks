/*
 * @Copyright 2018-2023 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;

/**
 * Used to create an ordering of the read-status.
 * <p>
 * The ID numerical order is such that we can {@link Sort#Desc} on it
 * and get the {@link BooklistGroup#READ_STATUS} to display as:
 * <ul>
 * <li>Reading: most interesting</li>
 * <li>Unread</li>
 * <li>Reading: least interesting</li>
 * </ul>
 * The numerical value {@link #id} is used in SQL but not stored other than in the
 * temporary book-list table.
 * The UI then uses that value to lookup the enum,
 * and calls {@link #getLabel(Context)} for formatting/displaying.
 */
public enum ReadStatus {
    /** Currently reading - the read-start-date is set, the read-end-date is not. */
    Reading(0, R.string.lbl_reading),
    /**
     * {@link com.hardbacknutter.nevertoomanybooks.database.DBDefinitions#DOM_BOOK_READ}
     * is {@code false}.
     */
    Unread(1, R.string.lbl_unread),
    /**
     * {@link com.hardbacknutter.nevertoomanybooks.database.DBDefinitions#DOM_BOOK_READ}
     * is {@code true}.
     */
    Read(2, R.string.lbl_read),
    /**
     * Never used/generated, but serves as a fallback option which should never be seen.
     */
    Unknown(3, R.string.bob_empty_read_status);

    private final int id;
    @StringRes
    private final int labelId;

    ReadStatus(final int id,
               @StringRes final int labelId) {
        this.id = id;
        this.labelId = labelId;
    }

    @NonNull
    public static ReadStatus getById(final int id) {
        return Arrays.stream(values()).filter(v -> v.id == id).findFirst().orElse(Unknown);
    }

    public int getId() {
        return id;
    }

    @NonNull
    public String getLabel(@NonNull final Context context) {
        return context.getString(labelId);
    }
}
