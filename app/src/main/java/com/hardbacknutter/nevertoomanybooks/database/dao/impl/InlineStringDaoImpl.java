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
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.InlineStringDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;

public abstract class InlineStringDaoImpl
        extends BaseDaoImpl
        implements InlineStringDao {

    /** name only. */
    private final String mSqlSelectAll;

    /** Global rename. */
    private final String mSqlUpdate;

    /**
     * Constructor.
     *
     * @param logTag of this DAO for logging.
     */
    InlineStringDaoImpl(@NonNull final String logTag,
                        @NonNull final String key) {
        super(logTag);

        mSqlSelectAll = SELECT_DISTINCT_ + key
                        + _FROM_ + DBDefinitions.TBL_BOOKS.getName()
                        + _WHERE_ + key + "<> ''"
                        + _ORDER_BY_ + key + _COLLATION;

        mSqlUpdate = UPDATE_ + DBDefinitions.TBL_BOOKS.getName()
                     + _SET_ + DBKey.UTC_DATE_LAST_UPDATED + "=current_timestamp"
                     + ',' + key + "=?"
                     + _WHERE_ + key + "=?";
    }

    @Override
    @NonNull
    public ArrayList<String> getList() {
        return getColumnAsStringArrayList(mSqlSelectAll);
    }

    @Override
    public void rename(@NonNull final String from,
                       @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = mDb.compileStatement(mSqlUpdate)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }
}
