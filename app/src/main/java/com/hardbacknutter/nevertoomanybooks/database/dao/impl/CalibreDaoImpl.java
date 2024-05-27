/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.util.logger.LoggerFactory;

public class CalibreDaoImpl
        extends BaseDaoImpl
        implements CalibreDao {

    private static final String TAG = "CalibreDaoImpl";

    private static final String ERROR_INSERT_FROM = "Insert from\n";
    private static final String ERROR_UPDATE_FROM = "Update from\n";

    @NonNull
    private final Supplier<CalibreLibraryDao> calibreLibraryDaoSupplier;

    /**
     * Constructor.
     *
     * @param db                        Underlying database
     * @param calibreLibraryDaoSupplier deferred supplier for the {@link CalibreLibraryDao}
     */
    public CalibreDaoImpl(@NonNull final SynchronizedDb db,
                          @NonNull final Supplier<CalibreLibraryDao> calibreLibraryDaoSupplier) {
        super(db, TAG);
        this.calibreLibraryDaoSupplier = calibreLibraryDaoSupplier;
    }

    @Override
    public void insertOrUpdate(@NonNull final Context context,
                               @NonNull final Book book)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // Just delete all current data and insert from scratch.
        delete(book);
        insert(context, book);
    }

    @Override
    public boolean insert(@NonNull final Context context,
                          @NonNull final Book book)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final CalibreLibraryDao libraryDao = calibreLibraryDaoSupplier.get();
        final CalibreLibrary library;

        if (book.contains(Book.BKEY_CALIBRE_LIBRARY)) {
            //reminder: do not use book.getCalibreLibrary();
            // There is no point in trying to find the library when we're actively
            // only now going to insert it.
            library = book.getParcelable(Book.BKEY_CALIBRE_LIBRARY);

            //noinspection DataFlowIssue
            libraryDao.fixId(context, library);
            if (library.getId() == 0) {
                if (libraryDao.insert(library) == -1) {
                    throw new DaoInsertException("CalibreLibrary insert failed");
                }
            }
        } else if (book.contains(DBKey.FK_CALIBRE_LIBRARY)) {
            library = libraryDao.findById(book.getLong(DBKey.FK_CALIBRE_LIBRARY))
                                .orElse(null);
            if (library == null) {
                // The book did not have a full library object;
                // It did have a library id, but that library does not exist.
                // This can happen if the import data contained old (pre) v2.5.1
                // encoded library data.
                // Log and bail out but do NOT throw an error!
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                    LoggerFactory.getLogger()
                                 .d(TAG, "CalibreLibrary invalid(1) for book=" + book.getId());
                }
                return false;
            }
        } else {
            // This can happen if the import data contained old (pre) v2.5.1
            // encoded library data.
            // Log and bail out but do NOT throw an error!
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                LoggerFactory.getLogger()
                             .d(TAG, "CalibreLibrary invalid(2) for book=" + book.getId());
            }
            return false;
        }

        try (SynchronizedStatement stmt = db.compileStatement(Sql.INSERT)) {
            stmt.bindLong(1, book.getId());
            stmt.bindLong(2, book.getInt(DBKey.CALIBRE_BOOK_ID));
            stmt.bindString(3, book.getString(DBKey.CALIBRE_BOOK_UUID));
            stmt.bindString(4, book.getString(DBKey.CALIBRE_BOOK_MAIN_FORMAT));
            stmt.bindLong(5, library.getId());
            if (stmt.executeInsert() == -1) {
                throw new DaoInsertException(ERROR_INSERT_FROM + book);
            }
        }

        return true;
    }

    @Override
    public boolean delete(@NonNull final Book book) {
        final int rowsAffected;
        try (SynchronizedStatement stmt = db.compileStatement(Sql.DELETE_BY_LOCAL_BOOK_ID)) {
            stmt.bindLong(1, book.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }
        return rowsAffected > 0;
    }

    private static final class Sql {

        static final String INSERT =
                INSERT_INTO_ + DBDefinitions.TBL_CALIBRE_BOOKS.getName()
                + '(' + DBKey.FK_BOOK
                + ',' + DBKey.CALIBRE_BOOK_ID
                + ',' + DBKey.CALIBRE_BOOK_UUID
                + ',' + DBKey.CALIBRE_BOOK_MAIN_FORMAT
                + ',' + DBKey.FK_CALIBRE_LIBRARY
                + ") VALUES (?,?,?,?,?)";

        static final String DELETE_BY_LOCAL_BOOK_ID =
                DELETE_FROM_ + DBDefinitions.TBL_CALIBRE_BOOKS.getName()
                + _WHERE_ + DBKey.FK_BOOK + "=?";
    }
}
