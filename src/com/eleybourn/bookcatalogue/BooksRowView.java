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

package com.eleybourn.bookcatalogue;

import android.database.Cursor;

import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;

import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_GENRE;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_ISBN;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_LOCATION;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_NOTES;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_RATING;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_READ;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_READ_END;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_ROWID;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_SIGNED;
import static com.eleybourn.bookcatalogue.database.ColumnInfo.KEY_TITLE;

/**
 * Convenience class to avoid having to write the same code in more than one place. This
 * class has get*() methods for the most common book-related fields. Passed a Cursor object
 * it will retrieve the specified value using the current cursor row.
 *
 * Both BooksCursor and BooksSnapshotCursor implement a getRowView() method that returns
 * a cached BookRowView based on the cursor.
 *
 * @author Philip Warner
 */
public class BooksRowView {

    /** Associated cursor object */
    private final Cursor mCursor;

    private int mIdCol = -2;
    private int mGoodreadsBookIdCol = -2;
    private int mBookUuidCol = -2;
    private int mIsbnCol = -2;
    private int mPrimaryAuthorCol = -2;
    private int mTitleCol = -2;
    private int mDescriptionCol = -2;
    private int mNotesCol = -2;
    private int mRatingCol = -2;
    private int mReadEndCol = -2;
    private int mReadCol = -2;
    private int mSignedCol = -2;
    private int mPublisherCol = -2;
    private int mDatePublishedCol = -2;
    private int mGenreCol = -2;
    private int mLanguageCol = -2;
    private int mLocationCol = -2;
    private int mSeriesCol = -2;

    /**
     * Constructor
     *
     * @param c Cursor to use
     */
    public BooksRowView(Cursor c) {
        mCursor = c;
    }

    public final long getId() {
        if (mIdCol < 0) {
            mIdCol = mCursor.getColumnIndex(KEY_ROWID);
            if (mIdCol < 0)
                throw new RuntimeException("ISBN column not in result set");
        }
        return mCursor.getLong(mIdCol);// mCurrentRow[mIsbnCol];
    }

