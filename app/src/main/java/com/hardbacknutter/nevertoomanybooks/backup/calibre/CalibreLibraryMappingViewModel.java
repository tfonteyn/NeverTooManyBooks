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
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.backup.ImportViewModel;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class CalibreLibraryMappingViewModel
        extends ImportViewModel {

    private static final String TAG = "CalibreLibraryMappingVM";

    private final ArrayList<CalibreLibrary> mLibraries = new ArrayList<>();

    private final List<Bookshelf> mBookshelfList = new ArrayList<>();
    private final List<String> mBookshelfNames = new ArrayList<>();

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

            reloadBookshelfList();
        }
    }

    @NonNull
    ArrayList<CalibreLibrary> getLibraries() {
        return mLibraries;
    }

    @NonNull
    CalibreLibrary getLibrary(final int position) {
        return mLibraries.get(position);
    }

    void setLibraries(@NonNull final CalibreLibrary physicalLibrary,
                      @Nullable final List<CalibreLibrary> virtualLibraries) {
        mLibraries.clear();
        mLibraries.add(physicalLibrary);
        if (virtualLibraries != null) {
            mLibraries.addAll(virtualLibraries);
        }
    }

    private void reloadBookshelfList() {
        mBookshelfList.clear();
        mBookshelfList.addAll(mDb.getBookshelves());

        mBookshelfNames.clear();
        mBookshelfNames.addAll(mBookshelfList.stream()
                                             .map(Bookshelf::getName)
                                             .collect(Collectors.toList()));
    }

    @NonNull
    List<Bookshelf> getBookshelfList() {
        return mBookshelfList;
    }

    boolean isLibraryNameAnExistingBookshelfName(final int libraryPosition) {
        return mBookshelfNames.contains(mLibraries.get(libraryPosition).getName());
    }

    void setLibraryBookshelf(final int libraryPosition,
                             final int bookshelfPosition) {
        final CalibreLibrary library = mLibraries.get(libraryPosition);
        final Bookshelf mappedBookshelf = mBookshelfList.get(bookshelfPosition);
        if (mappedBookshelf.getId() != library.getMappedBookshelf().getId()) {
            library.setMappedBookshelf(mappedBookshelf);
            mDb.update(library);
        }
    }

    Bookshelf createLibraryAsBookshelf(@NonNull final Context context,
                                       final int libraryPosition)
            throws DAO.DaoWriteException {
        final CalibreLibrary library = mLibraries.get(libraryPosition);
        final Bookshelf mappedBookshelf = library.createAsBookshelf(context, mDb);
        reloadBookshelfList();
        mDb.update(library);
        return mappedBookshelf;
    }
}
