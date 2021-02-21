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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;

public final class LocationDao
        extends BaseDao {

    /** Log tag. */
    private static final String TAG = "LocationDao";

    /** name only. */
    private static final String LOCATIONS =
            SELECT_DISTINCT_ + KEY_LOCATION
            + _FROM_ + TBL_BOOKS.getName()
            + _WHERE_ + KEY_LOCATION + "<> ''"
            + _ORDER_BY_ + KEY_LOCATION + _COLLATION;

    /** Global rename. */
    private static final String LOCATION =
            UPDATE_ + TBL_BOOKS.getName()
            + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
            + ',' + KEY_LOCATION + "=?"
            + _WHERE_ + KEY_LOCATION + "=?";

    /** Singleton. */
    private static LocationDao sInstance;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    private LocationDao(@NonNull final Context context,
                        @NonNull final String logTag) {
        super(context, logTag);
    }

    public static LocationDao getInstance() {
        if (sInstance == null) {
            sInstance = new LocationDao(App.getDatabaseContext(), TAG);
        }

        return sInstance;
    }

    /**
     * Get a unique list of all locations.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getList() {
        try (Cursor cursor = mDb.rawQuery(LOCATIONS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void update(@NonNull final String from,
                       @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = mDb.compileStatement(LOCATION)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }
}
