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

package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.MappedCursorRow;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.ItemWithIdFixup;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.goodreads.api.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler;
import com.eleybourn.bookcatalogue.goodreads.api.ListReviewsApiHandler.ReviewFields;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.BaseTask;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.Task;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Import all a users 'reviews' from goodreads; a users 'reviews' consists of all the books that
 * they have placed on bookshelves, irrespective of whether they have rated or reviewed the book.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 *
 * @author Philip Warner
 */
class GrImportAllTask
        extends BaseTask {

    private static final long serialVersionUID = -3535324410982827612L;
    /**
     * Number of books to retrieve in one batch; we are encouraged to make fewer API calls, so
     * setting this number high is good. 50 seems to take several seconds to retrieve, so it
     * was chosen.
     */
    private static final int BOOKS_PER_PAGE = 50;
    /**
     * Date before which updates are irrelevant.
     * Can be {@code null}, which implies all dates are included.
     */
    @Nullable
    private final String mUpdatesAfter;
    /** Flag indicating this job is a sync job: on completion, it will start an export. */
    private final boolean mIsSync;
    /** Current position in entire list of reviews. */
    private int mPosition;
    /** Total number of reviews user has. */
    private int mTotalBooks;
    /** Flag indicating this is the first time *this* object instance has been called. */
    private transient boolean mFirstCall = true;
    /** Date at which this job started downloading first page. */
    @Nullable
    private Date mStartDate;
    /** Lookup table of bookshelves defined currently and their goodreads canonical names. */
    @Nullable
    private transient Map<String, String> mBookshelfLookup;

    /**
     * Constructor.
     */
    GrImportAllTask(@NonNull final String description,
                    final boolean isSync) {

        super(description);

        mPosition = 0;
        mIsSync = isSync;
        // If it's a sync job, then find date of last successful sync and only apply
        // records from after that date. If no other job, then get all.
        if (mIsSync) {
            Date lastSync = GoodreadsManager.getLastSyncDate();
            if (lastSync == null) {
                mUpdatesAfter = null;
            } else {
                mUpdatesAfter = DateUtils.utcSqlDateTime(lastSync);
            }
        } else {
            mUpdatesAfter = null;
        }
    }

    /**
     * @param imageName to check
     *
     * @return {@code true} if the name does NOT contain the string 'nocover'
     */
    private static boolean hasCover(@Nullable final String imageName) {
        return imageName != null
                && !imageName.toLowerCase(LocaleUtils.getSystemLocale())
                             .contains(GoodreadsTasks.NO_COVER);
    }

    /**
     * Do the actual work.
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager,
                       @NonNull final Context context) {

        try (DAO db = new DAO()) {
            // Load the goodreads reviews
            boolean ok = processReviews(queueManager, db);
            // If it's a sync job, then start the 'send' part and save last syn date
            if (mIsSync) {
                GoodreadsManager.setLastSyncDate(mStartDate);
                QueueManager.getQueueManager().enqueueTask(
                        new GrSendAllBooksTask(context.getString(R.string.gr_title_send_book),
                                               true),
                        QueueManager.Q_MAIN);
            }
            return ok;
        } catch (@NonNull final AuthorizationException e) {
            Logger.error(this, e);
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

    /**
     * Repeatedly request review pages until we are done.
     */
    private boolean processReviews(@NonNull final QueueManager queueManager,
                                   @NonNull final DAO db)
            throws AuthorizationException {

        GoodreadsManager gr = new GoodreadsManager();
        ListReviewsApiHandler api = new ListReviewsApiHandler(gr);

        int currPage = mPosition / BOOKS_PER_PAGE;
        while (true) {
            // page numbers are 1-based; start at 0 and increment at start of each loop
            currPage++;

            // In case of a restart, reset position to first in page
            mPosition = BOOKS_PER_PAGE * (currPage - 1);

            Bundle books;

            // Call the API, return {@code false} if failed.
            try {
                // If we have not started successfully yet, record the date at which
                // the run() was called. This date is used if the job is a sync job.
                Date runDate = null;
                if (mStartDate == null) {
                    runDate = new Date();
                }
                books = api.run(currPage, BOOKS_PER_PAGE);
                // If we succeeded, and this is the first time, save the date
                if (mStartDate == null) {
                    mStartDate = runDate;
                }
            } catch (@NonNull final BookNotFoundException
                    | AuthorizationException
                    | IOException e) {
                setException(e);
                return false;
            }

            // Get the total, and if first call, save the object again so the UI can update.
            mTotalBooks = (int) books.getLong(ListReviewsApiHandler.ReviewFields.TOTAL);
            if (mFirstCall) {
                // So the details get updated
                queueManager.updateTask(this);
                mFirstCall = false;
            }

            // Get the reviews array and process it
            ArrayList<Bundle> reviews =
                    books.getParcelableArrayList(ListReviewsApiHandler.ReviewFields.REVIEWS);

            if (reviews == null || reviews.isEmpty()) {
                break;
            }

            for (Bundle review : reviews) {
                // Always check for an abort request
                if (isAborting()) {
                    return false;
                }

                if (mUpdatesAfter != null) {
                    String upd = review.getString(ListReviewsApiHandler.ReviewFields.UPDATED);
                    if (upd != null && upd.compareTo(mUpdatesAfter) > 0) {
                        return true;
                    }
                }

                // Processing may involve a SLOW thumbnail download...don't run in TX!
                processReview(db, review);
                //SyncLock tx = db.startTransaction(true);
                //try {
                //    processReview(db, review);
                //    db.setTransactionSuccessful();
                //} finally {
                //    db.endTransaction(tx);
                //}

                // Update after each book. Mainly for a nice UI.
                queueManager.updateTask(this);
                mPosition++;
            }
        }
        try {
            db.analyze();
        } catch (@NonNull final RuntimeException e) {
            // Do nothing. Not a critical step.
            Logger.error(this, e);
        }
        return true;
    }

    /**
     * Process one review (book).
     */
    private void processReview(@NonNull final DAO db,
                               @NonNull final Bundle review) {

        long grId = review.getLong(ReviewFields.DBA_GR_BOOK_ID);

        // Find the books in our database - NOTE: may be more than one!
        // First look by goodreads book ID
        BookCursor cursor = db.fetchBooksByGoodreadsBookId(grId);
        try {
            boolean found = cursor.moveToFirst();
            if (!found) {
                // Not found by GR id, look via ISBNs
                cursor.close();
                cursor = null;

                List<String> list = extractIsbnList(review);
                if (!list.isEmpty()) {
                    cursor = db.fetchBooksByIsbnList(list);
                    found = cursor.moveToFirst();
                }
            }

            if (found) {
                // If found, update ALL related books
                MappedCursorRow cursorRow = cursor.getCursorRow();
                do {
                    // Check for abort
                    if (isAborting()) {
                        break;
                    }
                    updateBook(db, cursorRow, review);
                } while (cursor.moveToNext());
            } else {
                // Create the book
                insertBook(db, review);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Passed a goodreads shelf name, return the best matching local bookshelf name,
     * or the original if no match found.
     *
     * @param db          Database adapter
     * @param grShelfName Goodreads shelf name
     *
     * @return Local name, or goodreads name if no match
     */
    @Nullable
    private String translateBookshelf(@NonNull final DAO db,
                                      @Nullable final String grShelfName) {

        if (grShelfName == null) {
            return null;
        }
        if (mBookshelfLookup == null) {
            List<Bookshelf> bookshelves = db.getBookshelves();
            mBookshelfLookup = new HashMap<>(bookshelves.size());
            for (Bookshelf bookshelf : bookshelves) {
                mBookshelfLookup.put(
                        GoodreadsManager.canonicalizeBookshelfName(bookshelf.getName()),
                        bookshelf.getName());
            }
        }

        String lcGrShelfName = grShelfName.toLowerCase(LocaleUtils.getPreferredLocal());
        return mBookshelfLookup.containsKey(lcGrShelfName) ? mBookshelfLookup.get(lcGrShelfName)
                                                           : grShelfName;
    }

    /**
     * Extract a list of ISBNs from the bundle.
     */
    @NonNull
    private List<String> extractIsbnList(@NonNull final Bundle review) {

        List<String> list = new ArrayList<>(5);
        addIfHasValue(list, review.getString(ListReviewsApiHandler.ReviewFields.ISBN13));
        addIfHasValue(list, review.getString(DBDefinitions.KEY_ISBN));
        return list;
    }

    /**
     * Update the book using the GR data.
     */
    private void updateBook(@NonNull final DAO db,
                            @NonNull final MappedCursorRow bookCursorRow,
                            @NonNull final Bundle review) {
        // Get last date book was sent to GR (may be null)
        String lastGrSync = bookCursorRow.getString(DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE);
        // If the review has an 'updated' date, then see if we can compare to book
        if (review.containsKey(ReviewFields.UPDATED)) {
            String lastUpdate = review.getString(ListReviewsApiHandler.ReviewFields.UPDATED);
            // If last update in GR was before last GR sync of book, then don't bother
            // updating book. This typically happens if the last update in GR was from us.
            if (lastUpdate != null && lastUpdate.compareTo(lastGrSync) < 0) {
                return;
            }
        }
        // We build a new book bundle each time since it will build on the existing
        // data for the given book (taken from the cursor), not just replace it.
        Book book = new Book(buildBundle(db, bookCursorRow, review));

        db.updateBook(bookCursorRow.getLong(DBDefinitions.KEY_PK_ID), book,
                      DAO.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
    }

    /**
     * Create a new book.
     */
    private void insertBook(@NonNull final DAO db,
                            @NonNull final Bundle review) {

        Book book = new Book(buildBundle(db, null, review));
        long id = db.insertBook(book);
        if (id > 0) {
            if (book.getBoolean(UniqueId.BKEY_COVER_IMAGE)) {
                String uuid = db.getBookUuid(id);
                // get the temporary downloaded file
                File source = StorageUtils.getTempCoverFile();
                File destination = StorageUtils.getCoverFile(uuid);
                // and rename it to the permanent UUID one.
                StorageUtils.renameFile(source, destination);
            }
        }
    }

    /**
     * Build a book bundle based on the goodreads 'review' data. Some data is just copied
     * while other data is processed (e.g. dates) and other are combined (authors & series).
     */
    @NonNull
    private Bundle buildBundle(@NonNull final DAO db,
                               @Nullable final MappedCursorRow bookCursorRow,
                               @NonNull final Bundle review) {

        Bundle bookData = new Bundle();

        //ENHANCE: https://github.com/eleybourn/Book-Catalogue/issues/812 - syn goodreads notes
        // Do not sync Notes<->Review. We will add a 'Review' field later.
        //addStringIfNonBlank(review, ReviewFields.DBA_NOTES, book,
        // ReviewFields.DBA_NOTES);

        addStringIfNonBlank(review, ReviewFields.DBA_TITLE,
                            bookData, DBDefinitions.KEY_TITLE);

        addStringIfNonBlank(review, ReviewFields.DBA_DESCRIPTION,
                            bookData, DBDefinitions.KEY_DESCRIPTION);

        addStringIfNonBlank(review, ReviewFields.DBA_FORMAT,
                            bookData, DBDefinitions.KEY_FORMAT);

        addStringIfNonBlank(review, ReviewFields.DBA_PUBLISHER,
                            bookData, DBDefinitions.KEY_PUBLISHER);

        addLongIfPresent(review, ReviewFields.DBA_GR_BOOK_ID,
                         bookData, DBDefinitions.KEY_GOODREADS_ID);

        // v200: Now storing as a string
        addStringIfNonBlank(review, ReviewFields.DBA_PAGES,
                            bookData, DBDefinitions.KEY_PAGES);

        addDateIfValid(review, ReviewFields.DBA_READ_START,
                       bookData, DBDefinitions.KEY_READ_START);

        String readEnd = addDateIfValid(review, ReviewFields.DBA_READ_END,
                                        bookData, DBDefinitions.KEY_READ_END);

        Double rating = addDoubleIfPresent(review, ReviewFields.DBA_RATING,
                                           bookData, DBDefinitions.KEY_RATING);

        // If it has a rating or a 'read_end' date, assume it's read. If these are missing then
        // DO NOT overwrite existing data since it *may* be read even without these fields.
        if ((rating != null && rating > 0) || (readEnd != null && !readEnd.isEmpty())) {
            bookData.putBoolean(Book.IS_READ, true);
        }

        /*
         * Find the best (longest) isbn.
         */
        List<String> list = extractIsbnList(review);
        if (!list.isEmpty()) {
            String best = list.get(0);
            int bestLen = best.length();
            for (String curr : list) {
                if (curr.length() > bestLen) {
                    best = curr;
                    bestLen = best.length();
                }
            }

            if (bestLen > 0) {
                bookData.putString(DBDefinitions.KEY_ISBN, best);
            }
        }

        /*
         * Build the publication date based on the components
         */
        String pubDate = GoodreadsManager.buildDate(review,
                                                    ReviewFields.PUB_YEAR,
                                                    ReviewFields.PUB_MONTH,
                                                    ReviewFields.PUB_DAY,
                                                    null);
        if (pubDate != null && !pubDate.isEmpty()) {
            bookData.putString(DBDefinitions.KEY_DATE_PUBLISHED, pubDate);
        }

        /*
         * process the Authors
         */
        ArrayList<Bundle> grAuthors = review.getParcelableArrayList(ReviewFields.AUTHORS);
        if (grAuthors == null) {
            Logger.warnWithStackTrace(this, "grAuthors was null");
            return bookData;
        }
        ArrayList<Author> authors;
        if (bookCursorRow == null) {
            // It's a new book. Start a clean list.
            authors = new ArrayList<>();
        } else {
            // it's an update. Get current authors.
            authors = db.getAuthorsByBookId(bookCursorRow.getLong(DBDefinitions.KEY_PK_ID));
        }

        for (Bundle grAuthor : grAuthors) {
            String name = grAuthor.getString(ReviewFields.AUTHOR_NAME_GF);
            if (name != null && !name.trim().isEmpty()) {
                authors.add(Author.fromString(name));
            }
        }
        bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, authors);

        /*
         * Cleanup the title by splitting off the Series (if present).
         */
        if (bookData.containsKey(DBDefinitions.KEY_TITLE)) {
            String bookTitle = bookData.getString(DBDefinitions.KEY_TITLE);
            Series.SeriesDetails details = Series.findSeriesFromBookTitle(bookTitle);
            if (details != null && !details.getName().isEmpty()) {
                ArrayList<Series> allSeries;
                if (bookCursorRow == null) {
                    allSeries = new ArrayList<>();
                } else {
                    allSeries = db.getSeriesByBookId(bookCursorRow.getLong(DBDefinitions.KEY_PK_ID));
                }

                Series newSeries = new Series(details.getName());
                newSeries.setNumber(details.getPosition());
                allSeries.add(newSeries);
                bookData.putString(DBDefinitions.KEY_TITLE,
                                   bookTitle.substring(0, details.startChar - 1));

                Series.pruneSeriesList(allSeries);
                bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, allSeries);
            }
        }

        /*
         * Process any bookshelves.
         */
        if (review.containsKey(ReviewFields.SHELVES)) {
            ArrayList<Bundle> grShelves = review.getParcelableArrayList(ReviewFields.SHELVES);
            if (grShelves == null) {
                Logger.warnWithStackTrace(this, "grShelves was null");
                return bookData;
            }

            //TEST: replaced this single line with getting the existing list
            //ArrayList<Bookshelf> bsList = new ArrayList<>();
            //--- begin 2019-02-04 ----
            ArrayList<Bookshelf> bsList;
            if (bookCursorRow == null) {
                bsList = new ArrayList<>();
            } else {
                bsList = db.getBookshelvesByBookId(bookCursorRow.getLong(DBDefinitions.KEY_PK_ID));
            }
            // --- end 2019-02-04 ---

            for (Bundle shelfBundle : grShelves) {
                String bsName = translateBookshelf(db, shelfBundle.getString(ReviewFields.SHELF));

                if (bsName != null && !bsName.isEmpty()) {
                    bsList.add(new Bookshelf(bsName, BooklistStyles.getDefaultStyle(db)));
                }
            }
            //TEST see above
            //--- begin 2019-02-04 ---
            ItemWithIdFixup.pruneList(db, bsList);
            //--- end 2019-02-04 ---

            bookData.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, bsList);
        }

        /*
         * New books only: use the Goodreads added date + get the thumbnail
         */
        if (bookCursorRow == null) {
            // Use the GR added date for new books
            addStringIfNonBlank(review, ReviewFields.ADDED,
                                bookData, DBDefinitions.KEY_DATE_ADDED);

            // fetch thumbnail
            String thumbnail;
            String largeImage = review.getString(ReviewFields.LARGE_IMAGE);
            String smallImage = review.getString(ReviewFields.SMALL_IMAGE);
            if (hasCover(largeImage)) {
                thumbnail = largeImage;
            } else if (hasCover(smallImage)) {
                thumbnail = smallImage;
            } else {
                thumbnail = null;
            }

            if (thumbnail != null) {
                String fileSpec = ImageUtils.saveImage(thumbnail, GoodreadsTasks.FILENAME_SUFFIX);
                if (fileSpec != null) {
                    ArrayList<String> imageList = new ArrayList<>();
                    imageList.add(fileSpec);
                    bookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, imageList);
                    ImageUtils.cleanupImages(bookData);
                }
            }
        }

        // We need to set BOTH of these fields, otherwise the add/update method will set the
        // last_update_date for us, and that would be ahead of the GR update date.
        String now = DateUtils.utcSqlDateTimeForToday();
        bookData.putString(DBDefinitions.KEY_GOODREADS_LAST_SYNC_DATE, now);
        bookData.putString(DBDefinitions.KEY_DATE_LAST_UPDATED, now);

        return bookData;
    }

    /**
     * Copy a non-blank and valid date string to the book bundle; will
     * attempt to translate as appropriate and will not add the date if it cannot
     * be parsed.
     *
     * @return reformatted sql date, or {@code null} if not able to parse
     */
    @Nullable
    private String addDateIfValid(@NonNull final Bundle source,
                                  @NonNull final String sourceKey,
                                  @NonNull final Bundle bookData,
                                  @NonNull final String destKey) {

        if (!source.containsKey(sourceKey)) {
            return null;
        }

        String val = source.getString(sourceKey);
        if (val == null || val.isEmpty()) {
            return null;
        }

        Date d = DateUtils.parseDate(val);
        if (d == null) {
            return null;
        }

        val = DateUtils.utcSqlDateTime(d);
        bookData.putString(destKey, val);
        return val;
    }

    /**
     * Add the value to the list if the value is actually 'real'.
     *
     * @param list  to add to
     * @param value to add
     */
    private void addIfHasValue(@NonNull final List<String> list,
                               @Nullable final String value) {
        if (value != null) {
            String v = value.trim();
            if (!v.isEmpty()) {
                list.add(v);
            }
        }
    }

    /**
     * Copy a non-blank string to the book bundle.
     */
    private void addStringIfNonBlank(@NonNull final Bundle source,
                                     @NonNull final String sourceKey,
                                     @NonNull final Bundle bookData,
                                     @NonNull final String destKey) {

        if (source.containsKey(sourceKey)) {
            String val = source.getString(sourceKey);
            if (val != null && !val.isEmpty()) {
                bookData.putString(destKey, val);
            }
        }
    }

    /**
     * Copy a Long value to the book bundle.
     */
    private void addLongIfPresent(@NonNull final Bundle source,
                                  @NonNull final String sourceKey,
                                  @NonNull final Bundle bookData,
                                  @NonNull final String destKey) {

        if (source.containsKey(sourceKey)) {
            long val = source.getLong(sourceKey);
            bookData.putLong(destKey, val);
        }
    }

    /**
     * Copy a Double value to the book bundle.
     */
    @Nullable
    private Double addDoubleIfPresent(@NonNull final Bundle source,
                                      @SuppressWarnings("SameParameterValue")
                                      @NonNull final String sourceKey,
                                      @NonNull final Bundle bookData,
                                      @SuppressWarnings("SameParameterValue")
                                      @NonNull final String destKey) {

        if (source.containsKey(sourceKey)) {
            double val = source.getDouble(sourceKey);
            bookData.putDouble(destKey, val);
            return val;
        } else {
            return null;
        }
    }

    /**
     * Make a more informative description.
     *
     * @param context Current context, for accessing resources.
     */
    @Override
    @NonNull
    @CallSuper
    public String getDescription(@NonNull final Context context) {

        String base = super.getDescription(context);
        if (mUpdatesAfter == null) {
            return base + " (" + context.getString(R.string.x_of_y, mPosition, mTotalBooks) + ')';
        } else {
            return base + " (" + mPosition + ')';
        }
    }

    @Override
    public int getCategory() {

        return Task.CAT_GOODREADS_IMPORT_ALL;
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void writeObject(@NonNull final ObjectOutputStream out)
            throws IOException {

        out.defaultWriteObject();
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void readObject(@NonNull final ObjectInputStream in)
            throws IOException, ClassNotFoundException {

        in.defaultReadObject();
        mFirstCall = true;
    }
}
