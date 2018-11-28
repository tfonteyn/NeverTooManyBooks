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
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.GetThumbnailTask;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DB Helper for Covers DB. It uses the Application Context.
 *
 * In the initial pass, the covers database has a single table whose members are accessed via unique
 * 'file names'.
 *
 * This class is used as singleton, to avoid running out of memory very quickly.
 * To be investigated some day.
 *
 * 2018-11-26: database location back to internal/private directory.
 * The bulk of space is used by the actual image file, not by the database.
 * To be reviewed when the location of the images can be user-configured.
 *
 * @author Philip Warner
 */
public class CoversDBAdapter implements AutoCloseable {

    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync */
    private static final Synchronizer mSynchronizer = new Synchronizer();
    /** DB location */
    private static final String COVERS_DATABASE_NAME = "covers.db";
    /** DB Version */
    private static final int COVERS_DATABASE_VERSION = 1;

    /** Static Factory object to create the custom cursor */
    private static final SQLiteDatabase.CursorFactory mTrackedCursorFactory = new SQLiteDatabase.CursorFactory() {
        @Override
        public Cursor newCursor(
                SQLiteDatabase db,
                @NonNull SQLiteCursorDriver masterQuery,
                @NonNull String editTable,
                @NonNull SQLiteQuery query) {
            return new TrackedCursor(masterQuery, editTable, query, mSynchronizer);
        }
    };

    /* Domain definitions */
    /** TBL_IMAGE */
    private static final DomainDefinition DOM_ID = new DomainDefinition("_id", TableInfo.TYPE_INTEGER, "", "primary key autoincrement");

    private static final DomainDefinition DOM_DATE = new DomainDefinition("date", TableInfo.TYPE_DATETIME, "not null", "default current_timestamp");
    private static final DomainDefinition DOM_TYPE = new DomainDefinition("type", TableInfo.TYPE_TEXT, "not null", "");    // T = Thumbnail; C = cover?
    private static final DomainDefinition DOM_IMAGE = new DomainDefinition("image", TableInfo.TYPE_BLOB, "not null", "");
    private static final DomainDefinition DOM_WIDTH = new DomainDefinition("width", TableInfo.TYPE_INTEGER, "not null", "");
    private static final DomainDefinition DOM_HEIGHT = new DomainDefinition("height", TableInfo.TYPE_INTEGER, "not null", "");
    private static final DomainDefinition DOM_SIZE = new DomainDefinition("size", TableInfo.TYPE_INTEGER, "not null", "");

    private static final DomainDefinition DOM_FILENAME = new DomainDefinition("filename", TableInfo.TYPE_TEXT, "", "");

    /** table definitions */
    private static final TableDefinition TBL_IMAGE = new TableDefinition("image",
            DOM_ID, DOM_TYPE, DOM_IMAGE, DOM_DATE, DOM_WIDTH, DOM_HEIGHT, DOM_SIZE, DOM_FILENAME);
    /**
     * run a count for the desired file. 1 == exists, 0 == not there
     */
    private static final String SQL_COUNT_ID = "SELECT COUNT(" + DOM_ID + ") FROM " + TBL_IMAGE + " WHERE " + DOM_FILENAME + "=?";
    /**
     * all tables
     */
    private static final TableDefinition[] TABLES = new TableDefinition[]{TBL_IMAGE};
    /**
     * Not debug!
     * close() will only really close if mInstanceCounter == 0 is reached.
     */
    @NonNull
    private static final AtomicInteger mInstanceCounter = new AtomicInteger();
    /**
     * We *try* to connect in the Constructor. But this can fail.
     * This is ok, as this class/db is for caching only.
     * So before using it, every method in this class MUST test on != null
     */
    private static SynchronizedDb mSyncedDb;

    /* table indexes */
    static {
        TBL_IMAGE
                .addIndex("id", true, DOM_ID)
                .addIndex("file", true, DOM_FILENAME)
                .addIndex("file_date", true, DOM_FILENAME, DOM_DATE);
    }

    /** List of statements we create so we can clean them when the instance is closed. */
    private final SqlStatementManager mStatements = new SqlStatementManager();

    /** {@link #saveFile(String, int, int, byte[])} */
    @Nullable
    private SynchronizedStatement mExistsStmt = null;

    /** singleton */
    private static CoversDBAdapter mInstance;

    /**
     * Get the singleton instance.
     *
     * Reminder: we always use the *application* context for the database connection.
     */
    public static CoversDBAdapter getInstance() {
        if (mInstance == null) {
            mInstance = new CoversDBAdapter();
        }
        // check each time, as it might have failed last time but might work now.
        if (mSyncedDb == null) {
            mInstance.open(BookCatalogueApp.getAppContext());
        }

        int noi = mInstanceCounter.incrementAndGet();
        if (/* always show debug */ BuildConfig.DEBUG) {
            Logger.info(mInstance, "instances created: " + noi);
        }
        return mInstance;
    }

