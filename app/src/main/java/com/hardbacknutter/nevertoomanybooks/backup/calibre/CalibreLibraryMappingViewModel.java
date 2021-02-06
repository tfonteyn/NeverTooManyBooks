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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ImportViewModel;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class CalibreLibraryMappingViewModel
        extends ImportViewModel {

    /** Log tag. */
    private static final String TAG = "CalibreLibraryMappingVM";

    private final ArrayList<CalibreLibrary> mLibraries = new ArrayList<>();
    private CalibreLibrary mCurrentLibrary;

    private DAO mDb;

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
        super.onCleared();
    }

    @Override
    public void init(@Nullable final Bundle args) {
        super.init(args);
        if (mDb == null) {
            mDb = new DAO(TAG);
        }
    }

    @NonNull
    List<Bookshelf> getBookshelfList() {
        return mDb.getBookshelves();
    }

    @NonNull
    ArrayList<CalibreLibrary> getLibraries() {
        return mLibraries;
    }

    public void setLibraries(@NonNull final ArchiveMetaData result) {
        // at this moment, all server libs have been synced with our database
        // and are mapped to a valid bookshelf

        mLibraries.clear();
        mLibraries.addAll(Objects.requireNonNull(
                result.getBundle().getParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST),
                "mLibraries"));
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
            mDb.update(mCurrentLibrary);
        }
    }

    void mapBookshelfToVirtualLibrary(@NonNull final Bookshelf bookshelf,
                                      final int position) {

        final CalibreVirtualLibrary vlib = mCurrentLibrary.getVirtualLibraries().get(position);
        if (bookshelf.getId() != vlib.getMappedBookshelfId()) {
            vlib.setMappedBookshelf(bookshelf.getId());
            mDb.update(vlib);
        }
    }

    @NonNull
    Bookshelf createLibraryAsBookshelf(@NonNull final Context context)
            throws DAO.DaoWriteException {

        final Bookshelf mappedBookshelf = mCurrentLibrary.createAsBookshelf(context, mDb);
        mDb.update(mCurrentLibrary);
        return mappedBookshelf;
    }

    @NonNull
    Bookshelf createVirtualLibraryAsBookshelf(@NonNull final Context context,
                                              final int position)
            throws DAO.DaoWriteException {

        final CalibreVirtualLibrary vlib = mCurrentLibrary.getVirtualLibraries().get(position);
        final Bookshelf mappedBookshelf = vlib.createAsBookshelf(context, mDb);
        mDb.update(vlib);
        return mappedBookshelf;
    }
}
