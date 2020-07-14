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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * Represents the underlying data for a book.
 * <p>
 * A note on the Locale of a Book, Series, Author, ...
 * Some of this is not implemented yet and may never be.
 * <p>
 * A Spanish book (written in Spanish) should return the Spanish Locale.
 * i.e. an original language book, should (obviously) return its original Locale.
 * A Spanish book (translated from English) should return an Spanish Locale.
 * i.e. a translated book, should return its translation Locale.
 * <p>
 * A Series should return the Locale as set by the user for that Series (not implemented yet).
 * If not set, then the Locale of the first book in the series.
 * Edge-case: books original in English, user has first book in Spanish, second book in English
 * -> the Series is wrongly designated as Spanish. Solution; user manually sets the Series Locale.
 * <p>
 * A Author should return the Locale as set by the user for that Author (not implemented yet),
 * This should normally be the primary language the author writes in.
 * i.e. usually the author's native language, but some authors will e.g. use english/french...
 * to reach a larger market without translation needs.
 * If not set, then the Locale of the first book (oldest copyright? oldest 'added'?) of that author.
 * <p>
 * A TocEntry...
 */
public class Book
        extends DataManager
        implements ItemWithTitle {

    /**
     * {@link DBDefinitions#KEY_TOC_BITMASK}
     * <p>
     * 0b001 = indicates if a book has one (bit unset) or multiple (bit set) works
     * 0b010 = indicates if a book has one (bit unset) or multiple (bit set) authors.
     * <p>
     * or in other words:
     * 0b000 = contains one 'work' and is written by a single author.
     * 0b001 = multiple 'work' and is written by a single author (anthology from ONE author)
     * 0b010 = multiple authors cooperating on a single 'work'
     * 0b011 = multiple authors and multiple 'work's (it's an anthology from multiple author)
     * <p>
     * Bit 0b010 should not actually occur, as this is a simple case of
     * collaborating authors on a single 'work' which is covered without the use of this field.
     */
    public static final int TOC_SINGLE_AUTHOR_SINGLE_WORK = 0;
    public static final int TOC_MULTIPLE_WORKS = 1;
    public static final int TOC_MULTIPLE_AUTHORS = 1 << 1;
    /**
     * Rating goes from 0 to 5 stars, in 0.5 increments.
     */
    public static final int RATING_STARS = 5;

    /**
     * Single front/back cover file specs.
     * <p>
     * <br>type: {@code String}
     */
    public static final String[] BKEY_FILE_SPEC = new String[2];

    /**
     * List of front/back cover file specs.
     * <p>
     * <br>type: {@code ArrayList<String>}
     */
    public static final String[] BKEY_FILE_SPEC_ARRAY = new String[2];

    /** Log tag. */
    private static final String TAG = "Book";

    /**
     * Bundle key for {@code ParcelableArrayList<Author>}.
     */
    public static final String BKEY_AUTHOR_ARRAY = TAG + ":author_array";

    /**
     * Bundle key for {@code ParcelableArrayList<Series>}.
     */
    public static final String BKEY_SERIES_ARRAY = TAG + ":series_array";

    /**
     * Bundle key for {@code ParcelableArrayList<Publisher>}.
     */
    public static final String BKEY_PUBLISHER_ARRAY = TAG + ":publisher_array";

    /**
     * Bundle key for {@code ParcelableArrayList<TocEntry>}.
     */
    public static final String BKEY_TOC_ARRAY = TAG + ":toc_array";

    /**
     * Bundle key for {@code ParcelableArrayList<Bookshelf>}.
     */
    public static final String BKEY_BOOKSHELF_ARRAY = TAG + ":bookshelf_array";

    /**
     * Bundle key for an {@code ArrayList<Long>} of book ID's.
     * <p>
     * <br>type: {@code Serializable}
     */
    public static final String BKEY_BOOK_ID_ARRAY = TAG + ":id_array";

    /**
     * Bundle key to pass a Bundle with book data around.
     * i.e. before the data becomes an actual {@link Book}.
     * <p>
     * <br>type: {@code Bundle}
     */
    public static final String BKEY_BOOK_DATA = TAG + ":plainBundle";

    static {
        // Single front cover
        BKEY_FILE_SPEC[0] = TAG + ":fileSpec:0";
        // Single back cover
        BKEY_FILE_SPEC[1] = TAG + ":fileSpec:1";

        // list of front covers
        BKEY_FILE_SPEC_ARRAY[0] = TAG + ":fileSpec_array:0";
        // list of back covers
        BKEY_FILE_SPEC_ARRAY[1] = TAG + ":fileSpec_array:1";
    }

    /**
     * Constructor.
     */
    public Book() {
    }

    /**
     * Constructor for Mock tests. Loads the bundle <strong>without</strong> type checks.
     *
     * @param rawData raw data bundle to use for the Book
     */
    @VisibleForTesting
    public Book(@NonNull final Bundle rawData) {
        super(rawData);
    }

    /**
     * Constructor. Loads the bundle <strong>with</strong> type checks.
     *
     * @param bookData data bundle to use for the Book
     *
     * @return new instance
     */
    public static Book from(@NonNull final Bundle bookData) {
        final Book book = new Book();
        book.putAll(bookData);
        return book;
    }


    /**
     * Using the current id, reload *all* other data for this book.
     *
     * @param db Database Access
     */
    public void reload(@NonNull final DAO db) {
        load(getId(), db);
    }

    /**
     * Load the book details from the database.
     *
     * @param bookId of book (may be 0 for new, in which case we do nothing)
     * @param db     Database Access
     */
    public void load(final long bookId,
                     @NonNull final DAO db) {
        if (bookId != 0) {
            try (Cursor bookCursor = db.fetchBookById(bookId)) {
                if (bookCursor.moveToFirst()) {
                    load(bookId, bookCursor, db);
                }
            }
        }
    }

    /**
     * Load the book details from the database.
     * The current book data is cleared before loading.
     *
     * @param bookCursor an already positioned Cursor to read from
     * @param db         to load linked array data from
     */
    public void load(@NonNull final Cursor bookCursor,
                     @NonNull final DAO db) {
        final int idCol = bookCursor.getColumnIndex(DBDefinitions.KEY_PK_ID);
        final long id = bookCursor.getLong(idCol);
        if (id <= 0) {
            throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + id);
        }
        load(id, bookCursor, db);
    }

    /**
     * Load the book details from the database.
     * The current book data is cleared before loading.
     *
     * @param bookId     of book must be != 0
     * @param bookCursor an already positioned Cursor to read from
     * @param db         to load linked array data from
     */
    public void load(@IntRange(from = 1) final long bookId,
                     @NonNull final Cursor bookCursor,
                     @NonNull final DAO db) {
        clear();
        putAll(bookCursor);
        // load lists (or init with empty lists)
        putParcelableArrayList(BKEY_BOOKSHELF_ARRAY, db.getBookshelvesByBookId(bookId));
        putParcelableArrayList(BKEY_AUTHOR_ARRAY, db.getAuthorsByBookId(bookId));
        putParcelableArrayList(BKEY_SERIES_ARRAY, db.getSeriesByBookId(bookId));
        putParcelableArrayList(BKEY_PUBLISHER_ARRAY, db.getPublishersByBookId(bookId));
        putParcelableArrayList(BKEY_TOC_ARRAY, db.getTocEntryByBook(bookId));
    }


    /**
     * Duplicate a book by putting APPLICABLE (not simply all of them) fields
     * in a Bundle ready for further processing.
     * i.o.w. this is <strong>NOT</strong> a copy constructor.
     *
     * @return bundle with book data
     * <p>
     * <b>Developer:</b> keep in sync with {@link DAO} .SqlAllBooks#BOOK
     */
    @NonNull
    public Bundle duplicate() {
        final Bundle bookData = new Bundle();

        // Q: Why don't we get the DataManager#mRawData, remove the identifiers/dates and use that?
        // A: because we would need to clone mRawData before we can start removing fields,
        //  From Bundle#clone() docs: Clones the current Bundle.
        //  The internal map is cloned, but the keys and values to which it refers are
        //  copied by reference.
        // ==> by reference...  so we would in effect be removing fields from the original book.
        // This would be ok if we discard the original object (in memory only)
        // but lets play this safe.

        // Do not copy any identifiers.
        // KEY_PK_ID
        // KEY_BOOK_UUID
        // KEY_EID_LIBRARY_THING
        // KEY_EID_ISFDB
        // KEY_EID_GOODREADS
        // ...
        // Do not copy these specific dates.
        // KEY_BOOK_DATE_ADDED
        // KEY_DATE_LAST_UPDATED
        // KEY_BOOK_GOODREADS_LAST_SYNC_DATE

        bookData.putString(DBDefinitions.KEY_TITLE,
                           getString(DBDefinitions.KEY_TITLE));
        bookData.putString(DBDefinitions.KEY_ISBN,
                           getString(DBDefinitions.KEY_ISBN));

        bookData.putParcelableArrayList(BKEY_AUTHOR_ARRAY,
                                        getParcelableArrayList(BKEY_AUTHOR_ARRAY));
        bookData.putParcelableArrayList(BKEY_SERIES_ARRAY,
                                        getParcelableArrayList(BKEY_SERIES_ARRAY));
        bookData.putParcelableArrayList(BKEY_PUBLISHER_ARRAY,
                                        getParcelableArrayList(BKEY_PUBLISHER_ARRAY));
        bookData.putParcelableArrayList(BKEY_TOC_ARRAY,
                                        getParcelableArrayList(BKEY_TOC_ARRAY));

        // publication data
        bookData.putString(DBDefinitions.KEY_PRINT_RUN,
                           getString(DBDefinitions.KEY_PRINT_RUN));
        bookData.putLong(DBDefinitions.KEY_TOC_BITMASK,
                         getLong(DBDefinitions.KEY_TOC_BITMASK));
        bookData.putString(DBDefinitions.KEY_DATE_PUBLISHED,
                           getString(DBDefinitions.KEY_DATE_PUBLISHED));
        bookData.putDouble(DBDefinitions.KEY_PRICE_LISTED,
                           getDouble(DBDefinitions.KEY_PRICE_LISTED));
        bookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                           getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        bookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                           getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION));
        bookData.putString(DBDefinitions.KEY_FORMAT,
                           getString(DBDefinitions.KEY_FORMAT));
        bookData.putString(DBDefinitions.KEY_COLOR,
                           getString(DBDefinitions.KEY_COLOR));
        bookData.putString(DBDefinitions.KEY_GENRE,
                           getString(DBDefinitions.KEY_GENRE));
        bookData.putString(DBDefinitions.KEY_LANGUAGE,
                           getString(DBDefinitions.KEY_LANGUAGE));
        bookData.putString(DBDefinitions.KEY_PAGES,
                           getString(DBDefinitions.KEY_PAGES));
        // common blurb
        bookData.putString(DBDefinitions.KEY_DESCRIPTION,
                           getString(DBDefinitions.KEY_DESCRIPTION));

        // partially edition info, partially use-owned info.
        bookData.putLong(DBDefinitions.KEY_EDITION_BITMASK,
                         getLong(DBDefinitions.KEY_EDITION_BITMASK));

        // user data

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(DBDefinitions.KEY_SIGNED,
                         getLong(DBDefinitions.KEY_SIGNED));

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(DBDefinitions.KEY_READ,
                         getLong(DBDefinitions.KEY_READ));

        bookData.putDouble(DBDefinitions.KEY_RATING,
                           getDouble(DBDefinitions.KEY_RATING));

        bookData.putString(DBDefinitions.KEY_PRIVATE_NOTES,
                           getString(DBDefinitions.KEY_PRIVATE_NOTES));

        bookData.putInt(DBDefinitions.KEY_BOOK_CONDITION,
                        getInt(DBDefinitions.KEY_BOOK_CONDITION));
        bookData.putInt(DBDefinitions.KEY_BOOK_CONDITION_COVER,
                        getInt(DBDefinitions.KEY_BOOK_CONDITION_COVER));

        bookData.putString(DBDefinitions.KEY_READ_START,
                           getString(DBDefinitions.KEY_READ_START));
        bookData.putString(DBDefinitions.KEY_READ_END,
                           getString(DBDefinitions.KEY_READ_END));
        bookData.putString(DBDefinitions.KEY_DATE_ACQUIRED,
                           getString(DBDefinitions.KEY_DATE_ACQUIRED));
        bookData.putDouble(DBDefinitions.KEY_PRICE_PAID,
                           getDouble(DBDefinitions.KEY_PRICE_PAID));
        bookData.putString(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                           getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY));

        return bookData;
    }


    /**
     * Check if this book has not been saved to the database yet.
     *
     * @return {@code true} if this is a new book
     */
    public boolean isNew() {
        return getId() == 0;
    }

    /**
     * Convenience Accessor.
     *
     * @return the book id.
     */
    public long getId() {
        return getLong(DBDefinitions.KEY_PK_ID);
    }

    /**
     * Get the unformatted title.
     *
     * @return title
     */
    @Override
    @NonNull
    public String getTitle() {
        return getString(DBDefinitions.KEY_TITLE);
    }

    /**
     * Get the label to use. This is for <strong>displaying only</strong>.
     *
     * @param context Current context
     *
     * @return the label to use.
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return reorderTitleForDisplaying(context, getLocale(context));
    }


    /**
     * Convenience method.
     * <p>
     * Get the Book's Locale (based on its language).
     *
     * @param context Current context
     *
     * @return the Locale, or the users preferred Locale if no language was set.
     */
    @NonNull
    public Locale getLocale(@NonNull final Context context) {
        return getAndUpdateLocale(context, LocaleUtils.getUserLocale(context), false);
    }

    /**
     * Use the book's language setting to determine the Locale.
     *
     * @param context        Current context
     * @param fallbackLocale Locale to use if the Book does not have a Locale of its own.
     * @param updateLanguage {@code true} to update the language field with the ISO code
     *                       if needed. {@code false} to leave it unchanged.
     *
     * @return the Locale.
     */
    @NonNull
    private Locale getAndUpdateLocale(@NonNull final Context context,
                                      @NonNull final Locale fallbackLocale,
                                      final boolean updateLanguage) {
        Locale bookLocale = null;
        if (contains(DBDefinitions.KEY_LANGUAGE)) {
            final String lang = getString(DBDefinitions.KEY_LANGUAGE);

            bookLocale = LocaleUtils.getLocale(context, lang);
            if (bookLocale == null) {
                return fallbackLocale;

            } else if (updateLanguage) {
                putString(DBDefinitions.KEY_LANGUAGE, lang);
            }
        }

        if (bookLocale != null) {
            return bookLocale;
        } else {
            // none, use fallback.
            return fallbackLocale;
        }
    }


    /**
     * Get the first author in the list of authors for this book.
     *
     * @return the Author or {@code null} if none present
     */
    @Nullable
    public Author getPrimaryAuthor() {
        final ArrayList<Author> authors = getParcelableArrayList(BKEY_AUTHOR_ARRAY);
        return authors.isEmpty() ? null : authors.get(0);
    }

    /**
     * Check if this Book has just a single Author.
     *
     * @return {@code true} if exactly 1 Author
     */
    public boolean isSingleAuthor() {
        return getParcelableArrayList(BKEY_AUTHOR_ARRAY).size() == 1;
    }

    /**
     * Update author details from DB.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public void refreshAuthorList(@NonNull final Context context,
                                  @NonNull final DAO db) {

        final Locale bookLocale = getLocale(context);
        final ArrayList<Author> list = getParcelableArrayList(BKEY_AUTHOR_ARRAY);
        for (Author author : list) {
            db.refresh(context, author, bookLocale);
        }
    }

    /**
     * Get the first Series in the list of Series for this book.
     *
     * @return the Series, or {@code null} if none present
     */
    @Nullable
    public Series getPrimarySeries() {
        final ArrayList<Series> list = getParcelableArrayList(BKEY_SERIES_ARRAY);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Update Series details from DB.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public void refreshSeriesList(@NonNull final Context context,
                                  @NonNull final DAO db) {

        final Locale bookLocale = getLocale(context);
        final ArrayList<Series> list = getParcelableArrayList(BKEY_SERIES_ARRAY);
        for (Series series : list) {
            db.refresh(context, series, bookLocale);
        }
    }

    /**
     * Get the first Publisher in the list of Publishers for this book.
     *
     * @return the Publisher, or {@code null} if none present
     */
    @Nullable
    public Publisher getPrimaryPublisher() {
        final ArrayList<Publisher> list = getParcelableArrayList(BKEY_PUBLISHER_ARRAY);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Update Publisher details from DB.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public void refreshPublishersList(@NonNull final Context context,
                                      @NonNull final DAO db) {

        final Locale bookLocale = getLocale(context);
        final ArrayList<Publisher> list = getParcelableArrayList(BKEY_PUBLISHER_ARRAY);
        for (Publisher publisher : list) {
            db.refresh(context, publisher, bookLocale);
        }
    }


    /**
     * Get the name of the loanee (if any).
     *
     * @param db Database Access
     *
     * @return name, or {@code ""} if none
     */
    @NonNull
    public String getLoanee(@NonNull final DAO db) {
        // We SHOULD have it...
        if (contains(DBDefinitions.KEY_LOANEE)) {
            return getString(DBDefinitions.KEY_LOANEE);

        } else {
            // but if not, go explicitly fetch it.
            String loanee = db.getLoaneeByBookId(getId());
            if (loanee == null) {
                loanee = "";
            }
            putString(DBDefinitions.KEY_LOANEE, loanee);
            return loanee;
        }
    }

    public boolean isAvailable(@NonNull final DAO db) {
        return getLoanee(db).isEmpty();
    }

    @SuppressWarnings("unused")
    public void deleteLoan(@NonNull final DAO db) {
        remove(DBDefinitions.KEY_LOANEE);
        db.lendBook(getId(), null);
    }


    /**
     * Toggle the read-status for this book.
     *
     * @param db Database Access
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    public boolean toggleRead(@NonNull final DAO db) {
        return setRead(db, !getBoolean(DBDefinitions.KEY_READ));
    }

    /**
     * Update the 'read' status of a book in the database + sets the 'read end' to today.
     * The book will have its 'read' status updated ONLY if the update went through.
     *
     * @param db     Database Access
     * @param isRead Flag for the 'read' status
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    private boolean setRead(@NonNull final DAO db,
                            final boolean isRead) {
        final boolean old = getBoolean(DBDefinitions.KEY_READ);

        if (db.setBookRead(getId(), isRead)) {
            putBoolean(DBDefinitions.KEY_READ, isRead);
            if (isRead) {
                putString(DBDefinitions.KEY_READ_END,
                          LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            } else {
                putString(DBDefinitions.KEY_READ_END, "");
            }
            return isRead;
        }

        return old;
    }


    /**
     * Add validators.
     * <p>
     * ENHANCE: add (partial) date validators ? any other validators needed ?
     */
    public void addValidators() {

        addValidator(DBDefinitions.KEY_TITLE,
                     NON_BLANK_VALIDATOR, R.string.lbl_title);
        addValidator(BKEY_AUTHOR_ARRAY,
                     NON_BLANK_VALIDATOR, R.string.lbl_author);

        addValidator(DBDefinitions.KEY_LANGUAGE,
                     NON_BLANK_VALIDATOR, R.string.lbl_language);

        addValidator(DBDefinitions.KEY_EDITION_BITMASK,
                     LONG_VALIDATOR, R.string.lbl_edition);
        addValidator(DBDefinitions.KEY_TOC_BITMASK,
                     LONG_VALIDATOR, R.string.lbl_table_of_content);

        addValidator(DBDefinitions.KEY_PRICE_LISTED,
                     BLANK_OR_DOUBLE_VALIDATOR, R.string.lbl_price_listed);
        addValidator(DBDefinitions.KEY_PRICE_PAID,
                     BLANK_OR_DOUBLE_VALIDATOR, R.string.lbl_price_paid);

        addCrossValidator(book -> {
            final String start = book.getString(DBDefinitions.KEY_READ_START);
            if (start.isEmpty()) {
                return;
            }
            final String end = book.getString(DBDefinitions.KEY_READ_END);
            if (end.isEmpty()) {
                return;
            }
            if (start.compareToIgnoreCase(end) > 0) {
                throw new ValidatorException(R.string.vldt_read_start_after_end);
            }
        });
    }

    /**
     * Examine the values and make any changes necessary before writing the data.
     * Called during {@link DAO#insert(Context, Book, int)}
     * and {@link DAO#update(Context, Book, int)}.
     *
     * @param context Current context
     * @param isNew   {@code true} if the book is new
     */
    public void preprocessForStoring(@NonNull final Context context,
                                     final boolean isNew) {

        // Handle Language field FIRST, we need it for _OB fields.
        final Locale bookLocale = getAndUpdateLocale(
                context, LocaleUtils.getUserLocale(context), true);

        // Handle TITLE
        if (contains(DBDefinitions.KEY_TITLE)) {
            final String obTitle = reorderTitleForSorting(context, bookLocale);
            putString(DBDefinitions.KEY_TITLE_OB, DAO.encodeOrderByColumn(obTitle, bookLocale));
        }

        // Handle TOC_BITMASK only, no handling of actual titles here,
        // but making sure TOC_MULTIPLE_AUTHORS is correct.
        final ArrayList<TocEntry> tocEntries = getParcelableArrayList(BKEY_TOC_ARRAY);
        if (!tocEntries.isEmpty()) {
            @Book.TocBits
            long type = getLong(DBDefinitions.KEY_TOC_BITMASK);
            if (TocEntry.hasMultipleAuthors(tocEntries)) {
                type |= Book.TOC_MULTIPLE_AUTHORS;
            }
            putLong(DBDefinitions.KEY_TOC_BITMASK, type);
        }

        // make sure we only store valid bits
        if (contains(DBDefinitions.KEY_EDITION_BITMASK)) {
            final int editions = getInt(DBDefinitions.KEY_EDITION_BITMASK) & Edition.BITMASK_ALL;
            putInt(DBDefinitions.KEY_EDITION_BITMASK, editions);
        }

        // cleanup/build all price related fields
        preprocessPrice(bookLocale, DBDefinitions.KEY_PRICE_LISTED,
                        DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        preprocessPrice(bookLocale, DBDefinitions.KEY_PRICE_PAID,
                        DBDefinitions.KEY_PRICE_PAID_CURRENCY);

        // make sure there are only valid external id's present
        preprocessExternalIds(isNew);

        // lastly, cleanup null and blank fields as needed.
        preprocessNullsAndBlanks(isNew);
    }

    /**
     * Helper for {@link #preprocessForStoring(Context, boolean)}.
     * <p>
     *
     * @param bookLocale  the book Locale
     * @param valueKey    key for the value field
     * @param currencyKey key for the currency field
     */
    @VisibleForTesting
    void preprocessPrice(@NonNull final Locale bookLocale,
                         @NonNull final String valueKey,
                         @NonNull final String currencyKey) {
        // handle a price without a currency.
        if (contains(valueKey) && !contains(currencyKey)) {
            // we presume the user bought the book in their own currency.
            final Money money = new Money(bookLocale, getString(valueKey));
            if (money.getCurrency() != null) {
                putDouble(valueKey, money.doubleValue());
                putString(currencyKey, money.getCurrency().toUpperCase(bookLocale));
                return;
            }
            // else just leave the original in the data
        }

        // Make sure currencies are uppercase
        if (contains(currencyKey)) {
            putString(currencyKey, getString(currencyKey).toUpperCase(bookLocale));
        }
    }

    /**
     * Helper for {@link #preprocessForStoring(Context, boolean)}.
     * <p>
     * For new books, remove zero values, empty strings and null values
     * Existing books, replace zero values and empty string with a {@code null}
     * <p>
     * Invalid values are always removed.
     * <p>
     * Further processing should be done in {@link #preprocessNullsAndBlanks(boolean)}.
     *
     * @param isNew {@code true} if the book is new
     */
    @VisibleForTesting
    void preprocessExternalIds(final boolean isNew) {
        for (Domain domain : DBDefinitions.NATIVE_ID_DOMAINS) {
            final String key = domain.getName();
            if (contains(key)) {
                switch (domain.getType()) {
                    case ColumnInfo.TYPE_INTEGER: {
                        final Object o = get(key);
                        try {
                            // null and empty strings become zero
                            final long v = getLong(key);

                            if (isNew && (o == null || v < 1)) {
                                // for new books, remove zero values altogether
                                remove(key);

                            } else if (o != null && v < 1) {
                                // existing books, replace zero values with a null
                                putNull(key);
                            }

                        } catch (@NonNull final NumberFormatException e) {
                            // always remove illegal input
                            remove(key);

                            if (BuildConfig.DEBUG /* always */) {
                                Logger.d(TAG, "preprocessExternalIds"
                                              + "|NumberFormatException"
                                              + "|name=" + key
                                              + "|value=`" + o + '`');
                            }
                        }
                        break;
                    }
                    case ColumnInfo.TYPE_TEXT: {
                        final Object o = get(key);
                        if (isNew && (o == null
                                      || o.toString().isEmpty()
                                      || "0".equals(o.toString()))) {
                            // for new books, remove null, "0" and empty strings altogether
                            remove(key);

                        } else if (o != null
                                   && (o.toString().isEmpty() || "0".equals(o.toString()))) {
                            // existing books, replace "0" and empty strings with a null
                            putNull(key);
                        }

                        break;
                    }
                    default:
                        if (BuildConfig.DEBUG /* always */) {
                            Log.d(TAG, "type=" + domain.getType());
                        }
                        break;
                }
            }
        }
    }

    /**
     * Helper for {@link #preprocessForStoring(Context, boolean)}.
     *
     * <ul>Fields in this Book, which have a default in the database and
     *      <li>which are not allowed to be blank but are</li>
     *      <li>which are not allowed to be null but are</li>
     * </ul>
     * <p>
     * For new books, remove those keys.
     * Existing books, replace those keys with the default value for the column.
     *
     * <p>
     * if the book is new: remove those keys,
     * otherwise make sure the values are reset to their defaults.
     *
     * @param isNew {@code true} if the book is new
     */
    @VisibleForTesting
    void preprocessNullsAndBlanks(final boolean isNew) {
        for (Domain domain : DBDefinitions.TBL_BOOKS.getDomains()) {
            final String key = domain.getName();
            if (contains(key) && domain.hasDefault()) {
                final Object value = get(key);
                if ((domain.isNotBlank() && value != null && value.toString().isEmpty())
                    || ((domain.isNotNull() && value == null))) {
                    if (isNew) {
                        remove(key);
                    } else {
                        // restore the column to its default value.
                        //noinspection ConstantConditions
                        putString(key, domain.getDefault());
                    }
                }
            }
        }
    }


    /**
     * Creates a chooser with matched apps for sharing some text.
     * <b>"I'm reading " + title + series + " by " + author + ratingString</b>
     *
     * @param context Current context
     *
     * @return the intent
     */
    @NonNull
    public Intent getShareIntent(@NonNull final Context context) {
        final String title = getString(DBDefinitions.KEY_TITLE);
        final String author = getString(DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

        final Series series = getPrimarySeries();
        final String seriesStr;
        if (series != null) {
            final String number = series.getNumber();
            seriesStr = " (" + series.getTitle() + (number.isEmpty() ? "" : "%23" + number) + ')';
        } else {
            seriesStr = "";
        }

        //remove trailing 0's
        final double rating = getDouble(DBDefinitions.KEY_RATING);
        final String ratingStr;
        if (rating > 0) {
            // force rounding
            final int ratingTmp = (int) rating;
            // get fraction
            final double decimal = rating - ratingTmp;
            if (decimal > 0) {
                ratingStr = '(' + String.valueOf(rating) + '/' + RATING_STARS + ')';
            } else {
                ratingStr = '(' + String.valueOf(ratingTmp) + '/' + RATING_STARS + ')';
            }
        } else {
            ratingStr = "";
        }

        // The share intent is limited to a single *type* of data.
        // We cannot send the cover AND the text; for now we send the text only.
//        String uuid = getString(DBDefinitions.KEY_BOOK_UUID);
//        // prepare the front-cover to post
//        File coverFile = AppDir.getCoverFile(context, uuid, 0);
//        if (coverFile.exists()) {
//            Uri uri = GenericFileProvider.getUriForFile(context, coverFile);
//        }

        final String text = context.getString(R.string.txt_share_book_im_reading,
                                              title, seriesStr, author, ratingStr);

        return Intent.createChooser(new Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, text),
                                    context.getString(R.string.menu_share_this));
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {TOC_MULTIPLE_WORKS, TOC_MULTIPLE_AUTHORS})
    public @interface TocBits {

    }

    public static final class Edition {

        /*
         * {@link DBDefinitions#KEY_EDITION_BITMASK}.
         * <p>
         * 0%00000000 = a generic edition, or we simply don't know what edition it is.
         * 0%00000001 = first edition
         * 0%00000010 = first impression
         * 0%00000100 = limited edition
         * 0%00001000 = slipcase
         * 0%00010000 = signed
         * <p>
         * 0%10000000 = book club
         * <p>
         * NEWTHINGS: edition: add bit flag and add to mask
         * Never change the bit value!
         */
        /** first edition ever of this work/content/story. */
        public static final int FIRST = 1;

        /** First printing of 'this' edition. */
        private static final int FIRST_IMPRESSION = 1 << 1;
        /** This edition had a limited run. (Numbered or not). */
        private static final int LIMITED = 1 << 2;
        /** This edition comes in a slipcase. */
        private static final int SLIPCASE = 1 << 3;
        /** This edition is signed. i.e the whole print-run of this edition is signed. */
        private static final int SIGNED = 1 << 4;
        /** It's a bookclub edition. */
        private static final int BOOK_CLUB = 1 << 7;
        /** Bitmask for all editions. Bit 5/6 not in use for now. */
        public static final int BITMASK_ALL = FIRST
                                              | FIRST_IMPRESSION
                                              | LIMITED
                                              | SLIPCASE
                                              | SIGNED
                                              | BOOK_CLUB;

        /** mapping the edition bit to a resource string for displaying. Ordered. */
        private static final Map<Integer, Integer> ALL = new LinkedHashMap<>();

        /*
         * NEWTHINGS: edition: add label for the type
         *
         * This is a LinkedHashMap, so the order below is the order they will show up on the screen.
         */
        static {
            ALL.put(FIRST, R.string.lbl_edition_first_edition);
            ALL.put(FIRST_IMPRESSION, R.string.lbl_edition_first_impression);
            ALL.put(LIMITED, R.string.lbl_edition_limited);
            ALL.put(SIGNED, R.string.lbl_signed);
            ALL.put(SLIPCASE, R.string.lbl_edition_slipcase);

            ALL.put(BOOK_CLUB, R.string.lbl_edition_book_club);
        }

        @NonNull
        public static Map<Integer, String> getEditions(@NonNull final Context context) {
            final Map<Integer, String> map = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> entry : ALL.entrySet()) {
                map.put(entry.getKey(), context.getString(entry.getValue()));
            }
            return map;
        }
    }
}
