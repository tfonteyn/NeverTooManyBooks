/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.StripInfoDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoCollectionData;

public class StripInfoDaoImpl
        extends BaseDaoImpl
        implements StripInfoDao {

    private static final String TAG = "StripInfoDaoImpl";

    private static final String ERROR_INSERT_FROM = "Insert from\n";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public StripInfoDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    @NonNull
    public Optional<StripInfoCollectionData> findByLocalBookId(@IntRange(from = 1) final long bookId) {
        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_LOCAL_BOOK_ID,
                                         new String[]{String.valueOf(bookId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            if (cursor.moveToFirst()) {
                return Optional.of(new StripInfoCollectionData(rowData));
            }
        }
        return Optional.empty();
    }

    @Override
    public void insertOrUpdate(@NonNull final Book book)
            throws DaoInsertException {

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
    public boolean insert(@NonNull final Book book)
            throws DaoInsertException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final StripInfoCollectionData data = book.getParcelable(StripInfoCollectionData.BKEY);
        if (data == null) {
            return false;
        }

        final String lastSync = data.getLastSync();
        final String dateTime = lastSync != null ? SqlEncode.dateTime(lastSync) : "";

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            int i = 0;
            stmt.bindLong(++i, book.getId());
            stmt.bindLong(++i, data.getSid());
            stmt.bindLong(++i, data.getCollectionId());
            stmt.bindBoolean(++i, data.isOwned());
            stmt.bindBoolean(++i, data.isDigital());
            stmt.bindBoolean(++i, data.isWanted());
            stmt.bindLong(++i, data.getAmount());
            stmt.bindString(++i, dateTime);

            if (stmt.executeInsert() == -1) {
                throw new DaoInsertException(ERROR_INSERT_FROM + data);
            }
        }

        return true;
    }

    @Override
    public boolean delete(@NonNull final Book book) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_LOCAL_BOOK_ID)) {
            stmt.bindLong(1, book.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }
        return rowsAffected > 0;
    }

    private static final class Sql {

        static final String INSERT =
                INSERT_INTO_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.STRIP_INFO_BOOK_ID
                + ',' + DBKey.STRIP_INFO_COLLECTION_ID
                + ',' + DBKey.STRIP_INFO_OWNED
                + ',' + DBKey.STRIP_INFO_DIGITAL
                + ',' + DBKey.STRIP_INFO_WANTED
                + ',' + DBKey.STRIP_INFO_AMOUNT
                + ',' + DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC
                + ") VALUES (?,?,?,?,?,?,?,?)";

        static final String FIND_BY_LOCAL_BOOK_ID =
                SELECT_ + DBKey.FK_BOOK
                + ',' + DBKey.STRIP_INFO_BOOK_ID
                + ',' + DBKey.STRIP_INFO_COLLECTION_ID
                + ',' + DBKey.STRIP_INFO_OWNED
                + ',' + DBKey.STRIP_INFO_DIGITAL
                + ',' + DBKey.STRIP_INFO_WANTED
                + ',' + DBKey.STRIP_INFO_AMOUNT
                + ',' + DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC
                + _FROM_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName()
                + _WHERE_ + DBKey.FK_BOOK + "=?";

        static final String DELETE_BY_LOCAL_BOOK_ID =
                DELETE_FROM_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName()
                + _WHERE_ + DBKey.FK_BOOK + "=?";
    }
}
