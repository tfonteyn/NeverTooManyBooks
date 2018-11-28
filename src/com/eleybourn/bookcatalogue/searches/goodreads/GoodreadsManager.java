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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsAuthorizationResultCheckTask;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.searches.goodreads.api.AuthUserApiHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.api.BookshelfListApiHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.api.BookshelfListApiHandler.BookshelfListFieldNames;
import com.eleybourn.bookcatalogue.searches.goodreads.api.IsbnToId;
import com.eleybourn.bookcatalogue.searches.goodreads.api.ReviewUpdateHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.api.SearchBooksApiHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.api.ShelfAddBookHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames;
import com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookByIdApiHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookByIsbnApiHandler;
import com.eleybourn.bookcatalogue.tasks.simpletasks.Terminator;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Event;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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

import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

/**
 * Class to wrap all Goodreads API calls and manage an API connection.
 *
 * ENHANCE: Link an {@link Event} to a book, and display in book list with exclamation triangle overwriting cover.
 *
 * @author Philip Warner
 */
public class GoodreadsManager {

    /**
     * website & Root URL for API calls. Right now, identical, but this leaves future changes easier.
     */
    public static final String WEBSITE = "https://www.goodreads.com";
    public static final String BASE_URL = WEBSITE;

    /** last time we synced with Goodreads */
    private static final String PREFS_LAST_SYNC_DATE = "GoodreadsManager.LastSyncDate";

    /* meta data keys in manifest */
    private static final String GOODREADS_DEV_KEY = "goodreads.dev_key";
    private static final String GOODREADS_DEV_SECRET = "goodreads.dev_secret";

    /* authorization tokens */
    private static final String ACCESS_TOKEN = "GoodReads.AccessToken.Token";
    private static final String ACCESS_SECRET = "GoodReads.AccessToken.Secret";
    private static final String REQUEST_TOKEN = "GoodReads.RequestToken.Token";
    private static final String REQUEST_SECRET = "GoodReads.RequestToken.Secret";
    /** the developer keys */
    private final static String DEV_KEY = BookCatalogueApp.getManifestString(GOODREADS_DEV_KEY);
    private final static String DEV_SECRET = BookCatalogueApp.getManifestString(GOODREADS_DEV_SECRET);

    /** the call back Intent URL. Must match the intent filter(s) setup in the manifest with Intent.ACTION_VIEW */
    private final static String AUTHORIZATION_CALLBACK = BookCatalogueApp.getAppContext().getPackageName() + "://goodreadsauth";
    /** to control access to mLastRequestTime, we synchronize on this final Object */
    @NonNull
    private static final Object LAST_REQUEST_TIME_LOCK = new Object();
    /** Set to true when the credentials have been successfully verified. */
    private static boolean mHasValidCredentials = false;
    /** Cached when credentials have been verified. */
    @Nullable
    private static String mAccessToken = null;
    @Nullable
    private static String mAccessSecret = null;
    /** Local copy of user name retrieved when the credentials were verified */
    @Nullable
    private static String mUsername = null;
    /** Local copy of user id retrieved when the credentials were verified */
    private static long mUserId = 0;
    /**
     * Stores the last time an API request was made to avoid breaking API rules.
     * Only modify this value from inside a synchronized (LAST_REQUEST_TIME_LOCK)
     */
    @NonNull
    private static Long mLastRequestTime = 0L;

    /** OAuth helpers */
    private final CommonsHttpOAuthConsumer mConsumer;
    /** OAuth helpers */
    private final OAuthProvider mProvider;

    @Nullable
    private IsbnToId mIsbnToId = null;
    @Nullable
    private GoodreadsBookshelves mBookshelfList = null;
    @Nullable
    private ShelfAddBookHandler mAddBookHandler = null;
    @Nullable
    private ReviewUpdateHandler mReviewUpdater = null;

