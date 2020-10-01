/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * DB Helper for Covers DB. It uses the Application Context.
 * This class is used as singleton, as it's needed for multiple concurrent threads.
 * <p>
 * Images are stored as JPEG, at 80% quality. This does not affect the file itself.
 * <p>
 * In the initial pass, the covers database has a single table whose members are accessed
 * via unique 'file names'.
 * <p>
 * 2018-11-26: database location back to internal storage.
 * The bulk of space is used by the actual image file, not by the database.
 * To be reviewed when the location of the images can be user-configured.
 * TODO: performance tests: cache enabled/disabled; do we actually need this db ?
 * <p>
 * note that {@link #DOM_WIDTH} and {@link #DOM_HEIGHT} are redundant/information only.
 * Lookup is done via the {@link #DOM_CACHE_ID} instead.
 */
public final class CoversDAO
        implements AutoCloseable {

    /** Log tag. */
    private static final String TAG = "CoversDAO";

    /** Compresses images to 80% to store in the cache. */
    private static final int IMAGE_QUALITY_PERCENTAGE = 80;

    /** DB name. */
    private static final String COVERS_DATABASE_NAME = "covers.db";
    /**
     * DB Version.
     */
    private static final int COVERS_DATABASE_VERSION = 1;

    /**
     * Static Synchronizer to coordinate access to <strong>this</strong> database.
     */
    private static final Synchronizer SYNCHRONIZER = new Synchronizer();

    /** Static Factory object to create the custom cursor. */
    private static final SQLiteDatabase.CursorFactory CURSOR_FACTORY =
            (db, d, et, q) -> new SynchronizedCursor(d, et, q, SYNCHRONIZER);

    /** Statement names. */
    private static final String STMT_EXISTS = "mExistsStmt";

    /* Domain definitions. */
    private static final String CKEY_PK_ID = "_id";
    private static final String CKEY_CACHE_ID = "filename";
    private static final String CKEY_IMAGE = "image";
    private static final String CKEY_UTC_DATETIME = "last_update_date";
    private static final String CKEY_WIDTH = "width";
    private static final String CKEY_HEIGHT = "height";

    /** TBL_IMAGE. */
    private static final Domain DOM_PK_ID =
            new Domain.Builder(CKEY_PK_ID, ColumnInfo.TYPE_INTEGER).primaryKey().build();

    private static final Domain DOM_CACHE_ID =
            new Domain.Builder(CKEY_CACHE_ID, ColumnInfo.TYPE_TEXT).notNull().build();

    private static final Domain DOM_IMAGE =
            new Domain.Builder(CKEY_IMAGE, ColumnInfo.TYPE_BLOB).notNull().build();

    private static final Domain DOM_UTC_DATETIME =
            new Domain.Builder(CKEY_UTC_DATETIME, ColumnInfo.TYPE_DATETIME)
                    .notNull().withDefaultCurrentTimeStamp().build();

    /** The actual stored bitmap width. */
    private static final Domain DOM_WIDTH =
            new Domain.Builder(CKEY_WIDTH, ColumnInfo.TYPE_INTEGER).notNull().build();

    /** The actual stored bitmap height. */
    private static final Domain DOM_HEIGHT =
            new Domain.Builder(CKEY_HEIGHT, ColumnInfo.TYPE_INTEGER).notNull().build();

    /** table definitions. */
    private static final TableDefinition TBL_IMAGE =
            new TableDefinition("image", DOM_PK_ID, DOM_IMAGE, DOM_UTC_DATETIME,
                                DOM_WIDTH, DOM_HEIGHT, DOM_CACHE_ID);

    /** Get a cached image. */
    private static final String SQL_GET_IMAGE =
            "SELECT " + CKEY_IMAGE + " FROM " + TBL_IMAGE.getName()
            + " WHERE " + CKEY_CACHE_ID + "=? AND " + CKEY_UTC_DATETIME + ">?";

    /** Run a count for the desired file. 1 == exists, 0 == not there. */
    private static final String SQL_COUNT_ID =
            "SELECT COUNT(" + CKEY_PK_ID + ") FROM " + TBL_IMAGE.getName()
            + " WHERE " + CKEY_CACHE_ID + "=?";

    /**
     * NOT DEBUG: close() will only really close all statements if INSTANCE_COUNTER == 0 is reached.
     */
    @NonNull
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();

    /**
     * We *try* to connect in the Constructor. But this can fail.
     * This is ok, as this class/db is for caching only.
     * So before using it, every method in this class MUST test on != {@code null}
     */
    private static SynchronizedDb sSyncedDb;
    /** singleton. */
    private static CoversDAO sCoversDAO;

    /* table indexes. */
    static {
        TBL_IMAGE
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("id", true, DOM_PK_ID)
                .addIndex(CKEY_CACHE_ID, true, DOM_CACHE_ID)
                .addIndex(CKEY_CACHE_ID + "_" + CKEY_UTC_DATETIME,
                          true, DOM_CACHE_ID, DOM_UTC_DATETIME);
    }

    /** Collection of statements pre-compiled for this object. */
    private SqlStatementManager mStatementManager;

    /** singleton. */
    private CoversDAO() {
    }

    /**
     * Get the singleton instance.
     *
     * @param context Current context
     *
     * @return instance
     */
    private static CoversDAO getInstance(@NonNull final Context context) {
        synchronized (CoversDAO.class) {
            if (sCoversDAO == null) {
                sCoversDAO = new CoversDAO();
            }
            // check each time, as it might have failed last time but might work now.
            if (sSyncedDb == null) {
                sCoversDAO.open(context);
                if (sSyncedDb != null) {
                    sCoversDAO.mStatementManager = new SqlStatementManager(sSyncedDb, TAG);
                }
            }

            int noi = INSTANCE_COUNTER.incrementAndGet();
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "getInstance|instances in use=" + noi);
            }
            return sCoversDAO;
        }
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

    /**
     * Get a cached image.
     *
     * @param context   Current context
     * @param uuid      UUID of the book
     * @param cIdx      0..n image index
     * @param maxWidth  used to construct the cacheId
     * @param maxHeight used to construct the cacheId
     *
     * @return Bitmap (if cached) or {@code null} (if not cached)
     */
    @Nullable
    @AnyThread
    public static Bitmap getImage(@NonNull final Context context,
                                  @NonNull final String uuid,
                                  @IntRange(from = 0, to = 1) final int cIdx,
                                  final int maxWidth,
                                  final int maxHeight) {
        // safely initialise if needed
        try (@SuppressWarnings("unused") CoversDAO dao = CoversDAO.getInstance(context)) {
            if (sSyncedDb == null) {
                return null;
            }

            final File file = Book.getUuidCoverFile(context, uuid, cIdx);
            if (file != null) {
                final long lm = file.lastModified();
                if (lm > 0) {
                    final String fileLastModified =
                            Instant.ofEpochMilli(lm)
                                   .atZone(ZoneOffset.UTC)
                                   .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    final String cacheId = constructCacheId(uuid, cIdx, maxWidth, maxHeight);

                    try (Cursor cursor = sSyncedDb.rawQuery(SQL_GET_IMAGE, new String[]{
                            cacheId, fileLastModified})) {
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
            Logger.error(context, TAG, e);
        }
        return null;
    }

    /**
     * Delete the cached covers associated with the passed book uuid.
     * <p>
     * The original code also had a 2nd 'delete' method with a different where clause:
     * // We use encodeString here because it's possible a user screws up the data and imports
     * // bad UUID's...this has happened.
     * // String whereClause = CKEY_CACHE_ID + " GLOB '" + DAO.encodeString(uuid) + ".*'";
     * In short: ENHANCE: bad data -> add covers.db 'filename' and book.uuid to {@link DBCleaner}
     *
     * @param context Current context
     * @param uuid    to delete
     */
    public static void delete(@NonNull final Context context,
                              @NonNull final String uuid) {
        // safely initialise if needed
        try (@SuppressWarnings("unused") CoversDAO dao = CoversDAO.getInstance(context)) {
            if (sSyncedDb == null) {
                return;
            }
            sSyncedDb.delete(TBL_IMAGE.getName(),
                             // starts with the uuid, remove all sizes and indexes
                             CKEY_CACHE_ID + " LIKE ?", new String[]{uuid + '%'});
        } catch (@NonNull final SQLiteException e) {
            Logger.error(context, TAG, e);
        }
    }

    /**
     * delete all rows.
     *
     * @param context Current context
     */
    public static void deleteAll(@NonNull final Context context) {
        // safely initialise if needed
        try (@SuppressWarnings("unused") CoversDAO dao = CoversDAO.getInstance(context)) {
            if (sSyncedDb == null) {
                return;
            }
            sSyncedDb.execSQL("DELETE FROM " + TBL_IMAGE.getName());
        } catch (@NonNull final SQLiteException e) {
            Logger.error(context, TAG, e);
        }
    }

    /**
     * Optimize the database.
     *
     * @param context Current context
     */
    public static void optimize(@NonNull final Context context) {
        // safely initialise if needed
        try (@SuppressWarnings("unused") CoversDAO dao = CoversDAO.getInstance(context)) {
            if (sSyncedDb == null) {
                return;
            }
            sSyncedDb.optimize();
        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
        }
    }

    /**
     * Open the database.
     *
     * @param context Current context
     */
    private void open(@NonNull final Context context) {
        final SQLiteOpenHelper coversHelper = CoversDbHelper.getInstance(context);
        // Try to connect.
        try {
            sSyncedDb = SynchronizedDb.getInstance(SYNCHRONIZER, coversHelper);
        } catch (@NonNull final RuntimeException e) {
            // Assume exception means DB corrupt. Don't care, it's only a cache.
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "Failed to open covers db", e);
            }
            // recreate a new one.
            try {
                context.deleteDatabase(COVERS_DATABASE_NAME);
                sSyncedDb = SynchronizedDb.getInstance(SYNCHRONIZER, coversHelper);

            } catch (@NonNull final RuntimeException e2) {
                // If we fail after trying to create a new DB, log and give up.
                Logger.error(context, TAG, e2, "Covers database unavailable");
            }
        }
    }

    /**
     * Generic function to close the database.
     * It does not 'close' the database in the literal sense, but
     * performs a cleanup by closing all open statements when there are no instances left.
     * (So it should really be called cleanup(); But it allows us to use try-with-resources.)
     */
    @Override
    public void close() {
        // must be in a synchronized, as we use noi twice.
        synchronized (INSTANCE_COUNTER) {
            final int noi = INSTANCE_COUNTER.decrementAndGet();
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "close|instances left: " + INSTANCE_COUNTER);
            }

            if (noi == 0) {
                if (sSyncedDb != null) {
                    mStatementManager.close();
                }
            }
        }
    }

    /**
     * Save the passed bitmap to a 'file' in the covers database.
     * Compresses to {@link #IMAGE_QUALITY_PERCENTAGE} first.
     * <p>
     * This will either insert or update a row in the database.
     * Failures are ignored; this is just a cache.
     *
     * @param uuid   UUID of the book
     * @param cIdx   0..n image index
     * @param bitmap to save
     * @param width  used to construct the cacheId
     * @param height used to construct the cacheId
     */
    @WorkerThread
    private void saveFile(@NonNull final String uuid,
                          @IntRange(from = 0, to = 1) final int cIdx,
                          @NonNull final Bitmap bitmap,
                          final int width,
                          final int height) {
        if (sSyncedDb == null) {
            return;
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Rapid scrolling of view could already have recycled the bitmap.
        if (bitmap.isRecycled()) {
            return;
        }
        try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY_PERCENTAGE, out);
        } catch (@NonNull final IllegalStateException e) {
            // Again: Rapid scrolling of view could already have recycled the bitmap.
            // java.lang.IllegalStateException: Can't compress a recycled bitmap
            // don't care at this point; this is just a cache; don't even log.
            return;
        }

        final byte[] image = out.toByteArray();

        final String cacheId = constructCacheId(uuid, cIdx, width, height);
        final ContentValues cv = new ContentValues();
        cv.put(CKEY_CACHE_ID, cacheId);
        cv.put(CKEY_IMAGE, image);
        cv.put(CKEY_WIDTH, bitmap.getHeight());
        cv.put(CKEY_HEIGHT, bitmap.getWidth());

        final SynchronizedStatement existsStmt = mStatementManager.get(
                STMT_EXISTS, () -> SQL_COUNT_ID);

        existsStmt.bindString(1, cacheId);
        if (existsStmt.simpleQueryForLongOrZero() == 0) {
            sSyncedDb.insert(TBL_IMAGE.getName(), null, cv);
        } else {
            cv.put(CKEY_UTC_DATETIME, LocalDateTime
                    .now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            sSyncedDb.update(TBL_IMAGE.getName(), cv,
                             CKEY_CACHE_ID + "=?", new String[]{cacheId});
        }
    }

    /**
     * Singleton SQLiteOpenHelper for the covers database.
     */
    public static final class CoversDbHelper
            extends SQLiteOpenHelper {

        private static CoversDbHelper sCoversDbHelper;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        private CoversDbHelper(@NonNull final Context context) {
            super(context.getApplicationContext(),
                  COVERS_DATABASE_NAME, CURSOR_FACTORY, COVERS_DATABASE_VERSION);
        }

        /**
         * Singleton Constructor.
         *
         * @param context Current context
         *
         * @return the instance
         */
        static CoversDbHelper getInstance(@NonNull final Context context) {
            synchronized (CoversDbHelper.class) {
                if (sCoversDbHelper == null) {
                    sCoversDbHelper = new CoversDbHelper(context);
                }
                return sCoversDbHelper;
            }
        }

        @Override
        public void onConfigure(@NonNull final SQLiteDatabase db) {
            // Turn ON foreign key support so that CASCADE etc. works.
            // This is the same as db.execSQL("PRAGMA foreign_keys = ON");
            db.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onCreate(@NonNull final SQLiteDatabase db) {
            TableDefinition.createTables(db, TBL_IMAGE);
        }

        @Override
        public void onUpgrade(@NonNull final SQLiteDatabase db,
                              final int oldVersion,
                              final int newVersion) {
            // This is a cache, so no data needs preserving. Drop & recreate.
            db.execSQL("DROP TABLE IF EXISTS " + TBL_IMAGE.getName());
            onCreate(db);
        }
    }

    /**
     * Background task to save a bitmap into the covers thumbnail database. Runs in the background
     * because it involves compression and IO, and can be safely queued. Failures can be ignored
     * because it is just writing to a cache used solely for optimization.
     * <p>
     * Standard AsyncTask for writing data. There is no point in more than one thread since
     * the database will force serialization of the updates.
     */
    public static final class ImageCacheWriterTask
            extends AsyncTask<Void, Void, Void> {

        /** Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "ImageCacheWriterTask";

        /** Used to prevent trying to read from the cache while we're writing to it. */
        private static final AtomicInteger RUNNING_TASKS = new AtomicInteger();

        /** Bitmap to store. */
        private final Bitmap mBitmap;
        /** Book UUID. */
        @NonNull
        private final String mUuid;
        private final int mIndex;
        private final int mWidth;
        private final int mHeight;

        /**
         * Create a task that will compress the passed bitmap and write it to the database,
         * it will also be recycled if flag is set.
         *
         * @param uuid   UUID of the book
         * @param cIdx   0..n image index
         * @param source Raw bitmap to store
         */
        @UiThread
        public ImageCacheWriterTask(@NonNull final String uuid,
                                    @IntRange(from = 0, to = 1) final int cIdx,
                                    final int width,
                                    final int height,
                                    @NonNull final Bitmap source) {
            mUuid = uuid;
            mIndex = cIdx;
            mWidth = width;
            mHeight = height;
            mBitmap = source;
        }

        /**
         * Check if there is an active task in the queue.
         *
         * @return {@code true} if there is
         */
        @UiThread
        public static boolean hasActiveTasks() {
            return RUNNING_TASKS.get() != 0;
        }

        @Override
        @WorkerThread
        protected Void doInBackground(@Nullable final Void... voids) {
            Thread.currentThread().setName(TAG);
            final Context context = App.getTaskContext();

            RUNNING_TASKS.incrementAndGet();

            try (CoversDAO coversDBAdapter = getInstance(context)) {
                coversDBAdapter.saveFile(mUuid, mIndex, mBitmap, mWidth, mHeight);
            }

            RUNNING_TASKS.decrementAndGet();
            return null;
        }
    }
}
