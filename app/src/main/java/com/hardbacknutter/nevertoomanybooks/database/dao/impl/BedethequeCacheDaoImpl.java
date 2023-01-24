/*
 * @Copyright 2018-2022 HardBackNutter
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

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.database.dao.BedethequeCacheDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BdtAuthor;

public class BedethequeCacheDaoImpl
        extends BaseDaoImpl
        implements BedethequeCacheDao {

    private static final String TAG = "BedethequeCacheDaoImpl";

    public BedethequeCacheDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    @Nullable
    public BdtAuthor findByName(@NonNull final String name,
                                @NonNull final Locale locale) {
        final String nameOb = SqlEncode.orderByColumn(name, locale);

        try (Cursor cursor = db.rawQuery(Sql.FIND_BY_NAME, new String[]{nameOb, nameOb})) {
            if (cursor.moveToFirst()) {
                final DataHolder rowData = new CursorRow(cursor);
                return new BdtAuthor(rowData.getLong(CacheDbHelper.PK_ID), rowData);
            } else {
                return null;
            }
        }
    }

    @Override
    public void update(@NonNull final BdtAuthor bdtAuthor,
                       @NonNull final Locale locale) {

        final String resolvedName = bdtAuthor.getResolvedName();

        final SynchronizedDb cacheDb = ServiceLocator.getInstance().getCacheDb();
        try (SynchronizedStatement stmt = cacheDb.compileStatement(Sql.UPDATE)) {
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
            stmt.executeUpdateDelete();
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
    public int count() {
        try (SynchronizedStatement stmt = db.compileStatement(
                SELECT_COUNT_FROM_ + CacheDbHelper.TBL_BDT_AUTHORS.getName())) {
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    public void purgeCache() {
        db.execSQL(DELETE_FROM_ + CacheDbHelper.TBL_BDT_AUTHORS.getName());
    }

    public static final class Sql {

        public static final String INSERT =
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

        private Sql() {
        }
    }
}