    private CoversDBAdapter() {
    }

    private void open(final @NonNull Context context) {
        final SQLiteOpenHelper coversHelper = new CoversDbHelper(context, mTrackedCursorFactory);

        // Try to connect.
        try {
            mSyncedDb = new SynchronizedDb(coversHelper, mSynchronizer);
        } catch (Exception e) {
            // Assume exception means DB corrupt. Log, rename, and retry
            Logger.error(e, "Failed to open covers db");
            if (!StorageUtils.renameFile(StorageUtils.getFile(COVERS_DATABASE_NAME),
                    StorageUtils.getFile(COVERS_DATABASE_NAME + ".dead"))) {
                Logger.error("Failed to rename dead covers database: ");
            }

            // try again?
            try {
                mSyncedDb = new SynchronizedDb(coversHelper, mSynchronizer);
            } catch (Exception e2) {
                // If we fail a second time (creating a new DB), then just give up.
                Logger.error(e2, "Covers database unavailable");
            }
        }
    }

    /**
     * Construct the cache ID for a given thumbnail spec.
     *
     * TODO: is this note still true ?
     * NOTE: Any changes to the resulting name MUST be reflect in {@link #eraseCachedBookCover}
     */
    @NonNull
    public static String getThumbnailCoverCacheId(final @NonNull String hash,
                                                  final int maxWidth,
                                                  final int maxHeight) {
        return hash + ".thumb." + maxWidth + "x" + maxHeight + ".jpg";
    }

    /**
     * Generic function to close the database
     * It does not 'close' the database in the literal sense, but
     * performs a cleanup by closing all open statements
     *
     * So it should really be called cleanup()
     * But it allows us to use try-with-resources.
     *
     * Consequently, there is no need to 'open' anything before running further operations.
     */
    @Override
    public void close() {
        // must be in a synchronized, as we use noi twice.
        synchronized (mInstanceCounter) {
            int noi = mInstanceCounter.decrementAndGet();
            if (/* always show debug */BuildConfig.DEBUG) {
                Logger.info(this, "instances left: " + mInstanceCounter);
            }

            if (noi == 0) {
                if (mSyncedDb != null) {
                    mStatements.close();
                }
            }
        }
    }

    /**
     * Delete the cached covers associated with the passed hash
     */
    public void deleteBookCover(final @NonNull String bookHash) {
        if (mSyncedDb == null) {
            return;
        }
        mSyncedDb.delete(TBL_IMAGE.getName(), DOM_FILENAME + " LIKE ?", new String[]{bookHash + "%"});
    }

