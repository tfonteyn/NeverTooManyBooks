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

package com.eleybourn.bookcatalogue.booklist;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;

/**
 * Cursor object that makes the underlying BooklistBuilder available to users of the Cursor, as
 * well as providing some information about the builder objects.
 *
 * @author Philip Warner
 */
public class BooklistCursor extends TrackedCursor implements BooklistSupportProvider {
    /** ID counter */
    private static Integer mBooklistCursorIdCounter = 0;
    /** Underlying BooklistBuilder object */
    private final BooklistBuilder mBuilder;
    /** ID of this cursor */
    private final long mId;
    /** Cached RowView for this cursor */
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
    BooklistCursor(@NonNull final SQLiteCursorDriver driver,
                   @NonNull final String editTable,
                   @NonNull final SQLiteQuery query,
                   @NonNull final BooklistBuilder builder,
                   @NonNull final Synchronizer sync) {
        super(driver, editTable, query, sync);
        // Allocate ID
        synchronized (mBooklistCursorIdCounter) {
            mId = ++mBooklistCursorIdCounter;
        }
        // Save builder.
        mBuilder = builder;
    }

    /**
     * Get the ID for this cursor.
     */
    public long getId() {
        return mId;
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
    public BooklistRowView getRowView() {
        if (mRowView == null) {
            mRowView = new BooklistRowView(this, mBuilder);
        }
        return mRowView;
    }

    /**
     * @return Get the number of levels in the book list.
     */
    public int numLevels() {
        return mBuilder.numLevels();
    }

    @Override
    public void close() {
        super.close();
    }
    /*
     * no need for this yet; it may even die because table is deleted and recreated.
     */
    //	public boolean requeryRebuild() {
    //		mBuilder.rebuild();
    //		return requery();
    //	}
}