    public final long getGoodreadsBookId() {
        if (mGoodreadsBookIdCol < 0) {
            mGoodreadsBookIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_GOODREADS_BOOK_ID.name);
            if (mGoodreadsBookIdCol < 0)
                throw new RuntimeException("Goodreads Book ID column not in result set");
        }
        return mCursor.getLong(mGoodreadsBookIdCol);// mCurrentRow[mIsbnCol];
    }

    public final String getBookUuid() {
        if (mBookUuidCol < 0) {
            mBookUuidCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_UUID.name);
            if (mBookUuidCol < 0)
                throw new RuntimeException("UUID column not in result set");
        }
        return mCursor.getString(mBookUuidCol);// mCurrentRow[mIsbnCol];
    }

    public final String getIsbn() {
        if (mIsbnCol < 0) {
            mIsbnCol = mCursor.getColumnIndex(KEY_ISBN);
            if (mIsbnCol < 0)
                throw new RuntimeException("ISBN column not in result set");
        }
        return mCursor.getString(mIsbnCol);// mCurrentRow[mIsbnCol];
    }

    public final String getPrimaryAuthorName() {
        if (mPrimaryAuthorCol < 0) {
            mPrimaryAuthorCol = mCursor.getColumnIndex(KEY_AUTHOR_FORMATTED_GIVEN_FIRST);
            if (mPrimaryAuthorCol < 0)
                throw new RuntimeException("Primary author column not in result set");
        }
        return mCursor.getString(mPrimaryAuthorCol);
//		return mCurrentRow[mPrimaryAuthorCol];
    }

    public final String getTitle() {
        if (mTitleCol < 0) {
            mTitleCol = mCursor.getColumnIndex(KEY_TITLE);
            if (mTitleCol < 0)
                throw new RuntimeException("Title column not in result set");
        }
        return mCursor.getString(mTitleCol);
//		return mCurrentRow[mTitleCol];
    }

    public final String getDescription() {
        if (mDescriptionCol < 0) {
            mDescriptionCol = mCursor.getColumnIndex(KEY_DESCRIPTION);
            if (mDescriptionCol < 0)
                throw new RuntimeException("Description column not in result set");
        }
        return mCursor.getString(mDescriptionCol);
//		return mCurrentRow[mDescriptionCol];
    }

    public final String getNotes() {
        if (mNotesCol < 0) {
            mNotesCol = mCursor.getColumnIndex(KEY_NOTES);
            if (mNotesCol < 0)
                throw new RuntimeException("Notes column not in result set");
        }
        return mCursor.getString(mNotesCol);
//		return mCurrentRow[mNotesCol];
    }

    public final double getRating() {
        if (mRatingCol < 0) {
            mRatingCol = mCursor.getColumnIndex(KEY_RATING);
            if (mRatingCol < 0)
                throw new RuntimeException("Rating column not in result set");
        }
        return mCursor.getDouble(mRatingCol);
    }

    public final String getReadEnd() {
        if (mReadEndCol < 0) {
            mReadEndCol = mCursor.getColumnIndex(KEY_READ_END);
            if (mReadEndCol < 0)
                throw new RuntimeException("Read-End column not in result set");
        }
        return mCursor.getString(mReadEndCol);
    }

    public final int getRead() {
        if (mReadCol < 0) {
            mReadCol = mCursor.getColumnIndex(KEY_READ);
            if (mReadCol < 0)
                throw new RuntimeException("READ column not in result set");
        }
        return mCursor.getInt(mReadCol);
//		return Integer.parseInt(mCurrentRow[mReadCol]);
    }

    private int getSigned() {
        if (mSignedCol < 0) {
            mSignedCol = mCursor.getColumnIndex(KEY_SIGNED);
            if (mSignedCol < 0)
                throw new RuntimeException("SIGNED column not in result set");
        }
        return mCursor.getInt(mSignedCol);
//		return Integer.parseInt(mCurrentRow[mReadCol]);
    }

    public final boolean isRead() {
        return getRead() != 0;
    }

    public final boolean isSigned() {
        return getSigned() != 0;
    }

    public final String getPublisher() {
        if (mPublisherCol < 0) {
            mPublisherCol = mCursor.getColumnIndex(KEY_PUBLISHER);
            if (mPublisherCol < 0)
                throw new RuntimeException("PUBLISHER column not in result set");
        }
        return mCursor.getString(mPublisherCol);
    }

    @SuppressWarnings("unused")
    public final String getDatePublished() {
        if (mDatePublishedCol < 0) {
            mDatePublishedCol = mCursor.getColumnIndex(KEY_DATE_PUBLISHED);
            if (mDatePublishedCol < 0)
                throw new RuntimeException("DATE_PUBLISHED column not in result set");
        }
        return mCursor.getString(mDatePublishedCol);
    }

    public final String getGenre() {
        if (mGenreCol < 0) {
            mGenreCol = mCursor.getColumnIndex(KEY_GENRE);
            if (mGenreCol < 0)
                throw new RuntimeException("GENRE column not in result set");
        }
        return mCursor.getString(mGenreCol);
    }

    public final String getLanguage() {
        if (mLanguageCol < 0) {
            mLanguageCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_LANGUAGE.name);
            if (mLanguageCol < 0)
                throw new RuntimeException("LANGUAGE column not in result set");
        }
        return mCursor.getString(mLanguageCol);
    }

    public final String getLocation() {
        if (mLocationCol < 0) {
            mLocationCol = mCursor.getColumnIndex(KEY_LOCATION);
            if (mLocationCol < 0)
                throw new RuntimeException("LOCATION column not in result set");
        }
        return mCursor.getString(mLocationCol);
    }

    public final String getSeries() {
        if (mSeriesCol < 0) {
            mSeriesCol = mCursor.getColumnIndex(KEY_SERIES_NAME);
            if (mSeriesCol < 0)
                throw new RuntimeException("SERIES column not in result set");
        }
        return mCursor.getString(mSeriesCol);
    }

    private String getString(final int position) {
        if (mCursor.isNull(position))
            return null;
        else
            return mCursor.getString(position);
    }

    public String getString(final String columnName) {
        final int position = mCursor.getColumnIndexOrThrow(columnName);
        return getString(position);
    }
}
