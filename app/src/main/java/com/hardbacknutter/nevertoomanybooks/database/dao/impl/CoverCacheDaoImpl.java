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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * DB Helper for Covers DB.
 * <p>
 * Images are stored as JPEG, at 80% quality. This does not affect the file itself.
 * <p>
 * The covers database has a single table whose members are accessed via unique 'file names'.
 * <p>
 * TODO: performance tests: cache enabled/disabled; do we actually need this db ?
 */
public class CoverCacheDaoImpl
        implements CoverCacheDao {

    /** Log tag. */
    private static final String TAG = "CoverCacheDaoImpl";

    /** Compresses images to 80% to store in the cache. */
    private static final int QUALITY = 80;
    /** Used to prevent trying to read from the cache while we're writing to it. */
    private static final AtomicInteger TASKS_WRITING = new AtomicInteger();

    @NonNull
    private final SynchronizedDb db;

    /**
     * Constructor.
     *
     * @param db Database Access
     */
    public CoverCacheDaoImpl(@NonNull final SynchronizedDb db) {
        this.db = db;
    }

    /**
     * Construct the cache id for a given thumbnail uuid.
     * We use this to allow caching of multiple copies of the same image (book uuid)
     * but with different dimensions.
     * <p>
     * <strong>Note:</strong> Any changes to the resulting name MUST be reflected in {@link #delete}
     *
     * @param uuid     UUID of the book
     * @param cIdx     0..n image index
     * @param maxWidth used to construct the cacheId
     *
     * @return cache id string
     */
    @NonNull
    private static String constructCacheId(@NonNull final String uuid,
                                           @IntRange(from = 0, to = 3) final int cIdx,
                                           final int maxWidth) {
        return uuid + '.' + cIdx + '.' + maxWidth;
    }

    @Override
    public int count() {
        //noinspection CheckStyle,OverlyBroadCatchBlock
        try {
            try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT)) {
                return (int) stmt.simpleQueryForLongOrZero();
            }
        } catch (@NonNull final RuntimeException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
        return 0;
    }

    @Override
    public boolean delete(@NonNull final String uuid) {
        // Remove files where the name starts with the uuid,
        // which will remove all sizes and indexes
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_IMAGE_ID)) {
            stmt.bindString(1, uuid + '%');
            rowsAffected = stmt.executeUpdateDelete(null);
        }
        return rowsAffected > 0;
    }

    @Override
    public void deleteAll() {
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_ALL)) {
            stmt.executeUpdateDelete(null);
        } catch (@NonNull final SQLException ignore) {
            // ignore
        }
    }

    @Nullable
    private Pair<String, String> selectionArgs(@NonNull final String uuid,
                                               @IntRange(from = 0, to = 3) final int cIdx,
                                               final int maxWidth) {

        final long fileLastModified = ServiceLocator
                .getInstance()
                .getCoverStorage()
                .getPersistedFile(uuid, cIdx)
                .map(File::lastModified)
                .orElse(0L);

        if (fileLastModified <= 0) {
            // no file
            return null;
        }

        final String cacheId = constructCacheId(uuid, cIdx, maxWidth);
        final String lastUpdatedAsIsoString = Instant
                .ofEpochMilli(fileLastModified)
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return new Pair<>(cacheId, lastUpdatedAsIsoString);
    }

    @Override
    @Nullable
    public Pair<String, String> hasBitmap(@NonNull final String uuid,
                                          @IntRange(from = 0, to = 3) final int cIdx,
                                          final int maxWidth) {
        if (isBusy()) {
            return null;
        }

        //noinspection CheckStyle,OverlyBroadCatchBlock
        try {
            final Pair<String, String> args = selectionArgs(uuid, cIdx, maxWidth);
            if (args == null) {
                return null;
            }

            try (SynchronizedStatement stmt = db.compileStatement(Sql.EXISTS_BY_ID)) {
                stmt.bindString(1, args.first);
                stmt.bindString(1, args.second);

                return stmt.simpleQueryForLongOrZero() == 1 ? args : null;
            }
        } catch (@NonNull final RuntimeException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
        return null;
    }

    @Override
    @Nullable
    public Bitmap getBitmap(@NonNull final Pair<String, String> args) {
        if (isBusy()) {
            return null;
        }

        //noinspection CheckStyle
        try {
            try (Cursor cursor = db.rawQuery(Sql.FIND_BY_ID,
                                             new String[]{args.first, args.second})) {
                if (cursor.moveToFirst()) {
                    final byte[] bytes = cursor.getBlob(0);
                    if (bytes != null) {
                        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    }
                }
            }
        } catch (@NonNull final RuntimeException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
        return null;
    }

    /**
     * Check if there is an active task in the queue.
     *
     * @return {@code true} if there is
     */
    @AnyThread
    private boolean isBusy() {
        return TASKS_WRITING.get() != 0;
    }

    @Override
    public void saveBitmap(@NonNull final String uuid,
                           @IntRange(from = 0, to = 3) final int cIdx,
                           @NonNull final Bitmap bitmap,
                           final int width) {
        // Start a task to send it to the cache.
        // Use the default serial executor as we only want a single write thread at a time.
        ASyncExecutor.STORAGE_WRITES.execute(() -> {
            TASKS_WRITING.incrementAndGet();
            try {
                // Rapid scrolling of view could already have recycled the bitmap.
                if (!bitmap.isRecycled()) {
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, out);

                    final String cacheId = constructCacheId(uuid, cIdx, width);

                    final boolean isNew;
                    try (SynchronizedStatement stmt = db.compileStatement(Sql.COUNT_BY_IMAGE_ID)) {
                        stmt.bindString(1, cacheId);
                        isNew = stmt.simpleQueryForLongOrZero() == 0;
                    }

                    if (isNew) {
                        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
                            stmt.bindString(1, cacheId);
                            stmt.bindBlob(2, out.toByteArray());
                            stmt.executeInsert(null);
                        }
                    } else {
                        try (SynchronizedStatement stmt = db.compileStatement(Sql.UPDATE)) {
                            stmt.bindString(1, cacheId);
                            stmt.bindBlob(2, out.toByteArray());
                            stmt.bindString(3, SqlEncode.dateTime(
                                    LocalDateTime.now(ZoneOffset.UTC)));

                            stmt.bindString(4, cacheId);
                            stmt.executeUpdateDelete(null);
                        }
                    }
                }
            } catch (@NonNull final IllegalStateException ignore) {
                // Again: Rapid scrolling of view could already have recycled the bitmap.
                // java.lang.IllegalStateException: Can't compress a recycled bitmap
                // don't care at this point; this is just a cache; don't even log.

            } catch (@NonNull final SQLException e) {
                // Disable the cache
                //FIXME: we should let the user know, and cancel any pending tasks...
                ServiceLocator.getInstance()
                              .getCoverStorage()
                              .setImageCachingEnabled(false);
                throw e;
            }

            TASKS_WRITING.decrementAndGet();
        });
    }

    private static final class Sql {
        private static final String _AND_ = " AND ";
        private static final String _FROM_ = " FROM ";
        private static final String _WHERE_ = " WHERE ";
        private static final String DELETE_FROM_ = "DELETE FROM ";
        private static final String SELECT_COUNT_FROM_ = "SELECT COUNT(*) FROM ";

        static final String INSERT =
                "INSERT INTO " + CacheDbHelper.TBL_IMAGE.getName()
                + '(' + CacheDbHelper.IMAGE_ID
                + ',' + CacheDbHelper.IMAGE_BLOB
                + ") VALUES (?,?)";

        static final String UPDATE =
                "UPDATE " + CacheDbHelper.TBL_IMAGE.getName()
                + " SET " + CacheDbHelper.IMAGE_ID + "=?"
                + ',' + CacheDbHelper.IMAGE_BLOB + "=?"
                + ',' + CacheDbHelper.IMAGE_LAST_UPDATED__UTC + "=?"
                + _WHERE_ + CacheDbHelper.IMAGE_ID + "=?";

        static final String COUNT =
                SELECT_COUNT_FROM_ + CacheDbHelper.TBL_IMAGE.getName();

        static final String FIND_BY_ID =
                "SELECT " + CacheDbHelper.IMAGE_BLOB
                + _FROM_ + CacheDbHelper.TBL_IMAGE.getName()
                + _WHERE_ + CacheDbHelper.IMAGE_ID + "=?"
                + _AND_ + CacheDbHelper.IMAGE_LAST_UPDATED__UTC + ">?";

        /** Check if we have a image which is 'newer' than its original file. */
        static final String EXISTS_BY_ID =
                "SELECT 1" + _FROM_ + CacheDbHelper.TBL_IMAGE.getName()
                + _WHERE_ + CacheDbHelper.IMAGE_ID + "=?"
                + _AND_ + CacheDbHelper.IMAGE_LAST_UPDATED__UTC + ">?";

        /** Run a count for the desired image. */
        static final String COUNT_BY_IMAGE_ID =
                SELECT_COUNT_FROM_ + CacheDbHelper.TBL_IMAGE.getName()
                + _WHERE_ + CacheDbHelper.IMAGE_ID + "=?";

        static final String DELETE_BY_IMAGE_ID =
                DELETE_FROM_ + CacheDbHelper.TBL_IMAGE.getName()
                + _WHERE_ + CacheDbHelper.IMAGE_ID + " LIKE ?";
        static final String DELETE_ALL =
                DELETE_FROM_ + CacheDbHelper.TBL_IMAGE.getName();
    }
}
