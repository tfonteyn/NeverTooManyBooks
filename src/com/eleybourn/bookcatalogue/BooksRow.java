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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;

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
public class BooksRow {

    /** Associated cursor object */
    private final Cursor mCursor;

    private int mIdCol = -2;

    private int mBookUuidCol = -2;
    private int mDatePublishedCol = -2;
    private int mDescriptionCol = -2;
    private int mFormatCol = -2;
    private int mGenreCol = -2;
    private int mGoodreadsBookIdCol = -2;
    private int mIsbnCol = -2;
    private int mLanguageCol = -2;
    private int mLocationCol = -2;
    private int mNotesCol = -2;
    private int mPrimaryAuthorCol = -2;
    private int mPublisherCol = -2;
    private int mRatingCol = -2;
    private int mReadCol = -2;
    private int mReadEndCol = -2;
    private int mSeriesCol = -2;
    private int mSignedCol = -2;
    private int mTitleCol = -2;

    public BooksRow(@NonNull final Cursor c) {
        mCursor = c;
    }

    @Nullable
    private String getString(final int position) {
        if (mCursor.isNull(position))
            return null;
        else
            return mCursor.getString(position);
    }

    @Nullable
    public String getString(@NonNull final String columnName) {
        final int position = mCursor.getColumnIndexOrThrow(columnName);
        return getString(position);
    }

    public final long getId() {
        if (mIdCol < 0) {
            mIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ID.name);
            if (mIdCol < 0)
                throw new RuntimeException("DOM_ID column not in result set");
        }
        return mCursor.getLong(mIdCol);
    }

    public final String getBookUuid() {
        if (mBookUuidCol < 0) {
            mBookUuidCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_UUID.name);
            if (mBookUuidCol < 0)
                throw new RuntimeException("DOM_BOOK_UUID column not in result set");
        }
        return mCursor.getString(mBookUuidCol);
    }

    @SuppressWarnings("unused")
    public final String getDatePublished() {
        if (mDatePublishedCol < 0) {
            mDatePublishedCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED.name);
            if (mDatePublishedCol < 0)
                throw new RuntimeException("DATE_PUBLISHED column not in result set");
        }
        return mCursor.getString(mDatePublishedCol);
    }

    public final String getDescription() {
        if (mDescriptionCol < 0) {
            mDescriptionCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_DESCRIPTION.name);
            if (mDescriptionCol < 0)
                throw new RuntimeException("DOM_DESCRIPTION column not in result set");
        }
        return mCursor.getString(mDescriptionCol);
    }

    public final String getFormat() {
        if (mFormatCol < 0) {
            mFormatCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_FORMAT.name);
            if (mFormatCol < 0)
                throw new RuntimeException("DOM_BOOK_FORMAT column not in result set");
        }
        return mCursor.getString(mFormatCol);
    }

    public final String getGenre() {
        if (mGenreCol < 0) {
            mGenreCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_GENRE.name);
            if (mGenreCol < 0)
                throw new RuntimeException("DOM_BOOK_GENRE column not in result set");
        }
        return mCursor.getString(mGenreCol);
    }

    public final long getGoodreadsBookId() {
        if (mGoodreadsBookIdCol < 0) {
            mGoodreadsBookIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_GOODREADS_BOOK_ID.name);
            if (mGoodreadsBookIdCol < 0)
                throw new RuntimeException("DOM_GOODREADS_BOOK_ID column not in result set");
        }
        return mCursor.getLong(mGoodreadsBookIdCol);
    }

    public final String getIsbn() {
        if (mIsbnCol < 0) {
            mIsbnCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_ISBN.name);
            if (mIsbnCol < 0)
                throw new RuntimeException("DOM_ISBN column not in result set");
        }
        return mCursor.getString(mIsbnCol);
    }

    public final String getLanguage() {
        if (mLanguageCol < 0) {
            mLanguageCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_LANGUAGE.name);
            if (mLanguageCol < 0)
                throw new RuntimeException("DOM_BOOK_LANGUAGE column not in result set");
        }
        return mCursor.getString(mLanguageCol);
    }

    public final String getLocation() {
        if (mLocationCol < 0) {
            mLocationCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_LOCATION.name);
            if (mLocationCol < 0)
                throw new RuntimeException("DOM_BOOK_LOCATION column not in result set");
        }
        return mCursor.getString(mLocationCol);
    }

    public final String getNotes() {
        if (mNotesCol < 0) {
            mNotesCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_NOTES.name);
            if (mNotesCol < 0)
                throw new RuntimeException("DOM_NOTES column not in result set");
        }
        return mCursor.getString(mNotesCol);
    }

    public final String getPrimaryAuthorName() {
        if (mPrimaryAuthorCol < 0) {
            mPrimaryAuthorCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST.name);
            if (mPrimaryAuthorCol < 0)
                throw new RuntimeException("Primary author column not in result set");
        }
        return mCursor.getString(mPrimaryAuthorCol);
    }

    public final String getPublisher() {
        if (mPublisherCol < 0) {
            mPublisherCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_PUBLISHER.name);
            if (mPublisherCol < 0)
                throw new RuntimeException("DOM_PUBLISHER column not in result set");
        }
        return mCursor.getString(mPublisherCol);
    }

    public final double getRating() {
        if (mRatingCol < 0) {
            mRatingCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_RATING.name);
            if (mRatingCol < 0)
                throw new RuntimeException("DOM_BOOK_RATING column not in result set");
        }
        return mCursor.getDouble(mRatingCol);
    }

    public final String getReadEnd() {
        if (mReadEndCol < 0) {
            mReadEndCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_READ_END.name);
            if (mReadEndCol < 0)
                throw new RuntimeException("DOM_BOOK_READ_END column not in result set");
        }
        return mCursor.getString(mReadEndCol);
    }

    public final boolean isRead() {
        return getRead() != 0;
    }

    public final int getRead() {
        if (mReadCol < 0) {
            mReadCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_READ.name);
            if (mReadCol < 0)
                throw new RuntimeException("DOM_BOOK_READ column not in result set");
        }
        return mCursor.getInt(mReadCol);
    }

    public final String getSeries() {
        if (mSeriesCol < 0) {
            mSeriesCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_SERIES_NAME.name);
            if (mSeriesCol < 0)
                throw new RuntimeException("DOM_SERIES_NAME column not in result set");
        }
        return mCursor.getString(mSeriesCol);
    }

    public final boolean isSigned() {
        return getSigned() != 0;
    }

    private int getSigned() {
        if (mSignedCol < 0) {
            mSignedCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_SIGNED.name);
            if (mSignedCol < 0)
                throw new RuntimeException("DOM_BOOK_SIGNED column not in result set");
        }
        return mCursor.getInt(mSignedCol);
    }

    public final String getTitle() {
        if (mTitleCol < 0) {
            mTitleCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_TITLE.name);
            if (mTitleCol < 0)
                throw new RuntimeException("DOM_TITLE column not in result set");
        }
        return mCursor.getString(mTitleCol);
    }
}
