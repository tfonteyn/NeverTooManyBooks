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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
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
    public static final String PREF_PREFIX = "goodreads.";
    /** Preference that controls display of alert about Goodreads. */
    public static final String PREFS_HIDE_ALERT = PREF_PREFIX + "hide_alert.";
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
     * Handle the {@link TaskListener.TaskStatus} and the goodreads extended {@link GrStatus}.
     *
     * @param context Current context
     * @param message to process
     *
     * @return a String to display to the user, or {@code null} when registration is needed.
     */
    @Nullable
    public static String handleResult(@NonNull final Context context,
                                      @NonNull final TaskListener.FinishMessage<Integer> message) {

        // FIRST Handle authorization efforts from RequestAuthTask
        if (message.taskId == R.id.TASK_ID_GR_REQUEST_AUTH) {
            // Did authorization fail ?
            if (message.result == GrStatus.AUTHORIZATION_FAILED) {
                return GrStatus.getString(context, GrStatus.AUTHORIZATION_FAILED);
            }

            // Did we fail after we tried again ?
            if (message.result == GrStatus.AUTHORIZATION_NEEDED) {
                // If so, report this, but do NOT return {@code null}
                // as otherwise we'd get into a registration loop.
                return context.getString(R.string.error_site_authentication_failed,
                                         context.getString(R.string.site_goodreads));
            }
        }

        // all is fine or the user cancelled: just return the result information message.
        if (message.status == TaskListener.TaskStatus.Success
            || message.status == TaskListener.TaskStatus.Cancelled) {
            return GrStatus.getString(context, message.result);

        } else {
            // Should we ask the user to register?
            if (message.result == GrStatus.AUTHORIZATION_NEEDED) {
                return null;
            }

            // Report the error
            String errorMsg = GrStatus.getString(context, message.result);
            // worst case, add the actual exception.
            if (message.result == GrStatus.UNEXPECTED_ERROR) {
                if (message.exception != null) {
                    errorMsg += ' ' + message.exception.getLocalizedMessage();
                }
            }
            return errorMsg;
        }
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
        return !name.contains("/nophoto/") && !name.contains("nocover");
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
            ShelvesListApiHandler handler = new ShelvesListApiHandler(context, mGoodreadsAuth);
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
     * Update a fixed set of fields; see method parameters.
     * <p>
     * Depending on the state of 'isRead', the date fields will determine which shelf is used.
     *
     * @param context      Current context
     * @param reviewId     to update
     * @param isRead       Flag to indicate we finished reading this book.
     * @param readStart    (optional) Date when we started reading this book, YYYY-MM-DD format
     * @param readEnd      (optional) Date when we finished reading this book, YYYY-MM-DD format
     * @param rating       Rating 0-5 with 0 == No rating
     * @param privateNotes (optional) Text for the Goodreads PRIVATE notes
     * @param review       (optional) Text for the review, PUBLIC
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    private void updateReview(@NonNull final Context context,
                              final long reviewId,
                              final boolean isRead,
                              @Nullable final String readStart,
                              @Nullable final String readEnd,
                              final int rating,
                              @Nullable final String privateNotes,
                              @SuppressWarnings("SameParameterValue")
                              @Nullable final String review)
            throws CredentialsException, Http404Exception, IOException {

        if (mReviewEditApiHandler == null) {
            mReviewEditApiHandler = new ReviewEditApiHandler(context, mGoodreadsAuth);
        }
        mReviewEditApiHandler.update(reviewId, isRead, readStart, readEnd, rating,
                                     privateNotes,
                                     review);
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
     * See {@link DAO#fetchBookForExportToGoodreads}
     *
     * @param context Current context
     * @param db      Database Access
     * @param rowData with book data to send
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
                           @NonNull final RowDataHolder rowData)
            throws CredentialsException, Http404Exception, IOException {

        final long bookId = rowData.getLong(DBDefinitions.KEY_PK_ID);

        // Get the list of shelves from Goodreads.
        // This is cached per instance of GoodreadsHandler.
        final GoodreadsShelves grShelfList = getShelves(context);

        long grBookId;
        Bundle grBook = null;

        // See if the book already has a Goodreads id and if it is valid.
        try {
            grBookId = rowData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);
            if (grBookId != 0) {
                // Get the book details to make sure we have a valid book ID
                final boolean[] thumbs = {false, false};
                grBook = getBookById(context, grBookId, thumbs, new Bundle());
            }
        } catch (@NonNull final CredentialsException | Http404Exception | IOException e) {
            grBookId = 0;
        }

        // wasn't there, see if we can find it using the ISBN instead.
        if (grBookId == 0) {
            final String isbnStr = rowData.getString(DBDefinitions.KEY_ISBN);
            if (isbnStr.isEmpty()) {
                return GrStatus.NO_ISBN;
            }
            final ISBN isbn = ISBN.createISBN(isbnStr);
            if (!isbn.isValid(true)) {
                return GrStatus.NO_ISBN;
            }

            // Get the book details using ISBN
            final boolean[] thumbs = {false, false};
            grBook = getBookByIsbn(context, isbn.asText(), thumbs, new Bundle());
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
            return GrStatus.BOOK_NOT_FOUND;

        } else {
            // We found a Goodreads book, update it
            long reviewId = 0;

            final Locale locale = LocaleUtils.getUserLocale(context);

            // Get the review id if we have the book details. For new books, it will not be present.
            if (grBook.containsKey(ShowBookApiHandler.ShowBookFieldName.REVIEW_ID)) {
                reviewId = grBook.getLong(ShowBookApiHandler.ShowBookFieldName.REVIEW_ID);
            }

            // Lists of shelf names and our best guess at the Goodreads canonical name
            final Collection<String> shelves = new ArrayList<>();
            final Collection<String> canonicalShelves = new ArrayList<>();

            // Build the list of shelves that we have in the local database for the book
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

            // If no exclusive shelves are specified, add pseudo-shelf to match Goodreads
            // because review.update does not seem to update them properly
            if (exclusiveCount == 0) {
                final String pseudoShelf;
                if (rowData.getInt(DBDefinitions.KEY_READ) != 0) {
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

            /* We should be safe always updating here because:
             * - all books that are already added have a review ID,
             * which we would have got from the bundle
             * - all new books will be added to at least one shelf,
             * which will have returned a review ID.
             * But, just in case, we check the review ID, and if 0,
             * we add the book to the 'Default' shelf.
             */
            if (reviewId == 0) {
                reviewId = addBookToShelf(context, grBookId, GoodreadsShelf.DEFAULT_SHELF);
            }

            // Now update the remaining review details.
            updateReview(context,
                         reviewId,
                         rowData.getBoolean(DBDefinitions.KEY_READ),
                         rowData.getString(DBDefinitions.KEY_READ_START),
                         rowData.getString(DBDefinitions.KEY_READ_END),
                         (int) rowData.getDouble(DBDefinitions.KEY_RATING),
                         rowData.getString(DBDefinitions.KEY_PRIVATE_NOTES),
                         null);

            return GrStatus.COMPLETED;
        }
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
            throw new IllegalArgumentException("No bookId");
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
}
