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

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class CalibreVirtualLibrary
        extends LibraryBase {

    public static final Creator<CalibreVirtualLibrary> CREATOR =
            new Creator<CalibreVirtualLibrary>() {
                @Override
                public CalibreVirtualLibrary createFromParcel(@NonNull final Parcel in) {
                    return new CalibreVirtualLibrary(in);
                }

                @Override
                public CalibreVirtualLibrary[] newArray(final int size) {
                    return new CalibreVirtualLibrary[size];
                }
            };

    /** The physical Calibre library row id. */
    private long mLibraryId;

    /** The Calibre search expression. */
    @NonNull
    private String mExpr;


    /**
     * Constructor without ID.
     */
    public CalibreVirtualLibrary(final long libraryId,
                                 @NonNull final String name,
                                 @NonNull final String expr,
                                 final long mappedBookshelfId) {
        super(name, mappedBookshelfId);

        mLibraryId = libraryId;
        mExpr = expr;
    }

    /**
     * Constructor without ID.
     */
    public CalibreVirtualLibrary(final long libraryId,
                                 @NonNull final String name,
                                 @NonNull final String expr,
                                 @NonNull final Bookshelf mappedBookshelf) {
        super(name, mappedBookshelf);

        mLibraryId = libraryId;
        mExpr = expr;
    }

    /**
     * Full constructor.
     *
     * @param id      row id
     * @param rowData with data
     */
    public CalibreVirtualLibrary(final long id,
                                 @NonNull final DataHolder rowData) {
        super(id, rowData);

        mLibraryId = rowData.getLong(DBKey.FK_CALIBRE_LIBRARY);
        mExpr = rowData.getString(DBKey.KEY_CALIBRE_VIRT_LIB_EXPR);
    }

    private CalibreVirtualLibrary(@NonNull final Parcel in) {
        super(in);

        mLibraryId = in.readInt();
        //noinspection ConstantConditions
        mExpr = in.readString();
    }

    public long getLibraryId() {
        return mLibraryId;
    }

    public void setLibraryId(final long libraryId) {
        mLibraryId = libraryId;
    }

    @NonNull
    public String getExpr() {
        return mExpr;
    }

    void setExpr(@NonNull final String expr) {
        mExpr = expr;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);

        dest.writeLong(mLibraryId);
        dest.writeString(mExpr);
    }

    @Override
    @NonNull
    public String toString() {
        return "CalibreVirtualLibrary{"
               + super.toString()
               + ", mLibraryId=" + mLibraryId
               + ", mExpr=" + mExpr
               + '}';
    }
}
