/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;
import com.eleybourn.bookcatalogue.database.definitions.ColumnInfo;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.GetImageTask;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * DB Helper for Covers DB. It uses the Application Context.
 * <p>
 * In the initial pass, the covers database has a single table whose members are accessed
 * via unique 'file names'.
 * <p>
 * This class is used as singleton, to avoid running out of memory very quickly.
 * To be investigated some day. Not sure how much multi-threaded access is hampered by this.
 * TODO: do some speed checks: cache enabled/disabled; do we actually need this db ?
 * <p>
 * 2018-11-26: database location back to internal storage.
 * The bulk of space is used by the actual image file, not by the database.
 * To be reviewed when the location of the images can be user-configured.
 *
 * @author Philip Warner
 */
public final class CoversDBA
        implements AutoCloseable {

    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync. */
    private static final Synchronizer SYNCHRONIZER = new Synchronizer();
    /** DB name. */
    private static final String COVERS_DATABASE_NAME = "covers.db";
    /**
     * DB Version.
     * v2: dropped size/type columns + shortened 'filename'.
     */
    private static final int COVERS_DATABASE_VERSION = 2;

    /** Static Factory object to create the custom cursor. */
    private static final SQLiteDatabase.CursorFactory TRACKED_CURSOR_FACTORY =
            new SQLiteDatabase.CursorFactory() {
                @Override
                public Cursor newCursor(
                        @NonNull final SQLiteDatabase db,
                        @NonNull final SQLiteCursorDriver masterQuery,
                        @NonNull final String editTable,
                        @NonNull final SQLiteQuery query) {
                    return new TrackedCursor(masterQuery, editTable, query, SYNCHRONIZER);
                }
            };

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

    private static final DomainDefinition DOM_WIDTH =
            new DomainDefinition("width", ColumnInfo.TYPE_INTEGER, true);

    private static final DomainDefinition DOM_HEIGHT =
            new DomainDefinition("height", ColumnInfo.TYPE_INTEGER, true);

    /** table definitions. */
    private static final TableDefinition TBL_IMAGE =
            new TableDefinition("image", DOM_PK_ID, DOM_IMAGE, DOM_DATE,
                                DOM_WIDTH, DOM_HEIGHT, DOM_CACHE_ID);
    private static final String SQL_GET_IMAGE = "SELECT " + DOM_IMAGE + " FROM " + TBL_IMAGE
            + " WHERE " + DOM_CACHE_ID + "=? AND " + DOM_DATE + ">?";
    /**
     * run a count for the desired file. 1 == exists, 0 == not there
     */
    private static final String SQL_COUNT_ID =
            "SELECT COUNT(" + DOM_PK_ID + ") FROM " + TBL_IMAGE
                    + " WHERE " + DOM_CACHE_ID + "=?";

    /**
     * Not debug!
     * close() will only really close if INSTANCE_COUNTER == 0 is reached.
     */
    @NonNull
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    /** Compresses images to 70%. */
    private static final int QUALITY_PERCENTAGE = 70;


    /**
     * We *try* to connect in the Constructor. But this can fail.
     * This is ok, as this class/db is for caching only.
     * So before using it, every method in this class MUST test on != null
     */
    private static SynchronizedDb mSyncedDb;
    /** singleton. */
    private static CoversDBA mInstance;

    /* table indexes. */
    static {
        TBL_IMAGE
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("id", true, DOM_PK_ID)
                .addIndex(DOM_CACHE_ID.name, true, DOM_CACHE_ID)
                .addIndex(DOM_CACHE_ID.name + '_' + DOM_DATE.name,
                          true, DOM_CACHE_ID, DOM_DATE);
    }

    /** List of statements we create so we can clean them when the instance is closed. */
    private final SqlStatementManager mStatements = new SqlStatementManager();

    private CoversDBA() {
    }

    /**
     * Get the singleton instance.
     * <p>
     * Reminder: we always use the *application* context for the database connection.
     */
    public static CoversDBA getInstance() {
        if (mInstance == null) {
            mInstance = new CoversDBA();
        }
        // check each time, as it might have failed last time but might work now.
        if (mSyncedDb == null) {
            mInstance.open(BookCatalogueApp.getAppContext());
        }

        int noi = INSTANCE_COUNTER.incrementAndGet();
        if (/* always show debug */ BuildConfig.DEBUG) {
            Logger.info(mInstance, "getInstance", "instances created: " + noi);
        }
        return mInstance;
    }

    /**
     * Construct the cache ID for a given thumbnail uuid.
     * We use this to allow caching of multiple copies of the same image (book uuid)
     * but with different dimensions.
     * <p>
     * NOTE: Any changes to the resulting name MUST be reflected in {@link #delete}
     *
     * @param uuid      used to construct the cacheId
     * @param maxWidth  used to construct the cacheId
     * @param maxHeight used to construct the cacheId
     */
    @NonNull
    public static String constructCacheId(@NonNull final String uuid,
                                          final int maxWidth,
                                          final int maxHeight) {
        return uuid + '.' + maxWidth + 'x' + maxHeight;
    }

    private void open(@NonNull final Context context) {
        final SQLiteOpenHelper coversHelper = new CoversDbHelper(context, TRACKED_CURSOR_FACTORY);

        // Try to connect.
        try {
            mSyncedDb = new SynchronizedDb(coversHelper, SYNCHRONIZER);
        } catch (RuntimeException e) {
            // Assume exception means DB corrupt. Log, rename, and retry
            Logger.error(e, "Failed to open covers db");
            if (!StorageUtils.renameFile(StorageUtils.getFile(COVERS_DATABASE_NAME),
                                         StorageUtils.getFile(COVERS_DATABASE_NAME + ".dead"))) {
                Logger.error("Failed to rename dead covers database: ");
            }

            // try again?
            try {
                mSyncedDb = new SynchronizedDb(coversHelper, SYNCHRONIZER);
            } catch (RuntimeException e2) {
                // If we fail a second time (creating a new DB), then just give up.
                Logger.error(e2, "Covers database unavailable");
            }
        }
    }

    /**
     * Generic function to close the database.
     * It does not 'close' the database in the literal sense, but
     * performs a cleanup by closing all open statements when there are no instances left.
     * <p>
     * So it should really be called cleanup()
     * But it allows us to use try-with-resources.
     * <p>
     * Consequently, there is no need to 'open' anything before running further operations.
     */
    @Override
    public void close() {
        // must be in a synchronized, as we use noi twice.
        synchronized (INSTANCE_COUNTER) {
            int noi = INSTANCE_COUNTER.decrementAndGet();
            if (/* always show debug */BuildConfig.DEBUG) {
                Logger.info(this, "close", "instances left: " + INSTANCE_COUNTER);
            }

            if (noi == 0) {
                if (mSyncedDb != null) {
                    mStatements.close();
                }
            }
        }
    }

    /**
     * Called in the UI thread, will return a cached image OR NULL.
     *
     * @param originalFile File representing original image file
     * @param uuid         used to construct the cacheId
     * @param maxWidth     used to construct the cacheId
     * @param maxHeight    used to construct the cacheId
     *
     * @return Bitmap (if cached) or null (if not cached)
     */
    @Nullable
    public Bitmap getImage(@NonNull final File originalFile,
                           @NonNull final String uuid,
                           final int maxWidth,
                           final int maxHeight) {
        if (mSyncedDb == null) {
            return null;
        }

        String cacheId = constructCacheId(uuid, maxWidth, maxHeight);
        String dateStr = DateUtils.utcSqlDateTime(new Date(originalFile.lastModified()));

        try (Cursor cursor = mSyncedDb.rawQuery(SQL_GET_IMAGE, new String[]{cacheId, dateStr})) {
            if (cursor.moveToFirst()) {
                byte[] bytes = cursor.getBlob(0);
                if (bytes != null) {
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }
            }
        } catch (RuntimeException e) {
            // It's possible the SDCard got removed and DB is now inaccessible
            // or the 'bytes' might be an invalid bitmap.
            Logger.error(e);
        }

        return null;
    }

    /**
     * Called in the UI thread, will return a cached image OR NULL.
     * and (if found) put it in the view.
     *
     * @param originalFile File representing original image file
     * @param uuid         used to construct the cacheId
     * @param maxWidth     used to construct the cacheId
     * @param maxHeight    used to construct the cacheId
     * @param destView     View to populate if non-null
     *
     * @return Bitmap (if cached) or null (if not cached)
     */
    @Nullable
    public Bitmap getImageAndPutIntoView(@NonNull final File originalFile,
                                         @NonNull final String uuid,
                                         final int maxWidth,
                                         final int maxHeight,
                                         @NonNull final ImageView destView) {

        Bitmap bitmap = getImage(originalFile, uuid, maxWidth, maxHeight);
        if (bitmap != null) {
            //
            // Remove any tasks that may be getting the image because they may overwrite
            // anything we do.
            // Remember: the view may have been re-purposed and have a different associated
            // task which must be removed from the view and removed from the queue.
            //
            GetImageTask.clearOldTaskFromView(destView);

            destView.setImageBitmap(bitmap);
        }
        return bitmap;
    }

    /**
     * Save the passed bitmap to a 'file' in the covers database.
     * Compresses to QUALITY_PERCENTAGE first.
     */
    public void saveFile(@NonNull final Bitmap bitmap,
                         @NonNull final String filename) {
        if (mSyncedDb == null) {
            return;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY_PERCENTAGE, out);
        byte[] image = out.toByteArray();

        ContentValues cv = new ContentValues();
        cv.put(DOM_CACHE_ID.name, filename);
        cv.put(DOM_IMAGE.name, image);
        cv.put(DOM_WIDTH.name, bitmap.getHeight());
        cv.put(DOM_HEIGHT.name, bitmap.getWidth());

        SynchronizedStatement existsStmt = mStatements.get(STMT_EXISTS);
        if (existsStmt == null) {
            existsStmt = mStatements.add(mSyncedDb, STMT_EXISTS, SQL_COUNT_ID);
        }
        existsStmt.bindString(1, filename);

        if (existsStmt.count() == 0) {
            mSyncedDb.insert(TBL_IMAGE.getName(), null, cv);
        } else {
            mSyncedDb.update(TBL_IMAGE.getName(), cv,
                             DOM_CACHE_ID.name + "=?",
                             new String[]{filename});
        }
    }

    /**
     * Delete the cached covers associated with the passed book uuid.
     * <p>
     * The original code also had a 2nd 'delete' method with a different where clause:
     * // We use encodeString here because it's possible a user screws up the data and imports
     * // bad UUIDs...this has happened.
     * // String whereClause = DOM_CACHE_ID + " glob '" + DBA.encodeString(uuid) + ".*'";
     * In short: ENHANCE: bad data -> add covers.db 'filename' and book.uuid to {@link DBCleaner}
     */
    public void delete(@NonNull final String uuid) {
        if (mSyncedDb == null) {
            return;
        }
        mSyncedDb.delete(TBL_IMAGE.getName(),
                         DOM_CACHE_ID + " LIKE ?",
                         // starts with the uuid, remove all sizes
                         new String[]{uuid + '%'});
    }

    /**
     * delete all rows.
     */
    public void deleteAll() {
        if (mSyncedDb == null) {
            return;
        }
        mSyncedDb.delete(TBL_IMAGE.getName(), null, null);
    }

    /**
     * Analyze the database.
     */
    public void analyze() {
        if (mSyncedDb == null) {
            return;
        }
        mSyncedDb.analyze();
    }

    public static class CoversDbHelper
            extends SQLiteOpenHelper {

        CoversDbHelper(@NonNull final Context context,
                       @SuppressWarnings("SameParameterValue")
                       @NonNull final SQLiteDatabase.CursorFactory factory) {
            super(context, COVERS_DATABASE_NAME, factory, COVERS_DATABASE_VERSION);
        }

        public static String getDatabasePath(@NonNull final Context context) {
            return context.getDatabasePath(COVERS_DATABASE_NAME).getAbsolutePath();
        }

        /**
         * As with SQLiteOpenHelper, routine called to create DB.
         */
        @Override
        @CallSuper
        public void onCreate(@NonNull final SQLiteDatabase db) {
            Logger.info(this, "onCreate", "database: " + db.getPath());
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
            Logger.info(this, "onUpgrade",
                        "Old database version: " + oldVersion);
            Logger.info(this, "onUpgrade",
                        "Upgrading database: " + db.getPath());
            // This is a cache, so no data needs preserving. Drop & recreate.
            db.execSQL("DROP TABLE IF EXISTS " + TBL_IMAGE);
            onCreate(db);
        }
    }
}
