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
 * The ID numerical order is such that we can {@link Sort#Desc} on it
 * and get the {@link BooklistGroup#READ_STATUS} to display as:
 * <ul>
 * <li>Reading: most interesting</li>
 * <li>Unread</li>
 * <li>Reading: least interesting</li>
 * </ul>
 * The 'Unknown' status is never generated, but used as
 * a fallback option which should never be seen.
 */
public enum ReadStatus {
    Reading(0, R.string.lbl_reading),
    Unread(1, R.string.lbl_unread),
    Read(2, R.string.lbl_read),
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
