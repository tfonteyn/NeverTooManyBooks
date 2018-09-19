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
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.ImageView;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.GetThumbnailTask;
import com.eleybourn.bookcatalogue.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.IndexDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;

/**
 * Singleton to preserve resources
 * <p>
 * DB Helper for Covers DB on external storage.
 * <p>
 * In the initial pass, the covers database has a single table whose members are accessed via unique
 * 'file names'.
 *
 * @author Philip Warner
 */
public class CoversDbHelper implements AutoCloseable {

    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync  */
    private static final Synchronizer mSynchronizer = new Synchronizer();
    /** DB location */
    private static final String COVERS_DATABASE_NAME = "covers.db";
    /** DB Version */
    private static final int COVERS_DATABASE_VERSION = 1;
    /**
     * Static Factory object to create the custom cursor
     */
    private static final CursorFactory mTrackedCursorFactory = new CursorFactory() {
        @Override
        public Cursor newCursor(
                SQLiteDatabase db,
                SQLiteCursorDriver masterQuery,
                String editTable,
                SQLiteQuery query) {
            return new TrackedCursor(masterQuery, editTable, query, mSynchronizer);
        }
    };
    private static final DomainDefinition DOM_ID = new DomainDefinition("_id", "integer", "", "primary key autoincrement");
    private static final DomainDefinition DOM_DATE = new DomainDefinition("date", "datetime", "not null", "default current_timestamp");

    // Domain definitions
    private static final DomainDefinition DOM_TYPE = new DomainDefinition("type", "text", "not null", "");    // T = Thumbnail; C = cover?
    private static final DomainDefinition DOM_IMAGE = new DomainDefinition("image", "blob", "not null", "");
    private static final DomainDefinition DOM_WIDTH = new DomainDefinition("width", "integer", "not null", "");
    private static final DomainDefinition DOM_HEIGHT = new DomainDefinition("height", "integer", "not null", "");
    private static final DomainDefinition DOM_SIZE = new DomainDefinition("size", "integer", "not null", "");
    private static final DomainDefinition DOM_FILENAME = new DomainDefinition("filename", "text", "", "");
    // table definitions
    private static final TableDefinition TBL_IMAGE = new TableDefinition("image",
            DOM_ID, DOM_TYPE, DOM_IMAGE, DOM_DATE, DOM_WIDTH, DOM_HEIGHT, DOM_SIZE, DOM_FILENAME);
    private static final TableDefinition[] TABLES = new TableDefinition[]{TBL_IMAGE};

    /**
     * We *try* to connect in the Constructor. But this can fail and is (it seems) not fatal.
     * So before using it, every method in this class MUST test on != null
     */
    private static SynchronizedDb mSyncedDb;

    /**
     * close() will only really close if 0 is reached, note this is NOT a debug count!
     * But.... we normally use this class as a singleton...
     * So... won't increase/decrease when using only singletons
     * Will be used if you create "new" instances yourself, so it's a nice debug as well.
     */
    private static Integer mCountToGetInstance = 0;
    /**
     * Our singleton
     */
    private static CoversDbHelper mInstance;

    static {
        TBL_IMAGE
                .addIndex("id", true, DOM_ID)
                .addIndex("file", true, DOM_FILENAME)
                .addIndex("file_date", true, DOM_FILENAME, DOM_DATE);
    }

    /**
     * List of statements we create so we can clean them when the instance is closed.
     */
    private final SqlStatementManager mStatements = new SqlStatementManager();
    /**
     * Delete the cached covers associated with the passed hash
     *
     * @param filename
     */
    private SynchronizedStatement mDeleteBookCoversStmt = null;
    /**
     * Save the passed encoded image data to a 'file'
     *
     * @param filename
     * @param bm
     */
    private SynchronizedStatement mExistsStmt = null;
    /**
     * Erase all images in the covers cache
     */
    private SynchronizedStatement mEraseCoverCacheStmt = null;