    /**
     * Standard constructor
     *
     * @author Philip Warner
     */
    public GoodreadsManager() {

        mConsumer = new CommonsHttpOAuthConsumer(DEV_KEY, DEV_SECRET);
        mProvider = new CommonsHttpOAuthProvider(
                BASE_URL + "/oauth/request_token",
                BASE_URL + "/oauth/access_token",
                BASE_URL + "/oauth/authorize");

        if (hasCredentials()) {
            mConsumer.setTokenWithSecret(mAccessToken, mAccessSecret);
        }
    }

    /**
     * Use mLastRequestTime to determine how long until the next request is allowed; and
     * update mLastRequestTime this needs to be synchronized across threads.
     *
     * Note that as a result of this approach mLastRequestTime may in fact be
     * in the future; callers to this routine effectively allocate time slots.
     *
     * This method will sleep() until it can make a request; if ten threads call this
     * simultaneously, one will return immediately, one will return 1 second later, another
     * two seconds etc.
     */
    private static void waitUntilRequestAllowed() {
        long now = System.currentTimeMillis();
        long wait;
        synchronized (LAST_REQUEST_TIME_LOCK) {
            wait = 1000 - (now - mLastRequestTime);

            // mLastRequestTime must be updated while synchronized. As soon as this
            // block is left, another block may perform another update.
            if (wait < 0) {
                wait = 0;
            }
            mLastRequestTime = now + wait;
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Create canonical representation based on the best guess as to the goodreads rules.
     */
    public static String canonicalizeBookshelfName(@NonNull String name) {
        StringBuilder canonical = new StringBuilder();
        name = name.toLowerCase();
        for (int i = 0; i < name.length(); i++) {
            Character c = name.charAt(i);
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
     * @param resultKey key to write to formatted date to
     */
    @SuppressLint("DefaultLocale")
    @Nullable
    public static String buildDate(final @NonNull Bundle data,
                                   final @NonNull String yearKey,
                                   final @NonNull String monthKey,
                                   final @NonNull String dayKey,
                                   final @Nullable String resultKey) {
        String date = null;
        if (data.containsKey(yearKey)) {
            date = String.format("%04d", data.getLong(yearKey));
            if (data.containsKey(monthKey)) {
                date += "-" + String.format("%02d", data.getLong(monthKey));
                if (data.containsKey(dayKey)) {
                    date += "-" + String.format("%02d", data.getLong(dayKey));
                }
            }
            if (resultKey != null && date != null && !date.isEmpty()) {
                data.putString(resultKey, date);
            }
        }
        return date;
    }

    /**
     * Get the date at which the last goodreads synchronization was run
     *
     * @return Last date
     */
    @Nullable
    public static Date getLastSyncDate() {
        String last = BookCatalogueApp.getStringPreference(PREFS_LAST_SYNC_DATE, null);
        if (last == null || last.isEmpty()) {
            return null;
        } else {
            try {
                return DateUtils.parseDate(last);
            } catch (Exception e) {
                Logger.error(e);
                return null;
            }
        }
    }

    /**
     * Set the date at which the last goodreads synchronization was run
     *
     * @param d Last date
     */
    public static void setLastSyncDate(final @Nullable Date d) {
        BookCatalogueApp.getSharedPreferences().edit().putString(PREFS_LAST_SYNC_DATE, d == null ? null : DateUtils.utcSqlDateTime(d)).apply();
    }

    /**
     * Clear the credentials from the preferences and local cache
     */
    public static void forgetCredentials() {
        mAccessToken = "";
        mAccessSecret = "";
        mHasValidCredentials = false;
        // Get the stored token values from prefs, and setup the consumer if present
        SharedPreferences.Editor ed = BookCatalogueApp.getSharedPreferences().edit();
        ed.putString(ACCESS_TOKEN, "");
        ed.putString(ACCESS_SECRET, "");
        ed.apply();
    }

    /**
     * Check if the access tokens are available (not if they are valid).
     * No network access.
     */
    public static boolean hasCredentials() {
        if (mAccessToken != null && !mAccessToken.isEmpty()
                && mAccessSecret != null && !mAccessSecret.isEmpty()) {
            return true;
        }

        // Get the stored token values from prefs, and setup the consumer if present

        mAccessToken = BookCatalogueApp.getStringPreference(ACCESS_TOKEN, null);
        mAccessSecret = BookCatalogueApp.getStringPreference(ACCESS_SECRET, null);

        return !(mAccessToken == null || mAccessToken.isEmpty() || mAccessSecret == null || mAccessSecret.isEmpty());
    }

    /**
     * Create an HttpClient with specifically set buffer sizes to deal with
     * potentially exorbitant settings on some HTC handsets.
     */
    @NonNull
    private HttpClient newHttpClient() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 30000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpConnectionParams.setLinger(params, 0);
        HttpConnectionParams.setTcpNoDelay(params, false);
        return new DefaultHttpClient(params);
    }

    /**
     * Utility routine called to sign a request and submit it then pass it off to a parser.
     *
     * @author Philip Warner
     */
    public void execute(final @NonNull HttpUriRequest request,
                        final @Nullable DefaultHandler requestHandler,
                        final boolean requiresSignature) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            IOException, NotAuthorizedException, BookNotFoundException, NetworkException {

        // Sign the request
        if (requiresSignature) {
            mConsumer.setTokenWithSecret(mAccessToken, mAccessSecret);
            mConsumer.sign(request);
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

        } catch (IOException e) {
            throw new NetworkException(e);
        }

        HttpEntity entity = response.getEntity();
        if (DEBUG_SWITCHES.GOODREADS && DEBUG_SWITCHES.DUMP_HTTP_RESPONSE && BuildConfig.DEBUG) {
            entity.writeTo(System.out);
            if (!entity.isRepeatable()) {
                return;
            }
        }
        is = entity.getContent();

        int code = response.getStatusLine().getStatusCode();
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED: {
                parseResponse(is, requestHandler);
                break;
            }
            case HttpURLConnection.HTTP_UNAUTHORIZED: {
                mHasValidCredentials = false;
                throw new NotAuthorizedException();
            }
            case HttpURLConnection.HTTP_NOT_FOUND: {
                throw new BookNotFoundException();
            }
            default:
                throw new NetworkException("Unexpected status code from API: " +
                        response.getStatusLine().getStatusCode() + "/" + response.getStatusLine().getReasonPhrase());
        }
    }

    /**
     * Utility routine called to sign a request and submit it then return the raw text output.
     *
     * @author Philip Warner
     */
    @NonNull
    public String executeRaw(final @NonNull HttpUriRequest request) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            IOException, NotAuthorizedException, BookNotFoundException, NetworkException {

        // Sign the request
        mConsumer.setTokenWithSecret(mAccessToken, mAccessSecret);
        mConsumer.sign(request);

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get a new client
        HttpClient httpClient = newHttpClient();

        // Submit the request then process result.
        HttpResponse response;
        try {
            response = httpClient.execute(request);
        } catch (IOException e) {
            throw new NetworkException(e);
        }

        StringBuilder html = new StringBuilder();
        HttpEntity e = response.getEntity();
        if (e != null) {
            InputStream in = e.getContent();
            if (in != null) {
                while (true) {
                    int i = in.read();
                    if (i == -1) {
                        break;
                    }
                    html.append((char) (i));
                }
            }
        }

        int code = response.getStatusLine().getStatusCode();
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                return html.toString();

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                mHasValidCredentials = false;
                throw new NotAuthorizedException();

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new BookNotFoundException();

            default:
                throw new NetworkException("Unexpected status code from API: " +
                        response.getStatusLine().getStatusCode() + "/" + response.getStatusLine().getReasonPhrase());
        }
    }

    /**
     * Utility routine called to pass a response off to a parser.
     *
     * @author Philip Warner
     */
    private void parseResponse(final @NonNull InputStream is,
                               final @Nullable DefaultHandler requestHandler) throws IOException {
        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();

        try {
            SAXParser parser = factory.newSAXParser();
            parser.parse(is, requestHandler);

            // only catch exceptions related to the parsing, others will be caught by the caller.
        } catch (@NonNull ParserConfigurationException | SAXException e) {
            Logger.error(e);
        }
    }

    @SuppressWarnings("unused")
    @Nullable
    public String getUsername() {
        if (!mHasValidCredentials) {
            throw new IllegalStateException("Goodreads credentials need to be validated before accessing user data");
        }

        return mUsername;
    }

    public long getUserId() {
        if (!mHasValidCredentials) {
            throw new IllegalStateException("Goodreads credentials need to be validated before accessing user data");
        }

        return mUserId;
    }

    /**
     * Wrapper to call ISBN->ID API
     */
    @SuppressWarnings("unused")
    public long isbnToId(final @NonNull String isbn) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
        if (mIsbnToId == null) {
            mIsbnToId = new IsbnToId(this);
        }
        return mIsbnToId.isbnToId(isbn);
    }

