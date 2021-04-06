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

import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.StripInfoDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_STRIPINFO_COLLECTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FK_BOOK;

public class StripInfoDaoImpl
        extends BaseDaoImpl
        implements StripInfoDao {

    private static final String TAG = "StripInfoDaoImpl";

    private static final String SQL_INSERT_QUEUED =
            INSERT_INTO_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName()
            + " (" + DBKeys.KEY_ESID_STRIP_INFO_BE + ") VALUES (?,?,?)";

    private static final String SQL_COUNT_QUEUED =
            SELECT_COUNT_FROM_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName();

    private static final String SQL_GET_QUEUED =
            SELECT_ + DBKeys.KEY_ESID_STRIP_INFO_BE
            + _FROM_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName()
            + _ORDER_BY_ + DBKeys.KEY_PK_ID;

    private static final String SQL_DELETE_QUEUED =
            DELETE_FROM_ + DBDefinitions.TBL_STRIPINFO_COLLECTION.getName();


    private static final String SQL_DELETE_ONE =
            SQL_DELETE_QUEUED + _WHERE_ + DBKeys.KEY_ESID_STRIP_INFO_BE + "=?";


    /**
     * Constructor.
     */
    public StripInfoDaoImpl() {
        super(TAG);
    }


    @Override
    public void updateOrInsert(@NonNull final Book book) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

    }

    @Override
    public int countQueuedBooks() {
        try (SynchronizedStatement stmt = mDb.compileStatement(SQL_COUNT_QUEUED)) {
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public List<Long> getQueuedBooks() {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SQL_GET_QUEUED, null)) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
            return list;
        }
    }

    @Override
    public void setQueuedBooks(@NonNull final List<Long> list) {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            // simply clear all
            mDb.execSQL(SQL_DELETE_QUEUED);

            try (SynchronizedStatement stmt = mDb.compileStatement(SQL_INSERT_QUEUED)) {
                for (final Long externalId : list) {
                    stmt.bindLong(1, externalId);
                    stmt.executeInsert();
                }
            }
            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }
    }

    @Override
    public void deleteQueued() {
        mDb.execSQL(SQL_DELETE_QUEUED);
    }


    @Override
    public boolean deleteOne(final long externalId) {
        try (SynchronizedStatement stmt = mDb.compileStatement(SQL_DELETE_ONE)) {
            stmt.bindLong(1, externalId);
            return stmt.executeUpdateDelete() == 1;
        }
    }

    public void delete(@NonNull final Book book) {
        mDb.delete(TBL_STRIPINFO_COLLECTION.getName(), KEY_FK_BOOK + "=?",
                   new String[]{String.valueOf(book.getId())});
    }

}
