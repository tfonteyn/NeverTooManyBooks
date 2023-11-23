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

import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorage;
import com.hardbacknutter.nevertoomanybooks.database.CacheDbHelper;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;

/**
 * DB Helper for Covers DB.
 * <p>
 * Images are stored as JPEG, at 80% quality. This does not affect the file itself.
 * <p>
 * In the initial pass, the covers database has a single table whose members are accessed
 * via unique 'file names'.
 * <p>
 * 2018-11-26: database location back to internal storage.
 * The bulk of space is used by the actual image file, not by the database.
 * To be reviewed when/if the location of the images can be user-configured.
 * TODO: performance tests: cache enabled/disabled; do we actually need this db ?
 */
public class CoverCacheDaoImpl
        implements CoverCacheDao {

    /** Log tag. */
    private static final String TAG = "CoverCacheDaoImpl";

    private static final String INSERT =
            "INSERT INTO " + CacheDbHelper.TBL_IMAGE.getName()
            + '(' + CacheDbHelper.IMAGE_ID + ',' + CacheDbHelper.IMAGE_BLOB + ") VALUES (?,?)";

    /** Get a cached image. */
    private static final String SQL_GET_IMAGE =
            "SELECT " + CacheDbHelper.IMAGE_BLOB
            + " FROM " + CacheDbHelper.TBL_IMAGE.getName()
            + " WHERE " + CacheDbHelper.IMAGE_ID + "=?"
            + " AND " + CacheDbHelper.IMAGE_LAST_UPDATED__UTC + ">?";

    /** Run a count for the desired file. 1 == exists, 0 == not there. */
    private static final String SQL_COUNT_ID =
            "SELECT COUNT(" + CacheDbHelper.PK_ID + ")"
            + " FROM " + CacheDbHelper.TBL_IMAGE.getName()
            + " WHERE " + CacheDbHelper.IMAGE_ID + "=?";

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM " + CacheDbHelper.TBL_IMAGE.getName();

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
     * @param maxHeight used to construct the cacheId
     *
     * @return cache id string
     */
    @NonNull
    private static String constructCacheId(@NonNull final String uuid,
                                           @IntRange(from = 0, to = 1) final int cIdx,
                                           final int maxWidth,
                                           final int maxHeight) {
        return uuid + '.' + cIdx + '.' + maxWidth + 'x' + maxHeight;
    }

    @Override
    public int count() {
        try {
            try (SynchronizedStatement stmt = db.compileStatement(SQL_COUNT)) {
                return (int) stmt.simpleQueryForLongOrZero();
            }
        } catch (@NonNull final RuntimeException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
        return 0;
    }

    @Override
    public boolean delete(@NonNull final String uuid) {
        try {
            // Remove files where the name starts with the uuid,
            // which will remove all sizes and indexes
            return 0 < db.delete(CacheDbHelper.TBL_IMAGE.getName(),
                                 CacheDbHelper.IMAGE_ID + " LIKE ?",
                                 new String[]{uuid + '%'});

        } catch (@NonNull final SQLException | IllegalArgumentException e) {
            LoggerFactory.getLogger().e(TAG, e);
            return false;
        }
    }

    @Override
    public void deleteAll() {
        try {
            db.execSQL("DELETE FROM " + CacheDbHelper.TBL_IMAGE.getName());
        } catch (@NonNull final SQLException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    @Override
    @Nullable
    @AnyThread
    public Bitmap getCover(@NonNull final String uuid,
                           @IntRange(from = 0, to = 1) final int cIdx,
                           final int maxWidth,
                           final int maxHeight) {
        try {
            final long lm = coverStorageSupplier.get().getPersistedFile(uuid, cIdx)
                                                .map(File::lastModified)
                                                .orElse(0L);
            if (lm > 0) {
                final String fileLastModified =
                        Instant.ofEpochMilli(lm)
                               .atZone(ZoneOffset.UTC)
                               .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                final String cacheId = constructCacheId(uuid, cIdx, maxWidth, maxHeight);

                try (Cursor cursor = db.rawQuery(
                        SQL_GET_IMAGE, new String[]{cacheId, fileLastModified})) {
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
    @Override
    public boolean isBusy() {
        return RUNNING_TASKS.get() != 0;
    }

    @Override
    @UiThread
    public void saveCover(@NonNull final String uuid,
                          @IntRange(from = 0, to = 1) final int cIdx,
                          @NonNull final Bitmap bitmap,
                          final int width,
                          final int height) {
        // Start a task to send it to the cache.
        // Use the default serial executor as we only want a single write thread at a time.
        // Failures are ignored as it is just writing to a cache used solely for optimization.
        ASyncExecutor.SERIAL.execute(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            RUNNING_TASKS.incrementAndGet();
            try {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                // Rapid scrolling of view could already have recycled the bitmap.
                if (!bitmap.isRecycled()) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, out);

                    final String cacheId = constructCacheId(uuid, cIdx, width, height);

                    final boolean exists;
                    try (SynchronizedStatement stmt = db.compileStatement(SQL_COUNT_ID)) {
                        stmt.bindString(1, cacheId);
                        exists = stmt.simpleQueryForLongOrZero() == 0;
                    }

                    if (exists) {
                        try (SynchronizedStatement stmt = db.compileStatement(INSERT)) {
                            stmt.bindString(1, cacheId);
                            stmt.bindBlob(2, out.toByteArray());
                            stmt.executeInsert();
                        }
                    } else {
                        final ContentValues cv = new ContentValues();
                        cv.put(CacheDbHelper.IMAGE_ID, cacheId);
                        cv.put(CacheDbHelper.IMAGE_BLOB, out.toByteArray());
                        cv.put(CacheDbHelper.IMAGE_LAST_UPDATED__UTC, LocalDateTime
                                .now(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                        db.update(CacheDbHelper.TBL_IMAGE.getName(), cv,
                                  CacheDbHelper.IMAGE_ID + "=?", new String[]{cacheId});
                    }
                }
            } catch (@NonNull final IllegalStateException ignore) {
                // Again: Rapid scrolling of view could already have recycled the bitmap.
                // java.lang.IllegalStateException: Can't compress a recycled bitmap
                // don't care at this point; this is just a cache; don't even log.

            } catch (@NonNull final RuntimeException e) {
                // do not crash... ever! This is just a cache!
                LoggerFactory.getLogger().e(TAG, e);
                // and disable the cache
                //FIXME: we should let the user know,
                // and cancel any pending tasks...
                coverStorageSupplier.get().setImageCachingEnabled(false);

            }

            RUNNING_TASKS.decrementAndGet();
        });
    }
}
