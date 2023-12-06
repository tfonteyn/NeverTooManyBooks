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

import android.database.Cursor;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BedethequeCacheDao;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BdtAuthor;

public class BedethequeCacheDaoImpl
        extends BaseDaoImpl
        implements BedethequeCacheDao {

    private static final String TAG = "BedethequeCacheDaoImpl";
    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public BedethequeCacheDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    @NonNull
    public Optional<BdtAuthor> findByName(@NonNull final String name,
                                          @NonNull final Locale locale) {
        final String nameOb = SqlEncode.orderByColumn(name, locale);

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{nameOb, nameOb})) {
            if (cursor.moveToFirst()) {
                final CursorRow rowData = new CursorRow(cursor);
                return Optional.of(new BdtAuthor(rowData.getLong(CacheDbHelper.PK_ID), rowData));
            } else {
                return Optional.empty();
            }
        }
    }

    @Override
    public void fixId(@NonNull final BdtAuthor bdtAuthor,
                      @NonNull final Locale locale) {
        final long found = findByName(bdtAuthor.getName(), locale)
                .map(BdtAuthor::getId).orElse(0L);
        bdtAuthor.setId(found);
    }

    @Override
    public void insert(@NonNull final Locale locale,
                       @NonNull final Supplier<BdtAuthor> recordSupplier)
            throws DaoInsertException, DaoUpdateException {

        if (Build.VERSION.SDK_INT < 30) {
            insertApiPre30(locale, recordSupplier);
            return;
        }

        BdtAuthor bdtAuthor;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            long iId;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
                while ((bdtAuthor = recordSupplier.get()) != null) {
                    stmt.bindString(1, bdtAuthor.getName());
                    stmt.bindString(2, SqlEncode.orderByColumn(bdtAuthor.getName(), locale));
                    stmt.bindString(3, bdtAuthor.getUrl());
                    iId = stmt.executeInsert();
                    if (iId != -1) {
                        bdtAuthor.setId(iId);
                    } else {
                        // Can't reset previous id's
                        throw new DaoInsertException(ERROR_INSERT_FROM + bdtAuthor);
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
    }

    private void insertApiPre30(@NonNull final Locale locale,
                                @NonNull final Supplier<BdtAuthor> recordSupplier)
            throws DaoInsertException, DaoUpdateException {

        BdtAuthor bdtAuthor;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final String sqlInsert = INSERT_INTO_ + CacheDbHelper.TBL_BDT_AUTHORS.getName()
                                     + '(' + CacheDbHelper.BDT_AUTHOR_NAME
                                     + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB
                                     + ',' + CacheDbHelper.BDT_AUTHOR_URL
                                     + ") VALUES(?,?,?)";

            final String sqlUpdate = UPDATE_ + CacheDbHelper.TBL_BDT_AUTHORS.getName()
                                     + _SET_ + CacheDbHelper.BDT_AUTHOR_URL + "=?"
                                     + _WHERE_ + DBKey.PK_ID + "=?";

            long iId;
            try (SynchronizedStatement stmtInsert = db.compileStatement(sqlInsert);
                 SynchronizedStatement stmtUpdate = db.compileStatement(sqlUpdate)) {
                while ((bdtAuthor = recordSupplier.get()) != null) {
                    // check if we already have this one
                    fixId(bdtAuthor, locale);

                    if (bdtAuthor.getId() == 0) {
                        stmtInsert.bindString(1, bdtAuthor.getName());
                        stmtInsert.bindString(2, SqlEncode.orderByColumn(bdtAuthor.getName(),
                                                                         locale));
                        stmtInsert.bindString(3, bdtAuthor.getUrl());
                        iId = stmtInsert.executeInsert();
                        if (iId != -1) {
                            bdtAuthor.setId(iId);
                        } else {
                            // Can't reset previous id's
                            throw new DaoInsertException(ERROR_INSERT_FROM + bdtAuthor);
                        }
                    } else {
                        stmtUpdate.bindString(1, bdtAuthor.getUrl());
                        stmtUpdate.bindLong(2, bdtAuthor.getId());
                        if (stmtUpdate.executeUpdateDelete() <= 0) {
                            throw new DaoUpdateException(ERROR_UPDATE_FROM + bdtAuthor);
                        }
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
    }

    @Override
    public void update(@NonNull final BdtAuthor bdtAuthor,
                       @NonNull final Locale locale)
            throws DaoUpdateException {

        final String resolvedName = bdtAuthor.getResolvedName();

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            final int rowsAffected;
            try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
                stmt.bindString(1, bdtAuthor.getName());
                stmt.bindString(2, SqlEncode.orderByColumn(bdtAuthor.getName(), locale));
                stmt.bindString(3, bdtAuthor.getUrl());
                stmt.bindBoolean(4, bdtAuthor.isResolved());
                if (resolvedName == null || resolvedName.equals(bdtAuthor.getName())) {
                    stmt.bindNull(5);
                    stmt.bindNull(6);
                } else {
                    stmt.bindString(5, resolvedName);
                    stmt.bindString(6, SqlEncode.orderByColumn(resolvedName, locale));
                }

                stmt.bindLong(7, bdtAuthor.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }

            if (rowsAffected > 0) {
                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return;
            }

            throw new DaoUpdateException(ERROR_UPDATE_FROM + bdtAuthor);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public boolean isAuthorPageCached(final char c1) {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.IS_PAGE_CACHED)) {
            stmt.bindString(1, c1 + "%");
            return stmt.simpleQueryForLongOrZero() != 0;
        }
    }

    @Override
    public int countAuthors() {
        try (SynchronizedStatement stmt = db.compileStatement(
                SELECT_COUNT_FROM_ + CacheDbHelper.TBL_BDT_AUTHORS.getName())) {
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public void clearCache() {
        db.execSQL(DELETE_FROM_ + CacheDbHelper.TBL_BDT_AUTHORS.getName());
    }

    private static final class Sql {

        /**
         * "ON CONFLICT" requires Android 11.
         *
         * @see #insertApiPre30(Locale, Supplier) for older versions.
         */
        static final String INSERT =
                INSERT_INTO_ + CacheDbHelper.TBL_BDT_AUTHORS.getName()
                + '(' + CacheDbHelper.BDT_AUTHOR_NAME
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB
                + ',' + CacheDbHelper.BDT_AUTHOR_URL
                + ") VALUES(?,?,?) ON CONFLICT(" + CacheDbHelper.BDT_AUTHOR_NAME_OB
                + ") DO UPDATE SET " + CacheDbHelper.BDT_AUTHOR_URL
                + "=excluded." + CacheDbHelper.BDT_AUTHOR_URL;
        static final String IS_PAGE_CACHED =
                "SELECT DISTINCT 1 FROM " + CacheDbHelper.TBL_BDT_AUTHORS.getName()
                + _WHERE_ + CacheDbHelper.BDT_AUTHOR_NAME + " LIKE ?";
        static final String FIND_BY_NAME =
                SELECT_
                + CacheDbHelper.PK_ID
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME
                + ',' + CacheDbHelper.BDT_AUTHOR_IS_RESOLVED
                + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME
                + ',' + CacheDbHelper.BDT_AUTHOR_URL
                + _FROM_ + CacheDbHelper.TBL_BDT_AUTHORS.getName()
                + _WHERE_ + CacheDbHelper.BDT_AUTHOR_NAME_OB + "=?"
                + _OR_ + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME_OB + "=?";

        static final String UPDATE =
                UPDATE_ + CacheDbHelper.TBL_BDT_AUTHORS.getName() + _SET_
                + CacheDbHelper.BDT_AUTHOR_NAME + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_NAME_OB + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_URL + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_IS_RESOLVED + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME + "=?"
                + ',' + CacheDbHelper.BDT_AUTHOR_RESOLVED_NAME_OB + "=?"
                + _WHERE_ + CacheDbHelper.PK_ID + "=?";
    }
}
