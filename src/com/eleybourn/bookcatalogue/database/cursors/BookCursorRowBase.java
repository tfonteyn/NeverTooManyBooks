package com.eleybourn.bookcatalogue.database.cursors;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;

/**
 * Convenience class to avoid having to write the same code in more than one place. This
 * class has get*() methods for the most common (all?) book-related fields. Passed a Cursor object
 * it will retrieve the specified value using the current cursor row.
 *
 * Unified {@link BookCursorRow} and {@link BooklistCursorRow}
 *
 * This base class should ONLY have accessors for fields actually present in the 'books' table
 * Others should be done via extended classes of this base one.
 */
public class BookCursorRowBase {

    /** Associated cursor object */
    @NonNull
    protected final Cursor mCursor;

    private int mIdCol = -2;

    private int mUuidCol = -2;
    private int mIsbnCol = -2;
    private int mLanguageCol = -2;
    private int mFormatCol = -2;
    private int mGenreCol = -2;
    private int mLocationCol = -2;
    private int mTitleCol = -2;
    private int mPublisherCol = -2;
    private int mReadCol = -2;
    private int mNotesCol = -2;
    private int mPagesCol = -2;
    private int mRatingCol = -2;
    private int mReadStartCol = -2;
    private int mReadEndCol = -2;
    private int mSignedCol = -2;
    private int mListPriceCol = -2;
    private int mListPriceCurrencyCol = -2;
    private int mPricePaidCol = -2;
    private int mPricePaidCurrencyCol = -2;
    private int mDescriptionCol = -2;
    private int mFirstPublicationCol = -2;
    private int mAnthologyMaskCol = -2;
    private int mEditionMaskCol = -2;
    private int mDateAddedCol = -2;
    private int mDateAcquiredCol = -2;
    private int mDateLastUpdatedCol = -2;
    private int mDatePublishedCol = -2;

    private int mLibraryThinkgBookIdCol = -2;
    private int mISFDBBookIdCol = -2;
    private int mGoodreadsBookIdCol = -2;
    private int mDateLastSyncedWithGoodReadsCol = -2;

    protected BookCursorRowBase(final @NonNull Cursor cursor) {
        mCursor = cursor;
    }

    @Nullable
    @Deprecated
    public String getString(final @NonNull String columnName) {
        final int position = mCursor.getColumnIndex(columnName);
        if (position < 0) {
            throw new DBExceptions.ColumnNotPresent(columnName);
        }
        return mCursor.isNull(position) ? null : mCursor.getString(position);
    }

    /**
     * Query underlying cursor for column index.
     */
    public int getColumnIndex(final @NonNull String columnName) {
        return mCursor.getColumnIndex(columnName);
    }

    /**
     * Get string from underlying cursor given a column index.
     */
    @Nullable
    public String getString(final int columnIndex) {
        return mCursor.getString(columnIndex);
    }


