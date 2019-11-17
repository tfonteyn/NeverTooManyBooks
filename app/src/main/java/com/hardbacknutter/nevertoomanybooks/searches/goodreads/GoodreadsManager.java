/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.goodreads;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import oauth.signpost.http.HttpParameters;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.goodreads.AuthorizationException;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsShelf;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsShelves;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsWork;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.AddBookToShelfApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.AuthUserApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.IsbnToIdApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ReviewEditApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.SearchBooksApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShelvesListApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShowBookApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShowBookApiHandler.ShowBookFieldName;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShowBookByIdApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShowBookByIsbnApiHandler;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;

/**
 * Class to wrap all Goodreads calls and manage a connection.
 */
public class GoodreadsManager
        implements SearchEngine,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByNativeId,
                   SearchEngine.ByText,
                   SearchEngine.CoverByIsbn {

    private static final String TAG = "GoodreadsManager";

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "_GR";
    /** Can only send requests at a throttled speed. */
    @NonNull
    public static final Throttler THROTTLER = new Throttler();
    public static final Locale SITE_LOCALE = Locale.US;
    /** Preferences prefix. */
    public static final String PREF_PREFIX = "goodreads.";
    /**
     * website & Root URL. Right now identical, but this leaves future changes easier.
     */
    static final String WEBSITE = "https://www.goodreads.com";
    public static final String BASE_URL = WEBSITE;
    /**
     * AUTHORIZATION_CALLBACK is the call back Intent URL.
     * Must match the intent filter(s) setup in the manifest with Intent.ACTION_VIEW
     * for this activity.
     * The scheme is hardcoded to avoid confusion between java and android package names.
     * <p>
     * scheme: com.hardbacknutter.nevertoomanybooks
     * host: goodreadsauth
     *
     * <pre>
     *     {@code
     *      <activity
     *          android:name=".goodreads.GoodreadsAuthorizationActivity"
     *          android:launchMode="singleInstance">
     *          <intent-filter>
     *              <action android:name="android.intent.action.VIEW" />
     *
     *              <category android:name="android.intent.category.DEFAULT" />
     *              <category android:name="android.intent.category.BROWSABLE" />
     *              <data
     *                  android:host="goodreadsauth"
     *                  android:scheme="com.hardbacknutter.nevertoomanybooks" />
     *          </intent-filter>
     *      </activity>
     *      }
     * </pre>
     */

    private static final String AUTHORIZATION_CALLBACK =
            "com.hardbacknutter.nevertoomanybooks://goodreadsauth";
    /** Browser url where to send the user to approve access. */
    private static final String AUTHORIZATION_WEBSITE_URL = BASE_URL + "/oauth/authorize?mobile=1";
    /** OAuth url to *request* access. */
    private static final String REQUEST_TOKEN_ENDPOINT_URL = BASE_URL + "/oauth/request_token";
    /** OAuth url to access. */
    private static final String ACCESS_TOKEN_ENDPOINT_URL = BASE_URL + "/oauth/access_token";
    /** Whether to show any Goodreads sync menus at all. */
    private static final String PREFS_SHOW_MENUS = PREF_PREFIX + "showMenu";
    /** Preference that controls display of alert about Goodreads. */
    private static final String PREFS_HIDE_ALERT = PREF_PREFIX + "hide_alert.";
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
    private ReviewEditApiHandler mReviewEditApiHandler;
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
//        mConsumer = new CommonsHttpOAuthConsumer(DEV_KEY, DEV_SECRET);
//        mProvider = new CommonsHttpOAuthProvider(
//                BASE_URL + "/oauth/request_token",
//                BASE_URL + "/oauth/access_token",
//                BASE_URL + "/oauth/authorize?mobile=1");

        // Native
        mConsumer = new DefaultOAuthConsumer(DEV_KEY, DEV_SECRET);
        mProvider = new DefaultOAuthProvider(
                REQUEST_TOKEN_ENDPOINT_URL,
                ACCESS_TOKEN_ENDPOINT_URL,
                AUTHORIZATION_WEBSITE_URL);

        // get the credentials
        hasCredentials();
    }

    /**
     * Check if Goodreads SYNC menus should be shown at all.
     * This does not affect searching on Goodreads.
     *
     * @param context Current context
     *
     * @return {@code true} if menus should be shown
     */
    public static boolean isShowSyncMenus(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PREFS_SHOW_MENUS, true);
    }

    /**
     * View a Book on the web site.
     *
     * @param context Current context
     * @param bookId  site native book id to show
     */
    public static void openWebsite(@NonNull final Context context,
                                   final long bookId) {
        String url = BASE_URL + "/book/show/" + bookId;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @SuppressWarnings("unused")
    static void resetTips(@NonNull final Context context) {
        TipManager.reset(context, PREFS_HIDE_ALERT);
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
    @Nullable
    public static String buildDate(@NonNull final Bundle data,
                                   @NonNull final String yearKey,
                                   @NonNull final String monthKey,
                                   @NonNull final String dayKey,
                                   @Nullable final String resultKey) {

        String date = null;
        if (data.containsKey(yearKey)) {
            date = String.format(Locale.ENGLISH, "%04d", data.getLong(yearKey));
            if (data.containsKey(monthKey)) {
                date += '-' + String.format(Locale.ENGLISH, "%02d", data.getLong(monthKey));
                if (data.containsKey(dayKey)) {
                    date += '-' + String.format(Locale.ENGLISH, "%02d", data.getLong(dayKey));
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
        return DateUtils.parseDate(
                PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                 .getString(PREFS_LAST_SYNC_DATE, null));
    }

    /**
     * Set the date at which the last Goodreads synchronization was run.
     *
     * @param context Current context
     * @param date    Last date
     */
    public static void setLastSyncDate(@NonNull final Context context,
                                       @Nullable final Date date) {
        String dateStr = date != null ? DateUtils.utcSqlDateTime(date) : null;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                         .putString(PREFS_LAST_SYNC_DATE, dateStr).apply();
    }

    /**
     * Clear the credentials from the preferences and local cache.
     *
     * @param context Current context
     */
    static void resetCredentials(@NonNull final Context context) {

        sAccessToken = "";
        sAccessSecret = "";
        sHasValidCredentials = false;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                         .putString(ACCESS_TOKEN, "")
                         .putString(ACCESS_SECRET, "")
                         .apply();
    }

    @AnyThread
    public static boolean hasKey() {
        boolean hasKey = !DEV_KEY.isEmpty() && !DEV_SECRET.isEmpty();
        if (!hasKey) {
            Logger.warn(App.getAppContext(), TAG, "hasKey", "Goodreads dev key not available");
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
    static boolean hasCredentials() {

        if (sAccessToken != null && !sAccessToken.isEmpty()
            && sAccessSecret != null && !sAccessSecret.isEmpty()) {
            return true;
        }

        // Get the stored token values from prefs
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(App.getAppContext());
        sAccessToken = prefs.getString(ACCESS_TOKEN, null);
        sAccessSecret = prefs.getString(ACCESS_SECRET, null);

        return sAccessToken != null && !sAccessToken.isEmpty()
               && sAccessSecret != null && !sAccessSecret.isEmpty();
    }

    @Override
    public boolean promptToRegister(@NonNull final Context context,
                                    final boolean required,
                                    @NonNull final String prefSuffix) {
        if (hasCredentials()) {
            return false;
        }

        final String key = PREFS_HIDE_ALERT + prefSuffix;
        boolean show = required || !PreferenceManager.getDefaultSharedPreferences(context)
                                                     .getBoolean(key, false);

        if (show) {
            Intent intent = new Intent(context, GoodreadsRegistrationActivity.class);
            StandardDialogs.registrationDialog(context, R.string.goodreads,
                                               intent, required, key);
        }
        return show;
    }

    /**
     * Sign a GET request.
     *
     * @param request Request to sign
     *
     * @throws IOException on failure
     */
    public void signGetRequest(@NonNull final HttpURLConnection request)
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

    /**
     * Sign a POST request with the post parameters.
     *
     * @param request      Request to sign
     * @param parameterMap (optional) POST parameters.
     *
     * @throws IOException on failure
     */
    public void signPostRequest(@NonNull final HttpURLConnection request,
                                @Nullable final Map<String, String> parameterMap)
            throws IOException {

        if (parameterMap != null) {
            // https://zewaren.net/oauth-java.html
            // The key to signing the POST fields is to add them as additional parameters,
            // but already percent-encoded; and also to add the realm header.
            HttpParameters parameters = new HttpParameters();
            for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
                // note we need to encode both key and value.
                parameters.put(OAuth.percentEncode(entry.getKey()),
                               OAuth.percentEncode(entry.getValue()));
            }
            parameters.put("realm", request.getURL().toString());

            mConsumer.setAdditionalParameters(parameters);
        }

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
     * Wrapper to call ISBN->ID.
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @SuppressWarnings("unused")
    public long isbnToId(@NonNull final String isbn)
            throws CredentialsException, BookNotFoundException, IOException {

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
            throws CredentialsException, BookNotFoundException, IOException {

        if (mShelvesList == null) {
            ShelvesListApiHandler handler = new ShelvesListApiHandler(this);
            Map<String, GoodreadsShelf> map = handler.getAll();
            mShelvesList = new GoodreadsShelves(map);
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
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    private long addBookToShelf(final long grBookId,
                                @SuppressWarnings("SameParameterValue")
                                @NonNull final String shelfName)
            throws CredentialsException, BookNotFoundException, IOException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(this);
        }
        return mAddBookToShelfApiHandler.add(grBookId, shelfName);
    }

    private long addBookToShelf(final long grBookId,
                                @NonNull final List<String> shelfNames)
            throws CredentialsException, BookNotFoundException, IOException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(this);
        }
        return mAddBookToShelfApiHandler.add(grBookId, shelfNames);
    }

    /**
     * Wrapper to remove a book from a shelf.
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
            throws CredentialsException, BookNotFoundException, IOException {

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
     * @param reviewId     to update
     * @param isRead       Flag to indicate we finished reading this book.
     * @param readStart    (optional) Date when we started reading this book, YYYY-MM-DD format
     * @param readEnd      (optional) Date when we finished reading this book, YYYY-MM-DD format
     * @param rating       Rating 0-5 with 0 == No rating
     * @param privateNotes (optional) Text for the Goodreads PRIVATE notes
     * @param review       (optional) Text for the review, PUBLIC
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
                              @Nullable final String privateNotes,
                              @SuppressWarnings("SameParameterValue")
                              @Nullable final String review)
            throws CredentialsException, BookNotFoundException, IOException {

        if (mReviewEditApiHandler == null) {
            mReviewEditApiHandler = new ReviewEditApiHandler(this);
        }
        mReviewEditApiHandler.update(reviewId, isRead, readStart, readEnd, rating,
                                     privateNotes,
                                     review);
    }

    /**
     * Wrapper to send an entire book, including shelves, to Goodreads.
     * <ul>The bookCursorRow row has to have:
     * <li>{@link DBDefinitions#KEY_PK_ID}</li>
     * <li>{@link DBDefinitions#KEY_EID_GOODREADS_BOOK}</li>
     * <li>{@link DBDefinitions#KEY_ISBN}</li>
     * <li>{@link DBDefinitions#KEY_READ}</li>
     * <li>{@link DBDefinitions#KEY_READ_START}</li>
     * <li>{@link DBDefinitions#KEY_READ_END}</li>
     * <li>{@link DBDefinitions#KEY_RATING}</li>
     * </ul>
     * <p>
     * See {@link DAO#fetchBookForExportToGoodreads}
     *
     * @param db         Database Access
     * @param bookCursor single book to send
     *
     * @return Disposition of book
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    @WorkerThread
    public ExportResult sendOneBook(@NonNull final Context context,
                                    @NonNull final DAO db,
                                    @NonNull final BookCursor bookCursor)
            throws CredentialsException, BookNotFoundException, IOException {

        long bookId = bookCursor.getLong(DBDefinitions.KEY_PK_ID);
        String isbn = bookCursor.getString(DBDefinitions.KEY_ISBN);

        // Get the list of shelves from Goodreads. This is cached per instance of GoodreadsManager.
        GoodreadsShelves grShelfList = getShelves();

        long grBookId;
        Bundle grBook = null;

        // See if the book already has a Goodreads id and if it is valid.
        try {
            grBookId = bookCursor.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);
            if (grBookId != 0) {
                // Get the book details to make sure we have a valid book ID
                grBook = getBookById(context, grBookId, false);
            }
        } catch (@NonNull final CredentialsException | BookNotFoundException | IOException e) {
            grBookId = 0;
        }

        boolean isNew = grBookId == 0;

        // wasn't there, see if we can find it using the ISBN instead.
        if (grBookId == 0 && !isbn.isEmpty()) {
            if (!ISBN.isValid(isbn)) {
                return ExportResult.noIsbn;
            }

            // Get the book details using ISBN
            grBook = getBookByIsbn(context, isbn, false);
            if (grBook.containsKey(DBDefinitions.KEY_EID_GOODREADS_BOOK)) {
                grBookId = grBook.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);
            }

            // If we got an ID, save it against the book
            if (grBookId != 0) {
                db.setGoodreadsBookId(bookId, grBookId);
            }
        }

        // Still nothing ? Give up.
        if (grBookId == 0) {
            return ExportResult.notFound;

        } else {
            // We found a Goodreads book, update it
            long reviewId = 0;

            // Get the review id if we have the book details. For new books, it will not be present.
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

                final String canonicalShelfName =
                        GoodreadsShelf.canonicalizeName(bookshelfName);
                canonicalShelves.add(canonicalShelfName);

                // Count how many of these shelves are exclusive in Goodreads.
                if (grShelfList.isExclusive(canonicalShelfName)) {
                    exclusiveCount++;
                }
            }

            // If no exclusive shelves are specified, add pseudo-shelf to match Goodreads
            // because review.update does not seem to update them properly
            if (exclusiveCount == 0) {
                String pseudoShelf;
                if (bookCursor.getInt(DBDefinitions.KEY_READ) != 0) {
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
                        // Ignore here; probably means the book was not on this shelf anyway
                    }
                }
            }

            // Add shelves to Goodreads if they are not currently there
            List<String> shelvesToAddTo = new ArrayList<>();
            for (String shelf : shelves) {
                // Get the name the shelf will have at Goodreads
                final String canonicalShelfName =
                        GoodreadsShelf.canonicalizeName(shelf);
                // Can only sent canonical shelf names if the book is on 0 or 1 of them.
                boolean okToSend = exclusiveCount < 2
                                   || !grShelfList.isExclusive(canonicalShelfName);

                if (okToSend && !grShelves.contains(canonicalShelfName)) {
                    shelvesToAddTo.add(shelf);
                }
            }
            if (!shelvesToAddTo.isEmpty()) {
                reviewId = addBookToShelf(grBookId, shelvesToAddTo);
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
                reviewId = addBookToShelf(grBookId, "Default");
            }

            // Now update the remaining review details.
            updateReview(reviewId,
                         bookCursor.getBoolean(DBDefinitions.KEY_READ),
                         bookCursor.getString(DBDefinitions.KEY_READ_START),
                         bookCursor.getString(DBDefinitions.KEY_READ_END),
                         (int) bookCursor.getDouble(DBDefinitions.KEY_RATING),
                         bookCursor.getString(DBDefinitions.KEY_PRIVATE_NOTES),
                         null);

            return ExportResult.sent;
        }
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String isbn,
                               final boolean fetchThumbnail)
            throws CredentialsException, IOException {

        try {
            return getBookByIsbn(context, isbn, fetchThumbnail);

        } catch (@NonNull final BookNotFoundException e) {
            // to bad.
            return new Bundle();
        }
    }

    @NonNull
    @Override
    public Bundle searchByNativeId(@NonNull final Context context,
                                   @NonNull final String nativeId,
                                   final boolean fetchThumbnail)
            throws CredentialsException, IOException {

        try {
            long bookId = Long.parseLong(nativeId);
            return getBookById(context, bookId, fetchThumbnail);

        } catch (@NonNull final BookNotFoundException | NumberFormatException e) {
            // to bad.
            return new Bundle();
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
    public Bundle search(@NonNull final Context context,
                         @Nullable final String isbn,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final /* not supported */ String publisher,
                         final boolean fetchThumbnail)
            throws CredentialsException,
                   IOException {

        try {
            if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
                List<GoodreadsWork> goodreadsWorks = new SearchBooksApiHandler(this)
                        .search(author + ' ' + title);

                if (!goodreadsWorks.isEmpty()) {
                    return getBookById(context, goodreadsWorks.get(0).bookId, fetchThumbnail);
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

    @Nullable
    @Override
    @WorkerThread
    public File getCoverImage(@NonNull final Context context,
                              @NonNull final String isbn,
                              @Nullable final ImageSize size) {
        if (!hasValidCredentials()) {
            return null;
        }

        return getCoverImageFallback(context, isbn);
    }

    @Override
    public boolean isAvailable() {
        // hasKey() is only here for when the developer forgot to add the dev key.
        // Checking it avoids confusion and endless debugging elsewhere.
        return hasKey()
               // makes sure we *have* credentials, but does not check them.
               && hasCredentials();
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context context) {
        return BASE_URL;
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.goodreads;
    }

    /**
     * Wrapper to search for a book.
     *
     * @param context Current context
     *
     * @return Bundle of GoodreadsWork objects
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    private Bundle getBookById(@NonNull final Context context,
                               final long bookId,
                               final boolean fetchThumbnail)
            throws CredentialsException, BookNotFoundException, IOException {

        if (bookId != 0) {
            ShowBookByIdApiHandler api = new ShowBookByIdApiHandler(context, this);
            // Run the search
            return api.get(bookId, fetchThumbnail);
        } else {
            throw new IllegalArgumentException("No bookId");
        }
    }

    /**
     * Wrapper to search for a book.
     *
     * @param context        Current context
     * @param isbn           to search for
     * @param fetchThumbnail Flag whether to get a thumbnail or not
     *
     * @return Bundle of GoodreadsWork objects
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    private Bundle getBookByIsbn(@NonNull final Context context,
                                 @Nullable final String isbn,
                                 final boolean fetchThumbnail)
            throws CredentialsException, BookNotFoundException, IOException {

        if (ISBN.isValid(isbn)) {
            ShowBookByIsbnApiHandler api = new ShowBookByIsbnApiHandler(context, this);
            // Run the search
            return api.get(isbn, fetchThumbnail);
        } else {
            throw new BookNotFoundException(isbn);
        }
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
     */
    @WorkerThread
    public void requestAuthorization()
            throws AuthorizationException,
                   IOException {

        Context context = App.getAppContext();

        String authUrl;

        // Don't do this; this is just part of OAuth and not the Goodreads API
        // THROTTLER.waitUntilRequestAllowed();

        // Get the URL to send the user to so they can authenticate.
        try {
            authUrl = mProvider.retrieveRequestToken(mConsumer,
                                                     AUTHORIZATION_CALLBACK);

        } catch (@NonNull final OAuthCommunicationException e) {
            throw new IOException(e);
        } catch (@NonNull final OAuthMessageSignerException | OAuthNotAuthorizedException e) {
            throw new AuthorizationException(e);
        } catch (@NonNull final OAuthExpectationFailedException e) {
            // this would be a bug
            throw new IllegalStateException(e);
        }

        // Some urls come back without a scheme, add it to make a valid URL for the parser
        if (!authUrl.startsWith("http://") && !authUrl.startsWith("https://")) {
            Logger.warn(App.getAppContext(), TAG, "requestAuthorization",
                        "no scheme for authUrl=" + authUrl);
            authUrl = "http://" + authUrl;
        }

        // Temporarily save the token; this GoodreadsManager object may be destroyed
        // before the web page returns.
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(REQUEST_TOKEN, mConsumer.getToken())
                         .putString(REQUEST_SECRET, mConsumer.getTokenSecret())
                         .apply();

        // Open the web page
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
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
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(App.getAppContext());
        String requestToken = prefs.getString(REQUEST_TOKEN, null);
        String requestSecret = prefs.getString(REQUEST_SECRET, null);

        // sanity check; the tokens are stored in #requestAuthorization
        if (requestToken == null || requestToken.isEmpty()
            || requestSecret == null || requestSecret.isEmpty()) {
            throw new IllegalStateException("No request token found in preferences");
        }

        // Update the consumer.
        mConsumer.setTokenWithSecret(requestToken, requestSecret);

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        THROTTLER.waitUntilRequestAllowed();

        // Get the access token
        try {
            mProvider.retrieveAccessToken(mConsumer, null);

        } catch (@NonNull final OAuthCommunicationException e) {
            throw new IOException(e);
        } catch (@NonNull final OAuthMessageSignerException | OAuthNotAuthorizedException e) {
            throw new AuthorizationException(e);
        } catch (@NonNull final OAuthExpectationFailedException e) {
            // this would be a bug
            throw new IllegalStateException(e);
        }

        // Cache and save the tokens
        sAccessToken = mConsumer.getToken();
        sAccessSecret = mConsumer.getTokenSecret();

        prefs.edit()
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
        sent, noIsbn, notFound, ioError, credentialsError, error;

        /**
         * Get a user message string id.
         *
         * @return string resource id, or 0 for 'no errors'
         */
        @StringRes
        public int getReasonStringId() {
            switch (this) {
                case sent:
                    return R.string.done;
                case noIsbn:

                    return R.string.gr_info_no_isbn;
                case notFound:

                    return R.string.warning_no_matching_book_found;

                case credentialsError:
                    return R.string.gr_auth_error;

                case error:
                case ioError:
                    return R.string.error_unexpected_error;
            }
            return R.string.error_unexpected_error;
        }
    }
}

