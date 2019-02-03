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

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookSearchActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.datamanager.BitmaskDataAccessor;
import com.eleybourn.bookcatalogue.datamanager.BooleanDataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItemBase;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.GenericFileProvider;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_ANTHOLOGY_BITMASK}.
     * Type: Boolean
     * true: anthology by one or more authors
     */
    public static final String HAS_MULTIPLE_WORKS = "+IsAnthology";

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_READ}.
     * Type: Boolean
     */
    public static final String IS_READ = "+IsRead";

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

    /** first edition ever of this content. */
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
     * Private constructor.
     * <p>
     * Either load from database if existing book, or a new Book.
     */
    private Book(@NonNull final DBA db,
                 final long bookId) {
        initValidatorsAndAccessors();
        if (bookId > 0) {
            reload(db, bookId);
        }
    }

    /**
     * Private constructor.
     *
     * @param bookId   of book (may be 0 for new)
     * @param bookData Bundle with book data
     */
    private Book(final long bookId,
                 @NonNull final Bundle bookData) {
        initValidatorsAndAccessors();
        putAll(bookData);
    }

    /**
     * This function will populate the Book in three different ways.
     * <p>
     * 1. If fields (BKEY_BOOK_DATA) have been passed from another activity
     * (e.g. {@link BookSearchActivity}) it will populate the Book from the bundle
     * <p>
     * 2. If a valid bookId exists it will populate the Book from the database
     * <p>
     * 3. It will leave the Book blank for new books.
     * <p>
     * So *always* returns a valid Book.
     *
     * @param bookId   of book (may be 0 for new)
     * @param bookData Bundle with book data (may be null)
     */
    @NonNull
    public static Book getBook(@NonNull final DBA db,
                               final long bookId,
                               @Nullable final Bundle bookData) {
        // if we have a populated bundle, use that.
        if (bookData != null) {
            return new Book(bookId, bookData);
        }
        // otherwise, create a new book and try to load the data from the database.
        return new Book(db, bookId);
    }

    /**
     * shortcut if we don't have a bundle.
     *
     * @param bookId of book (may be 0 for new)
     */
    @NonNull
    public static Book getBook(@NonNull final DBA db,
                               final long bookId) {
        return new Book(db, bookId);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean setRead(@NonNull final DBA db,
                                  final long bookId,
                                  final boolean isRead) {
        // load from database
        Book book = getBook(db, bookId);
        book.putBoolean(UniqueId.KEY_BOOK_READ, isRead);
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
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        String text = activity.getString(R.string.info_share_book_im_reading,
                                         title, author, series, ratingString);
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        shareIntent.putExtra(Intent.EXTRA_STREAM, coverURI);
        shareIntent.setType("text/plain");

        return shareIntent;
    }

    /**
     * Duplicate a book by putting applicable fields in a Bundle ready for further processing.
     *
     * @return bundle with book data
     */
    public Bundle duplicate() {
        final Bundle bookData = new Bundle();

        bookData.putString(UniqueId.KEY_TITLE,
                           getString(UniqueId.KEY_TITLE));
        bookData.putString(UniqueId.KEY_BOOK_ISBN,
                           getString(UniqueId.KEY_BOOK_ISBN));
        bookData.putString(UniqueId.KEY_BOOK_DESCRIPTION,
                           getString(UniqueId.KEY_BOOK_DESCRIPTION));

        bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY,
                                        getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY));
        bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY,
                                        getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY));

        bookData.putInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK,
                        getInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK));
        bookData.putParcelableArrayList(UniqueId.BKEY_TOC_TITLES_ARRAY,
                                        getParcelableArrayList(UniqueId.BKEY_TOC_TITLES_ARRAY));

        bookData.putString(UniqueId.KEY_BOOK_PUBLISHER,
                           getString(UniqueId.KEY_BOOK_PUBLISHER));
        bookData.putString(UniqueId.KEY_BOOK_DATE_PUBLISHED,
                           getString(UniqueId.KEY_BOOK_DATE_PUBLISHED));
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


        bookData.putString(UniqueId.KEY_BOOK_PRICE_LISTED,
                           getString(UniqueId.KEY_BOOK_PRICE_LISTED));
        bookData.putString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY,
                           getString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY));
        bookData.putString(UniqueId.KEY_BOOK_PRICE_PAID,
                           getString(UniqueId.KEY_BOOK_PRICE_PAID));
        bookData.putString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY,
                           getString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY));

        bookData.putInt(UniqueId.KEY_BOOK_READ,
                        getInt(UniqueId.KEY_BOOK_READ));
        bookData.putString(UniqueId.KEY_BOOK_READ_END,
                           getString(UniqueId.KEY_BOOK_READ_END));
        bookData.putString(UniqueId.KEY_BOOK_READ_START,
                           getString(UniqueId.KEY_BOOK_READ_START));

        bookData.putString(UniqueId.KEY_BOOK_NOTES,
                           getString(UniqueId.KEY_BOOK_NOTES));
        bookData.putDouble(UniqueId.KEY_BOOK_RATING,
                           getInt(UniqueId.KEY_BOOK_RATING));
        bookData.putString(UniqueId.KEY_BOOK_LOCATION,
                           getString(UniqueId.KEY_BOOK_LOCATION));
        bookData.putInt(UniqueId.KEY_BOOK_SIGNED,
                        getInt(UniqueId.KEY_BOOK_SIGNED));
        bookData.putInt(UniqueId.KEY_BOOK_EDITION_BITMASK,
                        getInt(UniqueId.KEY_BOOK_EDITION_BITMASK));

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
        int prevRead = getInt(UniqueId.KEY_BOOK_READ);
        String prevReadEnd = getString(UniqueId.KEY_BOOK_READ_END);

        putInt(UniqueId.KEY_BOOK_READ, isRead ? 1 : 0);
        putString(UniqueId.KEY_BOOK_READ_END, DateUtils.localSqlDateForToday());

        if (db.updateBook(getId(), this, 0) != 1) {
            //rollback
            putInt(UniqueId.KEY_BOOK_READ, prevRead);
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
        return this.getLong(UniqueId.KEY_ID);
    }

    public void reload(@NonNull final DBA db) {
        reload(db, this.getId());
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
                putBookshelfList(db.getBookshelvesByBookId(bookId));
                putAuthorList(db.getAuthorsByBookId(bookId));
                putSeriesList(db.getSeriesByBookId(bookId));
                putTOC(db.getTocEntryByBook(bookId));
            }
        }
    }

    /**
     * TODO: use {@link DataAccessor}.
     *
     * @return the bookshelf list on which this book sits.
     */
    @NonNull
    public ArrayList<Bookshelf> getBookshelfList() {
        return super.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
    }

    /**
     * @return a complete list of Bookshelves each reflecting the book being on that shelf or not
     */
    public ArrayList<CheckListItem<Bookshelf>> getEditableBookshelvesList(@NonNull final DBA db) {
        ArrayList<CheckListItem<Bookshelf>> list = new ArrayList<>();
        // get the list of all shelves the book is currently on.
        List<Bookshelf> currentShelves = getBookshelfList();
        // Loop through all bookshelves in the database and build the list for this book
        for (Bookshelf bookshelf : db.getBookshelves()) {
            list.add(new BookshelfCheckListItem(bookshelf, currentShelves.contains(bookshelf)));
        }
        return list;
    }

    /**
     * Special Accessor.
     */
    public void putBookshelfList(@NonNull final ArrayList<Bookshelf> list) {
        super.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
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
                    (edition & getInt(UniqueId.KEY_BOOK_EDITION_BITMASK)) != 0));
        }
        return list;
    }

    public void putEditions(@NonNull final ArrayList<Integer> result) {
        int bitmask = 0;
        for (Integer bit : result) {
            bitmask += bit;
        }
        putInt(UniqueId.KEY_BOOK_EDITION_BITMASK, bitmask);
    }

    /**
     * TODO: use {@link DataAccessor}.
     *
     * @return List of authors
     */
    @NonNull
    public ArrayList<Author> getAuthorList() {
        return super.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
    }

    /**
     * @return the first author in the list of authors for this book, or null if none
     */
    @Nullable
    public String getPrimaryAuthor() {
        ArrayList<Author> list = getAuthorList();
        return list.size() > 0 ? list.get(0).getDisplayName() : null;
    }

    /**
     * TODO: use {@link DataAccessor}.
     *
     * @return a formatted string for author list.
     */
    @NonNull
    public String getAuthorTextShort() {
        String newText;
        List<Author> list = getAuthorList();
        if (list.size() == 0) {
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
     * Special Accessor.
     */
    public void putAuthorList(@NonNull final ArrayList<Author> list) {
        super.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
    }

    /**
     * Update author details from DB.
     *
     * @param db Database connection
     */
    public void refreshAuthorList(@NonNull final DBA db) {
        ArrayList<Author> list = getAuthorList();
        for (Author author : list) {
            db.refreshAuthor(author);
        }
        putAuthorList(list);
    }

    /**
     * TODO: use {@link DataAccessor}.
     *
     * @return List of series
     */
    @NonNull
    public ArrayList<Series> getSeriesList() {
        return super.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
    }

    /**
     * @return the first series in the list of series for this book, or null if none
     */
    @Nullable
    public String getPrimarySeries() {
        ArrayList<Series> list = getSeriesList();
        return list.size() > 0 ? list.get(0).getName() : null;
    }

    /**
     * TODO: use {@link DataAccessor}.
     * <p>
     * Build a formatted string for series list.
     */
    @NonNull
    public String getSeriesTextShort() {
        String newText;
        ArrayList<Series> list = getSeriesList();
        if (list.size() == 0) {
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
     * Special Accessor.
     */
    public void putSeriesList(@NonNull final ArrayList<Series> list) {
        super.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
    }

    /**
     * TODO: use {@link DataAccessor}.
     *
     * @return Table Of Content (a TocEntry list)
     */
    @NonNull
    @CallSuper
    public ArrayList<TocEntry> getTOCList() {
        return super.getParcelableArrayList(UniqueId.BKEY_TOC_TITLES_ARRAY);
    }

    /**
     * Special Accessor.
     */
    @CallSuper
    public void putTOC(@NonNull final ArrayList<TocEntry> list) {
        super.putParcelableArrayList(UniqueId.BKEY_TOC_TITLES_ARRAY, list);
    }

    /**
     * // If there are thumbnails present, pick the biggest, delete others and rename.
     */
    public void cleanupThumbnails() {
        ImageUtils.cleanupThumbnails(mBundle);
    }

    /**
     * @return the underlying raw data.
     * DO NOT UPDATE THIS! IT SHOULD BE USED FOR READING DATA ONLY.
     */
    @NonNull
    public Bundle getRawData() {
        return mBundle;
    }

    /**
     * Build any special purpose validators/accessors.
     */
    private void initValidatorsAndAccessors() {
        addValidator(UniqueId.KEY_TITLE, NON_BLANK_VALIDATOR);
        addValidator(UniqueId.KEY_BOOK_PAGES, BLANK_OR_INTEGER_VALIDATOR);

        addValidator(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, INTEGER_VALIDATOR);
        addValidator(UniqueId.KEY_BOOK_EDITION_BITMASK, INTEGER_VALIDATOR);

        addValidator(UniqueId.KEY_BOOK_PRICE_LISTED, BLANK_OR_FLOAT_VALIDATOR);
        addValidator(UniqueId.KEY_BOOK_PRICE_PAID, BLANK_OR_FLOAT_VALIDATOR);


        addAccessor(IS_READ, new BooleanDataAccessor(UniqueId.KEY_BOOK_READ));

        /* This only handles the fact of being an anthology or not.
         * Does not handle 'multiple authors' */
        addAccessor(HAS_MULTIPLE_WORKS,
                    new BitmaskDataAccessor(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK,
                                            TocEntry.Type.MULTIPLE_WORKS));

    }

    /**
     * Loan this book to someone.
     *
     * @param db     the database
     * @param loanee person to lend to
     */
    public void loan(final DBA db,
                     final String loanee) {
        db.insertLoan(getId(), loanee);
    }

    /**
     * A loaned book is returned.
     *
     * @param db the database
     */
    public void loanReturned(final DBA db) {
        db.deleteLoan(getId());
    }

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

        public EditionCheckListItem() {
        }

        EditionCheckListItem(@NonNull final Integer bit,
                             @StringRes final int labelId,
                             final boolean selected) {
            super(bit, selected);
            mLabelId = labelId;
        }

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

        public String getLabel() {
            return BookCatalogueApp.getResString(mLabelId);
        }
    }

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

        public BookshelfCheckListItem() {
        }

        BookshelfCheckListItem(@NonNull final Bookshelf item,
                               final boolean selected) {
            super(item, selected);
        }

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

        @NonNull
        public String getLabel() {
            return getItem().getName();
        }
    }
}
