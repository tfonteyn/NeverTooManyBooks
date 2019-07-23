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

package com.eleybourn.bookcatalogue.searches.goodreads;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.MappedCursorRow;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.goodreads.AuthorizationException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsAuthorizationActivity;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsShelf;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsShelves;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsWork;
import com.eleybourn.bookcatalogue.goodreads.api.AddBookToShelfApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.AuthUserApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.IsbnToIdApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ReviewUpdateApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShelvesListApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookApiHandler.ShowBookFieldName;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookByIdApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookByIsbnApiHandler;
import com.eleybourn.bookcatalogue.searches.SearchEngine;
import com.eleybourn.bookcatalogue.utils.BookNotFoundException;
import com.eleybourn.bookcatalogue.utils.CredentialsException;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * Class to wrap all Goodreads API calls and manage an API connection.
 *
 * @author Philip Warner
 */
public class GoodreadsManager
        implements SearchEngine {

    /**
     * website & Root URL for API calls. Right now, identical,
     * but this leaves future changes easier.
     */
    public static final String WEBSITE = "https://www.goodreads.com";
    public static final String BASE_URL = WEBSITE;
    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "_GR";

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "GoodReads.";
    /** last time we synced with Goodreads. */
    private static final String PREFS_LAST_SYNC_DATE = PREF_PREFIX + "LastSyncDate";

    /** Used when requesting the website for authorization. Only temporarily stored. */
    private static final String REQUEST_TOKEN = PREF_PREFIX + "RequestToken.Token";
    private static final String REQUEST_SECRET = PREF_PREFIX + "RequestToken.Secret";

    /** authorization tokens. */
    private static final String ACCESS_TOKEN = PREF_PREFIX + "AccessToken.Token";
    private static final String ACCESS_SECRET = PREF_PREFIX + "AccessToken.Secret";

    /** meta data keys in manifest. */
    private static final String GOODREADS_DEV_KEY = "goodreads.dev_key";
    private static final String GOODREADS_DEV_SECRET = "goodreads.dev_secret";
    /** the developer keys. */
    private static final String DEV_KEY = App.getManifestString(GOODREADS_DEV_KEY);
    private static final String DEV_SECRET = App.getManifestString(GOODREADS_DEV_SECRET);
    /** to control access to sLastRequestTime, we synchronize on this final Object. */
    @NonNull
    private static final Object LAST_REQUEST_TIME_LOCK = new Object();
    /** error string. */
    private static final String INVALID_CREDENTIALS =
            "Goodreads credentials need to be validated before accessing user data";
    /** Set to {@code true} when the credentials have been successfully verified. */
    public static boolean sHasValidCredentials;
    /** Cached when credentials have been verified. */
    @Nullable
    private static String sAccessToken;
    @Nullable
    private static String sAccessSecret;
    /** Local copy of user name retrieved when the credentials were verified. */
    @Nullable
    private static String sUsername;
    /** Local copy of user id retrieved when the credentials were verified. */
    private static long sUserId;
    /**
     * Stores the last time an API request was made to avoid breaking API rules.
     * Only modify this value from inside a synchronized (LAST_REQUEST_TIME_LOCK)
     */
    @NonNull
    private static Long sLastRequestTime = 0L;
    /** OAuth helpers. */
    private final OAuthConsumer mConsumer;
    /** OAuth helpers. */
    private final OAuthProvider mProvider;
    /** Cache this common handler. */
    @Nullable
    private IsbnToIdApiHandler mIsbnToIdApiHandler;
    /** Cache this common handler. */
    @Nullable
    private AddBookToShelfApiHandler mAddBookToShelfApiHandler;
    /** Cache this common handler. */
    @Nullable
    private ReviewUpdateApiHandler mReviewUpdateApiHandler;
    /** Cached list of shelves. */
    @Nullable
    private GoodreadsShelves mShelvesList;

    /**
     * Constructor.
     * <p>
     * <a href="https://www.goodreads.com/api/documentation">
     * https://www.goodreads.com/api/documentation</a>
     */
    public GoodreadsManager() {
        // Apache Commons HTTP
        mConsumer = new CommonsHttpOAuthConsumer(DEV_KEY, DEV_SECRET);
        mProvider = new CommonsHttpOAuthProvider(
                BASE_URL + "/oauth/request_token",
                BASE_URL + "/oauth/access_token",
                BASE_URL + "/oauth/authorize?mobile=1");

        // Native
//        mConsumer = new DefaultOAuthConsumer(DEV_KEY, DEV_SECRET);
//        mProvider = new DefaultOAuthProvider(
//                BASE_URL + "/oauth/request_token",
//                BASE_URL + "/oauth/access_token",
//                BASE_URL + "/oauth/authorize?mobile=1");

        // get the credentials
        hasCredentials();
    }

    @NonNull
    public static String getBaseURL() {
        return BASE_URL;
    }

    public static void openWebsite(@NonNull final Context context,
                                   final long bookId) {
        String url = getBaseURL() + "/book/show/" + bookId;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    /**
     * Use sLastRequestTime to determine how long until the next request is allowed;
     * and update sLastRequestTime this needs to be synchronized across threads.
     * <p>
     * Note that as a result of this approach sLastRequestTime may in fact be
     * in the future; callers to this routine effectively allocate time slots.
     * <p>
     * This method will sleep() until it can make a request; if 10 threads call this
     * simultaneously, one will return immediately, one will return 1 second later,
     * another two seconds etc.
     */
    public static void waitUntilRequestAllowed() {

        long now = System.currentTimeMillis();
        long wait;
        synchronized (LAST_REQUEST_TIME_LOCK) {
            wait = 1_000 - (now - sLastRequestTime);

            // sLastRequestTime must be updated while synchronized. As soon as this
            // block is left, another block may perform another update.
            if (wait < 0) {
                wait = 0;
            }
            sLastRequestTime = now + wait;
        }
        if (wait > 0) {
            try {
                Log.d("GR", "wait=" + wait);
                Thread.sleep(wait);
            } catch (@NonNull final InterruptedException ignored) {
            }
        }
    }

    /**
     * Construct a full or partial date string based on the y/m/d fields.
     *
     * @param data      Bundle to use
     * @param yearKey   bundle key
     * @param monthKey  bundle key
     * @param dayKey    bundle key
     * @param resultKey key to write to formatted date to
     *
     * @return the date string, or {@code null} if invalid
     */
    @SuppressLint("DefaultLocale")
    @Nullable
    public static String buildDate(@NonNull final Bundle data,
                                   @NonNull final String yearKey,
                                   @NonNull final String monthKey,
                                   @NonNull final String dayKey,
                                   @Nullable final String resultKey) {

        String date = null;
        if (data.containsKey(yearKey)) {
            date = String.format("%04d", data.getLong(yearKey));
            if (data.containsKey(monthKey)) {
                date += '-' + String.format("%02d", data.getLong(monthKey));
                if (data.containsKey(dayKey)) {
                    date += '-' + String.format("%02d", data.getLong(dayKey));
                }
            }
            if (resultKey != null && !date.isEmpty()) {
                data.putString(resultKey, date);
            }
        }
        return date;
    }

    /**
     * Get the date at which the last Goodreads synchronization was run.
     *
     * @return Last date
     */
    @Nullable
    public static Date getLastSyncDate() {
        return DateUtils.parseDate(App.getPrefs().getString(PREFS_LAST_SYNC_DATE, null));
    }

    /**
     * Set the date at which the last Goodreads synchronization was run.
     *
     * @param date Last date
     */
    public static void setLastSyncDate(@Nullable final Date date) {

        String dateStr = date != null ? DateUtils.utcSqlDateTime(date) : null;
        App.getPrefs().edit().putString(PREFS_LAST_SYNC_DATE, dateStr).apply();
    }

    /**
     * Clear the credentials from the preferences and local cache.
     */
    public static void resetCredentials() {

        sAccessToken = "";
        sAccessSecret = "";
        sHasValidCredentials = false;
        App.getPrefs().edit()
           .putString(ACCESS_TOKEN, "")
           .putString(ACCESS_SECRET, "")
           .apply();
    }

    @AnyThread
    public static boolean hasKey() {
        boolean hasKey = !DEV_KEY.isEmpty() && !DEV_SECRET.isEmpty();
        if (!hasKey) {
            Logger.warn(GoodreadsManager.class, "hasKey", "Goodreads dev key not available");
        }
        return hasKey;
    }

    /**
     * Get the stored token values from prefs.
     * This is token availability only, we don't check if they are valid here.
     * <p>
     * No network access.
     *
     * @return {@code true} if we have credentials.
     */
    public static boolean hasCredentials() {

        if (sAccessToken != null && !sAccessToken.isEmpty()
                && sAccessSecret != null && !sAccessSecret.isEmpty()) {
            return true;
        }

        // Get the stored token values from prefs
        sAccessToken = App.getPrefs().getString(ACCESS_TOKEN, null);
        sAccessSecret = App.getPrefs().getString(ACCESS_SECRET, null);

        return sAccessToken != null && !sAccessToken.isEmpty()
                && sAccessSecret != null && !sAccessSecret.isEmpty();
    }

    public void sign(@NonNull final Object request)
            throws IOException {
        mConsumer.setTokenWithSecret(sAccessToken, sAccessSecret);
        try {
            mConsumer.sign(request);
        } catch (@NonNull final OAuthMessageSignerException
                | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new IOException(e);
        }
    }

    @SuppressWarnings("unused")
    public String getUserName() {
        // don't call hasCredentials(), if we don't have them at this time, blame the developer.
        if (!sHasValidCredentials) {
            throw new IllegalStateException(INVALID_CREDENTIALS);
        }
        return sUsername;
    }

    public long getUserId() {
        // don't call hasCredentials(), if we don't have them at this time, blame the developer.
        if (!sHasValidCredentials) {
            throw new IllegalStateException(INVALID_CREDENTIALS);
        }
        return sUserId;
    }

    /**
     * Wrapper to call ISBN->ID API.
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @SuppressWarnings("unused")
    public long isbnToId(@NonNull final String isbn)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (mIsbnToIdApiHandler == null) {
            mIsbnToIdApiHandler = new IsbnToIdApiHandler(this);
        }
        return mIsbnToIdApiHandler.isbnToId(isbn);
    }

    /**
     * Fetch all our shelves from Goodreads.
     * We only fetch them once and cache them.
     *
     * @return the Goodreads shelves
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    private GoodreadsShelves getShelves()
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (mShelvesList == null) {
            ShelvesListApiHandler handler = new ShelvesListApiHandler(this);
            Map<String, GoodreadsShelf> map = handler.getAll();
            mShelvesList = new GoodreadsShelves(map);
        }
        return mShelvesList;
    }

    /**
     * Wrapper to call API to add book to shelf.
     *
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @return reviewId
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    private long addBookToShelf(final long grBookId,
                                @NonNull final String shelfName)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(this);
        }
        return mAddBookToShelfApiHandler.add(grBookId, shelfName);
    }

    /**
     * Wrapper to call API to remove a book from a shelf.
     *
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    private void removeBookFromShelf(final long grBookId,
                                     @NonNull final String shelfName)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(this);
        }
        mAddBookToShelfApiHandler.remove(grBookId, shelfName);
    }

    /**
     * Update a fixed set of fields; see method parameters.
     * <p>
     * Depending on the state of 'isRead', the date fields will determine which shelf is used.
     *
     * @param reviewId   to update
     * @param isRead     Flag to indicate we finished reading this book.
     * @param readStart  (optional) Date when we started reading this book, YYYY-MM-DD format
     * @param readEnd    (optional) Date when we finished reading this book, YYYY-MM-DD format
     * @param rating     Rating 0-5 with 0 == No rating
     * @param reviewText (optional) Text for the review
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    private void updateReview(final long reviewId,
                              final boolean isRead,
                              @Nullable final String readStart,
                              @Nullable final String readEnd,
                              final int rating,
                              @SuppressWarnings("SameParameterValue") @Nullable final String reviewText)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (mReviewUpdateApiHandler == null) {
            mReviewUpdateApiHandler = new ReviewUpdateApiHandler(this);
        }
        mReviewUpdateApiHandler.update(reviewId, isRead, readStart, readEnd, rating, reviewText);
    }

    /**
     * Wrapper to send an entire book, including shelves, to Goodreads.
     * Summary what the cursor row has to have:
     * <ul>
     * <li>{@link DBDefinitions#KEY_PK_ID}</li>
     * <li>{@link DBDefinitions#KEY_GOODREADS_BOOK_ID}</li>
     * <li>{@link DBDefinitions#KEY_ISBN}</li>
     * <li>{@link DBDefinitions#KEY_READ}</li>
     * <li>{@link DBDefinitions#KEY_READ_START}</li>
     * <li>{@link DBDefinitions#KEY_READ_END}</li>
     * <li>{@link DBDefinitions#KEY_RATING}</li>
     * </ul>
     *
     * @param db            DB connection
     * @param bookCursorRow single book to send
     *
     * @return Disposition of book
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    public ExportResult sendOneBook(@NonNull final DAO db,
                                    @NonNull final MappedCursorRow bookCursorRow)
            throws CredentialsException,
                   IOException,
                   BookNotFoundException {


        long bookId = bookCursorRow.getLong(DBDefinitions.KEY_PK_ID);
        String isbn = bookCursorRow.getString(DBDefinitions.KEY_ISBN);

        // Get the list of shelves from Goodreads. This is cached per instance of GoodreadsManager.
        GoodreadsShelves grShelfList = getShelves();

        long grBookId;
        Bundle grBook = null;

        // See if the book has a Goodreads ID and if it is valid.
        try {
            grBookId = bookCursorRow.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID);
            if (grBookId != 0) {
                // Get the book details to make sure we have a valid book ID
                grBook = getBookById(grBookId, false);
            }
        } catch (@NonNull final BookNotFoundException | CredentialsException | IOException e) {
            grBookId = 0;
        }

        boolean isNew = grBookId == 0;

        if (grBookId == 0 && !isbn.isEmpty()) {
            if (!ISBN.isValid(isbn)) {
                return ExportResult.notFound;
            }

            try {
                // Get the book details using ISBN
                grBook = getBookByIsbn(isbn, false);
                if (grBook.containsKey(DBDefinitions.KEY_GOODREADS_BOOK_ID)) {
                    grBookId = grBook.getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID);
                }

                // If we got an ID, save it against the book
                if (grBookId != 0) {
                    db.setGoodreadsBookId(bookId, grBookId);
                }
            } catch (@NonNull final BookNotFoundException e) {
                return ExportResult.notFound;
            } catch (@NonNull final IOException e) {
                return ExportResult.networkError;
            }
        }


        if (grBookId == 0) {
            return ExportResult.noIsbn;
        } else {
            // We found a Goodreads book, update it
            long reviewId = 0;

            // Get the review ID if we have the book details. For new books, it will not be present.
            if (!isNew && grBook.containsKey(ShowBookFieldName.REVIEW_ID)) {
                reviewId = grBook.getLong(ShowBookApiHandler.ShowBookFieldName.REVIEW_ID);
            }

            // Lists of shelf names and our best guess at the Goodreads canonical name
            List<String> shelves = new ArrayList<>();
            List<String> canonicalShelves = new ArrayList<>();

            // Build the list of shelves that we have in the local database for the book
            int exclusiveCount = 0;
            for (Bookshelf bookshelf : db.getBookshelvesByBookId(bookId)) {
                final String bookshelfName = bookshelf.getName();
                shelves.add(bookshelfName);

                final String canonicalShelfName = GoodreadsShelf.canonicalizeName(bookshelfName);
                canonicalShelves.add(canonicalShelfName);

                // Count how many of these shelves are exclusive in Goodreads.
                if (grShelfList.isExclusive(canonicalShelfName)) {
                    exclusiveCount++;
                }
            }

            // TEST: If no exclusive shelves are specified, then add pseudo-shelf to match Goodreads
            // because review.update does not seem to update them properly
            if (exclusiveCount == 0) {
                String pseudoShelf;
                if (bookCursorRow.getInt(DBDefinitions.KEY_READ) != 0) {
                    pseudoShelf = "Read";
                } else {
                    pseudoShelf = "To Read";
                }
                if (!shelves.contains(pseudoShelf)) {
                    shelves.add(pseudoShelf);
                    canonicalShelves.add(GoodreadsShelf.canonicalizeName(pseudoShelf));
                }
            }

            // Get the names of the shelves the book is currently on at Goodreads
            List<String> grShelves = null;
            if (!isNew && grBook.containsKey(ShowBookFieldName.SHELVES)) {
                grShelves = grBook.getStringArrayList(ShowBookFieldName.SHELVES);
            }
            // not in info, or failed to get
            if (grShelves == null) {
                grShelves = new ArrayList<>();
            }

            // Remove from any shelves from Goodreads that are not in our local list
            for (String grShelf : grShelves) {
                if (!canonicalShelves.contains(grShelf)) {
                    try {
                        // Goodreads does not seem to like removing books from the special shelves.
                        if (!(grShelfList.isExclusive(grShelf))) {
                            removeBookFromShelf(grBookId, grShelf);
                        }
                    } catch (@NonNull final BookNotFoundException e) {
                        // Ignore for now; probably means book not on shelf anyway
                    } catch (@NonNull final CredentialsException | IOException e) {
                        return ExportResult.error;
                    }
                }
            }

            // Add shelves to Goodreads if they are not currently there
            for (String shelf : shelves) {
                // Get the name the shelf will have at Goodreads
                final String canonicalShelfName = GoodreadsShelf.canonicalizeName(shelf);
                // Can only sent canonical shelf names if the book is on 0 or 1 of them.
                boolean okToSend = exclusiveCount < 2
                        || !grShelfList.isExclusive(canonicalShelfName);

                if (okToSend && !grShelves.contains(canonicalShelfName)) {
                    try {
                        reviewId = addBookToShelf(grBookId, shelf);
                    } catch (@NonNull final BookNotFoundException
                            | IOException
                            | CredentialsException e) {
                        return ExportResult.error;
                    }
                }
            }

            /* We should be safe always updating here because:
             * - all books that are already added have a review ID,
             * which we would have got from the bundle
             * - all new books will be added to at least one shelf,
             * which will have returned a review ID.
             * But, just in case, we check the review ID, and if 0,
             * we add the book to the 'Default' shelf.
             */
            if (reviewId == 0) {
                try {
                    reviewId = addBookToShelf(grBookId, "Default");
                } catch (@NonNull final BookNotFoundException | IOException | CredentialsException e) {
                    return ExportResult.error;
                }
            }

            // Now update the remaining review details.
            try {
                // Do not sync Notes<->Review. We will add a 'Review' field later.
                // ('notes' has been disabled from the SQL)
                updateReview(reviewId,
                             bookCursorRow.getBoolean(DBDefinitions.KEY_READ),
                             bookCursorRow.getString(DBDefinitions.KEY_READ_START),
                             bookCursorRow.getString(DBDefinitions.KEY_READ_END),
                             (int) bookCursorRow.getDouble(DBDefinitions.KEY_RATING),
                             null);

            } catch (@NonNull final BookNotFoundException e) {
                return ExportResult.error;
            }

            return ExportResult.sent;
        }
    }

    /**
     * Search for a book.
     * <p>
     * The search will in fact search/find a list of 'works' but we only return the first one.
     *
     * @return first book found, or an empty bundle if none found.
     */
    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@Nullable final String isbn,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final /* not supported */ String publisher,
                         final boolean fetchThumbnail)
            throws CredentialsException,
                   IOException {
        try {
            // getBookByIsbn will check on isbn being valid.
            if (isbn != null && !isbn.isEmpty()) {
                return getBookByIsbn(isbn, fetchThumbnail);

            } else if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
                List<GoodreadsWork> goodreadsWorks = new SearchBooksApiHandler(this)
                        .search(author + ' ' + title);

                if (!goodreadsWorks.isEmpty()) {
                    return getBookById(goodreadsWorks.get(0).bookId, fetchThumbnail);
                } else {
                    return new Bundle();
                }

            } else {
                return new Bundle();
            }
        } catch (@NonNull final BookNotFoundException e) {
            // to bad.
            return new Bundle();
        }
    }

    /**
     * @param isbn to search for
     * @param size of image to get.
     *
     * @return found/saved File, or {@code null} if none found (or any other failure)
     */
    @Nullable
    @Override
    @WorkerThread
    public File getCoverImage(@NonNull final String isbn,
                              @Nullable final ImageSize size) {
        if (!hasValidCredentials()) {
            return null;
        }
        return SearchEngine.getCoverImageFallback(this, isbn);
    }

    /**
     * Wrapper to search for a book.
     *
     * @return Bundle of GoodreadsWork objects
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    private Bundle getBookById(final long bookId,
                               final boolean fetchThumbnail)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (bookId != 0) {
            ShowBookByIdApiHandler api = new ShowBookByIdApiHandler(this);
            // Run the search
            return api.get(bookId, fetchThumbnail);
        } else {
            throw new IllegalArgumentException("No bookId");
        }
    }

    /**
     * Wrapper to search for a book.
     *
     * @return Bundle of GoodreadsWork objects
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    private Bundle getBookByIsbn(@Nullable final String isbn,
                                 final boolean fetchThumbnail)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (ISBN.isValid(isbn)) {
            ShowBookByIsbnApiHandler api = new ShowBookByIsbnApiHandler(this);
            // Run the search
            return api.get(isbn, fetchThumbnail);
        } else {
            throw new BookNotFoundException(isbn);
        }
    }

    /**
     * Check developer key, credentials and website being up. Does NOT check credentials.
     *
     * @return {@code true} if we can use this site for searching.
     */
    @Override
    @WorkerThread
    public boolean isAvailable() {
        return hasKey() && hasCredentials() && NetworkUtils.isAlive(getBaseURL());
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.goodreads;
    }

    /**
     * Return the public developer key, used for GET queries.
     */
    @NonNull
    @AnyThread
    public String getDevKey() {
        return DEV_KEY;
    }

    /**
     * Check if the current credentials (either cached or in prefs) are valid.
     * If they have been previously checked and were valid, just use that result.
     * <p>
     * Network access if credentials need to be checked.
     * <p>
     * It is assumed that {@link #isAvailable()} has already been called.
     * Developer reminder: do NOT throw an exception from here.
     */
    @WorkerThread
    public boolean hasValidCredentials() {
        // If credentials have already been accepted, don't re-check.
        if (sHasValidCredentials) {
            return true;
        }

        // If we don't have credentials at all, just leave
        if (!hasCredentials()) {
            return false;
        }

        mConsumer.setTokenWithSecret(sAccessToken, sAccessSecret);

        try {
            AuthUserApiHandler authUserApi = new AuthUserApiHandler(this);
            if (authUserApi.getAuthUser() == 0) {
                return false;
            }

            // Future usage
            sUsername = authUserApi.getUsername();
            sUserId = authUserApi.getUserId();

            // Cache the result to avoid network calls later
            sHasValidCredentials = true;
            return true;

        } catch (@NonNull final RuntimeException e) {
            // Something went wrong. Clear the access token, set credentials as bad
            sHasValidCredentials = false;
            sAccessToken = null;
            return false;
        }
    }

    /**
     * Request authorization for this application, for the current user,
     * by going to the OAuth web page.
     *
     * @throws AuthorizationException with GoodReads
     * @throws IOException            on other failures
     * @author Philip Warner
     */
    @WorkerThread
    public void requestAuthorization()
            throws AuthorizationException,
                   IOException {

        String authUrl;

        // Don't do this; this is just part of OAuth and not the Goodreads API
        //waitUntilRequestAllowed();

        // Get the URL to send the user to so they can authenticate.
        try {
            authUrl = mProvider.retrieveRequestToken(mConsumer,
                                                     GoodreadsAuthorizationActivity.AUTHORIZATION_CALLBACK);

        } catch (@NonNull final OAuthMessageSignerException
                | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new IOException(e);

        } catch (OAuthNotAuthorizedException e) {
            throw new AuthorizationException(e);
        }

        //TEST: double check if this ever gives issues!
        if (!authUrl.startsWith("http://") && !authUrl.startsWith("https://")) {
            // Make a valid URL for the parser (some come back without a schema)
            authUrl = "http://" + authUrl;
            if (BuildConfig.DEBUG /* always */) {
                Logger.warn(this, "requestAuthorization", "replacing with: " + authUrl);
            }
        }

        // Temporarily save the token; this GoodreadsManager object may be destroyed
        // before the web page returns.
        App.getPrefs().edit()
           .putString(REQUEST_TOKEN, mConsumer.getToken())
           .putString(REQUEST_SECRET, mConsumer.getTokenSecret())
           .apply();

        // Open the web page, always use the app context here.
        App.getAppContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
    }

    /**
     * Called by the callback activity, @link AuthorizationResultCheckTask,
     * when a request has been authorized by the user.
     *
     * @throws AuthorizationException with GoodReads
     * @throws IOException            on other failures
     */
    @WorkerThread
    public void handleAuthenticationAfterAuthorization()
            throws AuthorizationException,
                   IOException {

        // Get the temporarily saved request tokens.
        String requestToken = App.getPrefs().getString(REQUEST_TOKEN, null);
        String requestSecret = App.getPrefs().getString(REQUEST_SECRET, null);

        // sanity check; the tokens are stored in #requestAuthorization
        if (requestToken == null || requestToken.isEmpty()
                || requestSecret == null || requestSecret.isEmpty()) {
            throw new IllegalStateException("No request token found in preferences");
        }

        // Update the consumer.
        mConsumer.setTokenWithSecret(requestToken, requestSecret);

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get the access token
        try {
            mProvider.retrieveAccessToken(mConsumer, null);

        } catch (@NonNull final OAuthMessageSignerException
                | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new IOException(e);

        } catch (OAuthNotAuthorizedException e) {
            throw new AuthorizationException(e);
        }

        // Cache and save the tokens
        sAccessToken = mConsumer.getToken();
        sAccessSecret = mConsumer.getTokenSecret();

        App.getPrefs().edit()
           .putString(ACCESS_TOKEN, sAccessToken)
           .putString(ACCESS_SECRET, sAccessSecret)
           .remove(REQUEST_TOKEN)
           .remove(REQUEST_SECRET)
           .apply();
    }

    /**
     * Enum to handle possible results of sending a book to Goodreads.
     */
    public enum ExportResult {
        error, sent, noIsbn, notFound, networkError
    }
}

