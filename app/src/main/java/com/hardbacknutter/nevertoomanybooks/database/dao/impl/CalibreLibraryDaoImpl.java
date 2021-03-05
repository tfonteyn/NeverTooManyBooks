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
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreVirtualLibrary;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_LIBRARIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_VIRTUAL_LIBRARIES;

public class CalibreLibraryDaoImpl
        extends BaseDaoImpl
        implements CalibreLibraryDao {

    /** Log tag. */
    private static final String TAG = "CalibreLibraryDaoImpl";

    private static final String BASE_SELECT_LIB =
            SELECT_ + DBKeys.KEY_PK_ID
            + ',' + DBKeys.KEY_FK_BOOKSHELF
            + ',' + DBKeys.KEY_CALIBRE_LIBRARY_UUID
            + ',' + DBKeys.KEY_CALIBRE_LIBRARY_STRING_ID
            + ',' + DBKeys.KEY_CALIBRE_LIBRARY_NAME
            + ',' + DBKeys.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE
            + _FROM_ + TBL_CALIBRE_LIBRARIES.getName();

    /**
     * Get the id of a {@link CalibreLibrary} by name.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     */
    private static final String SELECT_LIBRARY_ID_BY_NAME =
            SELECT_ + DBKeys.KEY_PK_ID + _FROM_ + TBL_CALIBRE_LIBRARIES.getName()
            + _WHERE_ + DBKeys.KEY_CALIBRE_LIBRARY_NAME + "=?" + _COLLATION;

    private static final String SELECT_LIBRARY_BY_UUID =
            BASE_SELECT_LIB + _WHERE_ + DBKeys.KEY_CALIBRE_LIBRARY_UUID + "=?";

    private static final String SELECT_LIBRARY_BY_STRING_ID =
            BASE_SELECT_LIB + _WHERE_ + DBKeys.KEY_CALIBRE_LIBRARY_STRING_ID + "=?";

    private static final String SELECT_LIBRARY_BY_ID =
            BASE_SELECT_LIB + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /** The list of all physical Calibre libraries. */
    private static final String SELECT_LIBRARIES =
            BASE_SELECT_LIB + _ORDER_BY_ + DBKeys.KEY_CALIBRE_LIBRARY_NAME + _COLLATION;


    private static final String BASE_SELECT_VLIB =
            SELECT_ + DBKeys.KEY_PK_ID
            + ',' + DBKeys.KEY_FK_BOOKSHELF
            + ',' + DBKeys.KEY_FK_CALIBRE_LIBRARY
            + ',' + DBKeys.KEY_CALIBRE_LIBRARY_NAME
            + ',' + DBKeys.KEY_CALIBRE_VIRT_LIB_EXPR
            + _FROM_ + TBL_CALIBRE_VIRTUAL_LIBRARIES.getName();

    /** The list of virtual libraries for a specified physical library. */
    private static final String SELECT_VLIBS_BY_LIBRARY_ID =
            BASE_SELECT_VLIB
            + _WHERE_ + DBKeys.KEY_FK_CALIBRE_LIBRARY + "=?"
            + _ORDER_BY_ + DBKeys.KEY_CALIBRE_LIBRARY_NAME + _COLLATION;

    /** The list of virtual libraries for a specified physical library. */
    private static final String SELECT_VLIB_BY_LIBRARY_ID_AND_NAME =
            BASE_SELECT_VLIB
            + _WHERE_ + DBKeys.KEY_FK_CALIBRE_LIBRARY + "=?"
            + _AND_ + DBKeys.KEY_CALIBRE_LIBRARY_NAME + "=?";


    private static final String INSERT_LIBRARY =
            INSERT_INTO_ + TBL_CALIBRE_LIBRARIES.getName()
            + '(' + DBKeys.KEY_CALIBRE_LIBRARY_UUID
            + ',' + DBKeys.KEY_CALIBRE_LIBRARY_STRING_ID
            + ',' + DBKeys.KEY_CALIBRE_LIBRARY_NAME
            + ',' + DBKeys.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE
            + ',' + DBKeys.KEY_FK_BOOKSHELF
            + ") VALUES (?,?,?,?,?)";

    private static final String INSERT_VIRTUAL_LIBRARY =
            INSERT_INTO_ + TBL_CALIBRE_VIRTUAL_LIBRARIES.getName()
            + '(' + DBKeys.KEY_FK_CALIBRE_LIBRARY
            + ',' + DBKeys.KEY_CALIBRE_LIBRARY_NAME
            + ',' + DBKeys.KEY_CALIBRE_VIRT_LIB_EXPR
            + ',' + DBKeys.KEY_FK_BOOKSHELF
            + ") VALUES (?,?,?,?)";

    /** Delete a single {@link CalibreLibrary}. */
    private static final String DELETE_LIBRARY_BY_ID =
            DELETE_FROM_ + TBL_CALIBRE_LIBRARIES.getName()
            + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /** Delete all virtual libs for a given library. */
    private static final String DELETE_VLIBS_BY_LIBRARY_ID =
            DELETE_FROM_ + TBL_CALIBRE_VIRTUAL_LIBRARIES.getName()
            + _WHERE_ + DBKeys.KEY_FK_CALIBRE_LIBRARY + "=?";

    /** Delete a single {@link CalibreVirtualLibrary}. */
    private static final String DELETE_VLIB_BY_ID =
            DELETE_FROM_ + TBL_CALIBRE_VIRTUAL_LIBRARIES.getName()
            + _WHERE_ + DBKeys.KEY_PK_ID + "=?";

    /** Get the id of a {@link Book} by Calibre UUID. */
    private static final String BY_CALIBRE_UUID =
            SELECT_ + DBKeys.KEY_FK_BOOK + _FROM_ + TBL_CALIBRE_BOOKS.getName()
            + _WHERE_ + DBKeys.KEY_CALIBRE_BOOK_UUID + "=?";

    /**
     * Constructor.
     */
    public CalibreLibraryDaoImpl() {
        super(TAG);
    }

    @Nullable
    private CalibreLibrary loadLibrary(final Cursor cursor) {
        if (cursor.moveToNext()) {
            final DataHolder rowData = new CursorRow(cursor);
            final CalibreLibrary library = new CalibreLibrary(rowData.getLong(DBKeys.KEY_PK_ID),
                                                              rowData);
            library.setVirtualLibraries(getVirtualLibraries(library.getId()));
            return library;
        }
        return null;
    }

    private long getLibrary(@NonNull final CalibreLibrary library) {

        try (SynchronizedStatement stmt = mDb.compileStatement(SELECT_LIBRARY_ID_BY_NAME)) {
            stmt.bindString(1, library.getName());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @Nullable
    public CalibreLibrary getLibrary(final long id) {
        try (Cursor cursor = mDb.rawQuery(SELECT_LIBRARY_BY_ID,
                                          new String[]{String.valueOf(id)})) {
            return loadLibrary(cursor);
        }
    }

    @Override
    @Nullable
    public CalibreLibrary getLibraryByUuid(@NonNull final String uuid) {
        try (Cursor cursor = mDb.rawQuery(SELECT_LIBRARY_BY_UUID, new String[]{uuid})) {
            return loadLibrary(cursor);
        }
    }

    @Override
    @Nullable
    public CalibreLibrary getLibraryByStringId(@NonNull final String libraryStringId) {
        try (Cursor cursor = mDb.rawQuery(SELECT_LIBRARY_BY_STRING_ID,
                                          new String[]{libraryStringId})) {
            return loadLibrary(cursor);
        }
    }

    @Override
    @NonNull
    public ArrayList<CalibreLibrary> getLibraries() {

        final ArrayList<CalibreLibrary> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SELECT_LIBRARIES, null)) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final CalibreLibrary library = new CalibreLibrary(rowData.getLong(DBKeys.KEY_PK_ID),
                                                                  rowData);
                library.setVirtualLibraries(getVirtualLibraries(library.getId()));
                list.add(library);
            }
        }
        return list;
    }

    /**
     * Get the list of Calibre <strong>virtual</strong>libraries for the given library id.
     *
     * @param libraryId to lookup
     *
     * @return list of virtual libs
     */
    @NonNull
    private ArrayList<CalibreVirtualLibrary> getVirtualLibraries(final long libraryId) {

        final ArrayList<CalibreVirtualLibrary> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SELECT_VLIBS_BY_LIBRARY_ID,
                                          new String[]{String.valueOf(libraryId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new CalibreVirtualLibrary(rowData.getLong(DBKeys.KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @Nullable
    public CalibreVirtualLibrary getVirtualLibrary(final long libraryId,
                                                   @NonNull final String name) {

        try (Cursor cursor = mDb.rawQuery(SELECT_VLIB_BY_LIBRARY_ID_AND_NAME,
                                          new String[]{String.valueOf(libraryId), name})) {

            final DataHolder rowData = new CursorRow(cursor);
            if (cursor.moveToFirst()) {
                return new CalibreVirtualLibrary(rowData.getLong(DBKeys.KEY_PK_ID), rowData);
            }
        }
        return null;
    }

    @Override
    public boolean update(@NonNull final CalibreVirtualLibrary library) {

        final ContentValues cv = new ContentValues();
        cv.put(DBKeys.KEY_FK_CALIBRE_LIBRARY, library.getLibraryId());
        cv.put(DBKeys.KEY_CALIBRE_LIBRARY_NAME, library.getName());
        cv.put(DBKeys.KEY_CALIBRE_VIRT_LIB_EXPR, library.getExpr());
        cv.put(DBKeys.KEY_FK_BOOKSHELF, library.getMappedBookshelfId());

        return 0 < mDb.update(TBL_CALIBRE_VIRTUAL_LIBRARIES.getName(), cv,
                              DBKeys.KEY_PK_ID + "=?",
                              new String[]{String.valueOf(library.getId())});
    }

    @Override
    public long insert(@NonNull final CalibreLibrary library) {

        try (SynchronizedStatement stmt = mDb.compileStatement(INSERT_LIBRARY)) {
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
    public long fixId(@NonNull final CalibreLibrary library) {
        final long id = getLibrary(library);
        library.setId(id);
        return id;
    }

    private void insertVirtualLibraries(@NonNull final CalibreLibrary library) {
        final ArrayList<CalibreVirtualLibrary> vlibs = library.getVirtualLibraries();
        if (!vlibs.isEmpty()) {
            try (SynchronizedStatement stmt = mDb.compileStatement(INSERT_VIRTUAL_LIBRARY)) {
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
        try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_VLIBS_BY_LIBRARY_ID)) {
            stmt.bindLong(1, libraryId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    public boolean update(@NonNull final CalibreLibrary library) {

        final ContentValues cv = new ContentValues();
        cv.put(DBKeys.KEY_CALIBRE_LIBRARY_UUID, library.getUuid());
        cv.put(DBKeys.KEY_CALIBRE_LIBRARY_STRING_ID, library.getLibraryStringId());
        cv.put(DBKeys.KEY_CALIBRE_LIBRARY_NAME, library.getName());
        cv.put(DBKeys.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE, library.getLastSyncDateAsString());
        cv.put(DBKeys.KEY_FK_BOOKSHELF, library.getMappedBookshelfId());

        final int rowsAffected = mDb.update(TBL_CALIBRE_LIBRARIES.getName(), cv,
                                            DBKeys.KEY_PK_ID + "=?",
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
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final CalibreLibrary library) {

        final int rowsAffected;

        try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_LIBRARY_BY_ID)) {
            stmt.bindLong(1, library.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            library.setId(0);
        }
        return rowsAffected == 1;
    }

    @Override
    @IntRange(from = 0)
    public long getBookIdFromCalibreUuid(@NonNull final String uuid) {
        try (SynchronizedStatement stmt = mDb.compileStatement(BY_CALIBRE_UUID)) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }
}
