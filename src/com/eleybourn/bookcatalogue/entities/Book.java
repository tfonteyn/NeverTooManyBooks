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
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookSearchActivity;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.datamanager.BitmaskDataAccessor;
import com.eleybourn.bookcatalogue.datamanager.BooleanDataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItemBase;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the underlying data for a book.
 *
 * ENHANCE: would be nice to make this Parcelable...
 *
 * @author pjw
 */
public class Book extends DataManager {

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_ANTHOLOGY_BITMASK}
     * Type: Boolean
     * true: anthology by one or more authors
     */
    public static final String IS_ANTHOLOGY = "+IsAnthology";

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_READ}
     * Type: Boolean
     */
    public static final String IS_READ = "+IsRead";

    /** mapping the edition bit to a resource string for displaying */
    @SuppressLint("UseSparseArrays")
    public static final Map<Integer, Integer> EDITIONS = new HashMap<>();

    /**
     * {@link DatabaseDefinitions#DOM_BOOK_EDITION_BITMASK}
     *
     * 0%00000001 = first edition
     * 0%00000010 = first impression
     * 0%00000100 = limited edition
     *
     * 0%10000000 = book club
     *
     * NEWKIND: edition
     */
    private static final int EDITION_FIRST = 1;
    private static final int EDITION_FIRST_IMPRESSION = 1 << 1;
    private static final int EDITION_LIMITED = 1 << 2;
    private static final int EDITION_BOOK_CLUB = 1 << 7;
