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
import android.os.Parcel;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

public class CalibreLibrary
        implements Entity {

    public static final Creator<CalibreLibrary> CREATOR =
            new Creator<CalibreLibrary>() {
                @Override
                public CalibreLibrary createFromParcel(@NonNull final Parcel in) {
                    return new CalibreLibrary(in);
                }

                @Override
                public CalibreLibrary[] newArray(final int size) {
                    return new CalibreLibrary[size];
                }
            };
    /** The physical Calibre library id. */
    @NonNull
    private final String mLibraryId;
    /** Row ID. */
    private long mId;
    /** Name library (either physical or virtual); as displayed to the user. */
    @NonNull
    private String mName;
    /**
     * If this is a virtual library, the Calibre search expression.
     * For a physical library: {@code ""}.
     */
    @NonNull
    private String mExpr;
    @NonNull
    private Bookshelf mMappedBookshelf;

    /**
     * Constructor without ID.
     */
    CalibreLibrary(@NonNull final String libraryId,
                   @NonNull final String name,
                   @NonNull final String expr,
                   @NonNull final Bookshelf mappedBookshelf) {
        mLibraryId = libraryId;
        mName = name;
        mExpr = expr;
        mMappedBookshelf = mappedBookshelf;
    }

    /**
     * Full constructor.
     *
     * @param id              the CalibreLibrary id
     * @param mappedBookshelf shelf
     * @param rowData         with data
     */
    public CalibreLibrary(final long id,
                          @NonNull final Bookshelf mappedBookshelf,
                          @NonNull final DataHolder rowData) {
        mId = id;
        mLibraryId = rowData.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_ID);
        mName = rowData.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_NAME);
        mExpr = rowData.getString(DBDefinitions.KEY_CALIBRE_VIRT_LIB_EXPR);
        mMappedBookshelf = mappedBookshelf;
    }

    private CalibreLibrary(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mLibraryId = in.readString();
        //noinspection ConstantConditions
        mName = in.readString();
        //noinspection ConstantConditions
        mExpr = in.readString();
        //noinspection ConstantConditions
        mMappedBookshelf = in.readParcelable(getClass().getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mLibraryId);
        dest.writeString(mName);
        dest.writeString(mExpr);
        dest.writeParcelable(mMappedBookshelf, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public String getLibraryId() {
        return mLibraryId;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public void setName(@NonNull final String name) {
        mName = name;
    }

    @NonNull
    public String getExpr() {
        return mExpr;
    }

    void setExpr(@NonNull final String expr) {
        mExpr = expr;
    }

    public boolean isVirtual() {
        return !mExpr.isEmpty();
    }

    public boolean isPhysical() {
        return mExpr.isEmpty();
    }

    @NonNull
    public Bookshelf getMappedBookshelf() {
        return mMappedBookshelf;
    }

    void setMappedBookshelf(@NonNull final Bookshelf mappedBookshelf) {
        mMappedBookshelf = mappedBookshelf;
    }

    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return mName;
    }

    /**
     * Use the library name to create a new bookshelf.
     * The style is taken from the current Bookshelf.
     *
     * @param context Current context
     * @param db      database access
     *
     * @return the new and mapped bookshelf
     *
     * @throws DAO.DaoWriteException on failure
     */
    @NonNull
    Bookshelf createAsBookshelf(@NonNull final Context context,
                                @NonNull final DAO db)
            throws DAO.DaoWriteException {

        final Bookshelf current = Bookshelf
                .getBookshelf(context, db, Bookshelf.PREFERRED, Bookshelf.DEFAULT);

        final Bookshelf bookshelf = new Bookshelf(mName, current.getStyle(context, db));
        if (db.insert(context, bookshelf) == -1) {
            throw new DAO.DaoWriteException("insert Bookshelf");
        }
        mMappedBookshelf = bookshelf;
        return mMappedBookshelf;
    }

    @Override
    @NonNull
    public String toString() {
        return "CalibreLibrary{"
               + "mId=" + mId
               + ", mLibraryId='" + mLibraryId + '\''
               + ", mName='" + mName + '\''
               + ", mMappedBookshelf=" + mMappedBookshelf.getName()
               + '}';
    }
}
