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

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LOANEE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_SERIES_FORMATTED;

/**
 * Convenience class to avoid having to write the same code in more than one place. This
 * class has get*() methods for the most common book-related fields. Passed a Cursor object
 * it will retrieve the specified value using the current cursor row.
 * <p>
 * {@link BookCursor#getCursorRow()} returns cached {@link BookCursorRow} based on the Cursor.
 *
 * @author Philip Warner
 */
public class BookCursorRow
        extends BookCursorRowBase {

    /**
     * Constructor.
     *
     * @param cursor the underlying cursor.
     */
    public BookCursorRow(@NonNull final Cursor cursor) {
        super(cursor);

        mMapper.addDomains(DOM_AUTHOR_FORMATTED,
                           DOM_AUTHOR_FORMATTED_GIVEN_FIRST,
                           DOM_SERIES_FORMATTED,
                           DOM_BOOK_LOANEE);
    }

    public final String getPrimaryAuthorNameFormatted() {
        return mMapper.getString(DOM_AUTHOR_FORMATTED);
    }

    public final String getPrimaryAuthorNameFormattedGivenFirst() {
        return mMapper.getString(DOM_AUTHOR_FORMATTED_GIVEN_FIRST);
    }

    public final String getPrimarySeriesFormatted() {
        return mMapper.getString(DOM_SERIES_FORMATTED);
    }

    public final String getLoanedTo() {
        return mMapper.getString(DOM_BOOK_LOANEE);
    }
}