    /**
     * Constructor. Fill in required fields.
     */
    private CoversDbHelper(@NonNull final Context context) {
        if (mSyncedDb == null) {
            final SQLiteOpenHelper mHelper = new CoversHelper(context,
                    StorageUtils.getFile(COVERS_DATABASE_NAME).getAbsolutePath(),
                    mTrackedCursorFactory,
                    COVERS_DATABASE_VERSION);

            // Try to connect.
            try {
                mSyncedDb = new SynchronizedDb(mHelper, mSynchronizer);
            } catch (Exception e) {
                // Assume exception means DB corrupt. Log, rename, and retry
                Logger.logError(e, "Failed to open covers db");
                File f = StorageUtils.getFile(COVERS_DATABASE_NAME);
                if (!f.renameTo(StorageUtils.getFile(COVERS_DATABASE_NAME + ".dead"))) {
                    Logger.logError("Failed to rename dead covers database: ");
                }

                // Connect again...
                try {
                    mSyncedDb = new SynchronizedDb(mHelper, mSynchronizer);
                } catch (Exception e2) {
                    // If we fail a second time (creating a new DB), then just give up.
                    Logger.logError(e2, "Covers database unavailable");
                }
            }
        }

        synchronized (this) {
            mCountToGetInstance++;
            if (BuildConfig.DEBUG) {
                System.out.println("CovDBA instances created: " + mCountToGetInstance);
            }
        }

    }

//    /**
//	 * Delete the named 'file'
//	 */
//	public void deleteFile(final String filename) {
//	    if (mSyncedDb == null) {
//            return;
//        }
//
//        SyncLock txLock = mSyncedDb.beginTransaction(true);
//        try {
//            mSyncedDb.execSQL("Drop table " + TBL_IMAGE);
//            DbUtils.createTables(mSyncedDb, new TableDefinition[]{TBL_IMAGE}, true);
//            mSyncedDb.setTransactionSuccessful();
//        } finally {
//            mSyncedDb.endTransaction(txLock);
//        }
//	}

    /**
     * Get the 'covers' DB from external storage.
     * <p>
     * Always use as:
     * try(CoversDbHelper coversDbHelper = CoversDbHelper.getInstance()) { use coversDbHelper here }
     * <p>
     * or call close() yourself ... but if you forget, you might waste resources
     */
    public static CoversDbHelper getInstance(@NonNull final Context context) {
        if (mInstance == null) {
            mInstance = new CoversDbHelper(context);
        }
        return mInstance;
    }

    /**
     * Construct the cache ID for a given thumbnail spec.
     * <p>
     * TODO: is this note still true ?
     * NOTE: Any changes to the resulting name MUST be reflect in CoversDbHelper.eraseCachedBookCover()
     */
    public static String getThumbnailCoverCacheId(@NonNull final String hash, final int maxWidth, final int maxHeight) {
        return hash + ".thumb." + maxWidth + "x" + maxHeight + ".jpg";
    }

    /**
     * Given arrays of table and index definitions, create the database.
     *  @param db     Blank database
     * @param tables Table list
     */
    static void createTables(@NonNull final SynchronizedDb db, @NonNull final TableDefinition[] tables) {
        for (TableDefinition t : tables) {
            t.create(db, true);
            for (IndexDefinition i : t.getIndexes()) {
                db.execSQL(i.getSql());
            }
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            mCountToGetInstance--;
            if (BuildConfig.DEBUG) {
                System.out.println("CovDBA instances left: " + mCountToGetInstance);
            }

            if (mCountToGetInstance == 0) {
                if (mSyncedDb != null) {
                    mStatements.close();
                }
            }
        }
    }

//	/**
//	 * Get the named 'file'
//	 *
//	 * @return	byte[] of image data
//	 */
//	public boolean isEntryValid(String filename, Date lastModified) {
//        if (mSyncedDb == null) {
//            return false;
//        }
//		Cursor c = mSyncedDb.query(TBL_IMAGE.getName(), new String[]{DOM_ID.name}, DOM_FILENAME + "=? and " + DOM_DATE + " > ?",
//								new String[]{filename, DateUtils.toSqlDateTime(lastModified)}, null, null, null);
//		try {
//			return c.moveToFirst();
//		} finally {
//			c.close();
//		}
//	}

