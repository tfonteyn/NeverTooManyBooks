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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderViewModel;

public class CalibreLibraryMappingViewModel
        extends SyncReaderViewModel {

    private final ArrayList<CalibreLibrary> mLibraries = new ArrayList<>();
    private CalibreLibrary mCurrentLibrary;
    private boolean mExtInstalled;

    @NonNull
    List<Bookshelf> getBookshelfList() {
        return ServiceLocator.getInstance().getBookshelfDao().getAll();
    }

    @NonNull
    ArrayList<CalibreLibrary> getLibraries() {
        return mLibraries;
    }


    public void extractLibraryData(@Nullable final SyncReaderMetaData metaData) {
        Objects.requireNonNull(metaData);

        // at this moment, all server libs have been synced with our database
        // and are mapped to a valid bookshelf

        mLibraries.clear();
        final Bundle data = metaData.getData();
        mLibraries.addAll(Objects.requireNonNull(
                data.getParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST),
                CalibreContentServer.BKEY_LIBRARY_LIST));

        mExtInstalled = data.getBoolean(CalibreContentServer.BKEY_EXT_INSTALLED);
    }

    boolean isExtInstalled() {
        return mExtInstalled;
    }

    @NonNull
    CalibreLibrary getCurrentLibrary() {
        return mCurrentLibrary;
    }

    void setCurrentLibrary(final int position) {
        mCurrentLibrary = mLibraries.get(position);
    }

    CalibreVirtualLibrary getVirtualLibrary(final int position) {
        return mCurrentLibrary.getVirtualLibraries().get(position);
    }


    void mapBookshelfToLibrary(@NonNull final Bookshelf bookshelf) {
        if (bookshelf.getId() != mCurrentLibrary.getMappedBookshelfId()) {
            mCurrentLibrary.setMappedBookshelf(bookshelf.getId());
            ServiceLocator.getInstance().getCalibreLibraryDao().update(mCurrentLibrary);
        }
    }

    void mapBookshelfToVirtualLibrary(@NonNull final Bookshelf bookshelf,
                                      final int position) {

        final CalibreVirtualLibrary vlib = mCurrentLibrary.getVirtualLibraries().get(position);
        if (bookshelf.getId() != vlib.getMappedBookshelfId()) {
            vlib.setMappedBookshelf(bookshelf.getId());
            ServiceLocator.getInstance().getCalibreLibraryDao().update(vlib);
        }
    }

    @NonNull
    Bookshelf createLibraryAsBookshelf(@NonNull final Context context)
            throws DaoWriteException {

        final Bookshelf mappedBookshelf = mCurrentLibrary.createAsBookshelf(context);
        ServiceLocator.getInstance().getCalibreLibraryDao().update(mCurrentLibrary);
        return mappedBookshelf;
    }

    @NonNull
    Bookshelf createVirtualLibraryAsBookshelf(@NonNull final Context context,
                                              final int position)
            throws DaoWriteException {

        final CalibreVirtualLibrary vlib = mCurrentLibrary.getVirtualLibraries().get(position);
        final Bookshelf mappedBookshelf = vlib.createAsBookshelf(context);
        ServiceLocator.getInstance().getCalibreLibraryDao().update(vlib);
        return mappedBookshelf;
    }

}
