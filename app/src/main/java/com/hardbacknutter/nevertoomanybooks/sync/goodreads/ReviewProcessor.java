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
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.sync.AuthorTypeMapper;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.ApiUtils;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.api.Review;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

public class ReviewProcessor {

    private static final String TAG = "ReviewProcessor";

    /** Lookup table of bookshelves defined currently and their Goodreads canonical names. */
    @NonNull
    private final Map<String, String> mBookshelfLookup;

    @NonNull
    private final AuthorTypeMapper mAuthorTypeMapper;

    @NonNull
    private final Context mContext;

    @NonNull
    private final Locale mUserLocale;

    @NonNull
    private final DateParser mISODateParser;
    @NonNull
    private final BookDao mBookDao;

    @NonNull
    private final ListStyle mDefaultStyle;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public ReviewProcessor(@NonNull final Context context) {
        mContext = context;

        mUserLocale = context.getResources().getConfiguration().getLocales().get(0);

        mBookDao = ServiceLocator.getInstance().getBookDao();

        mBookshelfLookup = GoodreadsShelf.createMapper(mUserLocale);
        mAuthorTypeMapper = new AuthorTypeMapper();
        mISODateParser = new ISODateParser();

        mDefaultStyle = ServiceLocator.getInstance().getStyles()
                                      .getDefault(context);
    }

    /**
     * Process one review (book).
     * https://www.goodreads.com/book/show/8263282-the-end-of-eternity
     *
     * @param review data to process
     */
    public void process(@NonNull final Bundle review)
            throws DiskFullException, CoverStorageException {

        final long grBookId = review.getLong(DBKey.SID_GOODREADS_BOOK);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_IMPORT) {
            Logger.d(TAG, "processReview", "grId=" + grBookId);
        }

        Cursor cursor = null;

