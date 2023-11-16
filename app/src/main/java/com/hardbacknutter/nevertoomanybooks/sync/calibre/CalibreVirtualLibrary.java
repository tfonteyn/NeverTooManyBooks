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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

public class CalibreVirtualLibrary
        extends LibraryBase {

    /** {@link Parcelable}. */
    public static final Creator<CalibreVirtualLibrary> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CalibreVirtualLibrary createFromParcel(@NonNull final Parcel in) {
            return new CalibreVirtualLibrary(in);
        }

        @Override
        @NonNull
        public CalibreVirtualLibrary[] newArray(final int size) {
            return new CalibreVirtualLibrary[size];
        }
    };

    /** The physical Calibre library row id. */
    private long libraryId;

    /** The Calibre search expression. */
    @NonNull
    private String searchExpression;


    /**
     * Constructor without ID.
     *
     * @param libraryId         row id for the physical library
     * @param name              the Calibre name for this virtual library
     * @param expr              the Calibre search expression which defines the virtual library
     * @param mappedBookshelfId the {@link Bookshelf} id this virtual library is mapped to
     */
    public CalibreVirtualLibrary(final long libraryId,
                                 @NonNull final String name,
                                 @NonNull final String expr,
                                 final long mappedBookshelfId) {
        super(name, mappedBookshelfId);

        this.libraryId = libraryId;
        searchExpression = expr;
    }

    /**
     * Constructor without ID.
     *
     * @param libraryId       row id for the physical library
     * @param name            the Calibre name for this virtual library
     * @param expr            the Calibre search expression which defines the virtual library
     * @param mappedBookshelf the {@link Bookshelf} this virtual library is mapped to
     */
    public CalibreVirtualLibrary(final long libraryId,
                                 @NonNull final String name,
                                 @NonNull final String expr,
                                 @NonNull final Bookshelf mappedBookshelf) {
        super(name, mappedBookshelf.getId());

        this.libraryId = libraryId;
        searchExpression = expr;
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

        libraryId = rowData.getLong(DBKey.FK_CALIBRE_LIBRARY);
        searchExpression = rowData.getString(DBKey.CALIBRE_VIRT_LIB_EXPR);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private CalibreVirtualLibrary(@NonNull final Parcel in) {
        super(in);

        libraryId = in.readInt();
        //noinspection DataFlowIssue
        searchExpression = in.readString();
    }

    public long getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(final long libraryId) {
        this.libraryId = libraryId;
    }

    @NonNull
    public String getExpr() {
        return searchExpression;
    }

    void setExpr(@NonNull final String expr) {
        searchExpression = expr;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);

        dest.writeLong(libraryId);
        dest.writeString(searchExpression);
    }

    @Override
    @NonNull
    public String toString() {
        return "CalibreVirtualLibrary{"
               + super.toString()
               + ", libraryId=" + libraryId
               + ", searchExpression=" + searchExpression
               + '}';
    }
}