    /**
     * Get the named 'file'
     *
     * @return byte[] of image data
     */
    @Nullable
    private byte[] getFile(final @NonNull String filename, final @NonNull Date lastModified) {
        if (mSyncedDb == null) {
            return null;
        }

        try (Cursor cursor = mSyncedDb.query(TBL_IMAGE.getName(),
                new String[]{DOM_IMAGE.name},
                DOM_FILENAME + "=? AND " + DOM_DATE + " > ?",
                new String[]{filename, DateUtils.utcSqlDateTime(lastModified)},
                null,
                null,
                null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return cursor.getBlob(0);
        }
    }

    /**
     * Save the passed bitmap to a 'file'
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred, or 1 for a successful update
     */
    @SuppressWarnings("UnusedReturnValue")
    public long saveFile(final @NonNull Bitmap bitmap, final @NonNull String filename) {
        if (mSyncedDb == null) {
            return -1L;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
        byte[] bytes = out.toByteArray();

        return saveFile(filename, bitmap.getHeight(), bitmap.getWidth(), bytes);
    }

    /**
     * Save the passed encoded image data to a 'file'
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred, or 1 for a successful update
     */
    private long saveFile(final @NonNull String filename,
                          final int height, final int width,
                          final @NonNull byte[] bytes) {
        if (mSyncedDb == null) {
            return -1L;
        }

        ContentValues cv = new ContentValues();
        cv.put(DOM_FILENAME.name, filename);
        cv.put(DOM_IMAGE.name, bytes);
        cv.put(DOM_DATE.name, DateUtils.utcSqlDateTimeForToday());
        cv.put(DOM_TYPE.name, "T");
        cv.put(DOM_WIDTH.name, height);
        cv.put(DOM_HEIGHT.name, width);
        cv.put(DOM_SIZE.name, bytes.length);

        if (mExistsStmt == null) {
            mExistsStmt = mStatements.add(mSyncedDb, "mExistsStmt", SQL_COUNT_ID);
        }
        mExistsStmt.bindString(1, filename);

        if (mExistsStmt.count() == 0) {
            return mSyncedDb.insert(TBL_IMAGE.getName(), null, cv);
        } else {
            return mSyncedDb.update(TBL_IMAGE.getName(), cv, DOM_FILENAME.name + "=?", new String[]{filename});
        }
    }

    /**
     * delete all rows
     */
    public void eraseCoverCache() {
        if (mSyncedDb == null) {
            return;
        }
        mSyncedDb.delete(TBL_IMAGE.getName(), null, null);
    }

    /**
     * Called in the UI thread, will return a cached image OR NULL.
     *
     * @param originalFile File representing original image file
     * @param destView     View to populate
     * @param hash         used to construct the cacheId
     * @param maxWidth     used to construct the cacheId
     * @param maxHeight    used to construct the cacheId
     *
     * @return Bitmap (if cached) or null (if not cached)
     */
    @Nullable
    public Bitmap fetchCachedImageIntoImageView(final @NonNull File originalFile,
                                                final @Nullable ImageView destView,
                                                final @NonNull String hash,
                                                final int maxWidth,
                                                final int maxHeight) {
        return fetchCachedImageIntoImageView(originalFile, destView, getThumbnailCoverCacheId(hash, maxWidth, maxHeight));
    }

    /**
     * Called in the UI thread, will return a cached image OR NULL.
     *
     * @param originalFile File representing original image file
     * @param destView     View to populate
     * @param cacheId      ID of the image in the cache
     *
     * @return Bitmap (if cached) or null (if not cached, or no database)
     */
    @Nullable
    private Bitmap fetchCachedImageIntoImageView(final @Nullable File originalFile,
                                                 final @Nullable ImageView destView,
                                                 final @NonNull String cacheId) {
        if (mSyncedDb == null) {
            return null;
        }

        Bitmap bitmap = null;   // resultant Bitmap (which we will return)

        byte[] bytes;
        Date expiryDate;
        if (originalFile == null) {
            expiryDate = new Date(0L);
        } else {
            expiryDate = new Date(originalFile.lastModified());
        }

        // Wrap in try/catch. It's possible the SDCard got removed and DB is now inaccessible
        try {
            bytes = getFile(cacheId, expiryDate);
        } catch (Exception e) {
            return null;
        }

        if (bytes != null) {
            try {
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception e) {
                Logger.error(e, "");
                return null;
            }
        }

        if (bitmap != null) {
            //
            // Remove any tasks that may be getting the image because they may overwrite anything we do.
            // Remember: the view may have been re-purposed and have a different associated task which
            // must be removed from the view and removed from the queue.
            //
            if (destView != null) {
                GetThumbnailTask.clearOldTaskFromView(destView);
            }

            // We found it in cache
            if (destView != null) {
                destView.setImageBitmap(bitmap);
            }
            // Return the image
        }
        return bitmap;
    }

    /**
     * Erase all cached images relating to the passed book UUID.
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    int eraseCachedBookCover(final @NonNull String uuid) {
        if (mSyncedDb == null) {
            return 0;
        }
        // We use encodeString here because it's possible a user screws up the data and imports
        // bad UUIDs...this has happened.
        String whereClause = DOM_FILENAME + " glob '" + CatalogueDBAdapter.encodeString(uuid) + ".*'";
        return mSyncedDb.delete(TBL_IMAGE.getName(), whereClause, new String[]{});
    }

    /**
     * Analyze the database
     */
    public void analyze() {
        if (mSyncedDb == null) {
            return;
        }
        mSyncedDb.analyze();
    }

    public static class CoversDbHelper extends SQLiteOpenHelper {

        CoversDbHelper(final @NonNull Context context,
                       @SuppressWarnings("SameParameterValue") final @NonNull SQLiteDatabase.CursorFactory factory) {
            super(context, COVERS_DATABASE_NAME, factory, COVERS_DATABASE_VERSION);
        }

        public static String getDatabasePath(final @NonNull Context context) {
            return context.getDatabasePath(COVERS_DATABASE_NAME).getAbsolutePath();
        }

        /**
         * As with SQLiteOpenHelper, routine called to create DB
         */
        @Override
        @CallSuper
        public void onCreate(final @NonNull SQLiteDatabase db) {
            Logger.info(this, "Creating database: " + db.getPath());
            TableDefinition.createTables(new SynchronizedDb(db, mSynchronizer), TABLES);
        }

        /**
         * As with SQLiteOpenHelper, routine called to upgrade DB
         */
        @Override
        @CallSuper
        public void onUpgrade(final @NonNull SQLiteDatabase db, final int oldVersion, final int newVersion) {
            Logger.info(this, "Upgrading database: " + db.getPath());
            throw new IllegalStateException("Upgrades not handled yet!");
        }


    }
}
