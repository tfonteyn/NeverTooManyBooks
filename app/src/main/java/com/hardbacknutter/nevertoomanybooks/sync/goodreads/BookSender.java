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
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GoodreadsDao;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.network.HttpStatusException;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ShowBookApiHandler;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

public class BookSender {

    private static final String TAG = "BookSender";

    @NonNull
    private final GoodreadsManager mGrManager;
    @NonNull
    private final GoodreadsDao mGoodreadsDao;
    @NonNull
    private final BookshelfDao mBookshelfDao;
    @NonNull
    private final Locale mUserLocale;

    @AnyThread
    public BookSender(@NonNull final Context context,
                      @NonNull final GoodreadsManager grManager) {
        mGrManager = grManager;
        mUserLocale = context.getResources().getConfiguration().getLocales().get(0);
        mGoodreadsDao = ServiceLocator.getInstance().getGoodreadsDao();
        mBookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
    }

    /**
     * Wrapper to send an entire book, including shelves, to Goodreads.
     * <ul>The bookData bundle has to have:
     *      <li>{@link DBKey#PK_ID}</li>
     *      <li>{@link DBKey#SID_GOODREADS_BOOK}</li>
     *      <li>{@link DBKey#KEY_ISBN}</li>
     *      <li>{@link DBKey#BOOL_READ}</li>
     *      <li>{@link DBKey#DATE_READ_START}</li>
     *      <li>{@link DBKey#DATE_READ_END}</li>
     *      <li>{@link DBKey#KEY_RATING}</li>
     * </ul>
     * <p>
     * See {@link GoodreadsDao#fetchBookForExport}
     *
     * @param bookData with book data to send
     *
     * @return {@link Status} encapsulating one of the {@link GrStatus} int codes.
     *
     * @throws IOException on other failures
     */
    @WorkerThread
    public Status send(@NonNull final DataHolder bookData)
            throws DiskFullException, CoverStorageException, IOException, CredentialsException,
                   HttpStatusException {

        final long bookId = bookData.getLong(DBKey.PK_ID);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_SEND) {
            Logger.d(TAG, "sendBook", "bookId=" + bookId);
        }

        long grBookId;
        Bundle grBookData = null;

        final boolean[] fetchCovers = {false, false};

