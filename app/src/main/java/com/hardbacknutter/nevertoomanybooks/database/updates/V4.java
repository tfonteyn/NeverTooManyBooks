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

package com.hardbacknutter.nevertoomanybooks.database.updates;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_DELETED_BOOKS;

class V4 {

    @NonNull
    private final Context context;
    @NonNull
    private final SQLiteDatabase db;
    private final int oldVersion;

    /**
     * Constructor.
     *
     * @param context    Current context
     * @param db         Underlying database
     * @param oldVersion The old database version.
     */
    V4(@NonNull final Context context,
       @NonNull final SQLiteDatabase db,
       final int oldVersion) {
        this.context = context;
        this.db = db;
        this.oldVersion = oldVersion;
    }

    /**
     * Perform all updates.
     * <p>
     * v4.0.0: 23
     * v4.4.0: 24
     * v4.5.0: 25
     *
     */
    void update() {
        if (oldVersion < 24) {
            db24();
        }
        if (oldVersion < 25) {
            db25();
        }
    }

    private void db24() {
        TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_TRANSLATION_ORIGINAL_TITLE);
    }

    private void db25() {
        TBL_DELETED_BOOKS.create(db, true);
        StartupViewModel.schedule(context, StartupViewModel.PK_REBUILD_FTS, true);
    }
}
