/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.AddBookToShelfApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.IsbnToIdApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ReviewEditApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.SearchBooksApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShelvesListApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShowBookApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShowBookByIdApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ShowBookByIsbnApiHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * Manages connections and interfaces between the {@code SearchEngine} and the actual
 * Goodreads API classes.
 *
 * <p>
 * <a href="https://www.goodreads.com/api/documentation">api documentation</a>
 */
public class GoodreadsHandler {

    public static final String BASE_URL = "https://www.goodreads.com";
    public static final Locale SITE_LOCALE = Locale.US;
    /** Preferences prefix. */
    private static final String PREF_PREFIX = "goodreads.";

    /** Preference that controls display of alert about Goodreads. */
    public static final String PREFS_HIDE_ALERT = PREF_PREFIX + "hide_alert.";
    /** last time we synced with Goodreads. */
    public static final String PREFS_LAST_SYNC_DATE = PREF_PREFIX + "last.sync.date";
    /** last id we send to Goodreads. */
    public static final String PREFS_LAST_BOOK_SEND = PREF_PREFIX + "last.send.id";

    /** Log tag. */
    private static final String TAG = "GoodreadsHandler";
    /** Whether to show any Goodreads sync menus at all. */
    private static final String PREFS_SHOW_MENUS = PREF_PREFIX + "showMenu";
    /** Whether to collect genre string from the popular bookshelves. */
    private static final String PREFS_COLLECT_GENRE = PREF_PREFIX + "search.collect.genre";


    /** Authentication handler. */
    @NonNull
    private final GoodreadsAuth mGoodreadsAuth;

    /** Cache this handler. */
    @Nullable
    private SearchBooksApiHandler mSearchBooksApiHandler;
    /** Cache this handler. */
    @Nullable
    private IsbnToIdApiHandler mIsbnToIdApiHandler;
    /** Cache this handler. */
    @Nullable
    private AddBookToShelfApiHandler mAddBookToShelfApiHandler;
    /** Cache this handler. */
    @Nullable
    private ReviewEditApiHandler mReviewEditApiHandler;

    /** Cached list of shelves. */
    @Nullable
    private GoodreadsShelves mShelvesList;

    /**
     * Constructor.
     *
     * @param grAuth Authentication handler
     */
    public GoodreadsHandler(@NonNull final GoodreadsAuth grAuth) {
        mGoodreadsAuth = grAuth;
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

    public static boolean isCollectGenre(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PREFS_COLLECT_GENRE, true);
    }

    /**
     * Construct a full or partial date string based on the y/m/d fields.
     *
     * @param source    Bundle to use
     * @param yearKey   bundle key
     * @param monthKey  bundle key
     * @param dayKey    bundle key
     * @param resultKey key to write to formatted date to
     *
     * @return the date string, or {@code null} if invalid
     */
    @Nullable
    public static String buildDate(@NonNull final Bundle source,
                                   @NonNull final String yearKey,
                                   @NonNull final String monthKey,
                                   @NonNull final String dayKey,
                                   @Nullable final String resultKey) {

        String date = null;
        if (source.containsKey(yearKey)) {
            date = String.format(Locale.ENGLISH, "%04d", source.getLong(yearKey));
            if (source.containsKey(monthKey)) {
                date += '-' + String.format(Locale.ENGLISH, "%02d", source.getLong(monthKey));
                if (source.containsKey(dayKey)) {
                    date += '-' + String.format(Locale.ENGLISH, "%02d", source.getLong(dayKey));
                }
            }
            if (resultKey != null && !date.isEmpty()) {
                source.putString(resultKey, date);
            }
        }
        return date;
    }

    /**
     * Digest the task outcome, and return a user displayable String message.
     *
     * @param context Current context
     * @param message to process
     *
     * @return a String to display to the user
     */
    @NonNull
    public static String digest(@NonNull final Context context,
                                @NonNull final TaskListener.FinishMessage<Integer> message) {

        // Regardless of the task status, we ONLY need to take the extended-status code
        // as passed in message.result into account.

        // the only time the result is null is when the task was cancelled before it started.
        if (message.result == null) {
            return GrStatus.getString(context, GrStatus.CANCELLED);
        }

        // If we have an unexpected exception, add the actual exception to the message.
        if (message.exception != null && message.result == GrStatus.FAILED_UNEXPECTED_EXCEPTION) {
            return GrStatus.getString(context, message.result)
                   + ' ' + message.exception.getLocalizedMessage();
        }

        // Everything else
        return GrStatus.getString(context, message.result);

    }

