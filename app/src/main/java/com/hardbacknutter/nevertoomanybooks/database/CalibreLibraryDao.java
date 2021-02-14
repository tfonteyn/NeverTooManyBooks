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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreVirtualLibrary;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_CALIBRE_LIBRARY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_CALIBRE_LIBRARY_STRING_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_CALIBRE_LIBRARY_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_CALIBRE_VIRT_LIB_EXPR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_CALIBRE_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_LIBRARIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_VIRTUAL_LIBRARIES;

public class CalibreLibraryDao
        extends BaseDao {

    private static final String STMT_GET_CALIBRE_LIBRARY_ID = "gCalLibId";

    public CalibreLibraryDao(@NonNull final Context context,
                             @NonNull final String logTag) {
        super(context, logTag);
    }

    /**
     * Creates a new {@link CalibreLibrary} in the database.
     *
     * @param library object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final CalibreLibrary /* in/out */ library) {

        try (SynchronizedStatement stmt = mSyncedDb
                .compileStatement(DAOSql.SqlInsert.CALIBRE_LIBRARY)) {
            stmt.bindString(1, library.getUuid());
            stmt.bindString(2, library.getLibraryStringId());
            stmt.bindString(3, library.getName());
            stmt.bindString(4, library.getLastSyncDateAsString());
            stmt.bindLong(5, library.getMappedBookshelfId());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                library.setId(iId);
                insertCalibreVirtualLibraries(library);
            }
            return iId;
        }
    }

    public long fixId(@NonNull final CalibreLibrary /* in/out */ library) {
        final long id = getCalibreLibrary(library);
        library.setId(id);
        return id;
    }

    private void insertCalibreVirtualLibraries(@NonNull final CalibreLibrary library) {
        final ArrayList<CalibreVirtualLibrary> vlibs = library.getVirtualLibraries();
        if (!vlibs.isEmpty()) {
            try (SynchronizedStatement stmt = mSyncedDb
                    .compileStatement(DAOSql.SqlInsert.CALIBRE_VIRTUAL_LIBRARY)) {
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

    private void deleteCalibreVirtualLibraries(final long libraryId) {
        try (SynchronizedStatement stmt = mSyncedDb
                .compileStatement(DAOSql.SqlDelete.CALIBRE_VIRTUAL_LIBRARIES_BY_LIBRARY_ID)) {
            stmt.bindLong(1, libraryId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Update a {@link CalibreLibrary}.
     *
     * @param library to update
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final CalibreLibrary library) {

        final ContentValues cv = new ContentValues();
        cv.put(KEY_CALIBRE_LIBRARY_UUID, library.getUuid());
        cv.put(KEY_CALIBRE_LIBRARY_STRING_ID, library.getLibraryStringId());
        cv.put(KEY_CALIBRE_LIBRARY_NAME, library.getName());
        cv.put(KEY_CALIBRE_LIBRARY_LAST_SYNC_DATE, library.getLastSyncDateAsString());
        cv.put(KEY_FK_BOOKSHELF, library.getMappedBookshelfId());

        final int rowsAffected = mSyncedDb.update(TBL_CALIBRE_LIBRARIES.getName(), cv,
                                                  KEY_PK_ID + "=?",
                                                  new String[]{String.valueOf(library.getId())});
        if (0 < rowsAffected) {
            // just delete and recreate...
            deleteCalibreVirtualLibraries(library.getId());
            insertCalibreVirtualLibraries(library);
            return true;
        }
        return false;
    }

    /**
     * Delete the passed {@link CalibreLibrary}.
     *
     * @param library to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final CalibreLibrary library) {

        final int rowsAffected;

        try (SynchronizedStatement stmt = mSyncedDb
                .compileStatement(DAOSql.SqlDelete.CALIBRE_LIBRARY_BY_ID)) {
            stmt.bindLong(1, library.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            library.setId(0);
        }
        return rowsAffected == 1;
    }

    @Nullable
    private CalibreLibrary loadCalibreLibrary(final Cursor cursor) {
        if (cursor.moveToNext()) {
            final DataHolder rowData = new CursorRow(cursor);
            final CalibreLibrary library = new CalibreLibrary(rowData.getLong(KEY_PK_ID),
                                                              rowData);
            library.setVirtualLibraries(getCalibreVirtualLibraries(library.getId()));
            return library;
        }
        return null;
    }

    private long getCalibreLibrary(@NonNull final CalibreLibrary library) {

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_CALIBRE_LIBRARY_ID, () -> DAOSql.SqlGetId.CALIBRE_LIBRARY_ID_BY_NAME);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, library.getName());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get the <strong>physical</strong> {@link CalibreLibrary} for the given row id.
     *
     * @param id to lookup
     *
     * @return physical library
     */
    @Nullable
    public CalibreLibrary getCalibreLibrary(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(
                DAOSql.SqlGet.CALIBRE_LIBRARY_BY_ID, new String[]{String.valueOf(id)})) {
            return loadCalibreLibrary(cursor);
        }
    }

    /**
     * Get the <strong>physical</strong> {@link CalibreLibrary} for the given uuid.
     *
     * @param uuid to lookup
     *
     * @return physical library
     */
    @Nullable
    public CalibreLibrary getCalibreLibraryByUuid(@NonNull final String uuid) {
        try (Cursor cursor = mSyncedDb.rawQuery(
                DAOSql.SqlGet.CALIBRE_LIBRARY_BY_LIBRARY_UUID, new String[]{uuid})) {
            return loadCalibreLibrary(cursor);
        }
    }

    /**
     * Get the <strong>physical</strong> {@link CalibreLibrary} for the given libraryStringId.
     *
     * @param libraryStringId to lookup
     *
     * @return physical library
     */
    @Nullable
    public CalibreLibrary getCalibreLibraryByStringId(@NonNull final String libraryStringId) {
        try (Cursor cursor = mSyncedDb.rawQuery(
                DAOSql.SqlGet.CALIBRE_LIBRARY_BY_LIBRARY_ID, new String[]{libraryStringId})) {
            return loadCalibreLibrary(cursor);
        }
    }

    public ArrayList<CalibreLibrary> getCalibreLibraries() {

        final ArrayList<CalibreLibrary> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(
                DAOSql.SqlSelectFullTable.CALIBRE_LIBRARIES, null)) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final CalibreLibrary library = new CalibreLibrary(rowData.getLong(KEY_PK_ID),
                                                                  rowData);
                library.setVirtualLibraries(getCalibreVirtualLibraries(library.getId()));
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
    private ArrayList<CalibreVirtualLibrary> getCalibreVirtualLibraries(final long libraryId) {

        final ArrayList<CalibreVirtualLibrary> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(
                DAOSql.SqlSelect.CALIBRE_VIRTUAL_LIBRARIES_BY_LIBRARY_ID,
                new String[]{String.valueOf(libraryId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new CalibreVirtualLibrary(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Get the <strong>virtual</strong> {@link CalibreLibrary} for the given library + name.
     * The mapped {@link Bookshelf} will have been resolved.
     *
     * @param libraryId to lookup
     * @param name      of the virtual library to lookup
     *
     * @return virtual library
     */
    @Nullable
    public CalibreVirtualLibrary getCalibreVirtualLibrary(final long libraryId,
                                                          @NonNull final String name) {

        try (Cursor cursor = mSyncedDb.rawQuery(
                DAOSql.SqlGet.CALIBRE_VIRTUAL_LIBRARY_BY_LIBRARY_ID_AND_NAME,
                new String[]{String.valueOf(libraryId), name})) {

            final DataHolder rowData = new CursorRow(cursor);
            if (cursor.moveToFirst()) {
                return new CalibreVirtualLibrary(rowData.getLong(KEY_PK_ID), rowData);
            }
        }
        return null;
    }

    /**
     * Update a {@link CalibreVirtualLibrary}.
     *
     * @param library to update
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final CalibreVirtualLibrary library) {

        final ContentValues cv = new ContentValues();
        cv.put(KEY_FK_CALIBRE_LIBRARY, library.getLibraryId());
        cv.put(KEY_CALIBRE_LIBRARY_NAME, library.getName());
        cv.put(KEY_CALIBRE_VIRT_LIB_EXPR, library.getExpr());
        cv.put(KEY_FK_BOOKSHELF, library.getMappedBookshelfId());

        return 0 < mSyncedDb.update(TBL_CALIBRE_VIRTUAL_LIBRARIES.getName(), cv,
                                    KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(library.getId())});
    }

}