        try {
            // Find the book in our local database - there may be more than one!
            // First look by Goodreads book ID
            cursor = mBookDao.fetchByKey(DBKey.SID_GOODREADS_BOOK,
                                         String.valueOf(grBookId));

            boolean found = cursor.getCount() > 0;
            if (!found) {
                // Not found by Goodreads id, try again using the ISBNs
                cursor.close();
                cursor = null;

                final List<String> list = extractIsbnList(review);
                if (!list.isEmpty()) {
                    cursor = mBookDao.fetchByIsbn(list);
                    found = cursor.getCount() > 0;
                }
            }

            if (found) {
                // Loop over all the books we found
                while (cursor.moveToNext()) {
                    final Book localBook = Book.from(cursor);

                    if (shouldUpdate(localBook, review)) {
                        // Update the book using the Goodreads data.
                        // IMPORTANT: we construct a NEW book with the DELTA-data which
                        // we want to commit to the existing book.
                        // This DELTA-data will be build by combining some essential data
                        // from the 'localBook' (e.g. local id)
                        // and the data we get from the Goodreads review.
                        final Book delta = buildBook(mContext, localBook, review);
                        try {
                            // <strong>WARNING:</strong> a failed update is ignored (but logged).
                            mBookDao.update(mContext, delta,
                                            BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                            | BookDao.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                        } catch (@NonNull final DaoWriteException e) {
                            // ignore, but log it.
                            Logger.error(TAG, e);
                        }
                    }
                }
            } else {
                // Create a new book with the Goodreads data.
                final Book book = buildBook(mContext, new Book(), review);
                try {
                    mBookDao.insert(mContext, book, BookDao.BOOK_FLAG_IS_BATCH_OPERATION);

                } catch (@NonNull final DaoWriteException e) {
                    // ignore, but log it.
                    Logger.error(TAG, e);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    private boolean shouldUpdate(@NonNull final Book book,
                                 @NonNull final Bundle review) {
        // If the review has an 'updated' date, check if we should update the book
        if (review.containsKey(Review.UPDATED)) {
            // the incoming review
            final LocalDateTime reviewUpd = Review.parseDate(review.getString(Review.UPDATED));
            // Get last time the book was sent to Goodreads (may be null)
            final LocalDateTime lastSyncDate = mISODateParser.parse(
                    book.getString(DBKey.UTC_DATE_LAST_SYNC_GOODREADS));

            // If the last update in Goodreads was before the last Goodreads sync of book,
            // don't bother updating book.
            // This typically happens if the last update in Goodreads was from us.
            return !(reviewUpd != null && lastSyncDate != null && reviewUpd.isBefore(lastSyncDate));

        } else {
            return true;
        }
    }

    /**
     * Build a {@link Book} based on the delta of the locally held data
     * and the Goodreads 'review' data.
     * i.e. for existing books, we copy the id and the language field to the result.
     *
     * @param context   Current context
     * @param localData the local Book; this can be an existing Book with local data
     *                  when we're updating, or a new Book() when inserting.
     * @param review    the source data from Goodreads
     *
     * @return a new Book object containing the delta data
     */
    @NonNull
    private Book buildBook(@NonNull final Context context,
                           @NonNull final Book localData,
                           @NonNull final Bundle review)
            throws DiskFullException, CoverStorageException {

        // The Book will'll populate with the delta data, and return from this method.
        final Book delta = new Book();
        delta.setStage(EntityStage.Stage.Dirty);

        final long bookId = localData.getLong(DBKey.PK_ID);
        // 0 for new, or the existing id for updates
        delta.putLong(DBKey.PK_ID, bookId);

        addLongIfPresent(review, DBKey.SID_GOODREADS_BOOK, delta);

        // The ListReviewsApi does not return the Book language.
        // So during an insert, the bookLocale will always be null.
        // During an update, we *might* get the the locale if the local book data
        // had the language set.
        final Locale bookLocale = localData.getLocale(context);
        // Copy the book language from the original book to the delta book
        final String language = localData.getString(DBKey.KEY_LANGUAGE);
        if (!language.isEmpty()) {
            delta.putString(DBKey.KEY_LANGUAGE, language);
        }

        String grTitle = review.getString(DBKey.KEY_TITLE);
        // Cleanup the title by splitting off the Series (if present).
        if (grTitle != null && !grTitle.isEmpty()) {
            final Matcher matcher = Series.TEXT1_BR_TEXT2_BR_PATTERN.matcher(grTitle);
            if (matcher.find()) {
                grTitle = matcher.group(1);
                // store the cleansed title
                if (grTitle != null && !grTitle.isEmpty()) {
                    delta.putString(DBKey.KEY_TITLE, grTitle);
                }

                final String seriesTitleWithNumber = matcher.group(2);
                if (seriesTitleWithNumber != null && !seriesTitleWithNumber.isEmpty()) {
                    final ArrayList<Series> seriesList =
                            localData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
                    final Series grSeries = Series.from(seriesTitleWithNumber);
                    seriesList.add(grSeries);
                    Series.pruneList(seriesList, context, false, bookLocale);
                    delta.putParcelableArrayList(Book.BKEY_SERIES_LIST, seriesList);
                }
            } else {
                delta.putString(DBKey.KEY_TITLE, grTitle);
            }
        }

        //https://github.com/eleybourn/Book-Catalogue/issues/812 - syn Goodreads notes
        // Do not sync Notes<->Review. The review notes are public on the site,
        // while 'our' notes are meant to be private.
        addStringIfNonBlank(review, DBKey.KEY_PRIVATE_NOTES, delta);

        addStringIfNonBlank(review, DBKey.KEY_DESCRIPTION, delta);
        addStringIfNonBlank(review, DBKey.KEY_FORMAT, delta);

        Review.copyDateIfValid(review, DBKey.DATE_READ_START,
                               delta, DBKey.DATE_READ_START);

        final String readEnd = Review.copyDateIfValid(review, DBKey.DATE_READ_END,
                                                      delta, DBKey.DATE_READ_END);

        final Double rating = addDoubleIfPresent(review, DBKey.KEY_RATING,
                                                 delta, DBKey.KEY_RATING);

        // If it has a rating or a 'read_end' date, assume it's read. If these are missing then
        // DO NOT overwrite existing data since it *may* be read even without these fields.
        if ((rating != null && rating > 0) || (readEnd != null && !readEnd.isEmpty())) {
            delta.putBoolean(DBKey.BOOL_READ, true);
        }

        // Pages: convert long to String, but don't overwrite if we got none
        final long pages = review.getLong(Review.PAGES);
        if (pages != 0) {
            delta.putString(DBKey.KEY_PAGES, String.valueOf(pages));
        }

        /*
         * Find the best (longest) isbn.
         */
        final List<String> list = extractIsbnList(review);
        if (!list.isEmpty()) {
            String bestIsbn = list.get(0);
            int bestLen = bestIsbn.length();
            for (final String curr : list) {
                if (curr.length() > bestLen) {
                    bestIsbn = curr;
                    bestLen = bestIsbn.length();
                }
            }

            if (bestLen > 0) {
                delta.putString(DBKey.KEY_ISBN, bestIsbn);
            }
        }

        /*
         * Build the publication date based on the components
         */
        ApiUtils.buildDate(review,
                           Review.PUBLICATION_YEAR,
                           Review.PUBLICATION_MONTH,
                           Review.PUBLICATION_DAY,
                           DBKey.DATE_BOOK_PUBLICATION);

        final ArrayList<Bundle> grAuthors = review.getParcelableArrayList(Review.AUTHORS);
        if (grAuthors != null) {
            final ArrayList<Author> authorList =
                    localData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);

            for (final Bundle grAuthor : grAuthors) {
                final String name = grAuthor.getString(Review.AUTHOR_NAME_GF);
                if (name != null && !name.trim().isEmpty()) {
                    final Author author = Author.from(name);
                    final String role = grAuthor.getString(Review.AUTHOR_ROLE);
                    if (role != null && !role.trim().isEmpty()) {
                        author.setType(mAuthorTypeMapper.map(bookLocale, role));
                    }
                    authorList.add(author);
                }
            }

            Author.pruneList(authorList, context, false, bookLocale);
            delta.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authorList);
        }


        final String grPublisher = review.getString(Review.PUBLISHER);
        if (grPublisher != null && !grPublisher.isEmpty()) {
            final ArrayList<Publisher> publisherList =
                    localData.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
            publisherList.add(Publisher.from(grPublisher));

            Publisher.pruneList(publisherList, context, false, bookLocale);
            delta.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publisherList);
        }


        final ArrayList<Bundle> grShelves = review.getParcelableArrayList(Review.SHELVES);
        if (grShelves != null) {
            final ArrayList<Bookshelf> bookshelfList =
                    localData.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST);
            for (final Bundle shelfData : grShelves) {
                // Explicitly use the user locale to handle shelf names
                final String name = mapShelf(mUserLocale, shelfData.getString(Review.SHELF));
                if (name != null && !name.isEmpty()) {
                    bookshelfList.add(new Bookshelf(name, mDefaultStyle));
                }
            }
            Bookshelf.pruneList(bookshelfList);
            delta.putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, bookshelfList);
        }