    /**
     * Check the url for certain keywords that would indicate a cover is, or is not, present.
     *
     * @param url to check
     *
     * @return {@code true} if the url indicates there is an actual image.
     */
    public static boolean hasCover(@Nullable final String url) {
        if (url == null) {
            return false;
        }
        final String name = url.toLowerCase(LocaleUtils.getSystemLocale());
        // these string can be part of an image 'name' indicating there is no cover image.
        return !name.contains("nophoto") && !name.contains("nocover");
    }

    /**
     * Wrapper to search for a book based on the given query.
     *
     * @param context Current context
     * @param query   to search
     *
     * @return the array of GoodreadsWork objects.
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @NonNull
    public List<GoodreadsWork> search(@NonNull final Context context,
                                      @NonNull final String query)
            throws CredentialsException, Http404Exception, IOException {

        if (mSearchBooksApiHandler == null) {
            mSearchBooksApiHandler = new SearchBooksApiHandler(context, mGoodreadsAuth);
        }
        return mSearchBooksApiHandler.search(query);
    }

    /**
     * Wrapper to call ISBN->ID.
     *
     * @param context Current context
     * @param isbn    to search for
     *
     * @return Goodreads book ID
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @SuppressWarnings("unused")
    public long isbnToId(@NonNull final Context context,
                         @NonNull final String isbn)
            throws CredentialsException, Http404Exception, IOException {

        if (mIsbnToIdApiHandler == null) {
            mIsbnToIdApiHandler = new IsbnToIdApiHandler(context, mGoodreadsAuth);
        }
        return mIsbnToIdApiHandler.isbnToId(isbn);
    }

    /**
     * Fetch all our shelves from Goodreads.
     * We only fetch them once and cache them.
     *
     * @param context Current context
     *
     * @return the Goodreads shelves
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @NonNull
    private GoodreadsShelves getShelves(@NonNull final Context context)
            throws CredentialsException, Http404Exception, IOException {

        if (mShelvesList == null) {
            final ShelvesListApiHandler handler =
                    new ShelvesListApiHandler(context, mGoodreadsAuth);
            mShelvesList = new GoodreadsShelves(handler.getAll());
        }
        return mShelvesList;
    }

    /**
     * Wrapper to add book to shelf.
     *
     * @param context   Current context
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @return reviewId
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    private long addBookToShelf(@NonNull final Context context,
                                final long grBookId,
                                @SuppressWarnings("SameParameterValue")
                                @NonNull final String shelfName)
            throws CredentialsException, Http404Exception, IOException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(context, mGoodreadsAuth);
        }
        return mAddBookToShelfApiHandler.add(grBookId, shelfName);
    }

    /**
     * Wrapper to add book to a list of shelves.
     *
     * @param context    Current context
     * @param grBookId   GoodReads book id
     * @param shelfNames list of GoodReads shelf name
     *
     * @return reviewId
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    private long addBookToShelf(@NonNull final Context context,
                                final long grBookId,
                                @NonNull final Iterable<String> shelfNames)
            throws CredentialsException, Http404Exception, IOException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(context, mGoodreadsAuth);
        }
        return mAddBookToShelfApiHandler.add(grBookId, shelfNames);
    }

    /**
     * Wrapper to remove a book from a shelf.
     *
     * @param context   Current context
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    private void removeBookFromShelf(@NonNull final Context context,
                                     final long grBookId,
                                     @NonNull final String shelfName)
            throws CredentialsException, Http404Exception, IOException {

        if (mAddBookToShelfApiHandler == null) {
            mAddBookToShelfApiHandler = new AddBookToShelfApiHandler(context, mGoodreadsAuth);
        }
        mAddBookToShelfApiHandler.remove(grBookId, shelfName);
    }

    /**
     * Wrapper to search for a book.
     *
     * @param context        Current context
     * @param grBookId       Goodreads book id to get
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to save results in (passed in to allow mocking)
     *
     * @return Bundle of GoodreadsWork objects
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @NonNull
    public Bundle getBookById(@NonNull final Context context,
                              final long grBookId,
                              @NonNull final boolean[] fetchThumbnail,
                              @NonNull final Bundle bookData)
            throws CredentialsException, Http404Exception, IOException {

        if (grBookId != 0) {
            final ShowBookByIdApiHandler api = new ShowBookByIdApiHandler(context, mGoodreadsAuth);
            return api.get(context, grBookId, fetchThumbnail, bookData);
        } else {
            throw new IllegalArgumentException(ErrorMsg.ZERO_ID_FOR_BOOK);
        }
    }

    /**
     * Wrapper to search for a book.
     *
     * @param context        Current context
     * @param validIsbn      ISBN to use, must be valid
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to save results in (passed in to allow mocking)
     *
     * @return Bundle of GoodreadsWork objects
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @NonNull
    public Bundle getBookByIsbn(@NonNull final Context context,
                                @NonNull final String validIsbn,
                                @NonNull final boolean[] fetchThumbnail,
                                @NonNull final Bundle bookData)
            throws CredentialsException, Http404Exception, IOException {

        final ShowBookByIsbnApiHandler api = new ShowBookByIsbnApiHandler(context, mGoodreadsAuth);
        return api.get(context, validIsbn, fetchThumbnail, bookData);
    }

    /**
     * Wrapper to send an entire book, including shelves, to Goodreads.
     * <ul>The book has to have:
     *      <li>{@link DBDefinitions#KEY_PK_ID}</li>
     *      <li>{@link DBDefinitions#KEY_EID_GOODREADS_BOOK}</li>
     *      <li>{@link DBDefinitions#KEY_ISBN}</li>
     *      <li>{@link DBDefinitions#KEY_READ}</li>
     *      <li>{@link DBDefinitions#KEY_READ_START}</li>
     *      <li>{@link DBDefinitions#KEY_READ_END}</li>
     *      <li>{@link DBDefinitions#KEY_RATING}</li>
     * </ul>
     * <p>
     * See {@link DAO#fetchBookForGoodreadsExport}
     *
     * @param context  Current context
     * @param db       Database Access
     * @param bookData with book data to send
     *
     * @return Disposition of book
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @WorkerThread
    @GrStatus.Status
    public int sendOneBook(@NonNull final Context context,
                           @NonNull final DAO db,
                           @NonNull final DataHolder bookData)
            throws CredentialsException, Http404Exception, IOException {

        final long bookId = bookData.getLong(DBDefinitions.KEY_PK_ID);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_SEND) {
            Logger.d(TAG, "sendOneBook|bookId=" + bookId);
        }

        // Get the list of shelves from Goodreads.
        // This is cached per instance of GoodreadsHandler.
        final GoodreadsShelves grShelfList = getShelves(context);

        long grBookId;
        Bundle grBook = null;

        // See if the book already has a Goodreads id and if it is valid.
        try {
            grBookId = bookData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);
            if (grBookId != 0) {
                // Get the book details to make sure we have a valid book ID
                final boolean[] fetchThumbnails = {false, false};
                grBook = getBookById(context, grBookId, fetchThumbnails, new Bundle());
            }
        } catch (@NonNull final Http404Exception ignore) {
            grBookId = 0;
        }

        // wasn't there, see if we can find it using the ISBN instead.
        if (grBookId == 0) {
            final String isbnStr = bookData.getString(DBDefinitions.KEY_ISBN);
            if (isbnStr.isEmpty()) {
                return GrStatus.FAILED_BOOK_HAS_NO_ISBN;
            }
            final ISBN isbn = ISBN.createISBN(isbnStr);
            if (!isbn.isValid(true)) {
                return GrStatus.FAILED_BOOK_HAS_NO_ISBN;
            }

            // Get the book details using ISBN
            final boolean[] fetchThumbnails = {false, false};
            grBook = getBookByIsbn(context, isbn.asText(), fetchThumbnails, new Bundle());
            grBookId = grBook.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);

            // If we got an ID, save it against the book
            if (grBookId != 0) {
                db.setGoodreadsBookId(bookId, grBookId);
            } else {
                // Still nothing... Give up.
                return GrStatus.FAILED_BOOK_NOT_FOUND_ON_GOODREADS;
            }
        }

        // We found a Goodreads book.
        // Get the review id if we have the book details. For new books, it will not be present.
        long reviewId = grBook.getLong(ShowBookApiHandler.ShowBookFieldName.REVIEW_ID);

        // Lists of shelf names and our best guess at the Goodreads canonical name
        final Collection<String> shelves = new ArrayList<>();
        final Collection<String> canonicalShelves = new ArrayList<>();

        final Locale locale = LocaleUtils.getUserLocale(context);

        // Build the list of shelves for the book that we have in the local database
        int exclusiveCount = 0;
        for (Bookshelf bookshelf : db.getBookshelvesByBookId(bookId)) {
            final String bookshelfName = bookshelf.getName();
            shelves.add(bookshelfName);

            final String canonicalShelfName =
                    GoodreadsShelf.canonicalizeName(locale, bookshelfName);
            canonicalShelves.add(canonicalShelfName);

            // Count how many of these shelves are exclusive in Goodreads.
            if (grShelfList.isExclusive(canonicalShelfName)) {
                exclusiveCount++;
            }
        }

        // If no exclusive shelves are specified, add a pseudo-shelf to match Goodreads
        // because review.update does not seem to update them properly
        if (exclusiveCount == 0) {
            final String pseudoShelf;
            if (bookData.getInt(DBDefinitions.KEY_READ) != 0) {
                pseudoShelf = "Read";
            } else {
                pseudoShelf = "To Read";
            }
            if (!shelves.contains(pseudoShelf)) {
                shelves.add(pseudoShelf);
                canonicalShelves.add(GoodreadsShelf.canonicalizeName(locale, pseudoShelf));
            }
        }

        // Get the names of the shelves the book is currently on at Goodreads
        List<String> grShelves = null;
        if (grBook.containsKey(ShowBookApiHandler.ShowBookFieldName.SHELVES)) {
            grShelves = grBook.getStringArrayList(ShowBookApiHandler.ShowBookFieldName.SHELVES);
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
                        removeBookFromShelf(context, grBookId, grShelf);
                    }
                } catch (@NonNull final Http404Exception e) {
                    // Ignore here; probably means the book was not on this shelf anyway
                }
            }
        }

        // Add shelves to Goodreads if they are not currently there
        final Collection<String> shelvesToAddTo = new ArrayList<>();
        for (String shelf : shelves) {
            // Get the name the shelf will have at Goodreads
            final String canonicalShelfName = GoodreadsShelf.canonicalizeName(locale, shelf);
            // Can only sent canonical shelf names if the book is on 0 or 1 of them.
            final boolean okToSend = exclusiveCount < 2
                                     || !grShelfList.isExclusive(canonicalShelfName);

            if (okToSend && !grShelves.contains(canonicalShelfName)) {
                shelvesToAddTo.add(shelf);
            }
        }
        if (!shelvesToAddTo.isEmpty()) {
            reviewId = addBookToShelf(context, grBookId, shelvesToAddTo);
        }

        // We should be safe always updating here because:
        // - all books that are already added have a review ID,
        //   which we would have got from the bundle
        // - all new books will be added to at least one shelf,
        //   which will have returned a review ID.
        // But, just in case, we check the review ID, and if 0,
        // we add the book to the 'Default' shelf.
        //
        if (reviewId == 0) {
            reviewId = addBookToShelf(context, grBookId, GoodreadsShelf.DEFAULT_SHELF);
        }

        // Finally update the remaining review details.
        if (mReviewEditApiHandler == null) {
            mReviewEditApiHandler = new ReviewEditApiHandler(context, mGoodreadsAuth);
        }
        mReviewEditApiHandler.update(reviewId,
                                     bookData.getBoolean(DBDefinitions.KEY_READ),
                                     bookData.getString(DBDefinitions.KEY_READ_START),
                                     bookData.getString(DBDefinitions.KEY_READ_END),
                                     (int) bookData.getDouble(DBDefinitions.KEY_RATING),
                                     null);

        return GrStatus.SUCCESS;
    }
}
