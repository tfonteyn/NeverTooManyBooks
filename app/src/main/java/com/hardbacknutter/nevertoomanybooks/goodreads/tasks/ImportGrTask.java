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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsShelf;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ReviewsListApiHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.ReviewsListApiHandler.Review;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.searches.AuthorTypeMapper;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * Import all a users 'reviews' from Goodreads; a users 'reviews' consists of all the books that
 * they have placed on bookshelves, irrespective of whether they have rated, reviewed
 * or marked the book as 'owned'.
 * <p>
 * Reminder on dates: parsing website dates, use {@link DateTimeFormatter#parse}.
 * Dates from the local database, use {@link DateParser}.
 */
class ImportGrTask
        extends BaseTQTask {

    /** Log tag. */
    private static final String TAG = "GR.ImportGrTask";

    /**
     * Number of books to retrieve in one batch; we are encouraged to make fewer calls, so
     * setting this number high is good. 50 seems to take several seconds to retrieve, so it
     * was chosen.
     */
    private static final int BOOKS_PER_PAGE = 50;

    private static final long serialVersionUID = 7980810111326540691L;

    /**
     * The date before which updates are irrelevant.
     * Can be {@code null}, which implies all dates are included.
     */
    @Nullable
    private final LocalDateTime mLastSyncDate;
    /** Flag indicating this job is a sync job: on completion, it will start an export. */
    private final boolean mIsSync;
    /** Date at which this job processed the first page successfully. */
    @Nullable
    private LocalDateTime mStartDate;
    /** Current position in entire list of reviews. */
    private int mPosition;

    /** Total number of reviews user has. */
    private int mTotalBooks;

    /** Flag indicating this is the first time *this* object instance has been called. */
    private transient boolean mFirstCall = true;

    /** Lookup table of bookshelves defined currently and their Goodreads canonical names. */
    @Nullable
    private transient Map<String, String> mBookshelfLookup;

    /**
     * Constructor.
     *
     * @param description for the task
     * @param context     Current context
     * @param isSync      Flag indicating this job is a sync job
     */
    ImportGrTask(@NonNull final String description,
                 @NonNull final Context context,
                 final boolean isSync) {

        super(description);

        mPosition = 0;
        mIsSync = isSync;
        // If it's a sync job, then find date of last successful sync and only apply
        // records from after that date. If no other job, then get all.
        if (mIsSync) {
            final String lastSyncDateStr = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getString(GoodreadsHandler.PREFS_LAST_SYNC_DATE, null);
            mLastSyncDate = DateParser.getInstance(context).parseISO(lastSyncDateStr);
        } else {
            mLastSyncDate = null;
        }
    }

    /**
     * Do the actual work.
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager,
                       @NonNull final Context context) {
        try (DAO db = new DAO(TAG)) {
            // Load the Goodreads reviews
            final boolean ok = importReviews(context, db, queueManager);

            // If it's a sync job, then start the 'send' part and save the last syn date
            if (mIsSync) {
                final String desc = context.getString(R.string.gr_title_send_book);
                final TQTask task = new SendBooksGrTask(desc, false, true);
                QueueManager.getQueueManager().enqueueTask(QueueManager.Q_MAIN, task);

                final String lastSyncDateStr =
                        mStartDate != null
                        ? mStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null;
                PreferenceManager.getDefaultSharedPreferences(context)
                                 .edit()
                                 .putString(GoodreadsHandler.PREFS_LAST_SYNC_DATE, lastSyncDateStr)
                                 .apply();
            }

            return ok;
        } catch (@NonNull final CredentialsException e) {
            setLastException(e);
            return false;
        }
    }

    /**
     * Repeatedly request review pages until we are done.
     *
     * @param context Current context
     * @param db      Database Access
     *
     * @return {@code true} if all went well
     *
     * @throws CredentialsException with GoodReads
     */
    private boolean importReviews(@NonNull final Context context,
                                  @NonNull final DAO db,
                                  @NonNull final QueueManager queueManager)
            throws CredentialsException {

        final GoodreadsAuth grAuth = new GoodreadsAuth(context);
        final ReviewsListApiHandler api = new ReviewsListApiHandler(context, grAuth);

        // the result from the API call for a single page.
        Bundle pageData;
        // the reviews entry in the 'pageData' bundle.
        ArrayList<Bundle> reviewsFromPage;

        int page = mPosition / BOOKS_PER_PAGE;
        do {
            if (isCancelled()) {
                return false;
            }

            // page numbers are 1.. based; start at 0 and increment at start of each loop
            page++;

            // set position to first in page
            mPosition = BOOKS_PER_PAGE * (page - 1);

            try {
                // If we have not started successfully yet, record the date at which
                // the run() was called. This date is used if the job is a sync job.
                final LocalDateTime startDate;
                if (mStartDate == null) {
                    startDate = LocalDateTime.now(ZoneOffset.UTC);
                } else {
                    startDate = null;
                }

                pageData = api.get(page, BOOKS_PER_PAGE);

                // If we succeeded, and this is the first time, save the date
                if (mStartDate == null) {
                    mStartDate = startDate;
                }
            } catch (@NonNull final CredentialsException | Http404Exception | IOException e) {
                setLastException(e);
                return false;
            }

            // Get the total, and if first call, update the task data
            mTotalBooks = (int) pageData.getLong(Review.TOTAL);
            if (mFirstCall) {
                // Update the task data, so the UI can reflect the status
                queueManager.updateTask(this);
                mFirstCall = false;
            }

            // Get the reviews array and process it
            reviewsFromPage = pageData.getParcelableArrayList(Review.REVIEWS);
            if (reviewsFromPage != null) {
                // Note that processing may involve a SLOW thumbnail download...
                // so we don't run the import in a transaction.
                for (Bundle review : reviewsFromPage) {
                    if (isCancelled()) {
                        return false;
                    }

                    // if we sync'd before, we make sure we only import changes.
                    if (mLastSyncDate != null) {
                        final LocalDateTime reviewUpd =
                                Review.parseDate(review.getString(Review.UPDATED));
                        if (reviewUpd != null && reviewUpd.isBefore(mLastSyncDate)) {

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_IMPORT) {
                                Logger.d(TAG, "skipping|grId=" + review.getLong(
                                        DBDefinitions.KEY_EID_GOODREADS_BOOK));
                            }
                            // skip to the next review
                            continue;
                        }
                    }

                    processReview(context, db, review);

                    // Update after each book, so the UI can reflect the status
                    queueManager.updateTask(this);
                    mPosition++;
                }
            }
            // loop until no results, or last page
        } while (reviewsFromPage != null && reviewsFromPage.size() == BOOKS_PER_PAGE);

        return true;
    }

    /**
     * Process one review (book).
     * https://www.goodreads.com/book/show/8263282-the-end-of-eternity
     *
     * @param context Current context
     * @param db      Database Access
     */
    private void processReview(@NonNull final Context context,
                               @NonNull final DAO db,
                               @NonNull final Bundle review) {

        final long grBookId = review.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_IMPORT) {
            Logger.d(TAG, "processReview|grId=" + grBookId);
        }

        // Find the book in our local database - there may be more than one!
        // First look by Goodreads book ID
        Cursor cursor = db.fetchBooksByGoodreadsBookId(grBookId);
        try {
            boolean found = cursor.getCount() > 0;
            if (!found) {
                // Not found by Goodreads id, try again using the ISBNs
                cursor.close();
                cursor = null;

                final List<String> list = extractIsbnList(review);
                if (!list.isEmpty()) {
                    cursor = db.fetchBooksByIsbnList(list);
                    found = cursor.getCount() > 0;
                }
            }

            if (found) {
                final Book book = new Book();
                while (cursor.moveToNext()) {
                    book.load(cursor, db);
                    updateBook(context, db, book, review);
                }

            } else {
                // it's a new book, add it
                insertBook(context, db, review);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Passed a Goodreads shelf name, return the best matching local bookshelf name,
     * or the original if no match found.
     *
     * @param locale      to use
     * @param db          Database Access
     * @param grShelfName Goodreads shelf name
     *
     * @return Local name, or Goodreads name if no match
     */
    @Nullable
    private String mapShelf(@NonNull final Locale locale,
                            @NonNull final DAO db,
                            @Nullable final String grShelfName) {

        if (grShelfName == null) {
            return null;
        }
        if (mBookshelfLookup == null) {
            final List<Bookshelf> bookshelves = db.getBookshelves();
            mBookshelfLookup = new HashMap<>(bookshelves.size());
            for (Bookshelf bookshelf : bookshelves) {
                mBookshelfLookup.put(GoodreadsShelf.canonicalizeName(locale, bookshelf.getName()),
                                     bookshelf.getName());
            }
        }

        final String lcGrShelfName = grShelfName.toLowerCase(locale);
        if (mBookshelfLookup.containsKey(lcGrShelfName)) {
            return mBookshelfLookup.get(lcGrShelfName);
        } else {
            return grShelfName;
        }
    }

    /**
     * Extract a list of ISBNs from the bundle.
     *
     * @return list of ISBN numbers
     */
    @NonNull
    private List<String> extractIsbnList(@NonNull final Bundle review) {

        final List<String> list = new ArrayList<>(5);
        addIfHasValue(review.getString(Review.ISBN13), list);
        addIfHasValue(review.getString(DBDefinitions.KEY_ISBN), list);
        return list;
    }

    /**
     * Create a new book with the Goodreads data.
     *
     * <strong>WARNING:</strong> a failed insert is ignored (but logged).
     *
     * @param context Current context
     * @param db      Database Access
     * @param review  the source data from Goodreads
     */
    private void insertBook(@NonNull final Context context,
                            @NonNull final DAO db,
                            @NonNull final Bundle review) {

        final Book book = buildBook(context, db, new Book(), review);
        try {
            db.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION);
            for (int cIdx = 0; cIdx < 2; cIdx++) {
                final String fileSpec = book.getString(Book.BKEY_FILE_SPEC[cIdx]);
                if (!fileSpec.isEmpty()) {
                    final File downloadedFile = new File(fileSpec);
                    final String uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);
                    final File destination = AppDir.getCoverFile(context, uuid, cIdx);
                    FileUtils.rename(downloadedFile, destination);
                }
            }
        } catch (@NonNull final DAO.DaoWriteException e) {
            // ignore, but log it.
            Logger.error(context, TAG, e);
        }
    }

    /**
     * Update the book using the Goodreads data.
     *
     * <strong>WARNING:</strong> a failed update is ignored (but logged).
     *
     * @param context Current context
     * @param db      Database Access
     * @param book    the local book to update
     * @param review  the source data from Goodreads
     */
    private void updateBook(@NonNull final Context context,
                            @NonNull final DAO db,
                            @NonNull final Book book,
                            @NonNull final Bundle review) {

        // If the review has an 'updated' date, then check if we should update the book
        if (review.containsKey(Review.UPDATED)) {
            // the incoming review
            final LocalDateTime reviewUpd = Review.parseDate(review.getString(Review.UPDATED));
            // Get last time the book was sent to Goodreads (may be null)
            final LocalDateTime lastSyncDate = DateParser.getInstance(context).parseISO(
                    book.getString(DBDefinitions.KEY_UTC_LAST_SYNC_DATE_GOODREADS));

            // If last update in Goodreads was before last Goodreads sync of book,
            // then don't bother updating book.
            // This typically happens if the last update in Goodreads was from us.
            if (reviewUpd != null && lastSyncDate != null && reviewUpd.isBefore(lastSyncDate)) {
                // Skip this book
                return;
            }
        }

        //IMPORTANT: we will construct a NEW BOOK, with the DELTA-data which
        // we want to commit to the existing book.
        final Book delta = buildBook(context, db, book, review);
        try {
            db.update(context, delta, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                      | DAO.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
        } catch (@NonNull final DAO.DaoWriteException e) {
            // ignore, but log it.
            Logger.error(context, TAG, e);
        }
    }

    /**
     * Build a {@link Book} based on the delta of the locally held data
     * and the Goodreads 'review' data.
     * i.e. for existing books, we copy the id and the language field to the result.
     *
     * @param context       Current context
     * @param db            Database Access
     * @param localData     the local Book; this can be an existing Book with local data
     *                      when we're updating, or a new Book() when inserting.
     * @param goodreadsData the source data from Goodreads
     *
     * @return a new Book object containing the delta data
     */
    @NonNull
    private Book buildBook(@NonNull final Context context,
                           @NonNull final DAO db,
                           @NonNull final Book localData,
                           @NonNull final Bundle goodreadsData) {

        // The Book will'll populate with the delta data, and return from this method.
        final Book delta = new Book();
        final long bookId = localData.getLong(DBDefinitions.KEY_PK_ID);
        // 0 for new, or the existing id for updates
        delta.putLong(DBDefinitions.KEY_PK_ID, bookId);

        // The ListReviewsApi does not return the Book language.
        // So during an insert, the bookLocale will always be null.
        // During an update, we *might* get the the locale if the local book data
        // had the language set.
        final Locale bookLocale = localData.getLocale(context);
        // Copy the book language from the original book to the delta book
        final String language = localData.getString(DBDefinitions.KEY_LANGUAGE);
        if (!language.isEmpty()) {
            delta.putString(DBDefinitions.KEY_LANGUAGE, language);
        }

        String grTitle = goodreadsData.getString(DBDefinitions.KEY_TITLE);
        // Cleanup the title by splitting off the Series (if present).
        if (grTitle != null && !grTitle.isEmpty()) {
            final Matcher matcher = Series.TEXT1_BR_TEXT2_BR_PATTERN.matcher(grTitle);
            if (matcher.find()) {
                grTitle = matcher.group(1);
                // store the cleansed title
                if (grTitle != null && !grTitle.isEmpty()) {
                    delta.putString(DBDefinitions.KEY_TITLE, grTitle);
                }

                final String seriesTitleWithNumber = matcher.group(2);
                if (seriesTitleWithNumber != null && !seriesTitleWithNumber.isEmpty()) {
                    final ArrayList<Series> seriesList =
                            localData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
                    final Series grSeries = Series.from(seriesTitleWithNumber);
                    seriesList.add(grSeries);
                    Series.pruneList(seriesList, context, db, false, bookLocale);
                    delta.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, seriesList);
                }
            } else {
                delta.putString(DBDefinitions.KEY_TITLE, grTitle);
            }
        }

        //https://github.com/eleybourn/Book-Catalogue/issues/812 - syn Goodreads notes
        // Do not sync Notes<->Review. The review notes are public on the site,
        // while 'our' notes are meant to be private.
        addStringIfNonBlank(goodreadsData, DBDefinitions.KEY_PRIVATE_NOTES, delta);

        addStringIfNonBlank(goodreadsData, DBDefinitions.KEY_DESCRIPTION, delta);
        addStringIfNonBlank(goodreadsData, DBDefinitions.KEY_FORMAT, delta);
        addLongIfPresent(goodreadsData, DBDefinitions.KEY_EID_GOODREADS_BOOK, delta);

        Review.copyDateIfValid(goodreadsData, DBDefinitions.KEY_READ_START,
                               delta, DBDefinitions.KEY_READ_START);

        final String readEnd = Review.copyDateIfValid(goodreadsData, DBDefinitions.KEY_READ_END,
                                                      delta, DBDefinitions.KEY_READ_END);

        final Double rating = addDoubleIfPresent(goodreadsData, DBDefinitions.KEY_RATING,
                                                 delta, DBDefinitions.KEY_RATING);

        // If it has a rating or a 'read_end' date, assume it's read. If these are missing then
        // DO NOT overwrite existing data since it *may* be read even without these fields.
        if ((rating != null && rating > 0) || (readEnd != null && !readEnd.isEmpty())) {
            delta.putBoolean(DBDefinitions.KEY_READ, true);
        }

        // Pages: convert long to String, but don't overwrite if we got none
        final long pages = goodreadsData.getLong(Review.PAGES);
        if (pages != 0) {
            delta.putString(DBDefinitions.KEY_PAGES, String.valueOf(pages));
        }

        /*
         * Find the best (longest) isbn.
         */
        final List<String> list = extractIsbnList(goodreadsData);
        if (!list.isEmpty()) {
            String bestIsbn = list.get(0);
            int bestLen = bestIsbn.length();
            for (String curr : list) {
                if (curr.length() > bestLen) {
                    bestIsbn = curr;
                    bestLen = bestIsbn.length();
                }
            }

            if (bestLen > 0) {
                delta.putString(DBDefinitions.KEY_ISBN, bestIsbn);
            }
        }

        /*
         * Build the publication date based on the components
         */
        final String pubDate = GoodreadsHandler.buildDate(goodreadsData,
                                                          Review.PUBLICATION_YEAR,
                                                          Review.PUBLICATION_MONTH,
                                                          Review.PUBLICATION_DAY,
                                                          null);
        if (pubDate != null && !pubDate.isEmpty()) {
            delta.putString(DBDefinitions.KEY_DATE_PUBLISHED, pubDate);
        }


        final ArrayList<Bundle> grAuthors = goodreadsData.getParcelableArrayList(Review.AUTHORS);
        if (grAuthors != null) {
            final ArrayList<Author> authorList =
                    localData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);

            for (Bundle grAuthor : grAuthors) {
                final String name = grAuthor.getString(Review.AUTHOR_NAME_GF);
                if (name != null && !name.trim().isEmpty()) {
                    final Author author = Author.from(name);
                    final String role = grAuthor.getString(Review.AUTHOR_ROLE);
                    if (role != null && !role.trim().isEmpty()) {
                        author.setType(AuthorTypeMapper.map(bookLocale, role));
                    }
                    authorList.add(author);
                }
            }

            Author.pruneList(authorList, context, db, false, bookLocale);
            delta.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, authorList);
        }


        final String grPublisher = goodreadsData.getString(Review.PUBLISHER);
        if (grPublisher != null && !grPublisher.isEmpty()) {
            final ArrayList<Publisher> publisherList =
                    localData.getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
            publisherList.add(Publisher.from(grPublisher));

            Publisher.pruneList(publisherList, context, db, false, bookLocale);
            delta.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, publisherList);
        }


        final ArrayList<Bundle> grShelves = goodreadsData.getParcelableArrayList(Review.SHELVES);
        if (grShelves != null) {
            // Explicitly use the user locale to handle shelf names
            final Locale userLocale = LocaleUtils.getUserLocale(context);

            final ArrayList<Bookshelf> bookshelfList =
                    localData.getParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY);
            for (Bundle shelfData : grShelves) {
                final String name = mapShelf(userLocale, db, shelfData.getString(Review.SHELF));
                if (name != null && !name.isEmpty()) {
                    bookshelfList.add(new Bookshelf(name, BooklistStyle.getDefault(context, db)));
                }
            }
            Bookshelf.pruneList(bookshelfList, db);
            delta.putParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY, bookshelfList);
        }

        // New books only: use the Goodreads added date + get the thumbnail
        if (bookId == 0) {
            // Use the Goodreads added date for new books
            Review.copyDateIfValid(goodreadsData, Review.ADDED,
                                   delta, DBDefinitions.KEY_UTC_ADDED);

            // fetch thumbnail
            final String coverUrl;
            final String sizeSuffix;
            final String largeImage = goodreadsData.getString(Review.LARGE_IMAGE);
            final String smallImage = goodreadsData.getString(Review.SMALL_IMAGE);
            if (GoodreadsHandler.hasCover(largeImage)) {
                sizeSuffix = Review.LARGE_IMAGE;
                coverUrl = largeImage;
            } else if (GoodreadsHandler.hasCover(smallImage)) {
                sizeSuffix = Review.SMALL_IMAGE;
                coverUrl = smallImage;
            } else {
                sizeSuffix = "";
                coverUrl = null;
            }

            if (coverUrl != null) {
                final String tmpName = delta.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK)
                                       + GoodreadsSearchEngine.FILENAME_SUFFIX + "_" + sizeSuffix;
                final String fileSpec =
                        ImageUtils.saveImage(context, coverUrl, tmpName,
                                             GoodreadsSearchEngine.CONNECT_TIMEOUT_MS,
                                             GoodreadsSearchEngine.READ_TIMEOUT_MS,
                                             GoodreadsSearchEngine.THROTTLER);
                if (fileSpec != null) {
                    delta.putString(Book.BKEY_FILE_SPEC[0], fileSpec);
                }
            }
        }

        // We need to set BOTH of these fields, otherwise the add/update method will set the
        // last_update_date for us, and that would be ahead of the Goodreads update date.
        final String utcNow = LocalDateTime
                .now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        delta.putString(DBDefinitions.KEY_UTC_LAST_SYNC_DATE_GOODREADS, utcNow);
        delta.putString(DBDefinitions.KEY_UTC_LAST_UPDATED, utcNow);

        return delta;
    }

    /**
     * Add the value to the list if the value is non-blank.
     *
     * @param value    to add
     * @param destList to add to
     */
    private void addIfHasValue(@Nullable final String value,
                               @NonNull final Collection<String> destList) {
        if (value != null) {
            final String v = value.trim();
            if (!v.isEmpty()) {
                destList.add(v);
            }
        }
    }

    /**
     * Copy a non-blank string to the book bundle.
     */
    private void addStringIfNonBlank(@NonNull final Bundle sourceBundle,
                                     @NonNull final String key,
                                     @NonNull final Book destBundle) {

        if (sourceBundle.containsKey(key)) {
            final String value = sourceBundle.getString(key);
            if (value != null && !value.isEmpty()) {
                destBundle.putString(key, value);
            }
        }
    }

    /**
     * Copy a Long value to the book bundle.
     */
    private void addLongIfPresent(@NonNull final Bundle sourceBundle,
                                  @SuppressWarnings("SameParameterValue")
                                  @NonNull final String key,
                                  @NonNull final Book destBundle) {

        if (sourceBundle.containsKey(key)) {
            final long value = sourceBundle.getLong(key);
            destBundle.putLong(key, value);
        }
    }

    /**
     * Copy a Double value to the book bundle.
     */
    @Nullable
    private Double addDoubleIfPresent(@NonNull final Bundle sourceBundle,
                                      @SuppressWarnings("SameParameterValue")
                                      @NonNull final String sourceKey,
                                      @NonNull final Book destBundle,
                                      @SuppressWarnings("SameParameterValue")
                                          @NonNull final String destKey) {

        if (sourceBundle.containsKey(sourceKey)) {
            final double value = sourceBundle.getDouble(sourceKey);
            destBundle.putDouble(destKey, value);
            return value;
        } else {
            return null;
        }
    }

    @Override
    public int getCategory() {
        return TQTask.CAT_IMPORT;
    }

    /**
     * Make a more informative description.
     *
     * @param context Current context
     */
    @Override
    @NonNull
    @CallSuper
    public String getDescription(@NonNull final Context context) {
        final String base = super.getDescription(context);
        if (mLastSyncDate == null) {
            return base + " (" + context.getString(R.string.x_of_y, mPosition, mTotalBooks) + ')';
        } else {
            return base + " (" + mPosition + ')';
        }
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @throws IOException on failure
     * @see Serializable
     */
    private void writeObject(@NonNull final ObjectOutputStream out)
            throws IOException {

        out.defaultWriteObject();
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @throws IOException            on failure
     * @throws ClassNotFoundException on failure
     * @see Serializable
     */
    private void readObject(@NonNull final ObjectInputStream is)
            throws IOException, ClassNotFoundException {

        is.defaultReadObject();
        mFirstCall = true;
    }
}
