/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.entities;

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
import androidx.annotation.StringRes;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertomanybooks.datamanager.accessors.BitmaskDataAccessor;
import com.hardbacknutter.nevertomanybooks.datamanager.accessors.BooleanDataAccessor;
import com.hardbacknutter.nevertomanybooks.datamanager.accessors.DataAccessor;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.dialogs.CheckListItem;
import com.hardbacknutter.nevertomanybooks.dialogs.CheckListItemBase;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

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
     * Key for accessor to the underlying {@link DBDefinitions#KEY_SIGNED}.
     * Type: Boolean
     */
    public static final String IS_SIGNED = "+IsSigned";

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

    /*
     * {@link DatabaseDefinitions#DOM_BOOK_EDITION_BITMASK}.
     * <p>
     * 0%00000001 = first edition
     * 0%00000010 = first impression
     * 0%00000100 = limited edition
     * <p>
     * 0%10000000 = book club
     * <p>
     * NEWKIND: edition
     * Never change the bit value!
     */
    //private static final int EDITION_NOTHING_SPECIAL = 0;
    /** first edition ever of this work/content/story. */
    private static final int EDITION_FIRST = 1;
    /** First printing of 'this' edition. */
    private static final int EDITION_FIRST_IMPRESSION = 1 << 1;
    /** This edition had a limited run. (Numbered or not). */
    private static final int EDITION_LIMITED = 1 << 2;
    /** It's a bookclub edition. boooo.... */
    private static final int EDITION_BOOK_CLUB = 1 << 7;
    private static final Pattern SERIES_NR_PATTERN = Pattern.compile("#", Pattern.LITERAL);

    /*
     * NEWKIND: edition.
     *
     * This is a LinkedHashMap, so the order below is the oder they will show up on the screen.
     */
    static {
        EDITIONS.put(EDITION_FIRST, R.string.lbl_edition_first_edition);
        EDITIONS.put(EDITION_FIRST_IMPRESSION, R.string.lbl_edition_first_impression);
        EDITIONS.put(EDITION_LIMITED, R.string.lbl_edition_limited);

        EDITIONS.put(EDITION_BOOK_CLUB, R.string.lbl_edition_book_club);
    }

    /**
     * Public Constructor.
     */
    public Book() {
        initValidatorsAndAccessors();
    }

    /**
     * Constructor.
     * <p>
     * If a valid bookId exists it will populate the Book from the database.
     * Otherwise will leave the Book blank for new books.
     *
     * @param bookId of book (may be 0 for new)
     * @param db     database, used to load the book data IF the bookId is valid.
     */
    public Book(final long bookId,
                @NonNull final DAO db) {
        initValidatorsAndAccessors();
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
        initValidatorsAndAccessors();
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
        File coverFile = StorageUtils.getCoverFile(uuid);
        Uri coverURI = FileProvider
                               .getUriForFile(context, GenericFileProvider.AUTHORITY, coverFile);


        // TEST: There's a problem with the facebook app in android,
        // so despite it being shown on the list it will not post any text unless the user types it.
        String text = context.getString(R.string.info_share_book_im_reading,
                                        title, author, series, ratingString);
        return new Intent(Intent.ACTION_SEND)
                       .setType("text/plain")
                       .putExtra(Intent.EXTRA_TEXT, text)
                       .putExtra(Intent.EXTRA_STREAM, coverURI);
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
        bookData.putLong(DBDefinitions.KEY_TOC_BITMASK,
                         getLong(DBDefinitions.KEY_TOC_BITMASK));
        bookData.putString(DBDefinitions.KEY_DATE_PUBLISHED,
                           getString(DBDefinitions.KEY_DATE_PUBLISHED));
        bookData.putString(DBDefinitions.KEY_PRICE_LISTED,
                           getString(DBDefinitions.KEY_PRICE_LISTED));
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

        bookData.putString(DBDefinitions.KEY_NOTES,
                           getString(DBDefinitions.KEY_NOTES));
        bookData.putString(DBDefinitions.KEY_LOCATION,
                           getString(DBDefinitions.KEY_LOCATION));
        bookData.putString(DBDefinitions.KEY_READ_START,
                           getString(DBDefinitions.KEY_READ_START));
        bookData.putString(DBDefinitions.KEY_READ_END,
                           getString(DBDefinitions.KEY_READ_END));
        bookData.putString(DBDefinitions.KEY_DATE_ACQUIRED,
                           getString(DBDefinitions.KEY_DATE_ACQUIRED));
        bookData.putString(DBDefinitions.KEY_PRICE_PAID,
                           getString(DBDefinitions.KEY_PRICE_PAID));
        bookData.putString(DBDefinitions.KEY_PRICE_PAID_CURRENCY,
                           getString(DBDefinitions.KEY_PRICE_PAID_CURRENCY));

        return bookData;
    }

    /**
     * Update the 'read' status of a book in the database + sets the 'read end' to today.
     * The book will have its 'read' status updated ONLY if the update went through.
     *
     * @param db     database
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
        initValidatorsAndAccessors();
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
        // If ID = 0, no details in DB
        if (bookId == 0) {
            return this;
        }

        try (BookCursor book = db.fetchBookById(bookId)) {
            if (book.moveToFirst()) {
                // clean slate
                clear();
                // Put all cursor fields in collection
                putAll(book);
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
            list.add(new EditionCheckListItem(
                    key, entry.getValue(),
                    (key & getLong(DBDefinitions.KEY_EDITION_BITMASK)) != 0));
        }
        return list;
    }

    /**
     * Convenience method to set the Edition.
     */
    public void putEditions(@NonNull final ArrayList<Integer> result) {
        int bitmask = 0;
        for (Integer bit : result) {
            bitmask += bit;
        }
        putLong(DBDefinitions.KEY_EDITION_BITMASK, bitmask);
    }

    /**
     * @return name of the first author in the list of authors for this book,
     * or {@code null} if none
     */
    @Nullable
    public String getPrimaryAuthor() {
        ArrayList<Author> authors = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        return authors.isEmpty() ? null : authors.get(0).getLabel();
    }

    /**
     * TODO: use {@link DataAccessor}.
     *
     * @return a formatted string for author list.
     */
    @NonNull
    public String getAuthorTextShort(@NonNull final Context context) {
        List<Author> list = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (list.isEmpty()) {
            return "";
        } else {
            String newText = list.get(0).getLabel();
            if (list.size() > 1) {
                newText += ' ' + context.getString(R.string.and_others);
            }
            return newText;
        }
    }

    /**
     * Update author details from DB.
     *
     * @param db Database Access
     */
    public void refreshAuthorList(@NonNull final DAO db) {
        ArrayList<Author> list = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        for (Author author : list) {
            db.refreshAuthor(author);
        }
        putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
    }

    /**
     * @return name of the first series in the list of series for this book, or {@code null} if none
     */
    @Nullable
    public String getPrimarySeries() {
        ArrayList<Series> list = getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        return list.isEmpty() ? null : list.get(0).getTitle();
    }

    /**
     * TODO: use {@link DataAccessor}.
     *
     * @param context Current context
     *
     * @return a formatted string for series list.
     */
    @NonNull
    public String getSeriesTextShort(@NonNull final Context context) {
        List<Series> list = getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        if (list.isEmpty()) {
            return "";
        } else {
            String newText = list.get(0).getLabel();
            if (list.size() > 1) {
                newText += ' ' + context.getString(R.string.and_others);
            }
            return newText;
        }
    }

    /**
     * Validate the locale (based on the Book's language) and reset the language if needed.
     *
     * @return the locale, or the users preferred locale if no language was set.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Locale updateLocale() {
        return getLocale(true);
    }

    /**
     * Get the Book's locale (based on its language).
     *
     * @return the locale, or the users preferred locale if no language was set.
     */
    @NonNull
    @Override
    public Locale getLocale() {
        return getLocale(false);
    }

    @Override
    @NonNull
    public String getTitle() {
        return getString(DBDefinitions.KEY_TITLE);
    }

    /**
     * Use the book's language setting to determine the Locale.
     *
     * @param updateLanguage {@code true} to update the language field with the ISO code
     *                       if needed. {@code false} to leave it unchanged.
     *
     * @return the locale, or the users preferred locale if no language was set.
     */
    @NonNull
    private Locale getLocale(final boolean updateLanguage) {
        Locale bookLocale = null;
        if (containsKey(DBDefinitions.KEY_LANGUAGE)) {
            String lang = getString(DBDefinitions.KEY_LANGUAGE);
            int len = lang.length();
            // try to convert to iso3 if needed.
            if (len != 2 && len != 3) {
                lang = LocaleUtils.getISO3Language(lang);
            }
            // we now have an ISO code, or an invalid language.
            bookLocale = new Locale(lang);
            if (!LocaleUtils.isValid(bookLocale)) {
                bookLocale = LocaleUtils.getPreferredLocale();
            } else if (updateLanguage) {
                putString(DBDefinitions.KEY_LANGUAGE, lang);
            }
        }

        if (bookLocale != null) {
            return bookLocale;
        } else {
            // this is not an issue as such, but helps during debug when the book *should*
            // have a language and did not.
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, "getLocale",
                                           "no language set",
                                           "id=" + getId(),
                                           "title=" + get(DBDefinitions.KEY_TITLE));
            }
            return LocaleUtils.getPreferredLocale();
        }
    }

    /**
     * Update series details from DB.
     *
     * @param db Database Access
     */
    public void refreshSeriesList(@NonNull final Context context,
                                  @NonNull final DAO db) {
        ArrayList<Series> list = getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        for (Series series : list) {
            db.refreshSeries(context, series);
        }
        putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
    }

    /**
     * Build any special purpose validators/accessors.
     * <p>
     * ENHANCE: add (partial) date validators ? any other validators needed ?
     */
    private void initValidatorsAndAccessors() {

        addValidator(DBDefinitions.KEY_TITLE, NON_BLANK_VALIDATOR);
        addValidator(UniqueId.BKEY_AUTHOR_ARRAY, NON_BLANK_VALIDATOR);

        // pages is now a text field.
        //addValidator(DBDefinitions.KEY_PAGES, BLANK_OR_INTEGER_VALIDATOR);

        addValidator(DBDefinitions.KEY_TOC_BITMASK, INTEGER_VALIDATOR);
        addValidator(DBDefinitions.KEY_EDITION_BITMASK, INTEGER_VALIDATOR);

        addValidator(DBDefinitions.KEY_PRICE_LISTED, BLANK_OR_FLOAT_VALIDATOR);
        addValidator(DBDefinitions.KEY_PRICE_PAID, BLANK_OR_FLOAT_VALIDATOR);


        // Booleans are stored as Long (0,1)
        addAccessor(IS_READ, new BooleanDataAccessor(DBDefinitions.KEY_READ));

        // Booleans are stored as Long (0,1)
        addAccessor(IS_SIGNED, new BooleanDataAccessor(DBDefinitions.KEY_SIGNED));

        // set/reset the single bit TocEntry.Type.MULTIPLE_WORKS in the bitmask.
        addAccessor(HAS_MULTIPLE_WORKS,
                    new BitmaskDataAccessor(DBDefinitions.KEY_TOC_BITMASK,
                                            TocEntry.Authors.MULTIPLE_WORKS));
        // set/reset the single bit TocEntry.Type.MULTIPLE_AUTHORS in the bitmask.
        addAccessor(HAS_MULTIPLE_AUTHORS,
                    new BitmaskDataAccessor(DBDefinitions.KEY_TOC_BITMASK,
                                            TocEntry.Authors.MULTIPLE_AUTHORS));
    }

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
     * Used to edit the Editions of this Book.
     */
    public static class EditionCheckListItem
            extends CheckListItemBase<Integer>
            implements Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<EditionCheckListItem> CREATOR =
                new Creator<EditionCheckListItem>() {
                    @Override
                    public EditionCheckListItem createFromParcel(@NonNull final Parcel source) {
                        return new EditionCheckListItem(source);
                    }

                    @Override
                    public EditionCheckListItem[] newArray(final int size) {
                        return new EditionCheckListItem[size];
                    }
                };
        @StringRes
        private final int mLabelId;

        /**
         * Constructor.
         *
         * @param bitMask  the item to encapsulate
         * @param labelId  resource id for the label to display
         * @param selected the current status
         */
        EditionCheckListItem(@NonNull final Integer bitMask,
                             @StringRes final int labelId,
                             final boolean selected) {
            super(bitMask, selected);
            mLabelId = labelId;
        }

        /**
         * {@link Parcelable} Constructor.
         *
         * @param in Parcel to construct the object from
         */
        private EditionCheckListItem(@NonNull final Parcel in) {
            super(in);
            mLabelId = in.readInt();
            item = in.readInt();
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(mLabelId);
            dest.writeInt(item);
        }

        /**
         * @return the label to display
         */
        @Override
        public String getLabel(@NonNull final Context context) {
            return context.getString(mLabelId);
        }
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

        /**
         * @param context Current context
         *
         * @return the label to display
         */
        @NonNull
        public String getLabel(@NonNull final Context context) {
            return getItem().getName();
        }
    }
}
