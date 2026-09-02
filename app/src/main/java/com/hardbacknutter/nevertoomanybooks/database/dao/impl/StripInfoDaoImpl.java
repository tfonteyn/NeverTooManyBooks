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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.database.Cursor;
import android.database.SQLException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
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
     * @param db Database Access
     */
    public StripInfoDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    @NonNull
    public Optional<StripInfoCollectionData>
    findByLocalBookId(@IntRange(from = 1) final long bookId) {
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
    public boolean insert(@NonNull final Book book)
            throws DaoWriteException {

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

            stmt.executeInsert(() -> ERROR_INSERT_FROM + data);

        } catch (@NonNull final SQLException e) {
            throw new DaoWriteException(e);
        }

        return true;
    }

    @Override
    public void delete(@NonNull final Book book) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_LOCAL_BOOK_ID)) {
            stmt.bindLong(1, book.getId());
            stmt.executeUpdateDelete(null);
        }
    }

    private static final class Sql {

        static final String INSERT =
                INSERT_INTO_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.STRIP_INFO.BOOK_ID
                + ',' + DBKey.STRIP_INFO.COLLECTION_ID
                + ',' + DBKey.STRIP_INFO.OWNED
                + ',' + DBKey.STRIP_INFO.DIGITAL
                + ',' + DBKey.STRIP_INFO.WANTED
                + ',' + DBKey.STRIP_INFO.AMOUNT
                + ',' + DBKey.STRIP_INFO.LAST_SYNC_DATE__UTC
                + ") VALUES (?,?,?,?,?,?,?,?)";

        static final String FIND_BY_LOCAL_BOOK_ID =
                SELECT_ + DBKey.FK_BOOK
                + ',' + DBKey.STRIP_INFO.BOOK_ID
                + ',' + DBKey.STRIP_INFO.COLLECTION_ID
                + ',' + DBKey.STRIP_INFO.OWNED
                + ',' + DBKey.STRIP_INFO.DIGITAL
                + ',' + DBKey.STRIP_INFO.WANTED
                + ',' + DBKey.STRIP_INFO.AMOUNT
                + ',' + DBKey.STRIP_INFO.LAST_SYNC_DATE__UTC
                + _FROM_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName()
                + _WHERE_ + DBKey.FK_BOOK + "=?";

        static final String DELETE_BY_LOCAL_BOOK_ID =
                DELETE_FROM_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName()
                + _WHERE_ + DBKey.FK_BOOK + "=?";
    }
}
