/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.sync.goodreads;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.network.HttpStatusException;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.AddBookToShelfApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.IsbnToIdApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ReviewEditApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShelfListApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShowBookByIdApiHandler;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShowBookByIsbnApiHandler;
import com.hardbacknutter.nevertoomanybooks.utils.dates.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

/**
 * This is a layer between the API handler classes and the Goodreads sync tasks.
 * <p>
 * Keeps a cache of the API handlers and the Goodreads bookshelves list.
 * <p>
 * <a href="https://www.goodreads.com/api/documentation">api documentation</a>
 */
public class GoodreadsManager {

    public static final String BASE_URL = "https://www.goodreads.com";
    public static final Locale SITE_LOCALE = Locale.US;

    public static final int CONNECTION_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 10_000;

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "GR";

    /** Preferences prefix. */
    public static final String PREF_KEY = "goodreads";

    /** Whether to collect genre string from the popular bookshelves. */
    private static final String PK_COLLECT_GENRE = PREF_KEY + ".search.collect.genre";
    /** last id we send to Goodreads. */
    private static final String PK_LAST_BOOK_SEND = PREF_KEY + ".last.send.id";
    /** last time we synced with Goodreads. */
    private static final String PK_LAST_SYNC_DATE = PREF_KEY + ".last.sync.date";

    /** A localized context. */
    @NonNull
    private final Context mContext;

    /** Authentication handler. */
    @NonNull
    private final GoodreadsAuth mGoodreadsAuth;

    /** Cache this handler. */
    @Nullable
    private IsbnToIdApiHandler mIsbnToIdApiHandler;
    /** Cache this handler. */
    @Nullable
    private AddBookToShelfApiHandler mAddBookToShelfApiHandler;
    /** Cache this handler. */
    @Nullable
    private ReviewEditApiHandler mReviewEditApiHandler;
    /** Cache this handler. */
    @Nullable
    private ShowBookByIdApiHandler mShowBookByIdApiHandler;
    /** Cache this handler. */
    @Nullable
    private ShowBookByIsbnApiHandler mShowBookByIsbnApiHandler;


    /** Cached list of shelves. */
    @Nullable
    private GoodreadsShelves mShelvesList;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param grAuth  Authentication handler
     */
    @WorkerThread
    public GoodreadsManager(@NonNull final Context context,
                            @NonNull final GoodreadsAuth grAuth) {
        mContext = context;

        mGoodreadsAuth = grAuth;
    }

    public static boolean isCollectGenre() {
        return ServiceLocator.getGlobalPreferences().getBoolean(PK_COLLECT_GENRE, true);
    }

    @Nullable
    public static LocalDateTime getLastSyncDate() {
        return new ISODateParser().parse(
                ServiceLocator.getGlobalPreferences().getString(PK_LAST_SYNC_DATE, null));
    }

