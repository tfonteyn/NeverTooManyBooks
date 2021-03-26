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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.GenreDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;

public class GenreDaoImpl
        extends BaseDaoImpl
        implements GenreDao {

    /** Log tag. */
    private static final String TAG = "GenreDaoImpl";

    /** name only. */
    private static final String SELECT_ALL =
            SELECT_DISTINCT_ + DBKeys.KEY_GENRE
            + _FROM_ + DBDefinitions.TBL_BOOKS.getName()
            + _WHERE_ + DBKeys.KEY_GENRE + "<> ''"
            + _ORDER_BY_ + DBKeys.KEY_GENRE + _COLLATION;

    /** Global rename. */
    private static final String RENAME =
            UPDATE_ + DBDefinitions.TBL_BOOKS.getName()
            + _SET_ + DBKeys.KEY_UTC_LAST_UPDATED + "=current_timestamp"
            + ',' + DBKeys.KEY_GENRE + "=?"
            + _WHERE_ + DBKeys.KEY_GENRE + "=?";

    /**
     * Constructor.
     */
    public GenreDaoImpl() {
        super(TAG);
    }

    @Override
    @NonNull
    public ArrayList<String> getList() {
        return getColumnAsStringArrayList(SELECT_ALL);
    }

    @Override
    public void rename(@NonNull final String from,
                       @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = mDb.compileStatement(RENAME)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }
}
