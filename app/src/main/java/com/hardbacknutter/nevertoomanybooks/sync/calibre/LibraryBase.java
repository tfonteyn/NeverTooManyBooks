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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Details;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

abstract class LibraryBase
        implements Parcelable, Entity {

    /** Row ID. */
    private long id;
    /** Name of the library; displayed to the user. */
    @NonNull
    private String name;

    @IntRange(from = 0)
    private long mappedBookshelfId;

    /**
     * Constructor without ID.
     *
     * @param name              the Calibre name for the library
     * @param mappedBookshelfId the {@link Bookshelf} id this library is mapped to
     */
    LibraryBase(@NonNull final String name,
                @IntRange(from = 0) final long mappedBookshelfId) {
        this.name = name;
        this.mappedBookshelfId = mappedBookshelfId;
    }

    /**
     * Full constructor.
     *
     * @param id      row id
     * @param rowData with data
     */
    LibraryBase(final long id,
                @NonNull final DataHolder rowData) {
        this.id = id;
        name = rowData.getString(DBKey.CALIBRE_LIBRARY_NAME);
        mappedBookshelfId = rowData.getLong(DBKey.FK_BOOKSHELF);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    LibraryBase(@NonNull final Parcel in) {
        id = in.readLong();
        //noinspection DataFlowIssue
        name = in.readString();
        mappedBookshelfId = in.readLong();
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(@IntRange(from = 0) final long id) {
        this.id = id;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context,
                           @NonNull final Details details,
                           @NonNull final Style style) {
        return name;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull final String name) {
        this.name = name;
    }

    public void setMappedBookshelf(@IntRange(from = 0) final long id) {
        mappedBookshelfId = id;
    }

    @IntRange(from = 0)
    public long getMappedBookshelfId() {
        return mappedBookshelfId;
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

        final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();

        final Bookshelf current = bookshelfDao.getBookshelf(context,
                                                            Bookshelf.USER_DEFAULT,
                                                            Bookshelf.HARD_DEFAULT)
                                              .orElseThrow();

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        final Bookshelf bookshelf = new Bookshelf(name, current.getStyle());
        bookshelfDao.insert(context, bookshelf, locale);

        mappedBookshelfId = bookshelf.getId();
        return bookshelf;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        dest.writeLong(mappedBookshelfId);
    }

    @Override
    @NonNull
    public String toString() {
        return "id=" + id
               + ", name=`" + name + '`'
               + ", mappedBookshelfId=" + mappedBookshelfId;
    }
}