    public final long getId() {
        if (mIdCol < 0) {
            mIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_PK_ID.name);
            if (mIdCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_PK_ID.name);
            }
        }
        return mCursor.getLong(mIdCol);
    }

    @NonNull
    public final String getBookUuid() {
        if (mUuidCol < 0) {
            mUuidCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_UUID.name);
            if (mUuidCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_UUID.name);
            }
        }
        return mCursor.getString(mUuidCol);
    }

    public final String getIsbn() {
        if (mIsbnCol < 0) {
            mIsbnCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_ISBN.name);
            if (mIsbnCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_ISBN.name);
            }
        }
        return mCursor.getString(mIsbnCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @NonNull
    public String getTitle() {
        if (mTitleCol < 0) {
            mTitleCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_TITLE.name);
            if (mTitleCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_TITLE.name);
            }
        }
        return mCursor.getString(mTitleCol);
    }

    public boolean isRead() {
        return getRead() != 0;
    }

    /**
     * Convenience function to retrieve column value.
     */
    public int getRead() {
        if (mReadCol < 0) {
            mReadCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_READ.name);
            if (mReadCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_READ.name);
            }
        }
        return (int) mCursor.getLong(mReadCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @NonNull
    public String getPublisherName() {
        if (mPublisherCol < 0) {
            mPublisherCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_PUBLISHER.name);
            if (mPublisherCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_PUBLISHER.name);
            }
        }
        return mCursor.getString(mPublisherCol);
    }

    public final String getDatePublished() {
        if (mDatePublishedCol < 0) {
            mDatePublishedCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED.name);
            if (mDatePublishedCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED.name);
            }
        }
        return mCursor.getString(mDatePublishedCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @NonNull
    public String getLanguageCode() {
        if (mLanguageCol < 0) {
            mLanguageCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_LANGUAGE.name);
            if (mLanguageCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_LANGUAGE.name);
            }
        }
        return mCursor.getString(mLanguageCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @NonNull
    public String getFormat() {
        if (mFormatCol < 0) {
            mFormatCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_FORMAT.name);
            if (mFormatCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_FORMAT.name);
            }
        }
        return mCursor.getString(mFormatCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @NonNull
    public String getGenre() {
        if (mGenreCol < 0) {
            mGenreCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_GENRE.name);
            if (mGenreCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_GENRE.name);
            }
        }
        return mCursor.getString(mGenreCol);
    }

    /**
     * Convenience function to retrieve column value.
     */
    @NonNull
    public String getLocation() {
        if (mLocationCol < 0) {
            mLocationCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_LOCATION.name);
            if (mLocationCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_LOCATION.name);
            }
        }
        return mCursor.getString(mLocationCol);
    }

    public final String getFirstPublication() {
        if (mFirstPublicationCol < 0) {
            mFirstPublicationCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_FIRST_PUBLICATION.name);
            if (mFirstPublicationCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_FIRST_PUBLICATION.name);
            }
        }
        return mCursor.getString(mFirstPublicationCol);
    }

    public final String getDescription() {
        if (mDescriptionCol < 0) {
            mDescriptionCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_DESCRIPTION.name);
            if (mDescriptionCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_DESCRIPTION.name);
            }
        }
        return mCursor.getString(mDescriptionCol);
    }

    public final String getNotes() {
        if (mNotesCol < 0) {
            mNotesCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_NOTES.name);
            if (mNotesCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_NOTES.name);
            }
        }
        return mCursor.getString(mNotesCol);
    }

    public final String getPages() {
        if (mPagesCol < 0) {
            mPagesCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_PAGES.name);
            if (mPagesCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_PAGES.name);
            }
        }
        return mCursor.getString(mPagesCol);
    }

    public final double getRating() {
        if (mRatingCol < 0) {
            mRatingCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_RATING.name);
            if (mRatingCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_RATING.name);
            }
        }
        return mCursor.getDouble(mRatingCol);
    }

    public final String getReadStart() {
        if (mReadStartCol < 0) {
            mReadStartCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_READ_START.name);
            if (mReadStartCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_READ_START.name);
            }
        }
        return mCursor.getString(mReadStartCol);
    }

    public final String getReadEnd() {
        if (mReadEndCol < 0) {
            mReadEndCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_READ_END.name);
            if (mReadEndCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_READ_END.name);
            }
        }
        return mCursor.getString(mReadEndCol);
    }

    public final String getListPrice() {
        if (mListPriceCol < 0) {
            mListPriceCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_PRICE_LISTED.name);
            if (mListPriceCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_PRICE_LISTED.name);
            }
        }
        return mCursor.getString(mListPriceCol);
    }

    public final String getListPriceCurrency() {
        if (mListPriceCurrencyCol < 0) {
            mListPriceCurrencyCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY.name);
            if (mListPriceCurrencyCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY.name);
            }
        }
        return mCursor.getString(mListPriceCurrencyCol);
    }

    public final String getPricePaid() {
        if (mPricePaidCol < 0) {
            mPricePaidCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_PRICE_PAID.name);
            if (mPricePaidCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_PRICE_PAID.name);
            }
        }
        return mCursor.getString(mPricePaidCol);
    }

    public final String getPricePaidCurrency() {
        if (mPricePaidCurrencyCol < 0) {
            mPricePaidCurrencyCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY.name);
            if (mPricePaidCurrencyCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY.name);
            }
        }
        return mCursor.getString(mPricePaidCurrencyCol);
    }

    public int getSigned() {
        if (mSignedCol < 0) {
            mSignedCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_SIGNED.name);
            if (mSignedCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_SIGNED.name);
            }
        }
        return mCursor.getInt(mSignedCol);
    }

    public final int getAnthologyBitMask() {
        if (mAnthologyMaskCol < 0) {
            mAnthologyMaskCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK.name);
            if (mAnthologyMaskCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK.name);
            }
        }
        return mCursor.getInt(mAnthologyMaskCol);
    }

    public final int getEditionBitMask() {
        if (mEditionMaskCol < 0) {
            mEditionMaskCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK.name);
            if (mEditionMaskCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK.name);
            }
        }
        return mCursor.getInt(mEditionMaskCol);
    }

    public final String getDateAcquired() {
        if (mDateAcquiredCol < 0) {
            mDateAcquiredCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED.name);
            if (mDateAcquiredCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED.name);
            }
        }
        return mCursor.getString(mDateAcquiredCol);
    }

    public final String getDateAdded() {
        if (mDateAddedCol < 0) {
            mDateAddedCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_DATE_ADDED.name);
            if (mDateAddedCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_DATE_ADDED.name);
            }
        }
        return mCursor.getString(mDateAddedCol);
    }

    public final String getDateLastUpdated() {
        if (mDateLastUpdatedCol < 0) {
            mDateLastUpdatedCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_LAST_UPDATE_DATE.name);
            if (mDateLastUpdatedCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_LAST_UPDATE_DATE.name);
            }
        }
        return mCursor.getString(mDateLastUpdatedCol);
    }

    public final long getLibraryThingBookId() {
        if (mLibraryThinkgBookIdCol < 0) {
            mLibraryThinkgBookIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID.name);
            if (mLibraryThinkgBookIdCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID.name);
            }
        }
        return mCursor.getLong(mLibraryThinkgBookIdCol);
    }

    public final long getISFDBBookId() {
        if (mISFDBBookIdCol < 0) {
            mISFDBBookIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_ISFDB_ID.name);
            if (mISFDBBookIdCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_ISFDB_ID.name);
            }
        }
        return mCursor.getLong(mISFDBBookIdCol);
    }

    public final long getGoodreadsBookId() {
        if (mGoodreadsBookIdCol < 0) {
            mGoodreadsBookIdCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID.name);
            if (mGoodreadsBookIdCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID.name);
            }
        }
        return mCursor.getLong(mGoodreadsBookIdCol);
    }

    public final String getDateLastSyncedWithGoodReads() {
        if (mDateLastSyncedWithGoodReadsCol < 0) {
            mDateLastSyncedWithGoodReadsCol = mCursor.getColumnIndex(DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name);
            if (mDateLastSyncedWithGoodReadsCol < 0) {
                throw new DBExceptions.ColumnNotPresent(DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE.name);
            }
        }
        return mCursor.getString(mDateLastSyncedWithGoodReadsCol);
    }
}
