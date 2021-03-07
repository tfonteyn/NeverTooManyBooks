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

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

abstract class LibraryBase
        implements Entity {

    /** Row ID. */
    private long mId;
    /** Name of the library; displayed to the user. */
    @NonNull
    private String mName;

    private long mMappedBookshelfId;

    LibraryBase(@NonNull final String name,
                final long mappedBookshelfId) {
        mName = name;
        mMappedBookshelfId = mappedBookshelfId;
    }

    LibraryBase(final long id,
                @NonNull final DataHolder rowData) {
        mId = id;
        mName = rowData.getString(DBKeys.KEY_CALIBRE_LIBRARY_NAME);
        mMappedBookshelfId = rowData.getLong(DBKeys.KEY_FK_BOOKSHELF);
    }

    LibraryBase(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mName = in.readString();
        mMappedBookshelfId = in.readLong();
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

    @NonNull
    public String getName() {
        return mName;
    }

    public void setName(@NonNull final String name) {
        mName = name;
    }

    void setMappedBookshelf(final long id) {
        mMappedBookshelfId = id;
    }

    public long getMappedBookshelfId() {
        return mMappedBookshelfId;
    }

    /**
     * Use the library name to create a new bookshelf.
     * The style is taken from the current Bookshelf.
     *
     * @param context Current context
     *
     * @return the new and mapped bookshelf
     *
     * @throws DaoWriteException on failure
     */
    @NonNull
    Bookshelf createAsBookshelf(@NonNull final Context context)
            throws DaoWriteException {

        final Bookshelf current = Bookshelf
                .getBookshelf(context, Bookshelf.PREFERRED, Bookshelf.DEFAULT);

        final Bookshelf bookshelf = new Bookshelf(mName, current.getStyle(context));
        if (ServiceLocator.getInstance().getBookshelfDao().insert(context, bookshelf) == -1) {
            throw new DaoWriteException("insert Bookshelf");
        }

        mMappedBookshelfId = bookshelf.getId();
        return bookshelf;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mName);
        dest.writeLong(mMappedBookshelfId);
    }

    @Override
    @NonNull
    public String toString() {
        return "LibraryBase{"
               + "mId=" + mId
               + ", mName='" + mName + '\''
               + ", mMappedBookshelfId=" + mMappedBookshelfId
               + '}';
    }
}
