/*
 * @Copyright 2018-2022 HardBackNutter
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
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
     */
    public CalibreLibraryDaoImpl() {
        super(TAG);
    }

    @Override
    @Nullable
    public CalibreLibrary getLibraryById(final long id) {
        try (Cursor cursor = db.rawQuery(SELECT_LIBRARY_BY_ID,
                                         new String[]{String.valueOf(id)})) {
            return loadLibrary(cursor);
        }
    }

    @Override
    @Nullable
    public CalibreLibrary findLibraryByUuid(@NonNull final String uuid) {
        try (Cursor cursor = db.rawQuery(SELECT_LIBRARY_BY_UUID, new String[]{uuid})) {
            return loadLibrary(cursor);
        }
    }

    @Override
    @Nullable
    public CalibreLibrary findLibraryByStringId(@NonNull final String libraryStringId) {
        try (Cursor cursor = db.rawQuery(SELECT_LIBRARY_BY_STRING_ID,
                                         new String[]{libraryStringId})) {
            return loadLibrary(cursor);
        }
    }

    @Nullable
    private CalibreLibrary loadLibrary(@NonNull final Cursor cursor) {
        if (cursor.moveToFirst()) {
            final DataHolder rowData = new CursorRow(cursor);
            final CalibreLibrary library = new CalibreLibrary(rowData.getLong(DBKey.PK_ID),
                                                              rowData);
            library.setVirtualLibraries(getVirtualLibraries(library.getId()));
            return library;
        }
        return null;
    }


    private long find(@NonNull final CalibreLibrary library) {
        try (SynchronizedStatement stmt = db.compileStatement(SELECT_LIBRARY_ID_BY_STRING_ID)) {
            stmt.bindString(1, library.getLibraryStringId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public ArrayList<CalibreLibrary> getAllLibraries() {

        final ArrayList<CalibreLibrary> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(SELECT_LIBRARIES, null)) {
            final DataHolder rowData = new CursorRow(cursor);
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
    public void fixId(@NonNull final CalibreLibrary library) {
        final long id = find(library);
        library.setId(id);
    }

    @Override
    public long insert(@NonNull final CalibreLibrary library) {

        try (SynchronizedStatement stmt = db.compileStatement(INSERT_LIBRARY)) {
            stmt.bindString(1, library.getUuid());
            stmt.bindString(2, library.getLibraryStringId());
            stmt.bindString(3, library.getName());
            stmt.bindString(4, library.getLastSyncDateAsString());
            stmt.bindLong(5, library.getMappedBookshelfId());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                library.setId(iId);
                insertVirtualLibraries(library);
            }
            return iId;
        }
    }

    @Override
    public boolean update(@NonNull final CalibreLibrary library) {

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.CALIBRE_LIBRARY_UUID, library.getUuid());
        cv.put(DBKey.CALIBRE_LIBRARY_STRING_ID, library.getLibraryStringId());
        cv.put(DBKey.CALIBRE_LIBRARY_NAME, library.getName());
        cv.put(DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC, library.getLastSyncDateAsString());
        cv.put(DBKey.FK_BOOKSHELF, library.getMappedBookshelfId());

        final int rowsAffected = db.update(TBL_CALIBRE_LIBRARIES.getName(), cv,
                                           DBKey.PK_ID + "=?",
                                           new String[]{String.valueOf(library.getId())});
        if (0 < rowsAffected) {
            // just delete and recreate...
            deleteVirtualLibraries(library.getId());
            insertVirtualLibraries(library);
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(@NonNull final CalibreLibrary library) {

        final int rowsAffected;

        try (SynchronizedStatement stmt = db.compileStatement(DELETE_LIBRARY_BY_ID)) {
            stmt.bindLong(1, library.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            library.setId(0);
        }
        return rowsAffected == 1;
    }


    /**
     * Get the list of Calibre <strong>virtual</strong>libraries for the given library id.
     *
     * @param libraryId row id for the physical library
     *
     * @return list of virtual libs
     */
    @NonNull
    private ArrayList<CalibreVirtualLibrary> getVirtualLibraries(final long libraryId) {

        final ArrayList<CalibreVirtualLibrary> list = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(SELECT_VLIBS_BY_LIBRARY_ID,
                                         new String[]{String.valueOf(libraryId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new CalibreVirtualLibrary(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @Nullable
    public CalibreVirtualLibrary getVirtualLibrary(final long libraryId,
                                                   @NonNull final String name) {

        try (Cursor cursor = db.rawQuery(SELECT_VLIB_BY_LIBRARY_ID_AND_NAME,
                                         new String[]{String.valueOf(libraryId), name})) {

            final DataHolder rowData = new CursorRow(cursor);
            if (cursor.moveToFirst()) {
                return new CalibreVirtualLibrary(rowData.getLong(DBKey.PK_ID), rowData);
            }
        }
        return null;
    }

    @Override
    public boolean update(@NonNull final CalibreVirtualLibrary library) {

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.FK_CALIBRE_LIBRARY, library.getLibraryId());
        cv.put(DBKey.CALIBRE_LIBRARY_NAME, library.getName());
        cv.put(DBKey.CALIBRE_VIRT_LIB_EXPR, library.getExpr());
        cv.put(DBKey.FK_BOOKSHELF, library.getMappedBookshelfId());

        return 0 < db.update(TBL_CALIBRE_VIRTUAL_LIBRARIES.getName(), cv,
                             DBKey.PK_ID + "=?",
                             new String[]{String.valueOf(library.getId())});
    }

    private void insertVirtualLibraries(@NonNull final CalibreLibrary library) {
        final ArrayList<CalibreVirtualLibrary> vlibs = library.getVirtualLibraries();
        if (!vlibs.isEmpty()) {
            try (SynchronizedStatement stmt = db.compileStatement(INSERT_VIRTUAL_LIBRARY)) {
                for (final CalibreVirtualLibrary vlib : vlibs) {
                    // always update the foreign key
                    vlib.setLibraryId(library.getId());

                    stmt.bindLong(1, vlib.getLibraryId());
                    stmt.bindString(2, vlib.getName());
                    stmt.bindString(3, vlib.getExpr());
                    stmt.bindLong(4, vlib.getMappedBookshelfId());
                    final long iId = stmt.executeInsert();
                    if (iId > 0) {
                        vlib.setId(iId);
                    }
                }
            }
        }
    }

    private void deleteVirtualLibraries(final long libraryId) {
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