//    /**
//     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_EDITION_BITMASK}
//     * Type: Boolean
//     */
//    private static final String IS_FIRST_EDITION = "+IsFirstEdition";
//    /**
//     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_EDITION_BITMASK}
//     * Type: Boolean
//     */
//    private static final String IS_FIRST_IMPRESSION = "+IsFirstImpression";
//    /**
//     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_EDITION_BITMASK}
//     * Type: Boolean
//     */
//    private static final String IS_LIMITED = "+LimitedEdition";
//    /**
//     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_EDITION_BITMASK}
//     * Type: Boolean
//     */
//    private static final String IS_BOOK_CLUB_EDITION = "+IsBookClubEdition";

    /* NEWKIND: edition */
    static {
        EDITIONS.put(EDITION_FIRST, R.string.lbl_edition_first_edition);
        EDITIONS.put(EDITION_FIRST_IMPRESSION, R.string.lbl_edition_first_impression);
        EDITIONS.put(EDITION_LIMITED, R.string.lbl_edition_limited);
        EDITIONS.put(EDITION_BOOK_CLUB, R.string.lbl_edition_book_club);
    }


    /**
     * Public Constructor. All overloaded constructors MUST call this();
     */
    public Book() {
        initValidatorsAndAccessors();
    }

    /**
     * Private constructor.
     *
     * Either load from database if existing book, or a new Book.
     */
    private Book(final @NonNull CatalogueDBAdapter db, final long bookId) {
        this();
        if (bookId > 0) {
            reload(db, bookId);
        }
    }

    /**
     * Public Constructor
     *
     * @param cursor with book data
     */
    public Book(final @NonNull Cursor cursor) {
        this();
        putAll(cursor);
    }

    /**
     * Private constructor.
     *
     * @param bookId of book (may be 0 for new)
     * @param bookData Bundle with book data
     */
    private Book(final long bookId, final @NonNull Bundle bookData) {
        this();
        putAll(bookData);
    }

    /**
     * This function will populate the Book in three different ways
     *
     * 1. If fields (BKEY_BOOK_DATA) have been passed from another activity
     * (e.g. {@link BookSearchActivity}) it will populate the Book from the bundle
     *
     * 2. If a valid bookId exists it will populate the Book from the database
     *
     * 3. It will leave the Book blank for new books.
     *
     * So *always* returns a valid Book.
     *
     * @param bookId of book (may be 0 for new)
     * @param bookData Bundle with book data (may be null)
     */
    @NonNull
    public static Book getBook(final @NonNull CatalogueDBAdapter db, final long bookId, final @Nullable Bundle bookData) {
        // if we have a populated bundle, use that.
            if (bookData != null) {
                return new Book(bookId, bookData);
        }
        // otherwise, create a new book and try to load the data from the database.
        return new Book(db, bookId);
    }

    /**
     * shortcut if we don't have a bundle
     *
     * @param bookId of book (may be 0 for new)
     */
    @NonNull
    public static Book getBook(final @NonNull CatalogueDBAdapter db, final long bookId) {
        return new Book(db, bookId);
    }

    /**
     * Erase everything in this instance and reset the special handlers
     */
    @Override
    @CallSuper
    public void clear() {
        super.clear();
        initValidatorsAndAccessors();
    }

    /**
     * Convenience Accessor
     */
    public long getBookId() {
        return this.getLong(UniqueId.KEY_ID);
    }

    public void reload(final @NonNull CatalogueDBAdapter db) {
        reload(db, this.getBookId());
    }

    /**
     * Load the book details from the database
     *
     * @param bookId of book (may be 0 for new, in which case we do nothing)
     */
    public void reload(final @NonNull CatalogueDBAdapter db, final long bookId) {
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
                putAuthorList(db.getBookAuthorList(bookId));
                putSeriesList(db.getBookSeriesList(bookId));
                putTOC(db.getTOCEntriesByBook(bookId));
            }
        }
    }

    /**
     * Utility routine to get a bookshelf list from a data manager
     *
     * TODO: use {@link DataAccessor}
     *
     * @return List of bookshelves
     */
    @NonNull
    public ArrayList<Bookshelf> getBookshelfList() {
        return super.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
    }

    /**
     * @return a complete list of Bookshelves each reflecting the book being on that shelf or not
     */
    public ArrayList<CheckListItem<Bookshelf>> getEditableBookshelvesList(final @NonNull CatalogueDBAdapter db) {
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
     * Special Formatter
     *
     * @return the list of bookshelves formatted as "shelf1, shelf2, shelf3, ...
     */
    public String getBookshelfListAsText() {
        List<String> list = new ArrayList<>();
        for (Bookshelf bookshelf : getBookshelfList()) {
            list.add(bookshelf.name);
        }
        return Utils.toDisplayString(list);
    }

    /**
     * Special Accessor
     */
    public void putBookshelfList(final @NonNull ArrayList<Bookshelf> list) {
        super.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
    }

    /**
     * Special Formatter
     *
     * @return a complete list of Editions each reflecting the book being that edition
     */
    public ArrayList<CheckListItem<Integer>> getEditableEditionList() {
        //Logger.info(this,"edition: " + Integer.toBinaryString(getInt(UniqueId.KEY_BOOK_EDITION_BITMASK)));

        ArrayList<CheckListItem<Integer>> list = new ArrayList<>();
        for (Integer edition : EDITIONS.keySet()) {
            list.add(new EditionCheckListItem(edition, EDITIONS.get(edition),
                    ((edition & getInt(UniqueId.KEY_BOOK_EDITION_BITMASK)) != 0)));
        }
        return list;
    }

    public void putEditions(final @NonNull ArrayList<Integer> result) {
        int bitmask = 0;
        for (Integer bit : result) {
            bitmask += bit;
        }
        putInt(UniqueId.KEY_BOOK_EDITION_BITMASK, bitmask);
    }

    /**
     * Utility routine to get an author list from a data manager
     *
     * TODO: use {@link DataAccessor}
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
     * Special Accessor.
     *
     * TODO: use {@link DataAccessor}
     *
     * Build a formatted string for author list.
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
                newText += " " + BookCatalogueApp.getResourceString(R.string.and_others);
            }
            return newText;
        }
    }

    /**
     * Special Accessor
     */
    public void putAuthorList(final @NonNull ArrayList<Author> list) {
        super.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
    }

    /**
     * Update author details from DB
     *
     * @param db Database connection
     */
    public void refreshAuthorList(final @NonNull CatalogueDBAdapter db) {
        ArrayList<Author> list = getAuthorList();
        for (Author author : list) {
            db.refreshAuthor(author);
        }
        putAuthorList(list);
    }

    /**
     * Utility routine to get an series list from a data manager
     *
     * TODO: use {@link DataAccessor}
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
        return list.size() > 0 ? list.get(0).name : null;
    }

    /**
     * Special Accessor.
     *
     * TODO: use {@link DataAccessor}
     *
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
                newText += " " + BookCatalogueApp.getResourceString(R.string.and_others);
            }
            return newText;
        }
    }

    /**
     * Special Accessor
     */
    public void putSeriesList(final @NonNull ArrayList<Series> list) {
        super.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
    }

    /**
     * Utility routine to get a Content (an TOCEntry list) from a data manager
     *
     * TODO: use {@link DataAccessor}
     *
     * @return List of anthology titles
     */
    @NonNull
    @CallSuper
    public ArrayList<TOCEntry> getTOC() {
        return super.getParcelableArrayList(UniqueId.BKEY_TOC_TITLES_ARRAY);
    }

    /**
     * Special Accessor
     */
    @CallSuper
    public void putTOC(final @NonNull ArrayList<TOCEntry> list) {
        super.putParcelableArrayList(UniqueId.BKEY_TOC_TITLES_ARRAY, list);
    }

    /**
     * // If there are thumbnails present, pick the biggest, delete others and rename.
     */
    public void cleanupThumbnails() {
        ImageUtils.cleanupThumbnails(mBundle);
    }

    /**
     * Get the underlying raw data.
     * DO NOT UPDATE THIS! IT SHOULD BE USED FOR READING DATA ONLY.
     */
    @NonNull
    public Bundle getRawData() {
        return mBundle;
    }

    /**
     * Build any special purpose validators/accessors
     */
    private void initValidatorsAndAccessors() {
        addValidator(UniqueId.KEY_TITLE, nonBlankValidator);
        addValidator(UniqueId.KEY_BOOK_PAGES, blankOrIntegerValidator);

        addValidator(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, integerValidator);
        addValidator(UniqueId.KEY_BOOK_EDITION_BITMASK, integerValidator);

        addValidator(UniqueId.KEY_BOOK_PRICE_LISTED, blankOrFloatValidator);
        addValidator(UniqueId.KEY_BOOK_PRICE_PAID, blankOrFloatValidator);


        addAccessor(IS_READ, new BooleanDataAccessor(UniqueId.KEY_BOOK_READ));

        /* This only handles the fact of being an anthology or not. Does not handle 'multiple authors' */
        addAccessor(IS_ANTHOLOGY,
                new BitmaskDataAccessor(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, DatabaseDefinitions.DOM_IS_ANTHOLOGY));

    }

    public static class EditionCheckListItem extends CheckListItemBase<Integer> implements Parcelable {
        @StringRes
        private int labelId;

        public EditionCheckListItem() {
        }

        EditionCheckListItem(final @NonNull Integer bit, final @StringRes int labelId, final boolean selected) {
            super(bit, selected);
            this.labelId = labelId;
        }

        EditionCheckListItem(final @NonNull Parcel in) {
            super(in);
            labelId = in.readInt();
            item = in.readInt();
        }

        @Override
        public void writeToParcel(final @NonNull Parcel dest, final int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(labelId);
            dest.writeInt(item);
        }

        public static final Creator<EditionCheckListItem> CREATOR = new Creator<EditionCheckListItem>() {
            @Override
            public EditionCheckListItem createFromParcel(final @NonNull Parcel in) {
                return new EditionCheckListItem(in);
            }

            @Override
            public EditionCheckListItem[] newArray(final int size) {
                return new EditionCheckListItem[size];
            }
        };

        public String getLabel() {
            return BookCatalogueApp.getResourceString(labelId);
        }
    }

    public static class BookshelfCheckListItem extends CheckListItemBase<Bookshelf> implements Parcelable {

        public BookshelfCheckListItem() {
        }

        BookshelfCheckListItem(final @NonNull Bookshelf item, final boolean selected) {
            super(item, selected);
        }

        BookshelfCheckListItem(final @NonNull Parcel in) {
            super(in);
            //API_UPGRADE 23 use readTypedObject(Bookshelf.CREATOR) which is more efficient
            item = in.readParcelable(Bookshelf.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, final int flags) {
            super.writeToParcel(dest, flags);
            //API_UPGRADE 23 use writeTypedObject which is more efficient
            dest.writeParcelable(item, flags);
        }

        public static final Creator<BookshelfCheckListItem> CREATOR = new Creator<BookshelfCheckListItem>() {
            @Override
            public BookshelfCheckListItem createFromParcel(final @NonNull Parcel in) {
                return new BookshelfCheckListItem(in);
            }

            @Override
            public BookshelfCheckListItem[] newArray(final int size) {
                return new BookshelfCheckListItem[size];
            }
        };

        public String getLabel() {
            return getItem().name;
        }
    }
}
