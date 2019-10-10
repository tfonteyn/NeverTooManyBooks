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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.datamanager.accessors.BitmaskDataAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.accessors.BooleanDataAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.accessors.DataAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.validators.ValidatorException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.BitmaskItem;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListItem;
import com.hardbacknutter.nevertoomanybooks.dialogs.checklist.CheckListItemBase;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Represents the underlying data for a book.
 */
public class Book
        extends DataManager
        implements ItemWithTitle {

    /**
     * Key for accessor to the underlying {@link DBDefinitions#KEY_READ}.
     * Type: Boolean
     */
    public static final String IS_READ = "+IsRead";

    /**
     * Key for accessor to the underlying {@link DBDefinitions#KEY_TOC_BITMASK}.
     * Type: Boolean
     * true: anthology by one or more authors
     */
    public static final String HAS_MULTIPLE_WORKS = "+HasMultiWorks";

    /**
     * Key for accessor to the underlying {@link DBDefinitions#KEY_TOC_BITMASK}.
     * Type: Boolean
     * true: anthology by multiple authors
     */
    public static final String HAS_MULTIPLE_AUTHORS = "+HasMultiAuthors";

    /**
     * Rating goes from 0 to 5 stars, in 0.5 increments.
     */
    public static final int RATING_STARS = 5;

    /** mapping the edition bit to a resource string for displaying. Ordered. */
    @SuppressLint("UseSparseArrays")
    public static final Map<Integer, Integer> EDITIONS = new LinkedHashMap<>();

    /**
     * {@link DBDefinitions#DOM_BOOK_TOC_BITMASK}
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

    /*
     * {@link DBDefinitions#DOM_BOOK_EDITION_BITMASK}.
     * <p>
     * 0%00000000 = a generic edition, or we simply don't know what edition it is.
     * 0%00000001 = first edition
     * 0%00000010 = first impression
     * 0%00000100 = limited edition
     * <p>
     * 0%10000000 = book club
     * <p>
     * NEWTHINGS: edition: add bit flag
     * Never change the bit value!
     */
    /** first edition ever of this work/content/story. */
    private static final int EDITION_FIRST = 1;
    /** First printing of 'this' edition. */
    private static final int EDITION_FIRST_IMPRESSION = 1 << 1;
    /** This edition had a limited run. (Numbered or not). */
    private static final int EDITION_LIMITED = 1 << 2;
    /** This edition comes in a slipcase. */
    private static final int EDITION_SLIPCASE = 1 << 3;
    /** It's a bookclub edition. boooo.... */
    private static final int EDITION_BOOK_CLUB = 1 << 7;


    private static final Pattern SERIES_NR_PATTERN = Pattern.compile("#", Pattern.LITERAL);

    /*
     * NEWTHINGS: edition: add label for the type
     *
     * This is a LinkedHashMap, so the order below is the order they will show up on the screen.
     */
    static {
        EDITIONS.put(EDITION_FIRST, R.string.lbl_edition_first_edition);
        EDITIONS.put(EDITION_FIRST_IMPRESSION, R.string.lbl_edition_first_impression);
        EDITIONS.put(EDITION_LIMITED, R.string.lbl_edition_limited);
        EDITIONS.put(EDITION_SLIPCASE, R.string.lbl_edition_slipcase);

        EDITIONS.put(EDITION_BOOK_CLUB, R.string.lbl_edition_book_club);
    }

    /**
     * Public Constructor.
     */
    public Book() {
        initAccessorsAndValidators();
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
        initAccessorsAndValidators();
        if (bookId > 0) {
            reload(db, bookId);
        }
    }

    /**
     * Constructor.
     * <p>
     * Populate a book object with the fields from the bundle.
     * Can contain an id, but does not have.
     *
     * @param bookData Bundle with book data
     */
    public Book(@NonNull final Bundle bookData) {
        initAccessorsAndValidators();
        putAll(bookData);
    }

    /**
     * Perform sharing of book. Create chooser with matched apps for sharing some text like:
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
        String uuid = getString(DBDefinitions.KEY_BOOK_UUID);

        if (!series.isEmpty()) {
            series = " (" + SERIES_NR_PATTERN.matcher(series)
                                             .replaceAll(Matcher.quoteReplacement("%23 "))
                     + ')';
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

        // prepare the cover to post
        Uri uri = FileProvider.getUriForFile(context, GenericFileProvider.AUTHORITY,
                                             StorageUtils.getCoverFileForUuid(uuid));

        // so despite it being shown on the list it will not post any text unless the user types it.
        String text = context.getString(R.string.info_share_book_im_reading,
                                        title, author, series, ratingString);
        return new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .putExtra(Intent.EXTRA_STREAM, uri);
    }

    /**
     * Duplicate a book by putting APPLICABLE (not simply all of them) fields
     * in a Bundle ready for further processing.
     *
     * @return bundle with book data
     * <p>
     * <b>Developer:</b> keep in sync with {@link DAO} .SqlColumns#BOOK
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
//        DOM_PK_ID
//        DOM_BOOK_UUID
//        DOM_BOOK_LIBRARY_THING_ID
//        DOM_BOOK_ISFDB_ID
//        DOM_BOOK_GOODREADS_ID

        // Do not copy these specific dates.
//        DOM_BOOK_DATE_ADDED
//        DOM_DATE_LAST_UPDATED
//        DOM_BOOK_GOODREADS_LAST_SYNC_DATE

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
        bookData.putString(DBDefinitions.KEY_LOCATION,
                           getString(DBDefinitions.KEY_LOCATION));
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
    public boolean setRead(@NonNull final DAO db,
                           final boolean isRead) {
        boolean old = getBoolean(Book.IS_READ);

        if (db.setBookRead(getId(), isRead)) {
            putBoolean(Book.IS_READ, isRead);
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
        initAccessorsAndValidators();
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

        try (BookCursor bookCursor = db.fetchBookById(bookId)) {
            if (bookCursor.moveToFirst()) {
                // clean slate
                clear();
                // allow the BookCursor to retrieve the real column types.
                bookCursor.setDb(db);
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
     * Gets a complete list of Bookshelves each reflecting the book being on that shelf or not.
     *
     * @param db Database Access
     *
     * @return the list
     */
    @NonNull
    public ArrayList<CheckListItem<Bookshelf>> getEditableBookshelvesList(@NonNull final DAO db) {

        ArrayList<CheckListItem<Bookshelf>> list = new ArrayList<>();
        // get the list of all shelves the book is currently on.
        List<Bookshelf> currentShelves = getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        // Loop through all bookshelves in the database and build the list for this book
        for (Bookshelf bookshelf : db.getBookshelves()) {
            list.add(new BookshelfCheckListItem(bookshelf, currentShelves.contains(bookshelf)));
        }
        return list;
    }

    /**
     * Gets a complete list of Editions each reflecting the book being that edition or not.
     *
     * @return the list
     */
    @NonNull
    public ArrayList<CheckListItem<Integer>> getEditableEditionList() {

        ArrayList<CheckListItem<Integer>> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : EDITIONS.entrySet()) {
            Integer key = entry.getKey();
            boolean selected = (key & getLong(DBDefinitions.KEY_EDITION_BITMASK)) != 0;
            list.add(new BitmaskItem(key, entry.getValue(), selected));
        }
        return list;
    }

    /**
     * Convenience method to set the Edition.
     *
     * @param editions List of integers, each representing a single bit==edition
     */
    public void putEditions(@NonNull final ArrayList<Integer> editions) {
        int bitmask = 0;
        for (Integer bit : editions) {
            bitmask |= bit;
        }
        putLong(DBDefinitions.KEY_EDITION_BITMASK, bitmask);
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
     * TODO: use {@link DataAccessor}.
     *
     * @param context Current context
     *
     * @return a formatted string for author list.
     */
    @NonNull
    public String getAuthorTextShort(@NonNull final Context context) {
        List<Author> list = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (list.isEmpty()) {
            return "";
        } else {
            String newText = list.get(0).getLabel(context);
            if (list.size() > 1) {
                newText += ' ' + context.getString(R.string.and_others);
            }
            return newText;
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
    public String getPrimarySeries() {
        ArrayList<Series> list = getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        return list.isEmpty() ? null : list.get(0).getTitle();
    }

    @Override
    @NonNull
    public String getTitle() {
        return getString(DBDefinitions.KEY_TITLE);
    }

    /**
     * Validate the Locale (based on the Book's language) and reset the language if needed.
     */
    public void updateLocale() {
        getLocale(Locale.getDefault(), true);
    }

    /**
     * Convenience method.
     *
     * Get the Book's Locale (based on its language).
     *
     * @return the Locale, or the users preferred Locale if no language was set.
     */
    @NonNull
    public Locale getLocale() {
        return getLocale(Locale.getDefault(), false);
    }

    /**
     * Use the book's language setting to determine the Locale.
     *
     * @param fallbackLocale Locale to use if the Book does not have a Locale of its own.
     * @param updateLanguage {@code true} to update the language field with the ISO code
     *                       if needed. {@code false} to leave it unchanged.
     *
     * @return the Locale.
     */
    @NonNull
    private Locale getLocale(@NonNull final Locale fallbackLocale,
                             final boolean updateLanguage) {
        Locale bookLocale = null;
        if (containsKey(DBDefinitions.KEY_LANGUAGE)) {
            String lang = getString(DBDefinitions.KEY_LANGUAGE);
            int len = lang.length();
            // try to convert to iso3 if needed.
            if (len != 2 && len != 3) {
                lang = LanguageUtils.getIso3fromDisplayName(lang, fallbackLocale);
            }

            // some languages have two iso3 codes; convert if needed.
            lang = LanguageUtils.iso3ToBibliographic(lang);

            // we now have an ISO code, or an invalid language.
            bookLocale = new Locale(lang);
            // so test it
            if (!LocaleUtils.isValid(bookLocale)) {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.warnWithStackTrace(this, "getLocale", "invalid locale",
                                              "lang=" + lang,
                                              "bookId=" + getId(),
                                              "title=" + getTitle());
                }
                // invalid, use fallback.
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
                Logger.debugWithStackTrace(this, "getLocale",
                                           "no language set",
                                           "id=" + getId(),
                                           "title=" + get(DBDefinitions.KEY_TITLE));
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
            db.refreshSeries(context, series, getLocale());
        }
        putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
    }

    /**
     * Build any special purpose accessors + validators.
     * <p>
     * ENHANCE: add (partial) date validators ? any other validators needed ?
     */
    private void initAccessorsAndValidators() {

        // Booleans are stored as Long (0,1)
        addAccessor(IS_READ, new BooleanDataAccessor(DBDefinitions.KEY_READ));

        // set/reset a single bit in a bitmask.
        addAccessor(HAS_MULTIPLE_WORKS,
                    new BitmaskDataAccessor(DBDefinitions.KEY_TOC_BITMASK, TOC_MULTIPLE_WORKS));
        addAccessor(HAS_MULTIPLE_AUTHORS,
                    new BitmaskDataAccessor(DBDefinitions.KEY_TOC_BITMASK, TOC_MULTIPLE_AUTHORS));

        addValidator(DBDefinitions.KEY_TITLE, NON_BLANK_VALIDATOR, R.string.lbl_title);
        addValidator(UniqueId.BKEY_AUTHOR_ARRAY, NON_BLANK_VALIDATOR, R.string.lbl_author);

        addValidator(DBDefinitions.KEY_EDITION_BITMASK, INTEGER_VALIDATOR, R.string.lbl_edition);
        addValidator(DBDefinitions.KEY_TOC_BITMASK, INTEGER_VALIDATOR,
                     R.string.lbl_table_of_content);

        addValidator(DBDefinitions.KEY_PRICE_LISTED, BLANK_OR_FLOAT_VALIDATOR,
                     R.string.lbl_price_listed);
        addValidator(DBDefinitions.KEY_PRICE_PAID, BLANK_OR_FLOAT_VALIDATOR,
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
        if (containsKey(DBDefinitions.KEY_LOANEE)) {
            return getString(DBDefinitions.KEY_LOANEE);
        } else {
            // if not, take the long road.
            return db.getLoaneeByBookId(getId());
        }
    }

    /**
     * Check if this book has at least one valid external id.
     *
     * @return {@code true} if we have an id
     */
    public boolean hasExternalId() {
        //NEWTHINGS: add new site specific ID: add
        return 0 != getLong(DBDefinitions.KEY_GOODREADS_BOOK_ID)
               || 0 != getLong(DBDefinitions.KEY_LIBRARY_THING_ID)
               || 0 != getLong(DBDefinitions.KEY_STRIP_INFO_BE_ID)
               || 0 != getLong(DBDefinitions.KEY_ISFDB_ID)
               || !getString(DBDefinitions.KEY_OPEN_LIBRARY_ID).isEmpty();
    }

    /**
     * Used to edit the {@link Bookshelf}'s this Book is on.
     */
    public static class BookshelfCheckListItem
            extends CheckListItemBase<Bookshelf>
            implements Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<BookshelfCheckListItem> CREATOR =
                new Creator<BookshelfCheckListItem>() {
                    @Override
                    public BookshelfCheckListItem createFromParcel(@NonNull final Parcel source) {
                        return new BookshelfCheckListItem(source);
                    }

                    @Override
                    public BookshelfCheckListItem[] newArray(final int size) {
                        return new BookshelfCheckListItem[size];
                    }
                };

        /**
         * Constructor.
         *
         * @param item     the item to encapsulate
         * @param selected the current status
         */
        BookshelfCheckListItem(@NonNull final Bookshelf item,
                               final boolean selected) {
            super(item, selected);
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private BookshelfCheckListItem(@NonNull final Parcel in) {
            super(in);
            item = in.readParcelable(getClass().getClassLoader());
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(item, flags);
        }

        @NonNull
        public String getLabel(@NonNull final Context context) {
            return getItem().getName();
        }
    }
}
