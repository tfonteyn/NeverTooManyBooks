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
import java.util.Map;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.accessors.BitmaskDataAccessor;
import com.eleybourn.bookcatalogue.datamanager.accessors.BooleanDataAccessor;
import com.eleybourn.bookcatalogue.datamanager.accessors.DataAccessor;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItemBase;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.GenericFileProvider;
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
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_READ}.
     * Type: Boolean
     */
    public static final String IS_READ = "+IsRead";

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_SIGNED}.
     * Type: Boolean
     */
    public static final String IS_SIGNED = "+IsSigned";

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_TOC_BITMASK}.
     * Type: Boolean
     * true: anthology by one or more authors
     */
    public static final String HAS_MULTIPLE_WORKS = "+HasMultiWorks";

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_TOC_BITMASK}.
     * Type: Boolean
     * true: anthology by multiple authors
     */
    public static final String HAS_MULTIPLE_AUTHORS = "+HasMultiAuthors";

    /** mapping the edition bit to a resource string for displaying. */
    @SuppressLint("UseSparseArrays")
    public static final Map<Integer, Integer> EDITIONS = new HashMap<>();

    /**
     * Rating goes from 0 to 5 stars, in 0.5 increments.
     */
    public static final int RATING_STARS = 5;

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
    /** First printing of the 'this' edition. */
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
        book.putString(UniqueId.KEY_BOOK_READ_END, DateUtils.localSqlDateForToday());
        return db.updateBook(bookId, book, 0) == 1;
    }

    /**
     * Perform sharing of book. Create chooser with matched apps for sharing some text like:
     * <b>"I'm reading " + title + " by " + author + series + " " + ratingString</b>
     */
    public Intent getShareBookIntent(@NonNull final Activity activity) {
        String title = getString(UniqueId.KEY_TITLE);
        double rating = getDouble(UniqueId.KEY_BOOK_RATING);
        String author = getString(DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST.name);
        String series = getString(DatabaseDefinitions.DOM_SERIES_FORMATTED.name);
        String uuid = getString(UniqueId.KEY_BOOK_UUID);

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

        bookData.putString(UniqueId.KEY_TITLE,
                           getString(UniqueId.KEY_TITLE));
        bookData.putString(UniqueId.KEY_BOOK_ISBN,
                           getString(UniqueId.KEY_BOOK_ISBN));

        bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                        getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY));
        bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                        getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY));
        bookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY,
                                        getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY));

        // publication data
        bookData.putString(UniqueId.KEY_BOOK_PUBLISHER,
                           getString(UniqueId.KEY_BOOK_PUBLISHER));
        bookData.putLong(UniqueId.KEY_BOOK_TOC_BITMASK,
                         getLong(UniqueId.KEY_BOOK_TOC_BITMASK));
        bookData.putString(UniqueId.KEY_BOOK_DATE_PUBLISHED,
                           getString(UniqueId.KEY_BOOK_DATE_PUBLISHED));
        bookData.putString(UniqueId.KEY_BOOK_PRICE_LISTED,
                           getString(UniqueId.KEY_BOOK_PRICE_LISTED));
        bookData.putString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY,
                           getString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY));
        bookData.putString(UniqueId.KEY_FIRST_PUBLICATION,
                           getString(UniqueId.KEY_FIRST_PUBLICATION));
        bookData.putString(UniqueId.KEY_BOOK_FORMAT,
                           getString(UniqueId.KEY_BOOK_FORMAT));
        bookData.putString(UniqueId.KEY_BOOK_GENRE,
                           getString(UniqueId.KEY_BOOK_GENRE));
        bookData.putString(UniqueId.KEY_BOOK_LANGUAGE,
                           getString(UniqueId.KEY_BOOK_LANGUAGE));
        bookData.putString(UniqueId.KEY_BOOK_PAGES,
                           getString(UniqueId.KEY_BOOK_PAGES));
        // common blurb
        bookData.putString(UniqueId.KEY_BOOK_DESCRIPTION,
                           getString(UniqueId.KEY_BOOK_DESCRIPTION));

        // partially edition info, partially use-owned info.
        bookData.putLong(UniqueId.KEY_BOOK_EDITION_BITMASK,
                         getLong(UniqueId.KEY_BOOK_EDITION_BITMASK));

        // user data

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(UniqueId.KEY_BOOK_SIGNED,
                         getLong(UniqueId.KEY_BOOK_SIGNED));

        // put/getBoolean is 'right', but as a copy, might as well just use long
        bookData.putLong(UniqueId.KEY_BOOK_READ,
                         getLong(UniqueId.KEY_BOOK_READ));

        bookData.putDouble(UniqueId.KEY_BOOK_RATING,
                           getDouble(UniqueId.KEY_BOOK_RATING));

        bookData.putString(UniqueId.KEY_BOOK_NOTES,
                           getString(UniqueId.KEY_BOOK_NOTES));
        bookData.putString(UniqueId.KEY_BOOK_LOCATION,
                           getString(UniqueId.KEY_BOOK_LOCATION));
        bookData.putString(UniqueId.KEY_BOOK_READ_START,
                           getString(UniqueId.KEY_BOOK_READ_START));
        bookData.putString(UniqueId.KEY_BOOK_READ_END,
                           getString(UniqueId.KEY_BOOK_READ_END));
        bookData.putString(UniqueId.KEY_BOOK_DATE_ACQUIRED,
                           getString(UniqueId.KEY_BOOK_DATE_ACQUIRED));
        bookData.putString(UniqueId.KEY_BOOK_PRICE_PAID,
                           getString(UniqueId.KEY_BOOK_PRICE_PAID));
        bookData.putString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY,
                           getString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY));

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
        String prevReadEnd = getString(UniqueId.KEY_BOOK_READ_END);

        putBoolean(Book.IS_READ, isRead);
        putString(UniqueId.KEY_BOOK_READ_END, DateUtils.localSqlDateForToday());

        if (db.updateBook(getId(), this, 0) != 1) {
            //rollback
            putBoolean(Book.IS_READ, prevRead);
            putString(UniqueId.KEY_BOOK_READ_END, prevReadEnd);
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
        return getLong(UniqueId.KEY_ID);
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
                putList(UniqueId.BKEY_BOOKSHELF_ARRAY, db.getBookshelvesByBookId(bookId));
                putList(UniqueId.BKEY_AUTHOR_ARRAY, db.getAuthorsByBookId(bookId));
                putList(UniqueId.BKEY_SERIES_ARRAY, db.getSeriesByBookId(bookId));
                putList(UniqueId.BKEY_TOC_ENTRY_ARRAY, db.getTocEntryByBook(bookId));
            }
        }
    }

    /**
     * @return a complete list of Bookshelves each reflecting the book being on that shelf or not
     */
    public ArrayList<CheckListItem<Bookshelf>> getEditableBookshelvesList(@NonNull final DBA db) {
        ArrayList<CheckListItem<Bookshelf>> list = new ArrayList<>();
        // get the list of all shelves the book is currently on.
        List<Bookshelf> currentShelves = getList(UniqueId.BKEY_BOOKSHELF_ARRAY);
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
        // Integer.toBinaryString(getInt(UniqueId.KEY_BOOK_EDITION_BITMASK)));

        ArrayList<CheckListItem<Integer>> list = new ArrayList<>();
        for (Integer edition : EDITIONS.keySet()) {
            //noinspection ConstantConditions
            list.add(new EditionCheckListItem(
                    edition, EDITIONS.get(edition),
                    (edition & getLong(UniqueId.KEY_BOOK_EDITION_BITMASK)) != 0));
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
        putLong(UniqueId.KEY_BOOK_EDITION_BITMASK, bitmask);
    }

    /**
     * @return name of the first author in the list of authors for this book, or null if none
     */
    @Nullable
    public String getPrimaryAuthor() {
        ArrayList<Author> list = getList(UniqueId.BKEY_AUTHOR_ARRAY);
        return !list.isEmpty() ? list.get(0).getDisplayName() : null;
    }

    /**
     * TODO: use {@link DataAccessor}.
     *
     * @return a formatted string for author list.
     */
    @NonNull
    public String getAuthorTextShort() {
        String newText;
        List<Author> list = getList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (list.isEmpty()) {
            return "";
        } else {
            newText = list.get(0).getDisplayName();
            if (list.size() > 1) {
                newText += ' ' + BookCatalogueApp.getResString(R.string.and_others);
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
        ArrayList<Author> list = getList(UniqueId.BKEY_AUTHOR_ARRAY);
        for (Author author : list) {
            db.refreshAuthor(author);
        }
        putList(UniqueId.BKEY_AUTHOR_ARRAY, list);
    }

    /**
     * Update series details from DB.
     *
     * @param db Database connection
     */
    public void refreshSeriesList(@NonNull final DBA db) {
        ArrayList<Series> list = getList(UniqueId.BKEY_SERIES_ARRAY);
        for (Series series : list) {
            db.refreshSeries(series);
        }
        putList(UniqueId.BKEY_SERIES_ARRAY, list);
    }

    /**
     * Convenience method to get a list.
     */
    @NonNull
    public <T extends Parcelable> ArrayList<T> getList(@NonNull final String key) {
        return super.getParcelableArrayList(key);
    }

    /**
     * Convenience method to set a list.
     */
    public void putList(@NonNull final String key,
                        @NonNull final ArrayList<? extends Parcelable> list) {
        super.putParcelableArrayList(key, list);
    }

    /**
     * @return name of the first series in the list of series for this book, or null if none
     */
    @Nullable
    public String getPrimarySeries() {
        ArrayList<Series> list = getList(UniqueId.BKEY_SERIES_ARRAY);
        return !list.isEmpty() ? list.get(0).getName() : null;
    }

    /**
     * TODO: use {@link DataAccessor}.
     * <p>
     * Build a formatted string for series list.
     */
    @NonNull
    public String getSeriesTextShort() {
        String newText;
        ArrayList<Series> list = getList(UniqueId.BKEY_SERIES_ARRAY);
        if (list.isEmpty()) {
            return "";
        } else {
            newText = list.get(0).getDisplayName();
            if (list.size() > 1) {
                newText += ' ' + BookCatalogueApp.getResString(R.string.and_others);
            }
            return newText;
        }
    }

    /**
     * Build any special purpose validators/accessors.
     * <p>
     * ENHANCE: add (partial) date validators ? any other validators needed ?
     */
    private void initValidatorsAndAccessors() {
        addValidator(UniqueId.KEY_TITLE, NON_BLANK_VALIDATOR);
        addValidator(UniqueId.KEY_BOOK_PAGES, BLANK_OR_INTEGER_VALIDATOR);

        addValidator(UniqueId.KEY_BOOK_TOC_BITMASK, INTEGER_VALIDATOR);
        addValidator(UniqueId.KEY_BOOK_EDITION_BITMASK, INTEGER_VALIDATOR);

        addValidator(UniqueId.KEY_BOOK_PRICE_LISTED, BLANK_OR_FLOAT_VALIDATOR);
        addValidator(UniqueId.KEY_BOOK_PRICE_PAID, BLANK_OR_FLOAT_VALIDATOR);


        /* Booleans are stored as Long (0,1) */
        addAccessor(IS_READ, new BooleanDataAccessor(UniqueId.KEY_BOOK_READ));

        /* Booleans are stored as Long (0,1) */
        addAccessor(IS_SIGNED, new BooleanDataAccessor(UniqueId.KEY_BOOK_SIGNED));

        /* set/reset the single bit TocEntry.Type.MULTIPLE_WORKS in the bitmask. */
        addAccessor(HAS_MULTIPLE_WORKS,
                    new BitmaskDataAccessor(UniqueId.KEY_BOOK_TOC_BITMASK,
                                            TocEntry.Type.MULTIPLE_WORKS));
        /* set/reset the single bit TocEntry.Type.MULTIPLE_AUTHORS in the bitmask. */
        addAccessor(HAS_MULTIPLE_AUTHORS,
                    new BitmaskDataAccessor(UniqueId.KEY_BOOK_TOC_BITMASK,
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

        /** {@link Parcelable}. */
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
        public String getLabel() {
            return BookCatalogueApp.getResString(mLabelId);
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

        /** {@link Parcelable}. */
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
        public String getLabel() {
            return getItem().getName();
        }
    }
}
