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
import android.util.SparseArray;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * Represents the underlying data for a book.
 */
public class Book
        extends DataManager
        implements ItemWithTitle {

    /**
     * Rating goes from 0 to 5 stars, in 0.5 increments.
     */
    public static final int RATING_STARS = 5;
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
    /** Extracting a series/nr from the book title. */
    private static final Pattern SERIES_NR_PATTERN = Pattern.compile("#", Pattern.LITERAL);
    /** Log tag. */
    private static final String TAG = "Book";

    /**
     * Public Constructor.
     */
    public Book() {
        addValidators();
    }

    @VisibleForTesting
    public Book(@NonNull final Bundle rawData) {
        super(rawData);
        addValidators();
    }

    /**
     * Constructor.
     * <p>
     * If a valid bookId exists it will populate the Book from the database.
     * Otherwise will leave the Book blank for new books.
     *
     * @param bookId of book (may be 0 for new)
     * @param db     Database Access
     */
    public Book(final long bookId,
                @NonNull final DAO db) {
        addValidators();
        if (bookId > 0) {
            reload(db, bookId);
        }
    }

    /**
     * Creates a chooser with matched apps for sharing some text like:
     * <b>"I'm reading " + title + " by " + author + series + " " + ratingString</b>
     *
     * @param context Current context
     *
     * @return the intent
     */
    @NonNull
    public Intent getShareBookIntent(@NonNull final Context context) {
        String title = getString(DBDefinitions.KEY_TITLE);
        double rating = getDouble(DBDefinitions.KEY_RATING);
        String author = getString(DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);
        String series = getString(DBDefinitions.KEY_SERIES_FORMATTED);

        if (!series.isEmpty()) {
            series = " (" + SERIES_NR_PATTERN.matcher(series).replaceAll("%23 ") + ')';
        }

        //remove trailing 0's
        String ratingString = "";
        if (rating > 0) {
            // force rounding
            int ratingTmp = (int) rating;
            // get fraction
            double decimal = rating - ratingTmp;
            if (decimal > 0) {
                ratingString = String.valueOf(rating) + '/' + RATING_STARS;
            } else {
                ratingString = String.valueOf(ratingTmp) + '/' + RATING_STARS;
            }
        }

        if (!ratingString.isEmpty()) {
            ratingString = '(' + ratingString + ')';
        }

        // The share intent is limited to a single *type* of data.
        // We cannot send the cover AND the text; for now we send the text only.
//        String uuid = getString(DBDefinitions.KEY_BOOK_UUID);
//        // prepare the front-cover to post
//        File coverFile = AppDir.getCoverFile(context, uuid, 0);
//        if (coverFile.exists()) {
//            Uri uri = GenericFileProvider.getUriForFile(context, coverFile);
//        }

        String text = context.getString(R.string.info_share_book_im_reading,
                                        title, author, series, ratingString);

        return Intent.createChooser(new Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, text),
                                    context.getString(R.string.menu_share_this));
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

        bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                        getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY));
        bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                        getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY));
        bookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY,
                                        getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY));

        // publication data
        bookData.putString(DBDefinitions.KEY_PUBLISHER,
                           getString(DBDefinitions.KEY_PUBLISHER));
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
        boolean old = getBoolean(DBDefinitions.KEY_READ);

        if (db.setBookRead(getId(), isRead)) {
            putBoolean(DBDefinitions.KEY_READ, isRead);
            if (isRead) {
                putString(DBDefinitions.KEY_READ_END, DateUtils.localSqlDateForToday());
            } else {
                putString(DBDefinitions.KEY_READ_END, "");
            }
            return isRead;
        }

        return old;
    }

    /**
     * Erase everything in this instance and reset the special handlers.
     */
    @Override
    @CallSuper
    public void clear() {
        super.clear();
        addValidators();
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
     * Check if this book has not been saved to the database yet.
     *
     * @return {@code true} if this is a new book
     */
    public boolean isNew() {
        return getId() == 0;
    }

    /**
     * Get a map with all valid native ids for this book.
     * All values will be cast to String.
     *
     * @return map, can be empty.
     */
    @NonNull
    public SparseArray<String> getNativeIds() {
        SparseArray<String> nativeIds = new SparseArray<>();
        for (String key : DBDefinitions.NATIVE_ID_KEYS) {
            String value = getString(key);
            if (!value.isEmpty() && !"0".equals(value)) {
                nativeIds.put(SearchSites.getSiteIdFromDBDefinitions(key), value);
            }
        }

        // explicitly add Amazon if we have a valid ISBN
        ISBN isbn = ISBN.createISBN(getString(DBDefinitions.KEY_ISBN));
        if (isbn.isValid(true)) {
            nativeIds.put(SearchSites.AMAZON, isbn.asText());
        }
        return nativeIds;
    }

    /**
     * Using the id, reload *all* other data for this book.
     *
     * @param db Database Access
     *
     * @return the book
     */
    @SuppressWarnings("UnusedReturnValue")
    public Book reload(@NonNull final DAO db) {
        return reload(db, getId());
    }

    /**
     * Load the book details from the database.
     *
     * @param db     Database Access
     * @param bookId of book (may be 0 for new, in which case we do nothing)
     *
     * @return the book
     */
    @NonNull
    public Book reload(@NonNull final DAO db,
                       final long bookId) {
        // If id = 0, no details in DB
        if (bookId == 0) {
            return this;
        }

        try (Cursor bookCursor = db.fetchBookById(bookId)) {
            if (bookCursor.moveToFirst()) {
                // clean slate
                clear();
                // Put all cursor fields in collection
                putAll(bookCursor);
                // load lists (or init with empty lists)
                //ENHANCE: use SQL GROUP_CONCAT() to get these lists at the same time as the book.
                //pro: one call for book and sublist(s)
                //con: the sublist comes in as one column. Will need json format to keep it flexible
                // and then decode here (or StringList custom (de)coding? hum...)
                putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY,
                                       db.getBookshelvesByBookId(bookId));
                putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, db.getAuthorsByBookId(bookId));
                putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, db.getSeriesByBookId(bookId));
                putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, db.getTocEntryByBook(bookId));
            }
        }
        return this;
    }

    /**
     * Get the name of the first author in the list of authors for this book.
     *
     * @param context Current context
     *
     * @return the name or {@code null} if none
     */
    @Nullable
    public String getPrimaryAuthor(@NonNull final Context context) {
        ArrayList<Author> authors = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        return authors.isEmpty() ? null : authors.get(0).getLabel(context);
    }

    /**
     * Get the Authors. If there is more then one, we get the first Author + an ellipsis.
     *
     * @param context Current context
     *
     * @return a formatted string for author list.
     */
    @NonNull
    public String getAuthorTextShort(@NonNull final Context context) {
        List<Author> list = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        // could/should? use AuthorListFormatter
        if (list.isEmpty()) {
            return "";
        } else {
            String text = list.get(0).getLabel(context);
            if (list.size() > 1) {
                return context.getString(R.string.and_others, text);
            }
            return text;
        }
    }

    /**
     * Update author details from DB.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public void refreshAuthorList(@NonNull final Context context,
                                  @NonNull final DAO db) {
        ArrayList<Author> list = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        for (Author author : list) {
            db.refreshAuthor(context, author);
        }
        putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
    }

    /**
     * Get the name of the first Series in the list of Series for this book.
     *
     * @return name, or {@code null} if none found
     */
    @Nullable
    public String getPrimarySeriesTitle() {
        ArrayList<Series> list = getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        return list.isEmpty() ? null : list.get(0).getTitle();
    }

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

    public ISBN getValidIsbnOrNull() {
        ISBN isbn = ISBN.createISBN(getString(DBDefinitions.KEY_ISBN));
        if (isbn.isValid(true)) {
            return isbn;
        } else {
            return null;
        }
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
            String lang = getString(DBDefinitions.KEY_LANGUAGE);

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
            // this is not an issue as such, but helps during debug when the book *should*
            // have a language and did not.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LOCALE) {
                Log.d(TAG, "getAndUpdateLocale|no language set"
                           + "|id=" + getId()
                           + "|title=" + getString(DBDefinitions.KEY_TITLE),
                      new Throwable());
            }
            // none, use fallback.
            return fallbackLocale;
        }
    }

    /**
     * Update Series details from DB.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public void refreshSeriesList(@NonNull final Context context,
                                  @NonNull final DAO db) {

        ArrayList<Series> list = getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        for (Series series : list) {
            db.refreshSeries(context, series, getLocale(context));
        }
        putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
    }

    /**
     * Add validators.
     * <p>
     * ENHANCE: add (partial) date validators ? any other validators needed ?
     */
    private void addValidators() {

        addValidator(DBDefinitions.KEY_TITLE, NON_BLANK_VALIDATOR, R.string.lbl_title);
        addValidator(UniqueId.BKEY_AUTHOR_ARRAY, NON_BLANK_VALIDATOR, R.string.lbl_author);

        addValidator(DBDefinitions.KEY_EDITION_BITMASK, LONG_VALIDATOR, R.string.lbl_edition);
        addValidator(DBDefinitions.KEY_TOC_BITMASK, LONG_VALIDATOR,
                     R.string.lbl_table_of_content);

        addValidator(DBDefinitions.KEY_PRICE_LISTED, BLANK_OR_DOUBLE_VALIDATOR,
                     R.string.lbl_price_listed);
        addValidator(DBDefinitions.KEY_PRICE_PAID, BLANK_OR_DOUBLE_VALIDATOR,
                     R.string.lbl_price_paid);

        addCrossValidator(book -> {
            String start = book.getString(DBDefinitions.KEY_READ_START);
            if (start.isEmpty()) {
                return;
            }
            String end = book.getString(DBDefinitions.KEY_READ_END);
            if (end.isEmpty()) {
                return;
            }
            if (start.compareToIgnoreCase(end) > 0) {
                throw new ValidatorException(R.string.vldt_read_start_after_end);
            }
        });
    }

    /**
     * Get the name of the loanee (if any).
     *
     * @param db Database Access
     *
     * @return name, or {@code null} if none
     */
    @Nullable
    public String getLoanee(@NonNull final DAO db) {
        // Hopefully we have it in the last cursor we fetched.
        if (contains(DBDefinitions.KEY_LOANEE)) {
            return getString(DBDefinitions.KEY_LOANEE);
        } else {
            // if not, take the long road.
            return db.getLoaneeByBookId(getId());
        }
    }

    /**
     * Examine the values and make any changes necessary before writing the data.
     * Called during {@link DAO#insertBook} and {@link DAO#updateBook}.
     *
     * @param context Current context
     * @param isNew   {@code true} if the book is new
     */
    public void preprocessForStoring(@NonNull final Context context,
                                     final boolean isNew) {

        // Handle Language field FIRST, we need it for _OB fields.
        Locale bookLocale = getAndUpdateLocale(context, LocaleUtils.getUserLocale(context), true);

        // Handle TITLE
        if (contains(DBDefinitions.KEY_TITLE)) {
            String obTitle = reorderTitleForSorting(context, bookLocale);
            putString(DBDefinitions.KEY_TITLE_OB, DAO.encodeOrderByColumn(obTitle, bookLocale));
        }

        // Handle TOC_BITMASK only, no handling of actual titles here,
        // but making sure TOC_MULTIPLE_AUTHORS is correct.
        ArrayList<TocEntry> tocEntries = getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
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
            int editions = getInt(DBDefinitions.KEY_EDITION_BITMASK) & Edition.BITMASK;
            putInt(DBDefinitions.KEY_EDITION_BITMASK, editions);
        }

        // cleanup/build all price related fields
        preprocessPrice(bookLocale, DBDefinitions.KEY_PRICE_LISTED,
                        DBDefinitions.KEY_PRICE_LISTED_CURRENCY);
        preprocessPrice(bookLocale, DBDefinitions.KEY_PRICE_PAID,
                        DBDefinitions.KEY_PRICE_PAID_CURRENCY);

        // make sure there are only valid external id's present
        preprocessExternalIds(context, isNew);

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
            Money money = new Money(bookLocale, getString(valueKey));
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
     * @param context Current context
     * @param isNew   {@code true} if the book is new
     */
    @VisibleForTesting
    void preprocessExternalIds(@NonNull final Context context,
                               final boolean isNew) {
        for (Domain domain : DBDefinitions.NATIVE_ID_DOMAINS) {
            String key = domain.getName();
            if (contains(key)) {
                switch (domain.getType()) {
                    case ColumnInfo.TYPE_INTEGER: {
                        Object o = get(key);
                        try {
                            // null and empty strings become zero
                            long v = getLong(key);

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
                                Logger.warn(context, TAG, "preprocessExternalIds"
                                                          + "|NumberFormatException"
                                                          + "|name=" + key
                                                          + "|value=" + o);
                            }
                        }
                        break;
                    }
                    case ColumnInfo.TYPE_TEXT: {
                        Object o = get(key);
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
                        Logger.warnWithStackTrace(context, TAG, "type=" + domain.getType());
                        break;
                }
            }
        }
    }

    /**
     * Helper for {@link #preprocessForStoring(Context, boolean)}.
     *
     * <ul>Fields in this Book, which have a default in the database and
     * <li>which are not allowed to be blank but are</li>
     * <li>which are not allowed to be null but are</li>
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
            String key = domain.getName();
            if (contains(key) && domain.hasDefault()) {
                Object value = get(key);
                if ((domain.isNotBlank() && value != null && value.toString().isEmpty())
                    ||
                    ((domain.isNotNull() && value == null))) {
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
     * Toggle the read-status for this book.
     *
     * @param db Database Access
     *
     * @return the new 'read' status. If the update failed, this will be the unchanged status.
     */
    public boolean toggleRead(@NonNull final DAO db) {
        return setRead(db, !getBoolean(DBDefinitions.KEY_READ));
    }

    public void deleteLoan(@NonNull final DAO db) {
        remove(DBDefinitions.KEY_LOANEE);
        db.deleteLoan(getId());
    }

    public boolean isAvailable(@NonNull final DAO db) {
        final String loanee = getLoanee(db);
        return loanee == null || loanee.isEmpty();
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
        /** Bitmask for all editions. */
        private static final int BITMASK = FIRST
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
            Map<Integer, String> map = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> entry : ALL.entrySet()) {
                map.put(entry.getKey(), context.getString(entry.getValue()));
            }
            return map;
        }
    }
}
