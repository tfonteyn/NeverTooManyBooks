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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsAuthorizationActivity;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsWork;
import com.eleybourn.bookcatalogue.goodreads.api.AuthUserApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.api.BookshelfListApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.BookshelfListApiHandler.GrBookshelfFields;
import com.eleybourn.bookcatalogue.goodreads.api.IsbnToIdHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ReviewUpdateHandler;
import com.eleybourn.bookcatalogue.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShelfAddBookHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookApiHandler.ShowBookFieldNames;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookByIdApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ShowBookByIsbnApiHandler;
import com.eleybourn.bookcatalogue.searches.SearchEngine;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
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

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "GoodReads.";

    /** last time we synced with Goodreads. */
    private static final String PREFS_LAST_SYNC_DATE = PREF_PREFIX + "LastSyncDate";

    /** authorization tokens. */
    private static final String ACCESS_TOKEN = PREF_PREFIX + "AccessToken.Token";
    private static final String ACCESS_SECRET = PREF_PREFIX + "AccessToken.Secret";
    private static final String REQUEST_TOKEN = PREF_PREFIX + "RequestToken.Token";
    private static final String REQUEST_SECRET = PREF_PREFIX + "RequestToken.Secret";

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
    private static final String ERROR_UNEXPECTED_STATUS_CODE_FROM_API = "Unexpected status code from API: ";

    /** Set to {@code true} when the credentials have been successfully verified. */
    private static boolean sHasValidCredentials;
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
    private final CommonsHttpOAuthConsumer mConsumer;
    /** OAuth helpers. */
    private final OAuthProvider mProvider;

    @Nullable
    private IsbnToIdHandler mIsbnToIdHandler;
    @Nullable
    private GoodreadsBookshelves mBookshelfList;
    @Nullable
    private ShelfAddBookHandler mAddBookHandler;
    @Nullable
    private ReviewUpdateHandler mReviewUpdater;

    /**
     * Constructor.
     * <p>
     * Original:
     * BASE_URL + "/oauth/request_token",
     * BASE_URL + "/oauth/access_token",
     * BASE_URL + "/oauth/authorize"
     * <p>
     * 2019-06-05: https://www.goodreads.com/api/documentation
     * <p>
     * /oauth/authorize?mobile=1
     */
    public GoodreadsManager() {
        mConsumer = new CommonsHttpOAuthConsumer(DEV_KEY, DEV_SECRET);
        mProvider = new CommonsHttpOAuthProvider(
                BASE_URL + "/oauth/request_token",
                BASE_URL + "/oauth/access_token",
                BASE_URL + "/oauth/authorize?mobile=1");

        if (hasCredentials()) {
            mConsumer.setTokenWithSecret(sAccessToken, sAccessSecret);
        }
    }

    @NonNull
    public static String getBaseURL() {
        return BASE_URL;
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
    private static void waitUntilRequestAllowed() {

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
     * Create canonical representation based on the best guess as to the goodreads rules.
     *
     * @param name to bless
     *
     * @return blessed name
     */
    public static String canonicalizeBookshelfName(@NonNull final String name) {

        StringBuilder canonical = new StringBuilder();
        String lcName = name.toLowerCase(LocaleUtils.getPreferredLocal());
        for (int i = 0; i < lcName.length(); i++) {
            char c = lcName.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                canonical.append(c);
            } else {
                canonical.append('-');
            }
        }
        return canonical.toString();
    }

    /**
     * Construct a full or partial date string based on the y/m/d fields.
     *
     * @param data      Bundle to use
     * @param yearKey   bundle key
     * @param monthKey  bundle key
     * @param dayKey    bundle key
     * @param resultKey key to write to formatted date to
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
     * Get the date at which the last goodreads synchronization was run.
     *
     * @return Last date
     */
    @Nullable
    public static Date getLastSyncDate() {
        return DateUtils.parseDate(App.getPrefs().getString(PREFS_LAST_SYNC_DATE, null));
    }

    /**
     * Set the date at which the last goodreads synchronization was run.
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
    public static void forgetCredentials() {

        sAccessToken = "";
        sAccessSecret = "";
        sHasValidCredentials = false;
        App.getPrefs().edit()
           .putString(ACCESS_TOKEN, "")
           .putString(ACCESS_SECRET, "")
           .apply();
    }

    /**
     * Check if the access tokens are available (not if they are valid).
     * No network access.
     */
    public static boolean hasCredentials() {

        if (sAccessToken != null && !sAccessToken.isEmpty()
                && sAccessSecret != null && !sAccessSecret.isEmpty()) {
            return true;
        }

        // Get the stored token values from prefs, and setup the consumer if present
        sAccessToken = App.getPrefs().getString(ACCESS_TOKEN, null);
        sAccessSecret = App.getPrefs().getString(ACCESS_SECRET, null);

        return !(sAccessToken == null || sAccessToken.isEmpty()
                || sAccessSecret == null || sAccessSecret.isEmpty());
    }

    /**
     * Create an HttpClient with specifically set buffer sizes to deal with
     * potentially exorbitant settings on some HTC handsets.
     */
    @NonNull
    private HttpClient newHttpClient() {

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 30_000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpConnectionParams.setLinger(params, 0);
        HttpConnectionParams.setTcpNoDelay(params, false);
        return new DefaultHttpClient(params);
    }

    /**
     * Sign a request and submit it; then pass it off to a parser.
     *
     * @author Philip Warner
     */
    public void execute(@NonNull final HttpUriRequest request,
                        @Nullable final DefaultHandler requestHandler,
                        final boolean requiresSignature)
            throws IOException,
                   AuthorizationException,
                   BookNotFoundException {

        // Sign the request
        if (requiresSignature) {
            mConsumer.setTokenWithSecret(sAccessToken, sAccessSecret);
            try {
                mConsumer.sign(request);
            } catch (@NonNull final OAuthMessageSignerException
                    | OAuthExpectationFailedException
                    | OAuthCommunicationException e) {
                throw new IOException(e);
            }
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get a new client
        HttpClient httpClient = newHttpClient();

        // Submit the request and process result.
        HttpResponse response;
        InputStream is;
        try {
            response = httpClient.execute(request);

        } catch (@NonNull final ClientProtocolException e) {
            throw new IOException(e);
        }

        HttpEntity entity = response.getEntity();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS && DEBUG_SWITCHES.DUMP_HTTP_RESPONSE) {
            entity.writeTo(System.out);
            if (!entity.isRepeatable()) {
                return;
            }
        }
        is = entity.getContent();

        int code = response.getStatusLine().getStatusCode();
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                parseResponse(is, requestHandler);
                break;

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                sHasValidCredentials = false;
                throw new AuthorizationException(R.string.goodreads);

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new BookNotFoundException();

            default:
                throw new IOException(ERROR_UNEXPECTED_STATUS_CODE_FROM_API
                                              + response.getStatusLine().getStatusCode()
                                              + '/' + response.getStatusLine().getReasonPhrase());
        }
    }

    /**
     * Sign a request and submit it then return the raw text output.
     *
     * @author Philip Warner
     */
    @NonNull
    public String executeRaw(@NonNull final HttpUriRequest request)
            throws IOException,
                   AuthorizationException,
                   BookNotFoundException {

        // Sign the request
        mConsumer.setTokenWithSecret(sAccessToken, sAccessSecret);
        try {
            mConsumer.sign(request);
        } catch (@NonNull final OAuthMessageSignerException
                | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new IOException(e);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get a new client
        HttpClient httpClient = newHttpClient();

        // Submit the request then process result.
        HttpResponse response = httpClient.execute(request);

        StringBuilder html = new StringBuilder();
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            InputStream in = entity.getContent();
            if (in != null) {
                while (true) {
                    int i = in.read();
                    if (i == -1) {
                        break;
                    }
                    html.append((char) i);
                }
            }
        }

        int code = response.getStatusLine().getStatusCode();
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                return html.toString();

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                sHasValidCredentials = false;
                throw new AuthorizationException(R.string.goodreads);

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new BookNotFoundException();

            default:
                throw new IOException(ERROR_UNEXPECTED_STATUS_CODE_FROM_API
                                              + response.getStatusLine().getStatusCode()
                                              + '/' + response.getStatusLine().getReasonPhrase());
        }
    }

    /**
     * Pass a response off to a parser.
     *
     * @author Philip Warner
     */
    private void parseResponse(@NonNull final InputStream is,
                               @Nullable final DefaultHandler handler)
            throws IOException {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = factory.newSAXParser();
            parser.parse(is, handler);
            // wrap parser exceptions in an IOException
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, e);
            }
            throw new IOException(e);
        }
    }

    @SuppressWarnings("unused")
    @Nullable
    public String getUsername() {
        if (!sHasValidCredentials) {
            throw new IllegalStateException(INVALID_CREDENTIALS);
        }
        return sUsername;
    }

    public long getUserId() {
        if (!sHasValidCredentials) {
            throw new IllegalStateException(INVALID_CREDENTIALS);
        }
        return sUserId;
    }

    /**
     * Wrapper to call ISBN->ID API.
     */
    @SuppressWarnings("unused")
    public long isbnToId(@NonNull final String isbn)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        if (mIsbnToIdHandler == null) {
            mIsbnToIdHandler = new IsbnToIdHandler(this);
        }
        return mIsbnToIdHandler.isbnToId(isbn);
    }

    @NonNull
    private GoodreadsBookshelves getShelves()
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        if (mBookshelfList == null) {
            Map<String, GoodreadsBookshelf> map = new HashMap<>();
            BookshelfListApiHandler handler = new BookshelfListApiHandler(this);
            int page = 1;
            while (true) {
                Bundle result = handler.run(page);
                List<Bundle> grShelves = result.getParcelableArrayList(GrBookshelfFields.SHELVES);
                if (grShelves == null || grShelves.isEmpty()) {
                    break;
                }

                for (Bundle grShelf : grShelves) {
                    GoodreadsBookshelf shelf = new GoodreadsBookshelf(grShelf);
                    map.put(shelf.getName(), shelf);
                }

                if (result.getLong(GrBookshelfFields.END) >= result.getLong(
                        GrBookshelfFields.TOTAL)) {
                    break;
                }

                page++;
            }
            mBookshelfList = new GoodreadsBookshelves(map);
        }
        return mBookshelfList;
    }

    /**
     * Wrapper to call API to add book to shelf.
     *
     * @return reviewId
     */
    private long addBookToShelf(@NonNull final String shelfName,
                                final long grBookId)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        if (mAddBookHandler == null) {
            mAddBookHandler = new ShelfAddBookHandler(this);
        }
        return mAddBookHandler.add(shelfName, grBookId);
    }

    /**
     * Wrapper to call API to remove a book from a shelf.
     */
    private void removeBookFromShelf(@NonNull final String shelfName,
                                     final long grBookId)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        if (mAddBookHandler == null) {
            mAddBookHandler = new ShelfAddBookHandler(this);
        }
        mAddBookHandler.remove(shelfName, grBookId);
    }

    private void updateReview(final long reviewId,
                              final boolean isRead,
                              @Nullable final String readAt,
                              @SuppressWarnings("SameParameterValue") @Nullable final String review,
                              final int rating)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        if (mReviewUpdater == null) {
            mReviewUpdater = new ReviewUpdateHandler(this);
        }
        mReviewUpdater.update(reviewId, isRead, readAt, review, rating);
    }

    /**
     * Wrapper to send an entire book, including shelves, to Goodreads.
     *
     * @param db            DB connection
     * @param bookCursorRow single book to send
     *
     * @return Disposition of book
     */
    @NonNull
    public ExportDisposition sendOneBook(@NonNull final DAO db,
                                         @NonNull final BookCursorRow bookCursorRow)
            throws AuthorizationException,
                   IOException,
                   BookNotFoundException {

        /* summary what the cursor row has to have:
         * - id
         * - goodreads id
         * - isbn
         * - read
         * - readEndDate
         * - rating
         */

        long bookId = bookCursorRow.getId();
        Bundle grBook = null;

        // Get the list of shelves from goodreads. This is cached per instance of GoodreadsManager.
        GoodreadsBookshelves grShelfList = getShelves();

        // Get the book ISBN
        String isbn = bookCursorRow.getIsbn();
        long grId;

        // See if the book has a goodreads ID and if it is valid.
        try {
            grId = bookCursorRow.getGoodreadsBookId();
            if (grId != 0) {
                // Get the book details to make sure we have a valid book ID
                grBook = getBookById(grId);
            }
        } catch (@NonNull final BookNotFoundException | AuthorizationException | IOException e) {
            grId = 0;
        }

        boolean isNew = grId == 0;

        if (grId == 0 && !isbn.isEmpty()) {

            if (!ISBN.isValid(isbn)) {
                return ExportDisposition.notFound;
            }

            try {
                // Get the book details using ISBN
                grBook = getBookByIsbn(isbn);
                if (grBook.containsKey(ShowBookFieldNames.BOOK_ID)) {
                    grId = grBook.getLong(ShowBookFieldNames.BOOK_ID);
                }

                // If we got an ID, save it against the book
                if (grId != 0) {
                    db.setGoodreadsBookId(bookId, grId);
                }
            } catch (@NonNull final BookNotFoundException e) {
                return ExportDisposition.notFound;
            } catch (@NonNull final IOException e) {
                return ExportDisposition.networkError;
            }
        }

        // If we found a goodreads book, update it
        if (grId == 0) {
            return ExportDisposition.noIsbn;
        } else {

            long reviewId = 0;

            // Get the review ID if we have the book details. For new books, it will not be present.
            if (!isNew && grBook.containsKey(ShowBookFieldNames.REVIEW_ID)) {
                reviewId = grBook.getLong(ShowBookFieldNames.REVIEW_ID);
            }

            // Lists of shelf names and our best guess at the goodreads canonical name
            List<String> shelves = new ArrayList<>();
            List<String> canonicalShelves = new ArrayList<>();

            // Build the list of shelves that we have in the local database for the book
            int exclusiveCount = 0;
            for (Bookshelf bookshelf : db.getBookshelvesByBookId(bookId)) {
                final String bookshelfName = bookshelf.getName();
                shelves.add(bookshelfName);

                final String canonicalShelfName = canonicalizeBookshelfName(bookshelfName);
                canonicalShelves.add(canonicalShelfName);

                // Count how many of these shelves are exclusive in goodreads.
                if (grShelfList.isExclusive(canonicalShelfName)) {
                    exclusiveCount++;
                }
            }

            // If no exclusive shelves are specified, then add pseudo-shelf to match goodreads
            // because review.update does not seem to update them properly
            if (exclusiveCount == 0) {
                String pseudoShelf;
                if (bookCursorRow.isRead()) {
                    //TEST: use a resource ? or is this a Goodreads string ?
                    pseudoShelf = "Read";
                } else {
                    pseudoShelf = "To Read";
                }
                if (!shelves.contains(pseudoShelf)) {
                    shelves.add(pseudoShelf);
                    canonicalShelves.add(canonicalizeBookshelfName(pseudoShelf));
                }
            }

            // Get the names of the shelves the book is currently on at Goodreads
            List<String> grShelves = null;
            if (!isNew && grBook.containsKey(ShowBookFieldNames.SHELVES)) {
                grShelves = grBook.getStringArrayList(ShowBookFieldNames.SHELVES);
            }
            // not in info, or failed to get
            if (grShelves == null) {
                grShelves = new ArrayList<>();
            }

            // Remove from any shelves from goodreads that are not in our local list
            for (String grShelf : grShelves) {
                if (!canonicalShelves.contains(grShelf)) {
                    try {
                        // Goodreads does not seem to like removing books from the special shelves.
                        if (!(grShelfList.isExclusive(grShelf))) {
                            removeBookFromShelf(grShelf, grId);
                        }
                    } catch (@NonNull final BookNotFoundException e) {
                        // Ignore for now; probably means book not on shelf anyway
                    } catch (@NonNull final AuthorizationException | IOException e) {
                        return ExportDisposition.error;
                    }
                }
            }

            // Add shelves to goodreads if they are not currently there
            for (String shelf : shelves) {
                // Get the name the shelf will have at goodreads
                final String canonicalShelfName = canonicalizeBookshelfName(shelf);
                // Can only sent canonical shelf names if the book is on 0 or 1 of them.
                boolean okToSend = exclusiveCount < 2
                        || !grShelfList.isExclusive(canonicalShelfName);

                if (okToSend && !grShelves.contains(canonicalShelfName)) {
                    try {
                        reviewId = addBookToShelf(shelf, grId);
                    } catch (@NonNull final BookNotFoundException | IOException | AuthorizationException e) {
                        return ExportDisposition.error;
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
                    reviewId = addBookToShelf("Default", grId);
                } catch (@NonNull final BookNotFoundException | IOException | AuthorizationException e) {
                    return ExportDisposition.error;
                }
            }
            // Now update the remaining review details.
            try {
                // Do not sync Notes<->Review. We will add a 'Review' field later.
                //updateReview(reviewId, books.isRead(), books.getReadEnd(),
                //                  books.getNotes(), ((int)books.getRating()) );
                // -> 'notes' has been disabled from the SQL.
                updateReview(reviewId,
                             bookCursorRow.isRead(),
                             bookCursorRow.getReadEnd(),
                             null,
                             (int) bookCursorRow.getRating());

            } catch (@NonNull final BookNotFoundException e) {
                return ExportDisposition.error;
            }

            return ExportDisposition.sent;
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
                         final /* not supported */ boolean fetchThumbnail)
            throws IOException, AuthorizationException {

        try {
            // getBookByIsbn will check on isbn being valid.
            if (isbn != null && !isbn.isEmpty()) {
                return getBookByIsbn(isbn);

            } else if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
                List<GoodreadsWork> result = new SearchBooksApiHandler(this)
                        .search(author + ' ' + title);

                if (!result.isEmpty()) {
                    return getBookById(result.get(0).bookId);
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
                              @Nullable final ImageSizes size) {
        if (!hasValidCredentials()) {
            return null;
        }
        return SearchEngine.getCoverImageFallback(this, isbn);
    }

    /**
     * Wrapper to search for a book.
     *
     * @return Bundle of GoodreadsWork objects
     */
    @NonNull
    private Bundle getBookById(final long bookId)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        if (bookId != 0) {
            ShowBookByIdApiHandler api = new ShowBookByIdApiHandler(this);
            // Run the search
            return api.get(bookId, true);
        } else {
            throw new IllegalArgumentException("No bookId");
        }
    }

    /**
     * Wrapper to search for a book.
     *
     * @return Bundle of GoodreadsWork objects
     */
    @NonNull
    private Bundle getBookByIsbn(@Nullable final String isbn)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        if (ISBN.isValid(isbn)) {
            ShowBookByIsbnApiHandler api = new ShowBookByIsbnApiHandler(this);
            // Run the search
            return api.get(isbn, true);
        } else {
            throw new ISBN.IsbnInvalidException(isbn);
        }
    }

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return !noKey() && NetworkUtils.isAlive(getBaseURL());
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.goodreads;
    }

    @AnyThread
    public boolean noKey() {
        boolean noKey = DEV_KEY.isEmpty() || DEV_SECRET.isEmpty();
        if (noKey) {
            Logger.warn(this, "noKey", "Goodreads dev key not available");
        }
        return noKey;
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
     *
     * @author Philip Warner
     */
    @WorkerThread
    public boolean hasValidCredentials() {
        // If credentials have already been accepted, don't re-check.
        return sHasValidCredentials || validateCredentials();
    }

    /**
     * Check if the current credentials (from prefs) are valid, and cache the result.
     * <p>
     * Network access.
     *
     * @author Philip Warner
     */
    @WorkerThread
    private boolean validateCredentials() {
        // Get the stored token values from prefs, and setup the consumer
        sAccessToken = App.getPrefs().getString(ACCESS_TOKEN, "");
        sAccessSecret = App.getPrefs().getString(ACCESS_SECRET, "");

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
     * Request authorization for this application, from the current user,
     * by going to the OAuth web page.
     *
     * @author Philip Warner
     */
    public void requestAuthorization()
            throws IOException,
                   AuthorizationException {

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

        } catch (@NonNull final OAuthNotAuthorizedException e) {
            throw new AuthorizationException(R.string.goodreads, e);
        }

        //TEST: double check if this ever gives issues!
        if (!authUrl.startsWith("http://") && !authUrl.startsWith("https://")) {
            // Make a valid URL for the parser (some come back without a schema)
            authUrl = "http://" + authUrl;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS) {
                Logger.warn(this, "requestAuthorization", "replacing with: " + authUrl);
            }
        }

        // Save the token; this object may well be destroyed before the web page has returned.
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
     * @author Philip Warner
     */
    @WorkerThread
    public void handleAuthentication()
            throws AuthorizationException,
                   IOException {

        // Get the saved request tokens.
        String tokenString = App.getPrefs().getString(REQUEST_TOKEN, null);
        String secretString = App.getPrefs().getString(REQUEST_SECRET, null);

        if (tokenString == null || tokenString.isEmpty()
                || secretString == null || secretString.isEmpty()) {
            throw new IllegalStateException("No request token found in preferences");
        }

        // Update the consumer.
        mConsumer.setTokenWithSecret(tokenString, secretString);

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get the access token
        try {
            mProvider.retrieveAccessToken(mConsumer, null);
        } catch (@NonNull final OAuthNotAuthorizedException e) {
            throw new AuthorizationException(R.string.goodreads, e);

        } catch (@NonNull final OAuthMessageSignerException
                | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new IOException(e);
        }

        // Cache and save the token
        sAccessToken = mConsumer.getToken();
        sAccessSecret = mConsumer.getTokenSecret();

        App.getPrefs().edit()
           .putString(ACCESS_TOKEN, sAccessToken)
           .putString(ACCESS_SECRET, sAccessSecret)
           .apply();
    }

    /**
     * Enum to handle possible results of sending a book to goodreads.
     */
    public enum ExportDisposition {
        error, sent, noIsbn, notFound, networkError
    }

    private static class GoodreadsBookshelf {

        @NonNull
        private final Bundle mBundle;

        GoodreadsBookshelf(@NonNull final Bundle bundle) {
            mBundle = bundle;
        }

        @NonNull
        String getName() {
            //noinspection ConstantConditions
            return mBundle.getString(GrBookshelfFields.NAME);
        }

        boolean isExclusive() {
            return mBundle.getBoolean(GrBookshelfFields.EXCLUSIVE);
        }
    }

    private static class GoodreadsBookshelves {

        @NonNull
        private final Map<String, GoodreadsBookshelf> mList;

        GoodreadsBookshelves(@NonNull final Map<String, GoodreadsBookshelf> list) {
            mList = list;
        }

        boolean isExclusive(@Nullable final String name) {
            GoodreadsBookshelf shelf = mList.get(name);
            return shelf != null && shelf.isExclusive();
        }
    }
}

