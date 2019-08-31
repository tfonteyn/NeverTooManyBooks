/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.TrackedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;

/**
 * Cursor object that makes the underlying BooklistBuilder available to users of the Cursor.
 */
public class BooklistCursor
        extends TrackedCursor
        implements CursorRowProvider, Closeable {

    /** Underlying BooklistBuilder object. */
    @NonNull
    private final BooklistBuilder mBuilder;
    /** Cached RowView for this cursor. */
    @Nullable
    private BooklistMappedCursorRow mCursorRow;

    /**
     * Constructor.
     *
     * @param driver    Part of standard cursor constructor.
     * @param editTable Part of standard cursor constructor.
     * @param query     Part of standard cursor constructor.
     * @param sync      Synchronizer object
     * @param builder   BooklistBuilder used to make the query on which this cursor is based.
     */
    BooklistCursor(@NonNull final SQLiteCursorDriver driver,
                   @NonNull final String editTable,
                   @NonNull final SQLiteQuery query,
                   @NonNull final Synchronizer sync,
                   @NonNull final BooklistBuilder builder) {
        super(driver, editTable, query, sync);
        mBuilder = builder;
    }

    /**
     * Get a BooklistMappedCursorRow for this cursor. Constructs one if necessary.
     *
     * @return BooklistMappedCursorRow
     */
    @Override
    @NonNull
    public BooklistMappedCursorRow getCursorRow() {
        if (mCursorRow == null) {
            mCursorRow = new BooklistMappedCursorRow(this, mBuilder.getStyle());
        }
        return mCursorRow;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistCursor{"
               + "mBuilder=" + mBuilder
               + ", mCursorRow.getId()="
               + (mCursorRow != null ? mCursorRow.getCursorMapper().getLong(DBDefinitions.KEY_PK_ID)
                                     : null)
               + '}';
    }
}