    public void deleteBookCover(final String bookHash) {
        if (mSyncedDb == null) {
            return;
        }

        if (mDeleteBookCoversStmt == null) {
            String sql = "Delete From " + TBL_IMAGE + " Where " + DOM_FILENAME + " LIKE ?";
            mDeleteBookCoversStmt = mStatements.add(mSyncedDb, "mDeleteBookCoversStmt", sql);
        }

        mDeleteBookCoversStmt.bindString(1, bookHash + "%");

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            mDeleteBookCoversStmt.execute();
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    /**
     * Get the named 'file'
     *
     * @return byte[] of image data
     */
    @Nullable
    private byte[] getFile(final String filename, final Date lastModified) {
        if (mSyncedDb == null) {
            return null;
        }

        try (Cursor c = mSyncedDb.query(TBL_IMAGE.getName(),
                new String[]{DOM_IMAGE.name},
                DOM_FILENAME + "=? and " + DOM_DATE + " > ?",
                new String[]{filename, DateUtils.toSqlDateTime(lastModified)},
                null,
                null,
                null)) {
            if (!c.moveToFirst())
                return null;
            return c.getBlob(0);
        }
    }

    /**
     * Save the passed bitmap to a 'file'
     */
    public void saveFile(@NonNull final Bitmap bm, @NonNull final  String filename) {
        if (mSyncedDb == null) {
            return;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 70, out);
        byte[] bytes = out.toByteArray();

        saveFile(filename, bm.getHeight(), bm.getWidth(), bytes);
    }

    private void saveFile(@NonNull final  String filename, final int height, final int width, @NonNull final  byte[] bytes) {
        if (mSyncedDb == null) {
            return;
        }

        if (mExistsStmt == null) {
            String sql = "Select Count(" + DOM_ID + ") From " + TBL_IMAGE + " Where " + DOM_FILENAME + " = ?";
            mExistsStmt = mStatements.add(mSyncedDb, "mExistsStmt", sql);
        }

        ContentValues cv = new ContentValues();

        cv.put(DOM_FILENAME.name, filename);
        cv.put(DOM_IMAGE.name, bytes);

        cv.put(DOM_DATE.name, DateUtils.toSqlDateTime(new Date()));
        cv.put(DOM_TYPE.name, "T");
        cv.put(DOM_WIDTH.name, height);
        cv.put(DOM_HEIGHT.name, width);
        cv.put(DOM_SIZE.name, bytes.length);

        mExistsStmt.bindString(1, filename);
        long rows;

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // count, so no SQLiteDoneException
            if (mExistsStmt.simpleQueryForLong() == 0) {
                rows = mSyncedDb.insert(TBL_IMAGE.getName(), null, cv);
            } else {
                rows = mSyncedDb.update(TBL_IMAGE.getName(), cv, DOM_FILENAME.name + " = ?", new String[]{filename});
            }
            if (rows == 0)
                throw new RuntimeException("Failed to insert data");
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    public void eraseCoverCache() {
        if (mSyncedDb == null) {
            return;
        }
        if (mEraseCoverCacheStmt == null) {
            String sql = "Delete From " + TBL_IMAGE;
            mEraseCoverCacheStmt = mStatements.add(mSyncedDb, "mEraseCoverCacheStmt", sql);
        }
        mEraseCoverCacheStmt.execute();
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
    public Bitmap fetchCachedImageIntoImageView(@NonNull final  File originalFile,
                                                @Nullable final ImageView destView,
                                                @NonNull final  String hash,
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
     * @return Bitmap (if cached) or NULL (if not cached)
     */
    @SuppressWarnings("WeakerAccess")
    @Nullable
    public Bitmap fetchCachedImageIntoImageView(@Nullable final File originalFile,
                                                @Nullable final ImageView destView,
                                                @NonNull final  String cacheId) {
        if (mSyncedDb == null) {
            return null;
        }

        Bitmap bm = null;   // resultant Bitmap (which we will return)

        byte[] bytes;
        Date expiry;
        if (originalFile == null)
            expiry = new Date(0L);
        else
            expiry = new Date(originalFile.lastModified());

        // Wrap in try/catch. It's possible the SDCard got removed and DB is now inaccessible
        try {
            bytes = getFile(cacheId, expiry);
        } catch (Exception e) {
            bytes = null;
        }

        if (bytes != null) {
            try {
                bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception e) {
                Logger.logError(e, "");
            }
        }

        if (bm != null) {
            //
            // Remove any tasks that may be getting the image because they may overwrite anything we do.
            // Remember: the view may have been re-purposed and have a different associated task which
            // must be removed from the view and removed from the queue.
            //
            if (destView != null)
                GetThumbnailTask.clearOldTaskFromView(destView);

            // We found it in cache
            if (destView != null)
                destView.setImageBitmap(bm);
            // Return the image
        }
        return bm;
    }

    /**
     * Erase all cached images relating to the passed book UUID.
     */
    public void eraseCachedBookCover(@NonNull final String uuid) {
        if (mSyncedDb == null) {
            return;
        }
        // We use encodeString here because it's possible a user screws up the data and imports
        // bad UUIDs...this has happened.
        String sql = DOM_FILENAME + " glob '" + CatalogueDBAdapter.encodeString(uuid) + ".*'";
        mSyncedDb.delete(TBL_IMAGE.getName(), sql, new String[]{});
    }

    /**
     * Analyze the database
     */
    public void analyze() {
        if (mSyncedDb == null) {
            return;
        }
        // Don't do VACUUM -- it's a complete rebuild
        //mSyncedDb.execSQL("vacuum");
        mSyncedDb.execSQL("analyze");
    }

    private static class CoversHelper extends SQLiteOpenHelper {

        CoversHelper(@NonNull final Context context,
                     @NonNull final String dbFilePath,
                     @NonNull final CursorFactory factory,
                     final int version) {
            super(context, dbFilePath, factory, version);
        }

        /**
         * As with SQLiteOpenHelper, routine called to create DB
         */
        @Override
        public void onCreate(@NonNull final SQLiteDatabase db) {
            createTables(new SynchronizedDb(db, mSynchronizer), TABLES);
        }

        /**
         * As with SQLiteOpenHelper, routine called to upgrade DB
         */
        @Override
        public void onUpgrade(@NonNull final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            throw new RuntimeException("Upgrades not handled yet!");
        }
    }
}
