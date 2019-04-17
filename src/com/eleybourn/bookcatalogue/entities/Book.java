/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.entities;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.accessors.BitmaskDataAccessor;
import com.eleybourn.bookcatalogue.datamanager.accessors.BooleanDataAccessor;
import com.eleybourn.bookcatalogue.datamanager.accessors.DataAccessor;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItemBase;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.GenericFileProvider;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Represents the underlying data for a book.
 * <p>
 * ENHANCE: would be nice to make this Parcelable...
 *
 * @author pjw
 */
public class Book
        extends DataManager {

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
    /** mapping the edition bit to a resource string for displaying. */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, Integer> EDITIONS = new HashMap<>();

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

    /* NEWKIND: edition. */
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
                @NonNull final DBA db) {
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
     * static helper to set the read-status for a given book id.
     * <p>
     * ENHANCE: create a dedicated SQL entry instead of loading the full book first.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean setRead(final long bookId,
                                  final boolean isRead,
                                  @NonNull final DBA db) {
        // load from database
        Book book = new Book(bookId, db);
        book.putBoolean(Book.IS_READ, isRead);
        book.putString(DBDefinitions.KEY_READ_END, DateUtils.localSqlDateForToday());
        return db.updateBook(bookId, book, 0) == 1;
    }

    /**
     * Perform sharing of book. Create chooser with matched apps for sharing some text like:
     * <b>"I'm reading " + title + " by " + author + series + " " + ratingString</b>
     */
    public Intent getShareBookIntent(@NonNull final Activity activity) {
        String title = getString(DBDefinitions.KEY_TITLE);
        double rating = getDouble(DBDefinitions.KEY_RATING);
        String author = getString(DBDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST.name);
        String series = getString(DBDefinitions.DOM_SERIES_FORMATTED.name);
        String uuid = getString(DBDefinitions.KEY_BOOK_UUID);

        if (!series.isEmpty()) {
            series = " (" + series.replace("#", "%23 ") + ')';
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
                .getUriForFile(activity, GenericFileProvider.AUTHORITY, coverFile);


        // TEST: There's a problem with the facebook app in android,
        // so despite it being shown on the list it will not post any text unless the user types it.
        String text = activity.getString(R.string.info_share_book_im_reading,
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
     * Dev note: keep in sync with {@link DBA.SqlColumns#BOOK}.
     */
    public Bundle duplicate() {
        final Bundle bookData = new Bundle();

        // Do not copy identifiers.
//        DOM_PK_ID
//        DOM_BOOK_UUID
//        DOM_BOOK_LIBRARY_THING_ID
//        DOM_BOOK_ISFDB_ID
//        DOM_BOOK_GOODREADS_BOOK_ID

        // Do not copy specific dates.
//        DOM_BOOK_DATE_ADDED
//        DOM_LAST_UPDATE_DATE
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
        bookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED,
                           getString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED));
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
     * @param db database
     *
     * @return <tt>true</tt> if the update was successful, <tt>false</tt> on failure
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean setRead(@NonNull final DBA db,
                           final boolean isRead) {
        // allow for rollback.
        boolean prevRead = getBoolean(Book.IS_READ);
        String prevReadEnd = getString(DBDefinitions.KEY_READ_END);

        putBoolean(Book.IS_READ, isRead);
        putString(DBDefinitions.KEY_READ_END, DateUtils.localSqlDateForToday());

        if (db.updateBook(getId(), this, 0) != 1) {
            //rollback
            putBoolean(Book.IS_READ, prevRead);
            putString(DBDefinitions.KEY_READ_END, prevReadEnd);
            return false;
        }
        return true;
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
        return getLong(DBDefinitions.KEY_ID);
    }

    /**
     * Using the id, reload *all* other data for this book.
     *
     * @param db the database
     */
    public void reload(@NonNull final DBA db) {
        reload(db, getId());
    }

    /**
     * Load the book details from the database.
     *
     * @param bookId of book (may be 0 for new, in which case we do nothing)
     */
    public void reload(@NonNull final DBA db,
                       final long bookId) {
        // If ID = 0, no details in DB
        if (bookId == 0) {
            return;
        }

        try (BookCursor book = db.fetchBookById(bookId)) {
            if (book.moveToFirst()) {
                // Put all cursor fields in collection
                putAll(book);
                // load lists (or init with empty lists)
                putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY,
                                       db.getBookshelvesByBookId(bookId));
                putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, db.getAuthorsByBookId(bookId));
                putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, db.getSeriesByBookId(bookId));
                putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, db.getTocEntryByBook(bookId));
            }
        }
    }

    /**
     * @return a complete list of Bookshelves each reflecting the book being on that shelf or not
     */
    public ArrayList<CheckListItem<Bookshelf>> getEditableBookshelvesList(@NonNull final DBA db) {
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
     * Special Formatter.
     *
     * @return a complete list of Editions each reflecting the book being that edition
     */
    public ArrayList<CheckListItem<Integer>> getEditableEditionList() {
        //Logger.info(this,"edition: " +
        // Integer.toBinaryString(getInt(UniqueId.KEY_EDITION_BITMASK)));

        ArrayList<CheckListItem<Integer>> list = new ArrayList<>();
        for (Integer edition : EDITIONS.keySet()) {
            //noinspection ConstantConditions
            list.add(new EditionCheckListItem(
                    edition, EDITIONS.get(edition),
                    (edition & getLong(DBDefinitions.KEY_EDITION_BITMASK)) != 0));
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
     * @return name of the first author in the list of authors for this book, or null if none
     */
    @Nullable
    public String getPrimaryAuthor() {
        ArrayList<Author> list = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        return !list.isEmpty() ? list.get(0).getDisplayName() : null;
    }

    /**
     * TODO: use {@link DataAccessor}.
     *
     * @return a formatted string for author list.
     */
    @NonNull
    public String getAuthorTextShort(@NonNull final Context context) {
        String newText;
        List<Author> list = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (list.isEmpty()) {
            return "";
        } else {
            newText = list.get(0).getDisplayName();
            if (list.size() > 1) {
                newText += ' ' + context.getString(R.string.and_others);
            }
            return newText;
        }
    }

    /**
     * Update author details from DB.
     *
     * @param db Database connection
     */
    public void refreshAuthorList(@NonNull final DBA db) {
        ArrayList<Author> list = getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        for (Author author : list) {
            db.refreshAuthor(author);
        }
        putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
    }

    /**
     * @return name of the first series in the list of series for this book, or null if none
     */
    @Nullable
    public String getPrimarySeries() {
        ArrayList<Series> list = getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        return !list.isEmpty() ? list.get(0).getName() : null;
    }

    /**
     * TODO: use {@link DataAccessor}.
     * <p>
     * Build a formatted string for series list.
     */
    @NonNull
    public String getSeriesTextShort(@NonNull final Context context) {
        String newText;
        ArrayList<Series> list = getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        if (list.isEmpty()) {
            return "";
        } else {
            newText = list.get(0).getDisplayName();
            if (list.size() > 1) {
                newText += ' ' + context.getString(R.string.and_others);
            }
            return newText;
        }
    }

    /**
     * Use the book's language setting to determine the Locale.
     *
     * @param updateLanguage <tt>true</tt> to update the language field with the iso3 code
     *                       if needed. <tt>false</tt> to leave it unchanged.
     *
     * @return the locale, or the users preferred locale if no language was set.
     */
    @NonNull
    public Locale getLocale(final boolean updateLanguage) {
        Locale bookLocale = null;
        if (containsKey(DBDefinitions.KEY_LANGUAGE)) {
            String lang = getString(DBDefinitions.KEY_LANGUAGE);
            int len = lang.length();
            // try to convert to is3 if needed.
            if (len != 2 && len != 3) {
                lang = LocaleUtils.getISO3Language(lang);
            }
            // we now have an iso3 code, or an invalid language.
            bookLocale = new Locale(lang);
            if (!LocaleUtils.isValid(bookLocale)) {
                bookLocale = LocaleUtils.getPreferredLocal();
            } else if (updateLanguage) {
                putString(DBDefinitions.KEY_LANGUAGE, lang);
            }
        }
        return bookLocale == null ? LocaleUtils.getPreferredLocal() : bookLocale;
    }

    /**
     * Update series details from DB.
     *
     * @param db Database connection
     */
    public void refreshSeriesList(@NonNull final DBA db) {
        ArrayList<Series> list = getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        for (Series series : list) {
            db.refreshSeries(series);
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
        addValidator(DBDefinitions.KEY_PAGES, BLANK_OR_INTEGER_VALIDATOR);

        addValidator(DBDefinitions.KEY_TOC_BITMASK, INTEGER_VALIDATOR);
        addValidator(DBDefinitions.KEY_EDITION_BITMASK, INTEGER_VALIDATOR);

        addValidator(DBDefinitions.KEY_PRICE_LISTED, BLANK_OR_FLOAT_VALIDATOR);
        addValidator(DBDefinitions.KEY_PRICE_PAID, BLANK_OR_FLOAT_VALIDATOR);


        /* Booleans are stored as Long (0,1) */
        addAccessor(IS_READ, new BooleanDataAccessor(DBDefinitions.KEY_READ));

        /* Booleans are stored as Long (0,1) */
        addAccessor(IS_SIGNED, new BooleanDataAccessor(DBDefinitions.KEY_SIGNED));

        /* set/reset the single bit TocEntry.Type.MULTIPLE_WORKS in the bitmask. */
        addAccessor(HAS_MULTIPLE_WORKS,
                    new BitmaskDataAccessor(DBDefinitions.KEY_TOC_BITMASK,
                                            TocEntry.Type.MULTIPLE_WORKS));
        /* set/reset the single bit TocEntry.Type.MULTIPLE_AUTHORS in the bitmask. */
        addAccessor(HAS_MULTIPLE_AUTHORS,
                    new BitmaskDataAccessor(DBDefinitions.KEY_TOC_BITMASK,
                                            TocEntry.Type.MULTIPLE_AUTHORS));
    }

//    /**
//     * Lend this book to someone.
//     *
//     * @param db     the database
//     * @param loanee person to lend to
//     */
//    public void lend(final DBA db,
//                     final String loanee) {
//        db.insertLoan(getId(), loanee);
//    }
//
//    /**
//     * A loaned book is returned.
//     *
//     * @param db the database
//     */
//    public void loanReturned(final DBA db) {
//        db.deleteLoan(getId());
//    }

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
        private int mLabelId;

        /**
         * Constructor.
         */
        public EditionCheckListItem() {
        }

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

        /** {@link Parcelable}. */
        EditionCheckListItem(@NonNull final Parcel in) {
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
         */
        public BookshelfCheckListItem() {
        }

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

        /** {@link Parcelable}. */
        BookshelfCheckListItem(@NonNull final Parcel in) {
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
         * @return the label to display
         */
        @NonNull
        public String getLabel(@NonNull final Context context) {
            return getItem().getName();
        }
    }

    /**
     * Field Formatter for a bitmask based Book Editions field.
     * <p>
     * Does not support {@link Fields.FieldFormatter#extract}.
     */
    public static class BookEditionsFormatter
            implements Fields.FieldFormatter {

        @NonNull
        @Override
        public String format(@NonNull final Fields.Field field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            int bitmask;
            try {
                bitmask = Integer.parseInt(source);
            } catch (NumberFormatException ignore) {
                return source;
            }
            Context context = field.getView().getContext();
            List<String> list = new ArrayList<>();
            for (Integer edition : EDITIONS.keySet()) {
                if ((edition & bitmask) != 0) {
                    //noinspection ConstantConditions
                    list.add(context.getString(EDITIONS.get(edition)));
                }
            }
            return Csv.toDisplayString(list, null);
        }
    }
}
