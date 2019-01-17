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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsAuthorizationResultCheckTask;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.BookNotFoundException;
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
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.RTE;

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
import oauth.signpost.exception.OAuthNotAuthorizedException;

/**
 * Class to wrap all Goodreads API calls and manage an API connection.
 *
 * @author Philip Warner
 */
public class GoodreadsManager {

    /**
     * website & Root URL for API calls. Right now, identical,
     * but this leaves future changes easier.
     */
    public static final String WEBSITE = "https://www.goodreads.com";
    public static final String BASE_URL = WEBSITE;
    private static final String TAG = "GoodReads.";
    /** last time we synced with Goodreads. */
    private static final String PREFS_LAST_SYNC_DATE = TAG + "LastSyncDate";
    /* authorization tokens. */
    private static final String ACCESS_TOKEN = TAG + "AccessToken.Token";
    private static final String ACCESS_SECRET = TAG + "AccessToken.Secret";
    private static final String REQUEST_TOKEN = TAG + "RequestToken.Token";
    private static final String REQUEST_SECRET = TAG + "RequestToken.Secret";

    /* meta data keys in manifest. */
    private static final String GOODREADS_DEV_KEY = "goodreads.dev_key";
    private static final String GOODREADS_DEV_SECRET = "goodreads.dev_secret";
    /** the developer keys. */
    private static final String DEV_KEY =
            BookCatalogueApp.getManifestString(GOODREADS_DEV_KEY);
    private static final String DEV_SECRET =
            BookCatalogueApp.getManifestString(GOODREADS_DEV_SECRET);

    /**
     * the call back Intent URL.
     * Must match the intent filter(s) setup in the manifest with Intent.ACTION_VIEW
     * <p>
     * scheme: BookCatalogueApp.getAppContext().getPackageName()
     * host: goodreadsauth
     *
     * <pre>
     *              <intent-filter>
     *                 <action android:name="android.intent.action.VIEW" />
     *
     *                 <category android:name="android.intent.category.DEFAULT" />
     *                 <category android:name="android.intent.category.BROWSABLE" />
     *
     *                 <data
     *                     android:host="goodreadsauth"
     *                     android:scheme="${packageName}" />
     *             </intent-filter>
     * </pre>
     */
    private static final String AUTHORIZATION_CALLBACK =
            BookCatalogueApp.getAppContext().getPackageName() + "://goodreadsauth";

    /** to control access to mLastRequestTime, we synchronize on this final Object. */
    @NonNull
    private static final Object LAST_REQUEST_TIME_LOCK = new Object();
    /** error string. */
    private static final String INVALID_CREDENTIALS =
            "Goodreads credentials need to be validated before accessing user data";

    /** Set to <tt>true</tt> when the credentials have been successfully verified. */
    private static boolean mHasValidCredentials = false;
    /** Cached when credentials have been verified. */
    @Nullable
    private static String mAccessToken;
    @Nullable
    private static String mAccessSecret;
    /** Local copy of user name retrieved when the credentials were verified. */
    @Nullable
    private static String mUsername;
    /** Local copy of user id retrieved when the credentials were verified. */
    private static long mUserId;
    /**
     * Stores the last time an API request was made to avoid breaking API rules.
     * Only modify this value from inside a synchronized (LAST_REQUEST_TIME_LOCK)
     */
    @NonNull
    private static Long mLastRequestTime = 0L;

    /** OAuth helpers. */
    private final CommonsHttpOAuthConsumer mConsumer;
    /** OAuth helpers. */
    private final OAuthProvider mProvider;

    @Nullable
    private IsbnToId mIsbnToId;
    @Nullable
    private GoodreadsBookshelves mBookshelfList;
    @Nullable
    private ShelfAddBookHandler mAddBookHandler;
    @Nullable
    private ReviewUpdateHandler mReviewUpdater;

    /**
     * Standard constructor.
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
     * <p>
     * Note that as a result of this approach mLastRequestTime may in fact be
     * in the future; callers to this routine effectively allocate time slots.
     * <p>
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
            char c = name.charAt(i);
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

        String last = Prefs.getPrefs().getString(PREFS_LAST_SYNC_DATE, null);
        if (last == null || last.isEmpty()) {
            return null;
        } else {
            try {
                return DateUtils.parseDate(last);
            } catch (RuntimeException e) {
                Logger.error(e);
                return null;
            }
        }
    }

    /**
     * Set the date at which the last goodreads synchronization was run.
     *
     * @param date Last date
     */
    public static void setLastSyncDate(@Nullable final Date date) {
        SharedPreferences.Editor ed = Prefs.getPrefs().edit();
        if (date == null) {
            ed.putString(PREFS_LAST_SYNC_DATE, null);
        } else {
            ed.putString(PREFS_LAST_SYNC_DATE, DateUtils.utcSqlDateTime(date));
        }
        ed.apply();
    }

