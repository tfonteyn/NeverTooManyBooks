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

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.StripInfoDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;

public class StripInfoDaoImpl
        extends BaseDaoImpl
        implements StripInfoDao {

    private static final String TAG = "StripInfoDaoImpl";

    private static final String SQL_DELETE =
            DELETE_FROM_ + DBDefinitions.TBL_STRIPINFO_COLLECTION_TO_IMPORT.getName();

    private static final String SQL_DELETE_ONE = SQL_DELETE
                                                 + _WHERE_ + DBKeys.KEY_ESID_STRIP_INFO_BE + "=?";

    private static final String SQL_INSERT =
            INSERT_INTO_ + DBDefinitions.TBL_STRIPINFO_COLLECTION_TO_IMPORT.getName()
            + " (" + DBKeys.KEY_ESID_STRIP_INFO_BE + ") VALUES (?)";

    private static final String SQL_GET_ALL =
            SELECT_ + DBKeys.KEY_ESID_STRIP_INFO_BE
            + _FROM_ + DBDefinitions.TBL_STRIPINFO_COLLECTION_TO_IMPORT.getName()
            + _ORDER_BY_ + DBKeys.KEY_PK_ID;

    /**
     * Constructor.
     */
    public StripInfoDaoImpl() {
        super(TAG);
    }

    @Override
    public void insert(@NonNull final List<Long> externalIdList) {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            // simply clear all
            mDb.execSQL(SQL_DELETE);

            try (SynchronizedStatement stmt = mDb.compileStatement(SQL_INSERT)) {
                for (final long externalId : externalIdList) {
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
    @NonNull
    public List<Long> getAll() {
        final List<Long> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SQL_GET_ALL, null)) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    @Override
    public boolean deleteOne(final long externalId) {
        try (SynchronizedStatement stmt = mDb.compileStatement(SQL_DELETE_ONE)) {
            stmt.bindLong(1, externalId);
            return stmt.executeUpdateDelete() == 1;
        }
    }

    @Override
    public void deleteAll() {
        mDb.execSQL(SQL_DELETE);
    }
}