        // New books only: use the Goodreads added date + get the thumbnail
        if (bookId == 0) {
            // Use the Goodreads added date for new books
            Review.copyDateIfValid(review, Review.ADDED, delta, DBKey.UTC_DATE_ADDED);

            final String fileSpec = ApiUtils.handleThumbnail(
                    review, Review.LARGE_IMAGE_URL, Review.SMALL_IMAGE_URL);
            if (fileSpec != null) {
                try {
                    delta.setCover(0, new File(fileSpec));
                } catch (@NonNull final IOException ignore) {
                    // ignore
                }
            }
        }

        // We need to set BOTH of these fields, otherwise the add/update method will set the
        // last_update_date for us, and that would be ahead of the Goodreads update date.
        final String utcNow = LocalDateTime.now(ZoneOffset.UTC)
                                           .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        delta.putString(DBKey.UTC_DATE_LAST_SYNC_GOODREADS, utcNow);
        delta.putString(DBKey.UTC_DATE_LAST_UPDATED, utcNow);

        return delta;
    }

    /**
     * Passed a Goodreads shelf name, return the best matching local bookshelf name,
     * or the original if no match found.
     *
     * @param userLocale  to use
     * @param grShelfName Goodreads shelf name
     *
     * @return Local name, or Goodreads name if no match
     */
    @Nullable
    private String mapShelf(@NonNull final Locale userLocale,
                            @Nullable final String grShelfName) {

        if (grShelfName == null) {
            return null;
        }

        final String lcGrShelfName = grShelfName.toLowerCase(userLocale);
        if (mBookshelfLookup.containsKey(lcGrShelfName)) {
            return mBookshelfLookup.get(lcGrShelfName);
        } else {
            return grShelfName;
        }
    }

    /**
     * Extract a list of ISBNs from the bundle.
     *
     * @param review data to process
     *
     * @return list of ISBN numbers
     */
    @NonNull
    private List<String> extractIsbnList(@NonNull final Bundle review) {

        final List<String> list = new ArrayList<>(5);
        addIfHasValue(review.getString(Review.ISBN13), list);
        addIfHasValue(review.getString(DBKey.KEY_ISBN), list);
        return list;
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
}
