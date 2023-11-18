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
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreVirtualLibrary;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_LIBRARIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_VIRTUAL_LIBRARIES;

public class CalibreLibraryDaoImpl
        extends BaseDaoImpl
        implements CalibreLibraryDao {

    /** Log tag. */
    private static final String TAG = "CalibreLibraryDaoImpl";

    private static final String ERROR_UPDATE_FROM = "Update from\n";
    private static final String ERROR_INSERT_FROM = "Insert from\n";

    private static final String BASE_SELECT_LIB =
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.FK_BOOKSHELF
            + ',' + DBKey.CALIBRE_LIBRARY_UUID
            + ',' + DBKey.CALIBRE_LIBRARY_STRING_ID
            + ',' + DBKey.CALIBRE_LIBRARY_NAME
            + ',' + DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC
            + _FROM_ + TBL_CALIBRE_LIBRARIES.getName();

    /**
     * Get the id of a {@link CalibreLibrary} by string-id.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     */
    private static final String SELECT_LIBRARY_ID_BY_STRING_ID =
            SELECT_ + DBKey.PK_ID + _FROM_ + TBL_CALIBRE_LIBRARIES.getName()
            + _WHERE_ + DBKey.CALIBRE_LIBRARY_STRING_ID + "=?" + _COLLATION;

    private static final String SELECT_LIBRARY_BY_UUID =
            BASE_SELECT_LIB + _WHERE_ + DBKey.CALIBRE_LIBRARY_UUID + "=?";

    private static final String SELECT_LIBRARY_BY_STRING_ID =
            BASE_SELECT_LIB + _WHERE_ + DBKey.CALIBRE_LIBRARY_STRING_ID + "=?";

    private static final String SELECT_LIBRARY_BY_ID =
            BASE_SELECT_LIB + _WHERE_ + DBKey.PK_ID + "=?";

    /** The list of all physical Calibre libraries. */
    private static final String SELECT_LIBRARIES =
            BASE_SELECT_LIB + _ORDER_BY_ + DBKey.CALIBRE_LIBRARY_NAME + _COLLATION;


    private static final String BASE_SELECT_VLIB =
            SELECT_ + DBKey.PK_ID
            + ',' + DBKey.FK_BOOKSHELF
            + ',' + DBKey.FK_CALIBRE_LIBRARY
            + ',' + DBKey.CALIBRE_LIBRARY_NAME
            + ',' + DBKey.CALIBRE_VIRT_LIB_EXPR
            + _FROM_ + TBL_CALIBRE_VIRTUAL_LIBRARIES.getName();

    /** The list of virtual libraries for a specified physical library. */
    private static final String SELECT_VLIBS_BY_LIBRARY_ID =
            BASE_SELECT_VLIB
            + _WHERE_ + DBKey.FK_CALIBRE_LIBRARY + "=?"
            + _ORDER_BY_ + DBKey.CALIBRE_LIBRARY_NAME + _COLLATION;

    /** The list of virtual libraries for a specified physical library. */
    private static final String SELECT_VLIB_BY_LIBRARY_ID_AND_NAME =
            BASE_SELECT_VLIB
            + _WHERE_ + DBKey.FK_CALIBRE_LIBRARY + "=?"
            + _AND_ + DBKey.CALIBRE_LIBRARY_NAME + "=?";


    private static final String INSERT_LIBRARY =
            INSERT_INTO_ + TBL_CALIBRE_LIBRARIES.getName()
            + '(' + DBKey.CALIBRE_LIBRARY_UUID
            + ',' + DBKey.CALIBRE_LIBRARY_STRING_ID
            + ',' + DBKey.CALIBRE_LIBRARY_NAME
            + ',' + DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC
            + ',' + DBKey.FK_BOOKSHELF
            + ") VALUES (?,?,?,?,?)";

    private static final String INSERT_VIRTUAL_LIBRARY =
            INSERT_INTO_ + TBL_CALIBRE_VIRTUAL_LIBRARIES.getName()
            + '(' + DBKey.FK_CALIBRE_LIBRARY
            + ',' + DBKey.CALIBRE_LIBRARY_NAME
            + ',' + DBKey.CALIBRE_VIRT_LIB_EXPR
            + ',' + DBKey.FK_BOOKSHELF
            + ") VALUES (?,?,?,?)";

    /** Delete a single {@link CalibreLibrary}. */
    private static final String DELETE_LIBRARY_BY_ID =
            DELETE_FROM_ + TBL_CALIBRE_LIBRARIES.getName()
            + _WHERE_ + DBKey.PK_ID + "=?";

    /** Delete all virtual libs for a given library. */
    private static final String DELETE_VLIBS_BY_LIBRARY_ID =
            DELETE_FROM_ + TBL_CALIBRE_VIRTUAL_LIBRARIES.getName()
            + _WHERE_ + DBKey.FK_CALIBRE_LIBRARY + "=?";

    /** Delete a single {@link CalibreVirtualLibrary}. */
    private static final String DELETE_VLIB_BY_ID =
            DELETE_FROM_ + TBL_CALIBRE_VIRTUAL_LIBRARIES.getName()
            + _WHERE_ + DBKey.PK_ID + "=?";

    /** Get the id of a {@link Book} by Calibre UUID. */
    private static final String BY_CALIBRE_UUID =
            SELECT_ + DBKey.FK_BOOK + _FROM_ + TBL_CALIBRE_BOOKS.getName()
            + _WHERE_ + DBKey.CALIBRE_BOOK_UUID + "=?";

    /**
     * Constructor.
     *
     * @param db Underlying database
     */
    public CalibreLibraryDaoImpl(@NonNull final SynchronizedDb db) {
        super(db, TAG);
    }

    @Override
    @NonNull
    public Optional<CalibreLibrary> getLibraryById(final long id) {
        try (Cursor cursor = db.rawQuery(SELECT_LIBRARY_BY_ID,
                                         new String[]{String.valueOf(id)})) {
            return loadLibrary(cursor);
        }
    }

    @Override
    @NonNull
    public Optional<CalibreLibrary> findLibraryByUuid(@NonNull final String uuid) {
        try (Cursor cursor = db.rawQuery(SELECT_LIBRARY_BY_UUID, new String[]{uuid})) {
            return loadLibrary(cursor);
        }
    }

    @NonNull
    @Override
    public Optional<CalibreLibrary> findLibraryByStringId(@NonNull final String libraryStringId) {
        try (Cursor cursor = db.rawQuery(SELECT_LIBRARY_BY_STRING_ID,
                                         new String[]{libraryStringId})) {
            return loadLibrary(cursor);
        }
    }

    @NonNull
    private Optional<CalibreLibrary> loadLibrary(@NonNull final Cursor cursor) {
        if (cursor.moveToFirst()) {
            final CursorRow rowData = new CursorRow(cursor);
            final CalibreLibrary library = new CalibreLibrary(rowData.getLong(DBKey.PK_ID),
                                                              rowData);
            library.setVirtualLibraries(getVirtualLibraries(library.getId()));
            return Optional.of(library);
        }
        return Optional.empty();
    }

    private long find(@NonNull final CalibreLibrary library) {
        try (SynchronizedStatement stmt = db.compileStatement(SELECT_LIBRARY_ID_BY_STRING_ID)) {
            stmt.bindString(1, library.getLibraryStringId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public List<CalibreLibrary> getAllLibraries() {
        final List<CalibreLibrary> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(SELECT_LIBRARIES, null)) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final CalibreLibrary library = new CalibreLibrary(rowData.getLong(DBKey.PK_ID),
                                                                  rowData);
                library.setVirtualLibraries(getVirtualLibraries(library.getId()));
                list.add(library);
            }
        }
        return list;
    }

    @Override
    public void fixId(@NonNull final Context context,
                      @NonNull final CalibreLibrary library) {

        // using the mapped bookshelf-if, lookup the actual Bookshelf (with fallbacks)
        final Bookshelf libBookshelf = Bookshelf.getBookshelf(context,
                                                              library.getMappedBookshelfId(),
                                                              Bookshelf.PREFERRED,
                                                              Bookshelf.DEFAULT)
                                                .orElseThrow();
        // and update the id
        library.setMappedBookshelf(libBookshelf.getId());

        // repeat for each virtual library, with fallback the above library Bookshelf.
        library.getVirtualLibraries().forEach(vLib -> {
            final Bookshelf vLibBookshelf = Bookshelf
                    .getBookshelf(context, vLib.getMappedBookshelfId())
                    .orElse(libBookshelf);
            vLib.setMappedBookshelf(vLibBookshelf.getId());
        });

        // finally, fix the library id itself - note that the vlib id's never need fixing as such
        final long id = find(library);
        library.setId(id);
    }

    @Override
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    public long insert(@NonNull final CalibreLibrary library)
            throws DaoInsertException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // The getMappedBookshelfId MUST have been previously verified/'fixId' against
            // the BookshelfDao!
            final long iId;
            try (SynchronizedStatement stmt = db.compileStatement(INSERT_LIBRARY)) {
                stmt.bindString(1, library.getUuid());
                stmt.bindString(2, library.getLibraryStringId());
                stmt.bindString(3, library.getName());
                stmt.bindString(4, library.getLastSyncDateAsString());
                stmt.bindLong(5, library.getMappedBookshelfId());
                iId = stmt.executeInsert();
            }
            if (iId > 0) {
                insertVirtualLibraries(library);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }

                library.setId(iId);
                return iId;
            }
            throw new DaoInsertException(ERROR_INSERT_FROM + library);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoInsertException(ERROR_INSERT_FROM + library, e);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public void update(@NonNull final CalibreLibrary library)
            throws DaoInsertException, DaoUpdateException {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            // The getMappedBookshelfId MUST have been previously verified/'fixId' against
            // the BookshelfDao!
            final ContentValues cv = new ContentValues();
            cv.put(DBKey.CALIBRE_LIBRARY_UUID, library.getUuid());
            cv.put(DBKey.CALIBRE_LIBRARY_STRING_ID, library.getLibraryStringId());
            cv.put(DBKey.CALIBRE_LIBRARY_NAME, library.getName());
            cv.put(DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC, library.getLastSyncDateAsString());
            cv.put(DBKey.FK_BOOKSHELF, library.getMappedBookshelfId());

            final int rowsAffected = db.update(TBL_CALIBRE_LIBRARIES.getName(), cv,
                                               DBKey.PK_ID + "=?",
                                               new String[]{String.valueOf(library.getId())});
            if (rowsAffected > 0) {
                // just delete and recreate...
                deleteVirtualLibraries(library.getId());
                insertVirtualLibraries(library);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
                return;
            }
            throw new DaoUpdateException(ERROR_UPDATE_FROM + library);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoUpdateException(ERROR_UPDATE_FROM + library, e);
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    @Override
    public boolean delete(@NonNull final CalibreLibrary library) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(DELETE_LIBRARY_BY_ID)) {
            stmt.bindLong(1, library.getId());
            rowsAffected = stmt.executeUpdateDelete();
        } catch (@NonNull final SQLException | IllegalArgumentException e) {
            return false;
        }
        if (rowsAffected > 0) {
            library.setId(0);
            return true;
        }
        return false;
    }

    /**
     * Get the list of Calibre <strong>virtual</strong>libraries for the given library id.
     *
     * @param libraryId row id for the physical library
     *
     * @return list of virtual libs
     */
    @NonNull
    private List<CalibreVirtualLibrary> getVirtualLibraries(final long libraryId) {

        final List<CalibreVirtualLibrary> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(SELECT_VLIBS_BY_LIBRARY_ID,
                                         new String[]{String.valueOf(libraryId)})) {
            final CursorRow rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new CalibreVirtualLibrary(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public Optional<CalibreVirtualLibrary> getVirtualLibrary(final long libraryId,
                                                             @NonNull final String name) {

        try (Cursor cursor = db.rawQuery(SELECT_VLIB_BY_LIBRARY_ID_AND_NAME,
                                         new String[]{String.valueOf(libraryId), name})) {

            final CursorRow rowData = new CursorRow(cursor);
            if (cursor.moveToFirst()) {
                return Optional.of(new CalibreVirtualLibrary(rowData.getLong(DBKey.PK_ID),
                                                             rowData));
            }
        }
        return Optional.empty();
    }

    @Override
    public void update(@NonNull final CalibreVirtualLibrary library)
            throws DaoUpdateException {

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.FK_CALIBRE_LIBRARY, library.getLibraryId());
        cv.put(DBKey.CALIBRE_LIBRARY_NAME, library.getName());
        cv.put(DBKey.CALIBRE_VIRT_LIB_EXPR, library.getExpr());
        cv.put(DBKey.FK_BOOKSHELF, library.getMappedBookshelfId());

        try {
            final int rowsAffected = db.update(TBL_CALIBRE_VIRTUAL_LIBRARIES.getName(), cv,
                                               DBKey.PK_ID + "=?",
                                               new String[]{String.valueOf(library.getId())});
            if (rowsAffected > 0) {
                return;
            }
            throw new DaoUpdateException(ERROR_UPDATE_FROM + library);
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoUpdateException(ERROR_UPDATE_FROM + library, e);
        }
    }

    private void insertVirtualLibraries(@NonNull final CalibreLibrary library)
            throws DaoInsertException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final List<CalibreVirtualLibrary> vLibs = library.getVirtualLibraries();
        if (!vLibs.isEmpty()) {
            try (SynchronizedStatement stmt = db.compileStatement(INSERT_VIRTUAL_LIBRARY)) {
                for (final CalibreVirtualLibrary vLib : vLibs) {
                    // always update the foreign key
                    vLib.setLibraryId(library.getId());

                    stmt.bindLong(1, vLib.getLibraryId());
                    stmt.bindString(2, vLib.getName());
                    stmt.bindString(3, vLib.getExpr());
                    stmt.bindLong(4, vLib.getMappedBookshelfId());
                    final long iId = stmt.executeInsert();
                    if (iId > 0) {
                        vLib.setId(iId);

                    } else {
                        // Reset all id's which might have been successful.
                        // The db will be reset by the transaction
                        vLibs.forEach(v -> v.setId(0));
                        throw new DaoInsertException(ERROR_INSERT_FROM + library);
                    }
                }
            }
        }
    }

    private void deleteVirtualLibraries(final long libraryId) {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        try (SynchronizedStatement stmt = db.compileStatement(DELETE_VLIBS_BY_LIBRARY_ID)) {
            stmt.bindLong(1, libraryId);
            stmt.executeUpdateDelete();
        }
    }


    @Override
    @IntRange(from = 0)
    public long getBookIdFromCalibreUuid(@NonNull final String uuid) {
        try (SynchronizedStatement stmt = db.compileStatement(BY_CALIBRE_UUID)) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }
}
