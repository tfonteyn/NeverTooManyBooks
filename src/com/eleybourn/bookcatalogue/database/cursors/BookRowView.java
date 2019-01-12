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

import android.database.Cursor;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;

/**
 * Convenience class to avoid having to write the same code in more than one place. This
 * class has get*() methods for the most common book-related fields. Passed a Cursor object
 * it will retrieve the specified value using the current cursor row.
 * <p>
 * {@link BookCursor#getCursorRow()} returns cached {@link BookRowView} based on the Cursor.
 *
 * @author Philip Warner
 */
public class BookRowView
        extends BookRowViewBase {

    private int mPrimaryAuthorCol = -2;
    private int mPrimaryAuthorGivenFirstCol = -2;
    private int mPrimarySeriesCol = -2;
    private int mLoanedToCol = -2;

    public BookRowView(@NonNull final Cursor cursor) {
        super(cursor);
    }

    public final String getPrimaryAuthorNameFormatted() {
        if (mPrimaryAuthorCol < 0) {
            mPrimaryAuthorCol = mCursor.getColumnIndex(
                    DatabaseDefinitions.DOM_AUTHOR_FORMATTED.name);
            if (mPrimaryAuthorCol < 0) {
                throw new DBExceptions.ColumnNotPresent(
                        DatabaseDefinitions.DOM_AUTHOR_FORMATTED.name);
            }
        }
        return mCursor.getString(mPrimaryAuthorCol);
    }

    public final String getPrimaryAuthorNameFormattedGivenFirst() {
        if (mPrimaryAuthorGivenFirstCol < 0) {
            mPrimaryAuthorGivenFirstCol = mCursor.getColumnIndex(
                    DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST.name);
            if (mPrimaryAuthorGivenFirstCol < 0) {
                throw new DBExceptions.ColumnNotPresent(
                        DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST.name);
            }
        }
        return mCursor.getString(mPrimaryAuthorGivenFirstCol);
    }

    public final String getPrimarySeriesFormatted() {
        if (mPrimarySeriesCol < 0) {
            mPrimarySeriesCol = mCursor.getColumnIndex(
                    DatabaseDefinitions.DOM_SERIES_FORMATTED.name);
            if (mPrimarySeriesCol < 0) {
                throw new DBExceptions.ColumnNotPresent(
                        DatabaseDefinitions.DOM_SERIES_FORMATTED.name);
            }
        }
        return mCursor.getString(mPrimarySeriesCol);
    }

    public final String getLoanedTo() {
        if (mLoanedToCol < 0) {
            mLoanedToCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_LOANED_TO.name);
            if (mLoanedToCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_LOANED_TO.name);
            }
        }
        return mCursor.getString(mLoanedToCol);
    }
}
