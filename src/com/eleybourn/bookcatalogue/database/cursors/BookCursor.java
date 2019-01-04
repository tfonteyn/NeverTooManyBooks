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
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;

import java.io.Closeable;

/**
 * Cursor implementation for book-related queries. The cursor wraps common
 * column lookups and reduces code clutter when accessing common columns by
 * providing a {@link BookRowView}
 *
 * @author Philip Warner
 */
public class BookCursor extends TrackedCursor implements Closeable {

    /** Get the row ID; need a local implementation so that get/setSelected() works. */
    private int mIdCol = -2;

    @Nullable
    private BookRowView mBookCursorRow;

    public BookCursor(@NonNull final SQLiteCursorDriver driver,
                      @NonNull final String editTable,
                      @NonNull final SQLiteQuery query,
                      @NonNull final Synchronizer sync) {
        super(driver, editTable, query, sync);
    }

    public final long getId() {
        if (mIdCol < 0) {
            mIdCol = getColumnIndex(DatabaseDefinitions.DOM_PK_ID.name);
            if (mIdCol < 0)
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_PK_ID.name);
        }
        return getLong(mIdCol);
    }

    @NonNull
    public BookRowView getCursorRow() {
        if (mBookCursorRow == null) {
            mBookCursorRow = new BookRowView(this);
        }
        return mBookCursorRow;
    }

    @Override
    @CallSuper
    public void close() {
        super.close();
        mBookCursorRow = null;
    }
}