    /**
     * Clear the credentials from the preferences and local cache.
     */
    public static void forgetCredentials() {

        mAccessToken = "";
        mAccessSecret = "";
        mHasValidCredentials = false;
        // Get the stored token values from prefs, and setup the consumer if present
        SharedPreferences.Editor ed = Prefs.getPrefs().edit();
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
        mAccessToken = Prefs.getPrefs().getString(ACCESS_TOKEN, null);
        mAccessSecret = Prefs.getPrefs().getString(ACCESS_SECRET, null);

        return !(mAccessToken == null || mAccessToken.isEmpty()
                || mAccessSecret == null || mAccessSecret.isEmpty());
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
     * Sign a request and submit it; then pass it off to a parser.
     *
     * @author Philip Warner
     */
    public void execute(@NonNull final HttpUriRequest request,
                        @Nullable final DefaultHandler requestHandler,
                        final boolean requiresSignature)
            throws IOException,
                   NotAuthorizedException,
                   BookNotFoundException {

        // Sign the request
        if (requiresSignature) {
            mConsumer.setTokenWithSecret(mAccessToken, mAccessSecret);
            try {
                mConsumer.sign(request);
            } catch (OAuthMessageSignerException
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

        } catch (ClientProtocolException e) {
            throw new IOException(e);
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
            case HttpURLConnection.HTTP_CREATED:
                parseResponse(is, requestHandler);
                break;

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                mHasValidCredentials = false;
                throw new NotAuthorizedException();

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new BookNotFoundException();

            default:
                throw new IOException("Unexpected status code from API: "
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
                   NotAuthorizedException,
                   BookNotFoundException {

        // Sign the request
        mConsumer.setTokenWithSecret(mAccessToken, mAccessSecret);
        try {
            mConsumer.sign(request);
        } catch (OAuthMessageSignerException
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
                mHasValidCredentials = false;
                throw new NotAuthorizedException();

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new BookNotFoundException();

            default:
                throw new IOException("Unexpected status code from API: "
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
                               @Nullable final DefaultHandler requestHandler)
            throws IOException {
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
            throw new IllegalStateException(INVALID_CREDENTIALS);
        }
        return mUsername;
    }

    public long getUserId() {
        if (!mHasValidCredentials) {
            throw new IllegalStateException(INVALID_CREDENTIALS);
        }
        return mUserId;
    }

    /**
     * Wrapper to call ISBN->ID API.
     */
    @SuppressWarnings("unused")
    public long isbnToId(@NonNull final String isbn)
            throws NotAuthorizedException,
                   BookNotFoundException,
                   IOException {

        if (mIsbnToId == null) {
            mIsbnToId = new IsbnToId(this);
        }
        return mIsbnToId.isbnToId(isbn);
    }

    @NonNull
    private GoodreadsBookshelves getShelves()
            throws NotAuthorizedException,
                   BookNotFoundException,
                   IOException {

        if (mBookshelfList == null) {
            Map<String, GoodreadsBookshelf> map = new HashMap<>();
            BookshelfListApiHandler handler = new BookshelfListApiHandler(this);
            int page = 1;
            while (true) {
                Bundle result = handler.run(page);
                List<Bundle> shelves = result.getParcelableArrayList(
                        BookshelfListFieldNames.SHELVES);
                if (shelves == null || shelves.isEmpty()) {
                    break;
                }

                for (Bundle b : shelves) {
                    GoodreadsBookshelf shelf = new GoodreadsBookshelf(b);
                    map.put(shelf.getName(), shelf);
                }

                if (result.getLong(BookshelfListFieldNames.END) >= result.getLong(
                        BookshelfListFieldNames.TOTAL)) {
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
     */
    private long addBookToShelf(@NonNull final String shelfName,
                                final long grBookId)
            throws NotAuthorizedException,
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
            throws NotAuthorizedException,
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
            throws NotAuthorizedException,
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
    public ExportDisposition sendOneBook(@NonNull final DBA db,
                                         @NonNull final BookRowView bookCursorRow)
            throws NotAuthorizedException,
                   IOException,
                   BookNotFoundException {

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
        } catch (BookNotFoundException | NotAuthorizedException | IOException e) {
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
            } catch (IOException e) {
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
                int shelfCol = shelfCsr.getColumnIndexOrThrow(
                        DatabaseDefinitions.DOM_BOOKSHELF.name);
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
                            this.removeBookFromShelf(grShelf, grId);
                        }
                    } catch (BookNotFoundException e) {
                        // Ignore for now; probably means book not on shelf anyway
                    } catch (NotAuthorizedException | IOException e) {
                        return ExportDisposition.error;
                    }
                }
            }

            // Add shelves to goodreads if they are not currently there
            for (String shelf : shelves) {
                // Get the name the shelf will have at goodreads
                final String canonicalShelfName = canonicalizeBookshelfName(shelf);
                // Can only sent canonical shelf names if the book is on 0 or 1 of them.
                boolean okToSend = (exclusiveCount < 2 || !grShelfList.isExclusive(
                        canonicalShelfName));
                if (okToSend && !grShelves.contains(canonicalShelfName)) {
                    try {
                        reviewId = this.addBookToShelf(shelf, grId);
                    } catch (BookNotFoundException | IOException | NotAuthorizedException e) {
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
                    reviewId = this.addBookToShelf("Default", grId);
                } catch (BookNotFoundException | IOException | NotAuthorizedException e) {
                    return ExportDisposition.error;
                }
            }
            // Now update the remaining review details.
            try {
                // Do not sync Notes<->Review. We will add a 'Review' field later.
                //this.updateReview(reviewId, books.isRead(), books.getReadEnd(),
                //                  books.getNotes(), ((int)books.getRating()) );
                this.updateReview(reviewId, bookCursorRow.isRead(), bookCursorRow.getReadEnd(),
                                  null, ((int) bookCursorRow.getRating()));

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
    public Bundle search(@NonNull final String isbn,
                         @NonNull final String author,
                         @NonNull final String title)
            throws NotAuthorizedException,
                   BookNotFoundException,
                   IOException {

        // getBookByIsbn will check on isbn being valid.
        if (!isbn.isEmpty()) {
            return getBookByIsbn(isbn);
        } else {
            // search will check on non-empty args
            List<GoodreadsWork> list = search(author + ' ' + title);
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
    public List<GoodreadsWork> search(@NonNull final String query)
            throws NotAuthorizedException,
                   BookNotFoundException,
                   IOException {

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
    private Bundle getBookById(final long bookId)
            throws NotAuthorizedException,
                   BookNotFoundException,
                   IOException {

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
    private Bundle getBookByIsbn(@Nullable final String isbn)
            throws NotAuthorizedException,
                   BookNotFoundException,
                   IOException {

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
     * <p>
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
     * <p>
     * Network access.
     *
     * @author Philip Warner
     */
    private boolean validateCredentials() {
        // Get the stored token values from prefs, and setup the consumer
        mAccessToken = Prefs.getPrefs().getString(ACCESS_TOKEN, "");
        mAccessSecret = Prefs.getPrefs().getString(ACCESS_SECRET, "");

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

        } catch (RuntimeException e) {
            // Something went wrong. Clear the access token, set credentials as bad
            mHasValidCredentials = false;
            mAccessToken = null;
            return false;
        }
    }

    /**
     * Request authorization for this application, from the current user,
     * by going to the OAuth web page.
     * <p>
     * No network access, as actual auth is done via a browser (with Intent.ACTION_VIEW).
     *
     * @author Philip Warner
     */
    public void requestAuthorization(@NonNull final Context context)
            throws IOException,
                   NotAuthorizedException {

        String authUrl;

        // Don't do this; this is just part of OAuth and not the Goodreads API
        //waitUntilRequestAllowed();

        // Get the URL
        try {
            authUrl = mProvider.retrieveRequestToken(mConsumer, AUTHORIZATION_CALLBACK);
        } catch (OAuthMessageSignerException
                | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new IOException(e);
        } catch (OAuthNotAuthorizedException e) {
            throw new NotAuthorizedException(e);
        }

        if (DEBUG_SWITCHES.GOODREADS && BuildConfig.DEBUG) {
            Logger.info(this,
                        "requestAuthorization authUrl: " + authUrl);
        }
        //TEST: double check if this ever gives issues!
        if (!authUrl.startsWith("http://") && !authUrl.startsWith("https://")) {
            // Make a valid URL for the parser (some come back without a schema)
            authUrl = "http://" + authUrl;
            if (DEBUG_SWITCHES.GOODREADS && BuildConfig.DEBUG) {
                Logger.info(this,
                            "requestAuthorization: replacing with: " + authUrl);
            }
        }

        // Save the token; this object may well be destroyed before the web page has returned.
        SharedPreferences.Editor ed = Prefs.getPrefs().edit();
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
    public void handleAuthentication()
            throws NotAuthorizedException,
                   IOException {

        // Get the saved request tokens.
        String tokenString = Prefs.getPrefs().getString(REQUEST_TOKEN, null);
        String secretString = Prefs.getPrefs().getString(REQUEST_SECRET, null);

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
        } catch (OAuthNotAuthorizedException e) {
            throw new NotAuthorizedException(e);

        } catch (OAuthMessageSignerException
                | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new IOException(e);
        }

        // Cache and save the token
        mAccessToken = mConsumer.getToken();
        mAccessSecret = mConsumer.getTokenSecret();

        SharedPreferences.Editor ed = Prefs.getPrefs().edit();
        ed.putString(ACCESS_TOKEN, mAccessToken);
        ed.putString(ACCESS_SECRET, mAccessSecret);
        ed.apply();
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

        GoodreadsBookshelf(@NonNull final Bundle b) {

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

    private static class GoodreadsBookshelves {

        @NonNull
        private final Map<String, GoodreadsBookshelf> mBookshelfList;

        GoodreadsBookshelves(@NonNull final Map<String, GoodreadsBookshelf> list) {

            mBookshelfList = list;
        }

        boolean isExclusive(@Nullable final String name) {

            return mBookshelfList.containsKey(name) && mBookshelfList.get(name).isExclusive();
        }
    }
}