    @NonNull
    private GoodreadsBookshelves getShelves() throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
        if (mBookshelfList == null) {
            Map<String, GoodreadsBookshelf> map = new HashMap<>();
            BookshelfListApiHandler handler = new BookshelfListApiHandler(this);
            int page = 1;
            while (true) {
                Bundle result = handler.run(page);
                List<Bundle> shelves = result.getParcelableArrayList(BookshelfListFieldNames.SHELVES);
                if (shelves == null || shelves.isEmpty()) {
                    break;
                }

                for (Bundle b : shelves) {
                    GoodreadsBookshelf shelf = new GoodreadsBookshelf(b);
                    map.put(shelf.getName(), shelf);
                }

                if (result.getLong(BookshelfListFieldNames.END) >= result.getLong(BookshelfListFieldNames.TOTAL)) {
                    break;
                }

                page++;
            }
            mBookshelfList = new GoodreadsBookshelves(map);
        }
        return mBookshelfList;
    }

    /**
     * Wrapper to call API to add book to shelf
     */
    private long addBookToShelf(final @NonNull String shelfName,
                                final long grBookId) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
        if (mAddBookHandler == null) {
            mAddBookHandler = new ShelfAddBookHandler(this);
        }
        return mAddBookHandler.add(shelfName, grBookId);
    }

    /**
     * Wrapper to call API to remove a book from a shelf
     */
    private void removeBookFromShelf(final @NonNull String shelfName,
                                     final long grBookId) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, NetworkException, IOException {
        if (mAddBookHandler == null) {
            mAddBookHandler = new ShelfAddBookHandler(this);
        }
        mAddBookHandler.remove(shelfName, grBookId);
    }

    private void updateReview(final long reviewId,
                              final boolean isRead,
                              final @Nullable String readAt,
                              @SuppressWarnings("SameParameterValue") final @Nullable String review,
                              final int rating) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
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
    public ExportDisposition sendOneBook(final @NonNull CatalogueDBAdapter db,
                                  final @NonNull BookRowView bookCursorRow) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, IOException, NetworkException, BookNotFoundException {
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
                grBook = this.getBookById(grId);
            }
        } catch (Exception e) {
            grId = 0;
        }

        boolean isNew = (grId == 0);

        if (grId == 0 && !isbn.isEmpty()) {

            if (!IsbnUtils.isValid(isbn)) {
                return ExportDisposition.notFound;
            }

            try {
                // Get the book details using ISBN
                grBook = this.getBookByIsbn(isbn);
                if (grBook.containsKey(ShowBookFieldNames.BOOK_ID)) {
                    grId = grBook.getLong(ShowBookFieldNames.BOOK_ID);
                }

                // If we got an ID, save it against the book
                if (grId != 0) {
                    db.setGoodreadsBookId(bookId, grId);
                }
            } catch (BookNotFoundException e) {
                return ExportDisposition.notFound;
            } catch (NetworkException e) {
                return ExportDisposition.networkError;
            }
        }

        // If we found a goodreads book, update it
        long reviewId = 0;
        if (grId != 0) {
            // Get the review ID if we have the book details. For new books, it will not be present.
            if (!isNew && grBook.containsKey(ShowBookFieldNames.REVIEW_ID)) {
                reviewId = grBook.getLong(ShowBookFieldNames.REVIEW_ID);
            }

            // Lists of shelf names and our best guess at the goodreads canonical name
            List<String> shelves = new ArrayList<>();
            List<String> canonicalShelves = new ArrayList<>();

            // Build the list of shelves that we have in the local database for the book
            int exclusiveCount = 0;
            try (Cursor shelfCsr = db.fetchBookshelvesByBookId(bookId)) {
                int shelfCol = shelfCsr.getColumnIndexOrThrow(DatabaseDefinitions.DOM_BOOKSHELF.name);
                // Collect all shelf names for this book
                while (shelfCsr.moveToNext()) {
                    final String shelfName = shelfCsr.getString(shelfCol);
                    final String canonicalShelfName = canonicalizeBookshelfName(shelfName);
                    shelves.add(shelfName);
                    canonicalShelves.add(canonicalShelfName);
                    // Count how many of these shelves are exclusive in goodreads.
                    if (grShelfList.isExclusive(canonicalShelfName)) {
                        exclusiveCount++;
                    }
                }
            }

            // If no exclusive shelves are specified, then add pseudo-shelf to match goodreads because
            // review.update does not seem to update them properly
            if (exclusiveCount == 0) {
                String pseudoShelf;
                if (bookCursorRow.isRead()) {
                    pseudoShelf = "Read"; //TEST: use a resource ? or is this a Goodreads string ?
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
                            this.removeBookFromShelf(grShelf, grId);
                        }
                    } catch (BookNotFoundException e) {
                        // Ignore for now; probably means book not on shelf anyway
                    } catch (Exception ignore) {
                        return ExportDisposition.error;
                    }
                }
            }

            // Add shelves to goodreads if they are not currently there
            for (String shelf : shelves) {
                // Get the name the shelf will have at goodreads
                final String canonicalShelfName = canonicalizeBookshelfName(shelf);
                // Can only sent canonical shelf names if the book is on 0 or 1 of them.
                boolean okToSend = (exclusiveCount < 2 || !grShelfList.isExclusive(canonicalShelfName));
                if (okToSend && !grShelves.contains(canonicalShelfName)) {
                    try {
                        reviewId = this.addBookToShelf(shelf, grId);
                    } catch (Exception e) {
                        return ExportDisposition.error;
                    }
                }
            }

            /* We should be safe always updating here because:
             * - all books that are already added have a review ID, which we would have got from the bundle
             * - all new books will be added to at least one shelf, which will have returned a review ID.
             * But, just in case, we check the review ID, and if 0, we add the book to the 'Default' shelf.
             */
            if (reviewId == 0) {
                try {
                    reviewId = this.addBookToShelf("Default", grId); //TEST: goodreads name!?
                } catch (Exception e) {
                    return ExportDisposition.error;
                }
            }
            // Now update the remaining review details.
            try {
                // Do not sync Notes<->Review. We will add a 'Review' field later.
                //this.updateReview(reviewId, books.isRead(), books.getReadEnd(), books.getNotes(), ((int)books.getRating()) );
                this.updateReview(reviewId, bookCursorRow.isRead(), bookCursorRow.getReadEnd(), null, ((int) bookCursorRow.getRating()));
            } catch (BookNotFoundException e) {
                return ExportDisposition.error;
            }

            return ExportDisposition.sent;
        } else {
            return ExportDisposition.noIsbn;
        }

    }

    /**
     * Wrapper to provide our ManagedSearchTask with a uniform call similar to others.
     * (except we return the data)
     */
    public Bundle search(final @NonNull String isbn,
                         final @NonNull String author,
                         final @NonNull String title) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
        // getBookByIsbn will check on isbn being valid.
        if (!isbn.isEmpty()) {
            return getBookByIsbn(isbn);
        } else {
            // search will check on non-empty args
            List<GoodreadsWork> list = search(author + " " + title);
            if (list.size() > 0) {
                GoodreadsWork w = list.get(0);
                return getBookById(w.bookId);
            } else {
                return new Bundle();
            }
        }
    }

    /**
     * Wrapper to search for a book.
     *
     * @param query String to search for
     *
     * @return List of GoodreadsWork objects
     */
    @NonNull
    public List<GoodreadsWork> search(final @NonNull String query) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, IOException, NetworkException {

        if (!query.trim().isEmpty()) {
            SearchBooksApiHandler searcher = new SearchBooksApiHandler(this);
            return searcher.search(query);
        } else {
            throw new IllegalArgumentException("No search criteria specified");
        }

    }

    /**
     * Wrapper to search for a book.
     *
     * @return Bundle of GoodreadsWork objects
     */
    @NonNull
    private Bundle getBookById(final long bookId) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
        if (bookId != 0) {
            ShowBookByIdApiHandler api = new ShowBookByIdApiHandler(this);
            // Run the search
            return api.get(bookId, true);
        } else {
            throw new IllegalArgumentException("No bookId specified");
        }

    }

    /**
     * Wrapper to search for a book.
     *
     * @return Bundle of GoodreadsWork objects
     */
    @NonNull
    private Bundle getBookByIsbn(final @Nullable String isbn) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, IOException, NetworkException {
        if (IsbnUtils.isValid(isbn)) {
            ShowBookByIsbnApiHandler api = new ShowBookByIsbnApiHandler(this);
            // Run the search
            return api.get(isbn, true);
        } else {
            throw new RTE.IsbnInvalidException(isbn);
        }

    }

    /** developer check. */
    public boolean isAvailable() {
        boolean gotKey = !DEV_KEY.isEmpty() && !DEV_SECRET.isEmpty();
        if (!gotKey) {
            Logger.info(this, "Goodreads keys not available");
        }
        return gotKey;
    }

    /**
     * Return the public developer key, used for GET queries.
     *
     * @author Philip Warner
     */
    @NonNull
    public String getDevKey() {
        return DEV_KEY;
    }

    /**
     * Check if the current credentials (either cached or in prefs) are valid.
     * If they have been previously checked and were valid, just use that result.
     *
     * Network access if credentials need to be checked.
     *
     * @author Philip Warner
     */
    public boolean hasValidCredentials() {
        // If credentials have already been accepted, don't re-check.
        return mHasValidCredentials || validateCredentials();
    }

    /**
     * Check if the current credentials (from prefs) are valid, and cache the result.
     *
     * Network access.
     *
     * @author Philip Warner
     */
    private boolean validateCredentials() {
        // Get the stored token values from prefs, and setup the consumer
        mAccessToken = BookCatalogueApp.getStringPreference(ACCESS_TOKEN, "");
        mAccessSecret = BookCatalogueApp.getStringPreference(ACCESS_SECRET, "");

        mConsumer.setTokenWithSecret(mAccessToken, mAccessSecret);

        try {
            AuthUserApiHandler authUserApi = new AuthUserApiHandler(this);

            if (authUserApi.getAuthUser() == 0) {
                return false;
            }

            // Future usage
            mUsername = authUserApi.getUsername();
            mUserId = authUserApi.getUserId();

            // Cache the result to avoid network calls later
            mHasValidCredentials = true;
            return true;

        } catch (Exception e) {
            // Something went wrong. Clear the access token, set credentials as bad
            mHasValidCredentials = false;
            mAccessToken = null;
            return false;
        }
    }

    /**
     * Request authorization for this application, from the current user, by going to the OAuth web page.
     *
     * No network access, as actual auth is done via a browser (with Intent.ACTION_VIEW).
     *
     * @author Philip Warner
     */
    public void requestAuthorization(final @NonNull Context context) throws NetworkException {
        String authUrl;

        // Don't do this; this is just part of OAuth and not the Goodreads API
        //waitUntilRequestAllowed();

        // Get the URL
        try {
            authUrl = mProvider.retrieveRequestToken(mConsumer, AUTHORIZATION_CALLBACK);
        } catch (OAuthCommunicationException e) {
            throw new NetworkException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (DEBUG_SWITCHES.GOODREADS && BuildConfig.DEBUG) {
            Logger.info(this, "requestAuthorization authUrl: " + authUrl);
        }
        //TEST: double check if this ever gives issues!
        if (!authUrl.startsWith("http://") && !authUrl.startsWith("https://")) {
            // Make a valid URL for the parser (some come back without a schema)
            authUrl = "http://" + authUrl;
            if (DEBUG_SWITCHES.GOODREADS && BuildConfig.DEBUG) {
                Logger.info(this, "requestAuthorization: replacing with: " + authUrl);
            }
        }

        // Save the token; this object may well be destroyed before the web page has returned.
        SharedPreferences.Editor ed = BookCatalogueApp.getSharedPreferences().edit();
        ed.putString(REQUEST_TOKEN, mConsumer.getToken());
        ed.putString(REQUEST_SECRET, mConsumer.getTokenSecret());
        ed.apply();

        // Open the web page
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
        context.startActivity(browserIntent);
    }

    /**
     * Called by the callback activity, {@link GoodreadsAuthorizationResultCheckTask},
     * when a request has been authorized by the user.
     *
     * @author Philip Warner
     */
    public void handleAuthentication() throws NotAuthorizedException {
        // Get the saved request tokens.
        String tokenString = BookCatalogueApp.getStringPreference(REQUEST_TOKEN, null);
        String secretString = BookCatalogueApp.getStringPreference(REQUEST_SECRET, null);

        if (tokenString == null || tokenString.isEmpty() || secretString == null || secretString.isEmpty()) {
            throw new IllegalStateException("No request token found in preferences");
        }

        // Update the consumer.
        mConsumer.setTokenWithSecret(tokenString, secretString);

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        waitUntilRequestAllowed();

        // Get the access token
        try {
            mProvider.retrieveAccessToken(mConsumer, null);
        } catch (oauth.signpost.exception.OAuthNotAuthorizedException e) {
            throw new NotAuthorizedException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Cache and save the token
        mAccessToken = mConsumer.getToken();
        mAccessSecret = mConsumer.getTokenSecret();

        SharedPreferences.Editor ed = BookCatalogueApp.getSharedPreferences().edit();
        ed.putString(ACCESS_TOKEN, mAccessToken);
        ed.putString(ACCESS_SECRET, mAccessSecret);
        ed.apply();
    }

    /**
     * Enum to handle possible results of sending a book to goodreads
     */
    public enum ExportDisposition {
        error, sent, noIsbn, notFound, networkError
    }

    private class GoodreadsBookshelf {
        @NonNull
        private final Bundle mBundle;

        GoodreadsBookshelf(final @NonNull Bundle b) {
            mBundle = b;
        }

        @Nullable
        String getName() {
            return mBundle.getString(BookshelfListFieldNames.NAME);
        }

        boolean isExclusive() {
            return mBundle.getBoolean(BookshelfListFieldNames.EXCLUSIVE);
        }
    }

    private class GoodreadsBookshelves {
        @NonNull
        private final Map<String, GoodreadsBookshelf> mBookshelfList;

        GoodreadsBookshelves(final @NonNull Map<String, GoodreadsBookshelf> list) {
            mBookshelfList = list;
        }

        boolean isExclusive(final @Nullable String name) {
            return mBookshelfList.containsKey(name) && mBookshelfList.get(name).isExclusive();
        }
    }
}

