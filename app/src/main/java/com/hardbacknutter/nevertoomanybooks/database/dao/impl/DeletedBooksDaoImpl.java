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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.DeletedBooksDao;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_DELETED_BOOKS;

public class DeletedBooksDaoImpl
        extends BaseDaoImpl
        implements DeletedBooksDao {

    private static final String TAG = "DeletedBooksDaoImpl";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public DeletedBooksDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    @NonNull
    public List<Pair<String, String>> getAll(@Nullable final LocalDateTime sinceDateTime) {
        final StringBuilder sql = new StringBuilder(
                SELECT_ + DBKey.BOOK_UUID + ',' + DBKey.DATE_ADDED__UTC
                + _FROM_ + TBL_DELETED_BOOKS.getName());
        @Nullable
        final String[] args;
        if (sinceDateTime != null) {
            sql.append(_WHERE_ + DBKey.DATE_ADDED__UTC + ">?");
            args = new String[]{SqlEncode.dateTime(sinceDateTime)};
        } else {
            args = null;
        }

        final List<Pair<String, String>> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql.toString(), args)) {
            while (cursor.moveToNext()) {
                list.add(new Pair<>(cursor.getString(0),
                                    cursor.getString(1)));
            }
        }
        return list;
    }

    @Override
    @WorkerThread
    public int importRecords(@NonNull final List<Pair<String, String>> list) {
        int count = 0;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            try (SynchronizedStatement stmt = db.compileStatement(
                    INSERT_OR_IGNORE_INTO_ + TBL_DELETED_BOOKS.getName()
                    + "(" + DBKey.BOOK_UUID + ',' + DBKey.DATE_ADDED__UTC + ") VALUES (?,?)")) {
                for (final Pair<String, String> record : list) {
                    stmt.bindString(1, record.first);
                    stmt.bindString(2, record.second);
                    final long iId = stmt.executeInsert();
                    // simply ignore failure, see SQL statement.
                    if (iId != -1) {
                        count++;
                    }
                }
            }

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
        return count;
    }

    @Override
    public int sync() {
        final List<String> all = getAll(null)
                .stream()
                .map(record -> record.first)
                .collect(Collectors.toList());
        return ServiceLocator.getInstance().getBookDao().deleteByUuid(all);
    }

    @Override
    public void purge() {
        db.execSQL(DELETE_FROM_ + TBL_DELETED_BOOKS.getName());
    }

}
