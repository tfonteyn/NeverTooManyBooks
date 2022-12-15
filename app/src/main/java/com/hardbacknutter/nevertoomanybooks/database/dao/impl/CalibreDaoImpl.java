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

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_BOOKS;

public class CalibreDaoImpl
        extends BaseDaoImpl
        implements CalibreDao {

    private static final String TAG = "CalibreDaoImpl";

    private static final String INSERT =
            INSERT_INTO_ + TBL_CALIBRE_BOOKS.getName()
            + '(' + DBKey.FK_BOOK
            + ',' + DBKey.CALIBRE_BOOK_ID
            + ',' + DBKey.CALIBRE_BOOK_UUID
            + ',' + DBKey.CALIBRE_BOOK_MAIN_FORMAT
            + ',' + DBKey.FK_CALIBRE_LIBRARY
            + ") VALUES (?,?,?,?,?)";

    /**
     * Constructor.
     */
    public CalibreDaoImpl() {
        super(TAG);
    }

    @Override
    public boolean updateOrInsert(@NonNull final Book book)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // Just delete all current data and insert from scratch.
        delete(book);
        return insert(book);
    }

    @Override
    public boolean insert(@NonNull final Book book)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final CalibreLibraryDao libraryDao = ServiceLocator.getInstance().getCalibreLibraryDao();
        final CalibreLibrary library;
        //reminder: do not use book.getCalibreLibrary(); that would be pointless here
        if (book.contains(Book.BKEY_CALIBRE_LIBRARY)) {
            library = book.getParcelable(Book.BKEY_CALIBRE_LIBRARY);

            //noinspection ConstantConditions
            libraryDao.fixId(library);
            if (library.getId() == 0) {
                if (libraryDao.insert(library) == -1) {
                    throw new DaoWriteException("CalibreLibrary insert failed");
                }
            }
        } else if (book.contains(DBKey.FK_CALIBRE_LIBRARY)) {
            library = libraryDao.getLibraryById(book.getLong(DBKey.FK_CALIBRE_LIBRARY));
            if (library == null) {
                // The book did not have a full library object;
                // It did have a library id, but that library does not exist.
                // This can happen if the import data contained old (pre) v2.5.1
                // encoded library data.
                // Log and bail out but do NOT throw an error!
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                    Logger.w(TAG, "CalibreLibrary invalid(1) for book=" + book.getId());
                }
                return false;
            }
        } else {
            // This can happen if the import data contained old (pre) v2.5.1
            // encoded library data.
            // Log and bail out but do NOT throw an error!
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                Logger.w(TAG, "CalibreLibrary invalid(2) for book=" + book.getId());
            }
            return false;
        }

        try (SynchronizedStatement stmt = db.compileStatement(INSERT)) {
            stmt.bindLong(1, book.getId());
            stmt.bindLong(2, book.getInt(DBKey.CALIBRE_BOOK_ID));
            stmt.bindString(3, book.getString(DBKey.CALIBRE_BOOK_UUID));
            stmt.bindString(4, book.getString(DBKey.CALIBRE_BOOK_MAIN_FORMAT));
            stmt.bindLong(5, library.getId());
            final long rowId = stmt.executeInsert();
            if (rowId == -1) {
                throw new DaoWriteException("Calibre data insert failed");
            }
        }

        return true;
    }

    @Override
    public boolean delete(@NonNull final Book book) {
        return 0 < db.delete(TBL_CALIBRE_BOOKS.getName(), DBKey.FK_BOOK + "=?",
                             new String[]{String.valueOf(book.getId())});
    }
}