        try {
            // See if the book already has a Goodreads id and if it is valid.
            grBookId = bookData.getLong(DBKey.SID_GOODREADS_BOOK);
            if (grBookId > 0) {
                try {
                    // Get the book details to make sure we have a valid book ID
                    grBookData = mGrManager.getBookById(grBookId, fetchCovers, new Bundle());

                } catch (@NonNull final HttpNotFoundException ignore) {
                    grBookId = 0;
                }
            }

            // wasn't there, see if we can find it using the ISBN instead.
            if (grBookId == 0) {
                final String isbnStr = bookData.getString(DBKey.KEY_ISBN);
                if (isbnStr.isEmpty()) {
                    return Status.NoIsbn;
                }
                final ISBN isbn = ISBN.createISBN(isbnStr);
                if (!isbn.isValid(true)) {
                    return Status.NoIsbn;
                }

                // Get the book details using the ISBN
                grBookData = mGrManager.getBookByIsbn(isbn.asText(), fetchCovers, new Bundle());
                grBookId = grBookData.getLong(DBKey.SID_GOODREADS_BOOK);

                // If we got an ID, save it against the book
                if (grBookId > 0) {
                    mGoodreadsDao.setGoodreadsBookId(bookId, grBookId);
                } else {
                    // Still nothing... Give up.
                    return Status.NotFound;
                }
            }

            // We found a Goodreads book.
            // Get the review id if we have the book details. For new books, it will not be present.
            //noinspection ConstantConditions
            long reviewId = grBookData.getLong(ShowBookApiHandler.SiteField.REVIEW_ID);

            // Lists of shelf names and our best guess at the Goodreads canonical name
            final Collection<String> shelves = new ArrayList<>();
            final Collection<String> canonicalShelves = new ArrayList<>();

            // Get the list of shelves from Goodreads.
            // This is cached per instance of GoodreadsManager.
            final GoodreadsShelves grShelfList = mGrManager.getShelves();

            // Build the list of shelves for the book that we have in the local database
            int exclusiveCount = 0;
            for (final Bookshelf bookshelf : mBookshelfDao.getBookshelvesByBookId(bookId)) {
                final String bookshelfName = bookshelf.getName();
                shelves.add(bookshelfName);

                final String canonicalShelfName =
                        GoodreadsShelf.canonicalizeName(mUserLocale, bookshelfName);
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
                if (bookData.getInt(DBKey.BOOL_READ) != 0) {
                    pseudoShelf = "Read";
                } else {
                    pseudoShelf = "To Read";
                }
                if (!shelves.contains(pseudoShelf)) {
                    shelves.add(pseudoShelf);
                    canonicalShelves.add(GoodreadsShelf.canonicalizeName(mUserLocale,
                                                                         pseudoShelf));
                }
            }

            // Get the names of the shelves the book is currently on at Goodreads
            List<String> grShelves = null;
            if (grBookData.containsKey(ShowBookApiHandler.SiteField.SHELVES)) {
                grShelves = grBookData.getStringArrayList(ShowBookApiHandler.SiteField.SHELVES);
            }
            // not in grBookData, or failed to get
            if (grShelves == null) {
                grShelves = new ArrayList<>();
            }

            // Remove from any shelves from Goodreads that are not in our local list
            for (final String grShelf : grShelves) {
                if (!canonicalShelves.contains(grShelf)) {
                    try {
                        // Goodreads does not seem to like removing books from the special shelves.
                        if (!(grShelfList.isExclusive(grShelf))) {
                            mGrManager.removeBookFromShelf(grBookId, grShelf);
                        }
                    } catch (@NonNull final HttpNotFoundException ignore) {
                        // Ignore here; probably means the book was not on this shelf anyway
                    }
                }
            }

            // Add shelves to Goodreads if they are not currently there
            final Collection<String> shelvesToAddTo = new ArrayList<>();
            for (final String shelf : shelves) {
                // Get the name the shelf will have at Goodreads
                final String canonicalShelfName = GoodreadsShelf
                        .canonicalizeName(mUserLocale, shelf);
                // Can only sent canonical shelf names if the book is on 0 or 1 of them.
                final boolean okToSend = exclusiveCount < 2
                                         || !grShelfList.isExclusive(canonicalShelfName);

                if (okToSend && !grShelves.contains(canonicalShelfName)) {
                    shelvesToAddTo.add(shelf);
                }
            }
            if (!shelvesToAddTo.isEmpty()) {
                reviewId = mGrManager.addBookToShelf(grBookId, shelvesToAddTo);
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
                reviewId = mGrManager.addBookToShelf(grBookId, GoodreadsShelf.DEFAULT_SHELF);
            }

            // Finally update the remaining review details.
            mGrManager.updateReview(reviewId, bookData);

        } catch (@NonNull final HttpNotFoundException | SAXException e) {
            return Status.NotFound;
        }

        // Record/report the success
        mGoodreadsDao.setSyncDate(bookId);
        return Status.Success;
    }

    public enum Status {
        Success(GrStatus.SUCCESS),
        NoIsbn(GrStatus.FAILED_BOOK_HAS_NO_ISBN),
        NotFound(GrStatus.FAILED_BOOK_NOT_FOUND_ON_GOODREADS);

        @GrStatus.Status
        private final int mStatus;

        Status(@GrStatus.Status final int status) {
            mStatus = status;
        }

        public GrStatus getGrStatus() {
            return new GrStatus(mStatus);
        }

        @GrStatus.Status
        public int getStatus() {
            return mStatus;
        }
    }
}
