/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.cursors.TrackedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * DB Helper for Covers DB. It uses the Application Context.
 * This class is used as singleton, as it's needed for multiple concurrent threads.
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

    /** Compresses images to 80% to store in the cache. This does not affect the file itself. */
    private static final int IMAGE_QUALITY_PERCENTAGE = 80;

    /** DB name. */
    private static final String COVERS_DATABASE_NAME = "covers.db";
    /**
     * DB Version.
     */
    private static final int COVERS_DATABASE_VERSION = 1;

    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync. */
    private static final Synchronizer SYNCHRONIZER = new Synchronizer();

    /** Static Factory object to create the custom cursor. */
    private static final SQLiteDatabase.CursorFactory TRACKED_CURSOR_FACTORY =
            (db, masterQuery, editTable, query) -> new TrackedCursor(masterQuery, editTable, query,
                                                                     SYNCHRONIZER);

    /** Statement names. */
    private static final String STMT_EXISTS = "mExistsStmt";

    /* Domain definitions. */
    /** TBL_IMAGE. */
    private static final DomainDefinition DOM_PK_ID =
            new DomainDefinition("_id");

    private static final DomainDefinition DOM_CACHE_ID =
            new DomainDefinition("filename", ColumnInfo.TYPE_TEXT, true);

    private static final DomainDefinition DOM_IMAGE =
            new DomainDefinition("image", ColumnInfo.TYPE_BLOB, true);

    private static final DomainDefinition DOM_DATE =
            new DomainDefinition("date", ColumnInfo.TYPE_DATETIME, true)
                    .setDefault("current_timestamp");

    /** The actual stored bitmap width. */
    private static final DomainDefinition DOM_WIDTH =
            new DomainDefinition("width", ColumnInfo.TYPE_INTEGER, true);

    /** The actual stored bitmap height. */
    private static final DomainDefinition DOM_HEIGHT =
            new DomainDefinition("height", ColumnInfo.TYPE_INTEGER, true);

    /** table definitions. */
    private static final TableDefinition TBL_IMAGE =
            new TableDefinition("image", DOM_PK_ID, DOM_IMAGE, DOM_DATE,
                                DOM_WIDTH, DOM_HEIGHT, DOM_CACHE_ID);
    private static final String SQL_GET_IMAGE = "SELECT " + DOM_IMAGE + " FROM " + TBL_IMAGE
                                                + " WHERE " + DOM_CACHE_ID + "=? AND " + DOM_DATE
                                                + ">?";
    /**
     * run a count for the desired file. 1 == exists, 0 == not there
     */
    private static final String SQL_COUNT_ID =
            "SELECT COUNT(" + DOM_PK_ID + ") FROM " + TBL_IMAGE
            + " WHERE " + DOM_CACHE_ID + "=?";

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
                .addIndex(DOM_CACHE_ID.getName(), true, DOM_CACHE_ID)
                .addIndex(DOM_CACHE_ID.getName() + '_' + DOM_DATE.getName(),
                          true, DOM_CACHE_ID, DOM_DATE);
    }

    /** List of statements we create so we can clean them when the instance is closed. */
    private final SqlStatementManager mStatements = new SqlStatementManager();

    /** singleton. */
    private CoversDAO() {
    }

    /**
     * Get the singleton instance.
     * <p>
     * Reminder: we always use the *application* context for the database connection.
     */
    private static CoversDAO getInstance() {
        if (sCoversDAO == null) {
            sCoversDAO = new CoversDAO();
        }
        // check each time, as it might have failed last time but might work now.
        if (sSyncedDb == null) {
            sCoversDAO.open();
        }

        int noi = INSTANCE_COUNTER.incrementAndGet();
        if (BuildConfig.DEBUG /* always */) {
            Logger.debug(sCoversDAO, "getInstance", "instances in use=" + noi);
        }
        return sCoversDAO;
    }

    /**
     * Construct the cache id for a given thumbnail uuid.
     * We use this to allow caching of multiple copies of the same image (book uuid)
     * but with different dimensions.
     * <p>
     * <strong>Note:</strong> Any changes to the resulting name MUST be reflected in {@link #delete}
     *
     * @param uuid      used to construct the cacheId
     * @param maxWidth  used to construct the cacheId
     * @param maxHeight used to construct the cacheId
     */
    @NonNull
    private static String constructCacheId(@NonNull final String uuid,
                                           final int maxWidth,
                                           final int maxHeight) {
        return uuid + '.' + maxWidth + 'x' + maxHeight;
    }

    /**
     * Get a cached image.
     *
     * @param uuid      of the image
     * @param maxWidth  used to construct the cacheId
     * @param maxHeight used to construct the cacheId
     *
     * @return Bitmap (if cached) or {@code null} (if not cached)
     */
    @Nullable
    @AnyThread
    public static Bitmap getImage(@NonNull final String uuid,
                                  final int maxWidth,
                                  final int maxHeight) {
        // safely initialise if needed
        try (@SuppressWarnings("unused") CoversDAO dummy = CoversDAO.getInstance()) {
            if (sSyncedDb == null) {
                return null;
            }

            File file = StorageUtils.getCoverFileForUuid(uuid);
            String dateStr = DateUtils.utcSqlDateTime(new Date(file.lastModified()));
            String cacheId = constructCacheId(uuid, maxWidth, maxHeight);
            try (Cursor cursor = sSyncedDb.rawQuery(SQL_GET_IMAGE,
                                                    new String[]{cacheId, dateStr})) {
                if (cursor.moveToFirst()) {
                    byte[] bytes = cursor.getBlob(0);
                    if (bytes != null) {
                        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    }
                }
            }
        } catch (@NonNull final RuntimeException e) {
            Logger.error(CoversDAO.class, e);
        }
        return null;
    }

    /**
     * Delete the cached covers associated with the passed book uuid.
     * <p>
     * The original code also had a 2nd 'delete' method with a different where clause:
     * // We use encodeString here because it's possible a user screws up the data and imports
     * // bad UUID's...this has happened.
     * // String whereClause = DOM_CACHE_ID + " glob '" + DAO.encodeString(uuid) + ".*'";
     * In short: ENHANCE: bad data -> add covers.db 'filename' and book.uuid to {@link DBCleaner}
     */
    public static void delete(@NonNull final String uuid) {
        // safely initialise if needed
        try (@SuppressWarnings("unused") CoversDAO dummy = CoversDAO.getInstance()) {
            if (sSyncedDb == null) {
                return;
            }
            sSyncedDb.delete(TBL_IMAGE.getName(),
                             // starts with the uuid, remove all sizes
                             DOM_CACHE_ID + " LIKE ?", new String[]{uuid + '%'});
        } catch (@NonNull final SQLiteException e) {
            Logger.error(CoversDAO.class, e);
        }
    }

    /**
     * delete all rows.
     */
    public static void deleteAll() {
        // safely initialise if needed
        try (@SuppressWarnings("unused") CoversDAO dummy = CoversDAO.getInstance()) {
            if (sSyncedDb == null) {
                return;
            }
            sSyncedDb.execSQL("DELETE FROM " + TBL_IMAGE);
        } catch (@NonNull final SQLiteException e) {
            Logger.error(CoversDAO.class, e);
        }
    }

    /**
     * Analyze the database.
     */
    public static void analyze() {
        // safely initialise if needed
        try (@SuppressWarnings("unused") CoversDAO dummy = CoversDAO.getInstance()) {
            if (sSyncedDb == null) {
                return;
            }
            sSyncedDb.analyze();
        } catch (@NonNull final RuntimeException e) {
            Logger.error(CoversDAO.class, e);
        }
    }

    private void open() {
        SQLiteOpenHelper coversHelper = CoversDbHelper.getInstance(TRACKED_CURSOR_FACTORY);
        // Try to connect.
        try {
            sSyncedDb = new SynchronizedDb(coversHelper, SYNCHRONIZER);
        } catch (@NonNull final RuntimeException e) {
            // Assume exception means DB corrupt. Don't care, it's only a cache.
            Logger.error(this, e, "Failed to open covers db");
            App.getAppContext().deleteDatabase(COVERS_DATABASE_NAME);

            // retry...
            try {
                sSyncedDb = new SynchronizedDb(coversHelper, SYNCHRONIZER);
            } catch (@NonNull final RuntimeException e2) {
                // If we fail after creating a new DB, just give up.
                Logger.error(this, e2, "Covers database unavailable");
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
            int noi = INSTANCE_COUNTER.decrementAndGet();
            if (BuildConfig.DEBUG /* always */) {
                Logger.debug(this, "close",
                             "instances left: " + INSTANCE_COUNTER);
            }

            if (noi == 0) {
                if (sSyncedDb != null) {
                    mStatements.close();
                }
            }
        }
    }

    /**
     * Save the passed bitmap to a 'file' in the covers database.
     * Compresses to {@link #IMAGE_QUALITY_PERCENTAGE} first.
     * <p>
     * This will either insert or update a row in the database.
     */
    @WorkerThread
    private void saveFile(@NonNull final String uuid,
                          @NonNull final Bitmap bitmap,
                          final int maxWidth,
                          final int maxHeight) {
        if (sSyncedDb == null) {
            return;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
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

        byte[] image = out.toByteArray();

        String cacheId = constructCacheId(uuid, maxWidth, maxHeight);
        ContentValues cv = new ContentValues();
        cv.put(DOM_CACHE_ID.getName(), cacheId);
        cv.put(DOM_IMAGE.getName(), image);
        cv.put(DOM_WIDTH.getName(), bitmap.getHeight());
        cv.put(DOM_HEIGHT.getName(), bitmap.getWidth());

        SynchronizedStatement existsStmt = mStatements.get(STMT_EXISTS);
        if (existsStmt == null) {
            existsStmt = mStatements.add(sSyncedDb, STMT_EXISTS, SQL_COUNT_ID);
        }
        existsStmt.bindString(1, cacheId);
        if (existsStmt.count() == 0) {
            sSyncedDb.insert(TBL_IMAGE.getName(), null, cv);
        } else {
            sSyncedDb.update(TBL_IMAGE.getName(), cv,
                             DOM_CACHE_ID.getName() + "=?", new String[]{cacheId});
        }
    }

    /**
     * Singleton SQLiteOpenHelper for the covers database.
     */
    public static final class CoversDbHelper
            extends SQLiteOpenHelper {

        private static CoversDbHelper sCoversDbHelper;

        /**
         * Singleton.
         *
         * @param factory CursorFactory: to use for creating cursor objects
         */
        private CoversDbHelper(@SuppressWarnings("SameParameterValue")
                               @NonNull final SQLiteDatabase.CursorFactory factory) {
            // *always* use the app context!
            super(App.getAppContext(), COVERS_DATABASE_NAME, factory, COVERS_DATABASE_VERSION);
        }

        /**
         * @param factory CursorFactory: to use for creating cursor objects
         *
         * @return the instance
         */
        static CoversDbHelper getInstance(@SuppressWarnings("SameParameterValue")
                                          @NonNull final SQLiteDatabase.CursorFactory factory) {
            if (sCoversDbHelper == null) {
                sCoversDbHelper = new CoversDbHelper(factory);
            }
            return sCoversDbHelper;
        }

        public static File getDatabasePath(@NonNull final Context context) {
            return context.getDatabasePath(COVERS_DATABASE_NAME);
        }

        /**
         * As with SQLiteOpenHelper, routine called to create DB.
         */
        @Override
        public void onCreate(@NonNull final SQLiteDatabase db) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugEnter(this, "onCreate", "database=" + db.getPath());
            }
            TableDefinition.createTables(new SynchronizedDb(db, SYNCHRONIZER), TBL_IMAGE);
        }

        /**
         * As with SQLiteOpenHelper, routine called to upgrade DB.
         */
        @Override
        @CallSuper
        public void onUpgrade(@NonNull final SQLiteDatabase db,
                              final int oldVersion,
                              final int newVersion) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugEnter(this, "onUpgrade",
                                  "Old database version: " + oldVersion,
                                  "Upgrading database: " + db.getPath());
            }
            // This is a cache, so no data needs preserving. Drop & recreate.
            db.execSQL("DROP TABLE IF EXISTS " + TBL_IMAGE);
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

        private static final AtomicInteger RUNNING_TASKS = new AtomicInteger();

        /** Bitmap to store. */
        private final Bitmap mBitmap;
        /** Book UUID. */
        @NonNull
        private final String mUuid;
        private final int mMaxWidth;
        private final int mMaxHeight;

        /**
         * Create a task that will compress the passed bitmap and write it to the database,
         * it will also be recycled if flag is set.
         *
         * @param uuid   the book UUID, i.e. the filename for the cover
         * @param source Raw bitmap to store
         */
        @UiThread
        public ImageCacheWriterTask(@NonNull final String uuid,
                                    final int maxWidth,
                                    final int maxHeight,
                                    @NonNull final Bitmap source) {
            mUuid = uuid;
            mMaxWidth = maxWidth;
            mMaxHeight = maxHeight;
            mBitmap = source;
        }

        /**
         * @return {@code true} if there is an active task in the queue.
         */
        @UiThread
        public static boolean hasActiveTasks() {
            return RUNNING_TASKS.get() != 0;
        }

        @Override
        @WorkerThread
        protected Void doInBackground(final Void... params) {
            Thread.currentThread().setName("ImageCacheWriterTask");

            RUNNING_TASKS.incrementAndGet();

            try (CoversDAO coversDBAdapter = getInstance()) {
                coversDBAdapter.saveFile(mUuid, mBitmap, mMaxWidth, mMaxHeight);
            }

            RUNNING_TASKS.decrementAndGet();
            return null;
        }
    }
}
