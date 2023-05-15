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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.InlineStringDao;

public abstract class InlineStringDaoImpl
        extends BaseDaoImpl
        implements InlineStringDao {

    /** name only. */
    @NonNull
    private final String sqlSelectAll;

    /** Global rename. */
    @NonNull
    private final String sqlUpdate;

    /**
     * Constructor.
     *
     * @param db         Underlying database
     * @param logTag     of this DAO for logging
     * @param columnName the name of the inline column
     */
    InlineStringDaoImpl(@NonNull final SynchronizedDb db,
                        @NonNull final String logTag,
                        @NonNull final String columnName) {
        super(db, logTag);

        sqlSelectAll = SELECT_DISTINCT_ + columnName
                       + _FROM_ + DBDefinitions.TBL_BOOKS.getName()
                       + _WHERE_ + columnName + "<> ''"
                       + _ORDER_BY_ + columnName + _COLLATION;

        sqlUpdate = UPDATE_ + DBDefinitions.TBL_BOOKS.getName()
                    + _SET_ + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
                    + ',' + columnName + "=?"
                    + _WHERE_ + columnName + "=?";
    }

    @Override
    @NonNull
    public List<String> getList() {
        return getColumnAsStringArrayList(sqlSelectAll);
    }

    @Override
    public void rename(@NonNull final String from,
                       @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = db.compileStatement(sqlUpdate)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }
}
