/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderViewModel;

@SuppressWarnings("WeakerAccess")
public class CalibreLibraryMappingViewModel
        extends SyncReaderViewModel {

    private final List<CalibreLibrary> libraries = new ArrayList<>();

    private BookshelfDao bookshelfDao;
    private CalibreLibraryDao calibreLibraryDao;

    private CalibreLibrary currentLibrary;
    private boolean extInstalled;

    /**
     * Pseudo constructor.
     *
     * @param context Current Context
     * @param args    Bundle with arguments
     */
    public void init(@NonNull final Context context,
                     @NonNull final Bundle args) {
        super.init(context, args);
        if (bookshelfDao == null) {
            final ServiceLocator serviceLocator = ServiceLocator.getInstance();
            bookshelfDao = serviceLocator.getBookshelfDao();
            calibreLibraryDao = serviceLocator.getCalibreLibraryDao();
        }
    }

    @NonNull
    List<Bookshelf> getBookshelfList() {
        return bookshelfDao.getAll();
    }

    @NonNull
    List<CalibreLibrary> getLibraries() {
        return libraries;
    }

    void extractLibraryData(@Nullable final SyncReaderMetaData metaData) {
        Objects.requireNonNull(metaData);

        // at this moment, all server libs have been synced with our database
        // and are mapped to a valid bookshelf

        libraries.clear();
        final Bundle data = metaData.getData();
        libraries.addAll(Objects.requireNonNull(
                data.getParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST),
                CalibreContentServer.BKEY_LIBRARY_LIST));

        extInstalled = data.getBoolean(CalibreContentServer.BKEY_EXT_INSTALLED);
    }

    boolean isExtInstalled() {
        return extInstalled;
    }

    @NonNull
    CalibreLibrary getCurrentLibrary() {
        return currentLibrary;
    }

    void setCurrentLibrary(final int position) {
        currentLibrary = libraries.get(position);
    }

    CalibreVirtualLibrary getVirtualLibrary(final int position) {
        return currentLibrary.getVirtualLibraries().get(position);
    }


    void mapBookshelfToLibrary(@NonNull final Bookshelf bookshelf)
            throws DaoWriteException {
        if (bookshelf.getId() != currentLibrary.getMappedBookshelfId()) {
            currentLibrary.setMappedBookshelf(bookshelf.getId());
            calibreLibraryDao.update(currentLibrary);
        }
    }

    void mapBookshelfToVirtualLibrary(@NonNull final Bookshelf bookshelf,
                                      final int position)
            throws DaoWriteException {

        final CalibreVirtualLibrary vlib = currentLibrary.getVirtualLibraries().get(position);
        if (bookshelf.getId() != vlib.getMappedBookshelfId()) {
            vlib.setMappedBookshelf(bookshelf.getId());
            calibreLibraryDao.update(vlib);
        }
    }

    @NonNull
    Bookshelf createLibraryAsBookshelf(@NonNull final Context context)
            throws DaoWriteException {

        final Bookshelf mappedBookshelf = createAsBookshelf(context, currentLibrary);
        calibreLibraryDao.update(currentLibrary);
        return mappedBookshelf;
    }

    @NonNull
    Bookshelf createVirtualLibraryAsBookshelf(@NonNull final Context context,
                                              final int position)
            throws DaoWriteException {

        final CalibreVirtualLibrary vlib = currentLibrary.getVirtualLibraries().get(position);

        final Bookshelf mappedBookshelf = createAsBookshelf(context, vlib);
        calibreLibraryDao.update(vlib);
        return mappedBookshelf;
    }

    /**
     * Use the library name to create a new bookshelf.
     * The style is taken from the current Bookshelf.
     *
     * @param context Current context
     * @param library to use
     *
     * @return the new and mapped bookshelf
     *
     * @throws DaoWriteException on failure
     */
    @NonNull
    private Bookshelf createAsBookshelf(@NonNull final Context context,
                                        @NonNull final LibraryBase library)
            throws DaoWriteException {

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        final Bookshelf current = bookshelfDao.getCurrent().orElseGet(bookshelfDao::getDefault);
        final Bookshelf bookshelf = new Bookshelf(library.getName(), current.getStyle());
        bookshelfDao.insert(context, bookshelf, locale);
        library.setMappedBookshelf(bookshelf.getId());

        return bookshelf;
    }
}
