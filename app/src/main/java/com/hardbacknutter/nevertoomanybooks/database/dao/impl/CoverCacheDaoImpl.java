/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Process;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
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
    private static final AtomicInteger RUNNING_TASKS = new AtomicInteger();

    @NonNull
    private final SynchronizedDb db;
    @NonNull
    private final Supplier<CoverStorage> coverStorageSupplier;

    /**
     * Constructor.
     *
     * @param db                   Underlying database
     * @param coverStorageSupplier deferred supplier for the {@link CoverStorage}
     */
    public CoverCacheDaoImpl(@NonNull final SynchronizedDb db,
                             @NonNull final Supplier<CoverStorage> coverStorageSupplier) {
        this.db = db;
        this.coverStorageSupplier = coverStorageSupplier;
    }

    /**
     * Construct the cache id for a given thumbnail uuid.
     * We use this to allow caching of multiple copies of the same image (book uuid)
     * but with different dimensions.
     * <p>
     * <strong>Note:</strong> Any changes to the resulting name MUST be reflected in {@link #delete}
     *
     * @param uuid      UUID of the book
     * @param cIdx      0..n image index
     * @param maxWidth  used to construct the cacheId
     * @return cache id string
     */
    @NonNull
    private static String constructCacheId(@NonNull final String uuid,
                                           @IntRange(from = 0, to = 1) final int cIdx,
                                           final int maxWidth) {
        return uuid + '.' + cIdx + '.' + maxWidth;
    }

    @Override
    public int count() {
        //noinspection CheckStyle
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
            rowsAffected = stmt.executeUpdateDelete();
        }
        return rowsAffected > 0;
    }

    @Override
    public void deleteAll() {
        try {
            db.execSQL(Sql.DELETE_ALL);
        } catch (@NonNull final SQLException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    @Override
    @Nullable
    @AnyThread
    public Bitmap getCover(@NonNull final String uuid,
                           @IntRange(from = 0, to = 1) final int cIdx,
                           final int maxWidth) {
        if (isBusy()) {
            return null;

        }
        //noinspection CheckStyle
        try {
            final long lm = coverStorageSupplier.get().getPersistedFile(uuid, cIdx)
                                                .map(File::lastModified)
                                                .orElse(0L);
            if (lm > 0) {
                final String fileLastModified =
                        Instant.ofEpochMilli(lm)
                               .atZone(ZoneOffset.UTC)
                               .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                final String cacheId = constructCacheId(uuid, cIdx, maxWidth);

                try (Cursor cursor = db.rawQuery(
                        Sql.FIND_BY_ID, new String[]{cacheId, fileLastModified})) {
                    if (cursor.moveToFirst()) {
                        final byte[] bytes = cursor.getBlob(0);
                        if (bytes != null) {
                            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        }
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
        return RUNNING_TASKS.get() != 0;
    }

    @Override
    @UiThread
    public void saveCover(@NonNull final String uuid,
                          @IntRange(from = 0, to = 1) final int cIdx,
                          @NonNull final Bitmap bitmap,
                          final int width) {
        // Start a task to send it to the cache.
        // Use the default serial executor as we only want a single write thread at a time.
        ASyncExecutor.SERIAL.execute(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            RUNNING_TASKS.incrementAndGet();
            //noinspection CheckStyle
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
                            if (stmt.executeInsert() == -1) {
                                logAndDisableCache(new DaoInsertException(cacheId));
                            }
                        }
                    } else {
                        final ContentValues cv = new ContentValues();
                        cv.put(CacheDbHelper.IMAGE_ID, cacheId);
                        cv.put(CacheDbHelper.IMAGE_BLOB, out.toByteArray());
                        cv.put(CacheDbHelper.IMAGE_LAST_UPDATED__UTC, LocalDateTime
                                .now(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                        if (0 >= db.update(CacheDbHelper.TBL_IMAGE.getName(), cv,
                                           CacheDbHelper.IMAGE_ID + "=?",
                                           new String[]{cacheId})) {
                            logAndDisableCache(new DaoUpdateException(cacheId));
                        }
                    }
                }
            } catch (@NonNull final IllegalStateException ignore) {
                // Again: Rapid scrolling of view could already have recycled the bitmap.
                // java.lang.IllegalStateException: Can't compress a recycled bitmap
                // don't care at this point; this is just a cache; don't even log.

            } catch (@NonNull final RuntimeException e) {
                logAndDisableCache(e);
            }

            RUNNING_TASKS.decrementAndGet();
        });
    }

    private void logAndDisableCache(@NonNull final Throwable e) {
        LoggerFactory.getLogger().e(TAG, e);
        //FIXME: we should let the user know, and cancel any pending tasks...
        coverStorageSupplier.get().setImageCachingEnabled(false);
    }

    private static final class Sql {
        static final String _FROM_ = " FROM ";
        static final String _WHERE_ = " WHERE ";
        static final String DELETE_FROM_ = "DELETE FROM ";

        static final String INSERT =
                "INSERT INTO " + CacheDbHelper.TBL_IMAGE.getName()
                + '(' + CacheDbHelper.IMAGE_ID
                + ',' + CacheDbHelper.IMAGE_BLOB
                + ") VALUES (?,?)";
        static final String COUNT =
                "SELECT COUNT(*) FROM " + CacheDbHelper.TBL_IMAGE.getName();

        static final String FIND_BY_ID =
                "SELECT " + CacheDbHelper.IMAGE_BLOB
                + _FROM_ + CacheDbHelper.TBL_IMAGE.getName()
                + _WHERE_ + CacheDbHelper.IMAGE_ID + "=?"
                + " AND " + CacheDbHelper.IMAGE_LAST_UPDATED__UTC + ">?";

        /** Run a count for the desired file. 1 == exists, 0 == not there. */
        static final String COUNT_BY_IMAGE_ID =
                "SELECT COUNT(" + CacheDbHelper.PK_ID + ")"
                + _FROM_ + CacheDbHelper.TBL_IMAGE.getName()
                + _WHERE_ + CacheDbHelper.IMAGE_ID + "=?";

        static final String DELETE_BY_IMAGE_ID =
                DELETE_FROM_ + CacheDbHelper.TBL_IMAGE.getName()
                + _WHERE_ + CacheDbHelper.IMAGE_ID + " LIKE ?";
        static final String DELETE_ALL =
                DELETE_FROM_ + CacheDbHelper.TBL_IMAGE.getName();
    }
}
