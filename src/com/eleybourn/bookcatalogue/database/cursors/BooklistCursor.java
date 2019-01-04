/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.database.cursors;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.booklist.BooklistSupportProvider;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;

import java.io.Closeable;

/**
 * Cursor object that makes the underlying BooklistBuilder available to users of the Cursor, as
 * well as providing some information about the builder objects.
 *
 * @author Philip Warner
 */
public class BooklistCursor extends TrackedCursor implements BooklistSupportProvider, Closeable {
    /** Underlying BooklistBuilder object */
    @NonNull
    private final BooklistBuilder mBuilder;
    /** Cached RowView for this cursor */
    @Nullable
    private BooklistRowView mRowView = null;

    /**
     * Constructor
     *
     * @param driver    Part of standard cursor constructor.
     * @param editTable Part of standard cursor constructor.
     * @param query     Part of standard cursor constructor.
     * @param builder   BooklistBuilder used to make the query on which this cursor is based.
     * @param sync      Synchronizer object
     */
    public BooklistCursor(@NonNull final SQLiteCursorDriver driver,
                          @NonNull final String editTable,
                          @NonNull final SQLiteQuery query,
                          @NonNull final BooklistBuilder builder,
                          @NonNull final Synchronizer sync) {
        super(driver, editTable, query, sync);
        mBuilder = builder;
    }

    /**
     * Get the builder used to make this cursor.
     */
    @NonNull
    public BooklistBuilder getBuilder() {
        return mBuilder;
    }

    /**
     * Get a RowView for this cursor. Constructs one if necessary.
     */
    @NonNull
    public BooklistRowView getCursorRow() {
        if (mRowView == null) {
            mRowView = new BooklistRowView(this, mBuilder);
        }
        return mRowView;
    }
}
