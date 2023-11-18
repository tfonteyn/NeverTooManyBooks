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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderViewModel;

public class CalibreLibraryMappingViewModel
        extends SyncReaderViewModel {

    private final List<CalibreLibrary> libraries = new ArrayList<>();
    private CalibreLibraryDao calibreLibraryDao;

    private CalibreLibrary currentLibrary;
    private boolean extInstalled;

    @Override
    public void init(@NonNull final Bundle args) {
        super.init(args);
        if (calibreLibraryDao == null) {
            calibreLibraryDao = ServiceLocator.getInstance().getCalibreLibraryDao();
        }
    }

    @NonNull
    List<Bookshelf> getBookshelfList() {
        return ServiceLocator.getInstance().getBookshelfDao().getAll();
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

        final Bookshelf mappedBookshelf = currentLibrary.createAsBookshelf(context);
        calibreLibraryDao.update(currentLibrary);
        return mappedBookshelf;
    }

    @NonNull
    Bookshelf createVirtualLibraryAsBookshelf(@NonNull final Context context,
                                              final int position)
            throws DaoWriteException {

        final CalibreVirtualLibrary vlib = currentLibrary.getVirtualLibraries().get(position);
        final Bookshelf mappedBookshelf = vlib.createAsBookshelf(context);
        calibreLibraryDao.update(vlib);
        return mappedBookshelf;
    }
}
