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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.StripInfoDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class StripInfoDaoImpl
        extends BaseDaoImpl
        implements StripInfoDao {

    private static final String TAG = "StripInfoDaoImpl";

    private static final String INSERT =
            INSERT_INTO_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName()
            + '(' + DBKey.FK_BOOK
            + ',' + DBKey.SID_STRIP_INFO
            + ',' + DBKey.STRIP_INFO_COLL_ID
            + ',' + DBKey.STRIP_INFO_OWNED
            + ',' + DBKey.STRIP_INFO_DIGITAL
            + ',' + DBKey.STRIP_INFO_WANTED
            + ',' + DBKey.STRIP_INFO_AMOUNT
            + ',' + DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC
            + ") VALUES (?,?,?,?,?,?,?,?)";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public StripInfoDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    public void updateOrInsert(@NonNull final Book book)
            throws DaoWriteException {
        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // Just delete all current data and insert from scratch.
        delete(book);
        insert(book);
    }

    @Override
    public void insert(@NonNull final Book book)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!book.contains(DBKey.SID_STRIP_INFO)) {
                throw new IllegalStateException("No StripInfo data");
            }
        }

        try (SynchronizedStatement stmt = db.compileStatement(INSERT)) {
            int i = 0;
            stmt.bindLong(++i, book.getId());
            stmt.bindLong(++i, book.getInt(DBKey.SID_STRIP_INFO));
            stmt.bindLong(++i, book.getInt(DBKey.STRIP_INFO_COLL_ID));
            stmt.bindBoolean(++i, book.getBoolean(DBKey.STRIP_INFO_OWNED));
            stmt.bindBoolean(++i, book.getBoolean(DBKey.STRIP_INFO_DIGITAL));
            stmt.bindBoolean(++i, book.getBoolean(DBKey.STRIP_INFO_WANTED));
            stmt.bindLong(++i, book.getInt(DBKey.STRIP_INFO_AMOUNT));
            stmt.bindString(++i, book.getString(DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC));

            final long rowId = stmt.executeInsert();
            if (rowId == -1) {
                throw new DaoInsertException("StripInfo data insert failed");
            }
        }
    }

    @Override
    public boolean delete(@NonNull final Book book) {
        return 0 < db.delete(DBDefinitions.TBL_STRIPINFO_COLLECTION.getName(),
                             DBKey.FK_BOOK + "=?",
                             new String[]{String.valueOf(book.getId())});
    }
}
