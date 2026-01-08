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

package com.hardbacknutter.nevertoomanybooks.booklist.grouping;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * Used to create an ordering of the read-status.
 * <p>
 * The ID numerical order is such that we can {@link Sort#Desc} on it
 * and get the {@link BooklistGroup#READ_STATUS} to display as:
 * <ul>
 * <li>Reading: most interesting</li>
 * <li>Unread</li>
 * <li>Read: least interesting</li>
 * </ul>
 * The numerical value {@link #id} is used in SQL but not stored other than in the
 * temporary book-list table.
 * The UI then uses that value to look up the enum,
 * and calls {@link #getLabel(Context)} for formatting/displaying.
 * <p>
 *  <strong>Never change the ID values</strong>, they get stored in the db.
 */
public enum ReadStatus
        implements Entity {
    /**
     * Currently reading - the read-start-date is set, the read-end-date is not.
     */
    Reading(1),
    /**
     * {@link com.hardbacknutter.nevertoomanybooks.database.DBDefinitions#DOM_BOOK_READ}
     * is {@code false}.
     */
    Unread(2),
    /**
     * {@link com.hardbacknutter.nevertoomanybooks.database.DBDefinitions#DOM_BOOK_READ}
     * is {@code true}.
     */
    Read(3),
    /**
     * Never used/generated, but serves as a fallback option which should never be seen.
     */
    Unknown(0);

    /** WHEN/WHERE clause. */
    public static final String W_READING =
            DBDefinitions.TBL_BOOKS.dot(DBKey.READ_START__DATE) + "<>''"
            + " AND " + DBDefinitions.TBL_BOOKS.dot(DBKey.READ_END__DATE) + "=''";
    /** WHEN/WHERE clause. */
    public static final String W_READ = DBDefinitions.TBL_BOOKS.dot(DBKey.READ__BOOL) + "=1";
    /** WHEN/WHERE clause. */
    public static final String W_UNREAD = DBDefinitions.TBL_BOOKS.dot(DBKey.READ__BOOL) + "=0";

    private final int id;

    ReadStatus(final int id) {
        this.id = id;
    }

    /**
     * Get the list of all status value.
     * <p>
     * <strong>Excludes</strong> the unknown status.
     * <p>
     * Uses the same order as the numerical order.
     *
     * @return list
     */
    @NonNull
    public static List<ReadStatus> getAll() {
        return List.of(Reading, Unread, Read);
    }

    /**
     * Lookup by id.
     * <p>
     * Import/Export and database usage only.
     *
     * @param id to lookup
     *
     * @return type; or {@link #Unknown} for any invalid id.
     */
    @NonNull
    public static ReadStatus byId(final int id) {
        return Arrays.stream(values())
                     .filter(v -> v.id == id)
                     .findFirst()
                     .orElse(Unknown);
    }

    /**
     * Get the internal id.
     * <p>
     * Import/Export and database usage only.
     *
     * @return id
     */
    public long getId() {
        return id;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context,
                           @Nullable final Details details,
                           @Nullable final Style style) {
        return context.getResources().getStringArray(R.array.lbl_read_status)[id];
    }
}