    public static void setLastSyncDate(@Nullable final LocalDateTime dateTime) {
        final SharedPreferences global = ServiceLocator.getGlobalPreferences();
        if (dateTime == null) {
            global.edit().remove(PK_LAST_SYNC_DATE).apply();
        } else {
            global.edit()
                  .putString(PK_LAST_SYNC_DATE,
                             dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                  .apply();
        }
    }

    public static long getLastBookIdSend() {
        return ServiceLocator.getGlobalPreferences().getLong(PK_LAST_BOOK_SEND, 0);
    }

    public static void putLastBookIdSend(final long lastBookSend) {
        ServiceLocator.getGlobalPreferences()
                      .edit().putLong(PK_LAST_BOOK_SEND, lastBookSend)
                      .apply();
    }

    /**
     * Wrapper to update a book (review).
     *
     * <ul>The bookData bundle has to have:
     *      <li>{@link DBKey#BOOL_READ}</li>
     *      <li>{@link DBKey#DATE_READ_START}</li>
     *      <li>{@link DBKey#DATE_READ_END}</li>
     *      <li>{@link DBKey#KEY_RATING}</li>
     * </ul>
     *
     * @param reviewId Goodreads review id to update
     * @param bookData Bundle with the required data for the review.
     */
    void updateReview(final long reviewId,
                      @NonNull final DataHolder bookData)
            throws CredentialsException, IOException, SAXException {
        if (mReviewEditApiHandler == null) {
            mReviewEditApiHandler = new ReviewEditApiHandler(mContext, mGoodreadsAuth);
        }
        mReviewEditApiHandler.update(reviewId,
                                     bookData.getBoolean(DBKey.BOOL_READ),
                                     bookData.getString(DBKey.DATE_READ_START),
                                     bookData.getString(DBKey.DATE_READ_END),
                                     (int) bookData.getDouble(DBKey.KEY_RATING),
                                     null);
    }

    /**
     * Wrapper to search for a book.
     *
     * @param grBookId    Goodreads book id to get
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return Bundle of Goodreads book data
     *
     * @throws IOException on failures
     */
    @NonNull
    Bundle getBookById(final long grBookId,
                       @NonNull final boolean[] fetchCovers,
                       @NonNull final Bundle bookData)
            throws DiskFullException, CoverStorageException, IOException, CredentialsException,
                   HttpNotFoundException, HttpStatusException, SAXException {

        if (mShowBookByIdApiHandler == null) {
            mShowBookByIdApiHandler = new ShowBookByIdApiHandler(mContext, mGoodreadsAuth);
        }
        return mShowBookByIdApiHandler.searchByExternalId(grBookId, fetchCovers, bookData);
    }

    /**
     * Wrapper to search for a book.
     *
     * @param validIsbn   ISBN to use, must be valid
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return Bundle with Goodreads book data
     *
     * @throws IOException on failures
     */
    @NonNull
    Bundle getBookByIsbn(@NonNull final String validIsbn,
                         @NonNull final boolean[] fetchCovers,
                         @NonNull final Bundle bookData)
            throws DiskFullException, CoverStorageException, IOException, CredentialsException,
                   HttpNotFoundException, HttpStatusException, SAXException {

        if (mShowBookByIsbnApiHandler == null) {
            mShowBookByIsbnApiHandler = new ShowBookByIsbnApiHandler(mContext, mGoodreadsAuth);
        }
        return mShowBookByIsbnApiHandler.searchByIsbn(validIsbn, fetchCovers, bookData);
    }

    /**
     * Fetch all our shelves from Goodreads.
     * We only fetch them once and cache them.
     *
     * @return the Goodreads shelves
     *
     * @throws IOException on failures
     */
    @NonNull
    GoodreadsShelves getShelves()
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException {

        if (mShelvesList == null) {
            final ShelfListApiHandler handler = new ShelfListApiHandler(mContext, mGoodreadsAuth);
            mShelvesList = new GoodreadsShelves(handler.getAll());
        }
        return mShelvesList;
    }

    /**
     * Wrapper to add book to shelf.
     *
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @return reviewId
     *
     * @throws IOException on failures
     */
    long addBookToShelf(final long grBookId,
                        @SuppressWarnings("SameParameterValue")
                        @NonNull final String shelfName)
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(mContext, mGoodreadsAuth);
        }
        return mAddBookToShelfApiHandler.add(grBookId, shelfName);
    }

    /**
     * Wrapper to add book to a list of shelves.
     *
     * @param grBookId   GoodReads book id
     * @param shelfNames list of GoodReads shelf name
     *
     * @return reviewId
     *
     * @throws IOException on failures
     */
    long addBookToShelf(final long grBookId,
                        @NonNull final Collection<String> shelfNames)
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(mContext, mGoodreadsAuth);
        }
        return mAddBookToShelfApiHandler.add(grBookId, shelfNames);
    }

    /**
     * Wrapper to remove a book from a shelf.
     *
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @throws IOException on failures
     */
    void removeBookFromShelf(final long grBookId,
                             @NonNull final String shelfName)
            throws CredentialsException, IOException, SAXException,
                   HttpNotFoundException, HttpStatusException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(mContext, mGoodreadsAuth);
        }
        mAddBookToShelfApiHandler.remove(grBookId, shelfName);
    }

    /**
     * Wrapper to call ISBN->ID.
     *
     * @param isbn to search for
     *
     * @return Goodreads book ID
     *
     * @throws IOException on failures
     */
    @SuppressWarnings("unused")
    long isbnToId(@NonNull final String isbn)
            throws CredentialsException, IOException,
                   HttpNotFoundException, HttpStatusException {

        if (mIsbnToIdApiHandler == null) {
            mIsbnToIdApiHandler = new IsbnToIdApiHandler(mContext, mGoodreadsAuth);
        }
        return mIsbnToIdApiHandler.isbnToId(isbn);
    }

}
