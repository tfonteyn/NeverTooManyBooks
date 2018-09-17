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

package com.eleybourn.bookcatalogue.cursors;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.NonNull;
import android.util.LongSparseArray;

import com.eleybourn.bookcatalogue.BooksRow;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;

/**
 * Cursor implementation for book-related queries. The cursor wraps common
 * column lookups and reduces code clutter when accessing common columns.
 *
 * The cursor also simulates a 'selected' flag for each book based on a HashMap of book IDs.
 *
 * @author Philip Warner
 */
public class BooksCursor extends TrackedCursor implements AutoCloseable {

    /** HashMap of selected book IDs */
    private final LongSparseArray<Boolean> mSelections = new LongSparseArray<>();
    /** Get the row ID; need a local implementation so that get/setSelected() works. */
    private int mIdCol = -2;
    /** Get a RowView */
    private BooksRow mView;

    public BooksCursor(@NonNull final SQLiteCursorDriver driver,
                       @NonNull final String editTable,
                       @NonNull final SQLiteQuery query,
                       @NonNull final Synchronizer sync) {
        super(driver, editTable, query, sync);
    }

    public final long getId() {
        if (mIdCol < 0) {
            mIdCol = getColumnIndex(DatabaseDefinitions.DOM_ID.name);
            if (mIdCol < 0)
                throw new RuntimeException("ISBN column not in result set");
        }
        return getLong(mIdCol);
    }

    public BooksRow getRowView() {
        if (mView == null) {
            mView = new BooksRow(this);
        }
        return mView;
    }

    /**
     * Clear the RowView and selections, if any
     */
    @Override
    public void close() {
        super.close();
        mSelections.clear();
        mView = null;
    }
}
