package com.eleybourn.bookcatalogue.database.cursors;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.ColumnNotPresentException;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_PAID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_TOC_BITMASK;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKS;

/**
 * Convenience class to avoid having to write the same code in more than one place. This
 * class has get*() methods for the most common (all?) book-related fields.
 * Passed a Cursor object it will retrieve the specified value using the current cursor row.
 * <p>
 * Unified {@link BookCursorRow} and {@link BooklistCursorRow}
 * <p>
 * This base class should ONLY have accessors for fields actually present in the 'books' table
 * Others should be done via extended classes of this base one.
 */
public class BookCursorRowBase {

    /** Associated cursor object. */
    @NonNull
    final Cursor mCursor;
    @NonNull
    final ColumnMapper mMapper;

    /**
     * Constructor.
     *
     * @param cursor the underlying cursor to use.
     */
    BookCursorRowBase(@NonNull final Cursor cursor) {
        mCursor = cursor;
        mMapper = new ColumnMapper(cursor, TBL_BOOKS);
    }

    /**
     * @param columnName to get
     *
     * @return the column index.
     */
    public int getColumnIndex(@NonNull final String columnName) {
        return mCursor.getColumnIndex(columnName);
    }

    /**
     * @param columnName to get
     *
     * @return a string from underlying cursor
     */
    @Nullable
    public String getString(@NonNull final String columnName) {
        final int position = mCursor.getColumnIndex(columnName);
        if (position < 0) {
            throw new ColumnNotPresentException(columnName);
        }
        return mCursor.isNull(position) ? null : mCursor.getString(position);
    }

    /**
     * @param columnIndex to get
     *
     * @return a string from underlying cursor
     */
    @Nullable
    public String getString(final int columnIndex) {
        return mCursor.getString(columnIndex);
    }

    /**
     * @param columnName to get
     *
     * @return a boolean from underlying cursor.
     */
    public boolean getBoolean(@NonNull final String columnName) {
        final int position = mCursor.getColumnIndex(columnName);
        if (position < 0) {
            throw new ColumnNotPresentException(columnName);
        }
        return mCursor.getInt(position) == 1;
    }

    /**
     * @param columnIndex to get
     *
     * @return a boolean from underlying cursor.
     */
    public boolean getBoolean(final int columnIndex) {
        return mCursor.getInt(columnIndex) == 1;
    }

    public final long getId() {
        return mMapper.getLong(DOM_PK_ID);
    }

    @NonNull
    public final String getBookUuid() {
        return mMapper.getString(DOM_BOOK_UUID);
    }

    @NonNull
    public final String getIsbn() {
        return mMapper.getString(DOM_BOOK_ISBN);
    }

    @NonNull
    public String getTitle() {
        return mMapper.getString(DOM_TITLE);
    }

    public boolean isRead() {
        return mMapper.getInt(DOM_BOOK_READ) != 0;
    }

    public int getRead() {
        return mMapper.getInt(DOM_BOOK_READ);
    }

    @NonNull
    public String getPublisherName() {
        return mMapper.getString(DOM_BOOK_PUBLISHER);
    }

    @NonNull
    public final String getDatePublished() {
        return mMapper.getString(DOM_BOOK_DATE_PUBLISHED);
    }

    @NonNull
    public String getLanguageCode() {
        return mMapper.getString(DOM_BOOK_LANGUAGE);
    }

    @NonNull
    public String getFormat() {
        return mMapper.getString(DOM_BOOK_FORMAT);
    }

    @NonNull
    public String getGenre() {
        return mMapper.getString(DOM_BOOK_GENRE);
    }

    @NonNull
    public String getLocation() {
        return mMapper.getString(DOM_BOOK_LOCATION);
    }

    @NonNull
    public final String getFirstPublication() {
        return mMapper.getString(DOM_FIRST_PUBLICATION);
    }

    @NonNull
    public final String getDescription() {
        return mMapper.getString(DOM_BOOK_DESCRIPTION);
    }

    @NonNull
    public final String getNotes() {
        return mMapper.getString(DOM_BOOK_NOTES);
    }

    @NonNull
    public final String getPages() {
        return mMapper.getString(DOM_BOOK_PAGES);
    }

    public final double getRating() {
        return mMapper.getDouble(DOM_BOOK_RATING);
    }

    @NonNull
    public final String getReadStart() {
        return mMapper.getString(DOM_BOOK_READ_START);
    }

    @NonNull
    public final String getReadEnd() {
        return mMapper.getString(DOM_BOOK_READ_END);
    }

    @NonNull
    public final String getListPrice() {
        return mMapper.getString(DOM_BOOK_PRICE_LISTED);
    }

    @NonNull
    public final String getListPriceCurrency() {
        return mMapper.getString(DOM_BOOK_PRICE_LISTED_CURRENCY);
    }

    @NonNull
    public final String getPricePaid() {
        return mMapper.getString(DOM_BOOK_PRICE_PAID);
    }

    @NonNull
    public final String getPricePaidCurrency() {
        return mMapper.getString(DOM_BOOK_PRICE_PAID_CURRENCY);
    }

    public int getSigned() {
        return mMapper.getInt(DOM_BOOK_SIGNED);
    }

    public final int getAnthologyBitMask() {
        return mMapper.getInt(DOM_BOOK_TOC_BITMASK);
    }

    public final int getEditionBitMask() {
        return mMapper.getInt(DOM_BOOK_EDITION_BITMASK);
    }

    @NonNull
    public final String getDateAcquired() {
        return mMapper.getString(DOM_BOOK_DATE_ACQUIRED);
    }

    @NonNull
    public final String getDateAdded() {
        return mMapper.getString(DOM_BOOK_DATE_ADDED);
    }

    @NonNull
    public final String getDateLastUpdated() {
        return mMapper.getString(DOM_LAST_UPDATE_DATE);
    }

    public final long getLibraryThingBookId() {
        return mMapper.getLong(DOM_BOOK_LIBRARY_THING_ID);
    }

    @NonNull
    public final String getOpenLibraryBookId() {
        return mMapper.getString(DOM_BOOK_OPEN_LIBRARY_ID);
    }

    public final long getISFDBBookId() {
        return mMapper.getLong(DOM_BOOK_ISFDB_ID);
    }

    public final long getGoodreadsBookId() {
        return mMapper.getLong(DOM_BOOK_GOODREADS_BOOK_ID);
    }

    @NonNull
    public final String getDateLastSyncedWithGoodreads() {
        return mMapper.getString(DOM_BOOK_GOODREADS_LAST_SYNC_DATE);
    }
}
