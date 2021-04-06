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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
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

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.dao.CoverCacheDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;

import static com.hardbacknutter.nevertoomanybooks.database.CoversDbHelper.CKEY_CACHE_ID;
import static com.hardbacknutter.nevertoomanybooks.database.CoversDbHelper.CKEY_IMAGE;
import static com.hardbacknutter.nevertoomanybooks.database.CoversDbHelper.CKEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.CoversDbHelper.CKEY_UTC_DATETIME;
import static com.hardbacknutter.nevertoomanybooks.database.CoversDbHelper.TBL_IMAGE;
import static com.hardbacknutter.nevertoomanybooks.database.dao.impl.BaseDaoImpl.SELECT_COUNT_FROM_;

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

    /** Get a cached image. */
    private static final String SQL_GET_IMAGE =
            "SELECT " + CKEY_IMAGE + " FROM " + TBL_IMAGE.getName()
            + " WHERE " + CKEY_CACHE_ID + "=? AND " + CKEY_UTC_DATETIME + ">?";

    /** Run a count for the desired file. 1 == exists, 0 == not there. */
    private static final String SQL_COUNT_ID =
            "SELECT COUNT(" + CKEY_PK_ID + ") FROM " + TBL_IMAGE.getName()
            + " WHERE " + CKEY_CACHE_ID + "=?";

    private static final String SQL_COUNT = SELECT_COUNT_FROM_ + TBL_IMAGE.getName();

    /** Compresses images to 80% to store in the cache. */
    private static final int IMAGE_QUALITY_PERCENTAGE = 80;
    /** Used to prevent trying to read from the cache while we're writing to it. */
    private static final AtomicInteger RUNNING_TASKS = new AtomicInteger();
    @NonNull
    private final SynchronizedDb mDb;

    /**
     * Constructor.
     */
    public CoverCacheDaoImpl() {
        mDb = ServiceLocator.getCoversDb();
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
            try (SynchronizedStatement stmt = mDb.compileStatement(SQL_COUNT)) {
                return (int) stmt.simpleQueryForLongOrZero();
            }
        } catch (@NonNull final RuntimeException e) {
            Logger.error(TAG, e);
        }
        return 0;
    }

    @Override
    public void delete(@NonNull final String uuid) {
        try {
            // starts with the uuid, remove all sizes and indexes
            mDb.delete(TBL_IMAGE.getName(), CKEY_CACHE_ID + " LIKE ?",
                       new String[]{uuid + '%'});
        } catch (@NonNull final SQLiteException e) {
            Logger.error(TAG, e);
        }
    }

    @Override
    public void deleteAll() {
        try {
            mDb.execSQL("DELETE FROM " + TBL_IMAGE.getName());
        } catch (@NonNull final SQLiteException e) {
            Logger.error(TAG, e);
        }
    }

    @Override
    @Nullable
    @AnyThread
    public Bitmap getCover(@NonNull final Context context,
                           @NonNull final String uuid,
                           @IntRange(from = 0, to = 1) final int cIdx,
                           final int maxWidth,
                           final int maxHeight) {
        try {
            final File file = Book.getUuidCoverFile(uuid, cIdx);
            if (file != null) {
                final long lm = file.lastModified();
                if (lm > 0) {
                    final String fileLastModified =
                            Instant.ofEpochMilli(lm)
                                   .atZone(ZoneOffset.UTC)
                                   .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                    final String cacheId = constructCacheId(uuid, cIdx, maxWidth, maxHeight);

                    try (Cursor cursor = mDb.rawQuery(
                            SQL_GET_IMAGE, new String[]{cacheId, fileLastModified})) {
                        if (cursor.moveToFirst()) {
                            final byte[] bytes = cursor.getBlob(0);
                            if (bytes != null) {
                                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            }
                        }
                    }
                }
            }
        } catch (@NonNull final RuntimeException e) {
            Logger.error(TAG, e);
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
                    bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY_PERCENTAGE, out);

                    final String cacheId = constructCacheId(uuid, cIdx, width, height);

                    final ContentValues cv = new ContentValues();
                    cv.put(CKEY_CACHE_ID, cacheId);
                    cv.put(CKEY_IMAGE, out.toByteArray());

                    final boolean exists;
                    try (SynchronizedStatement stmt = mDb.compileStatement(SQL_COUNT_ID)) {
                        stmt.bindString(1, cacheId);
                        exists = stmt.simpleQueryForLongOrZero() == 0;
                    }

                    if (exists) {
                        mDb.insert(TBL_IMAGE.getName(), cv);
                    } else {
                        cv.put(CKEY_UTC_DATETIME, LocalDateTime
                                .now(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                        mDb.update(TBL_IMAGE.getName(), cv,
                                   CKEY_CACHE_ID + "=?", new String[]{cacheId});
                    }
                }
            } catch (@NonNull final IllegalStateException ignore) {
                // Again: Rapid scrolling of view could already have recycled the bitmap.
                // java.lang.IllegalStateException: Can't compress a recycled bitmap
                // don't care at this point; this is just a cache; don't even log.

            } catch (@NonNull final RuntimeException e) {
                // do not crash... ever! This is just a cache!
                Logger.error(TAG, e);
                // and disable the cache
                // We don't bother cancelling any pending tasks... oh well...
                ImageUtils.setImageCachingEnabled(false);
                //FIXME: we should let the user know....
            }

            RUNNING_TASKS.decrementAndGet();
        });
    }
}
