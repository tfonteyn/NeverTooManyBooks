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

import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.datamanager.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the underlying data for a book.
 *
 * @author pjw
 */
public class Book extends DataManager {

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_ANTHOLOGY_BITMASK}
     * boolean
     */
    public static final String IS_ANTHOLOGY = "+IsAnthology";

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_READ}
     * boolean
     */
    public static final String IS_READ = "+IsRead";

    /**
     * Constructor. All overloaded constructors MUST call this();
     */
    public Book() {
        initValidatorsAndAccessors();
    }

    public Book(final long bookId) {
        this();
        if (bookId > 0) {
            reload(bookId);
        }
    }

    /**
     * Constructor
     *
     * @param cursor with book data
     */
    public Book(@NonNull final Cursor cursor) {
        this();
        putAll(cursor);
    }

    /**
     * Main Constructor
     *
     * @param bookId of book (may be 0 for new)
     * @param bundle Bundle with book data (may be null)
     */
    public Book(final long bookId, @Nullable final Bundle bundle) {
        this();
        // Load from bundle or database
        if (bundle != null) {
            putAll(bundle);
        } else if (bookId > 0) {
            reload(bookId);
        }
    }

    /**
     * Erase everything in this instance and reset the special handlers
     *
     * @return self, for chaining
     */
    @Override
    @NonNull
    @CallSuper
    public DataManager clear() {
        super.clear();
        initValidatorsAndAccessors();
        return this;
    }

    /** hide the special accessor */
    public boolean isAnthology() {
        return getBoolean(IS_ANTHOLOGY);
    }

    /**
     * Convenience Accessor
     */
    public long getBookId() {
        return this.getLong(UniqueId.KEY_ID);
    }

    /**
     * Load the book details from the database
     */
    public void reload(final long bookId) {
        // If ID = 0, no details in DB
        if (bookId == 0) {
            return;
        }

        // Connect to DB and get cursor for book details
        CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        db.open();
        try (BooksCursor book = db.fetchBookById(bookId)) {
            if (book.moveToFirst()) {
                // Put all cursor fields in collection
                putAll(book);

                putBookshelfList(db.getBookshelvesByBookId(bookId));
                putAuthorList(db.getBookAuthorList(bookId));
                putSeriesList(db.getBookSeriesList(bookId));
                putContentList(db.getAnthologyTitleListByBook(bookId));
            }
        } finally {
            db.close();
        }
    }

    /**
     * Special Accessor for easier debug, TBR
     */
    @NonNull
    @Override
    @CallSuper
    @Deprecated
    public DataManager putSerializable(@NonNull final String key, @NonNull final Serializable value) {
        if (BuildConfig.DEBUG) {
            Logger.info(this, " putSerializable, key=" + key + " , type=" + value.getClass().getCanonicalName());
        }
        super.putSerializable(key, value);
        return this;
    }

    /**
     * TODO: use {@link DataAccessor}
     *
     * @return the list of bookshelves formatted as "shelf1, shelf2, shelf3, ...
     */
    public String getBookshelfListAsText() {
        String s;
        final List<Bookshelf> list = getBookshelfList();
        if (list.size() == 0) {
            s = "";
        } else {
            final StringBuilder text = new StringBuilder(list.get(0).name);
            for (int i = 1; i < list.size(); i++) {
                text.append(Bookshelf.SEPARATOR).append(" ").append(list.get(i));
            }
            s = text.toString();
        }
        return s;
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
        ArrayList<Bookshelf> list = super.getSerializable(UniqueId.BKEY_BOOKSHELF_ARRAY);
        return list != null ? list : new ArrayList<Bookshelf>();
    }

    /**
     * Special Accessor
     */
    public void putBookshelfList(@NonNull final ArrayList<Bookshelf> list) {
        super.putSerializable(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
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
        ArrayList<Author> list = super.getSerializable(UniqueId.BKEY_AUTHOR_ARRAY);
        return list != null ? list : new ArrayList<Author>();
    }

    /**
     * Special Accessor
     */
    public void putAuthorList(@NonNull final ArrayList<Author> list) {
        super.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, list);
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
     * Utility routine to get an series list from a data manager
     *
     * TODO: use {@link DataAccessor}
     *
     * @return List of series
     */
    @NonNull
    public ArrayList<Series> getSeriesList() {
        ArrayList<Series> list = super.getSerializable(UniqueId.BKEY_SERIES_ARRAY);
        return list != null ? list : new ArrayList<Series>();
    }

    /**
     * Special Accessor
     */
    public void putSeriesList(@NonNull final ArrayList<Series> list) {
        super.putSerializable(UniqueId.BKEY_SERIES_ARRAY, list);
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
     * Utility routine to get a Content (an AnthologyTitle list) from a data manager
     *
     * TODO: use {@link DataAccessor}
     *
     * @return List of anthology titles
     */
    @NonNull
    @CallSuper
    public ArrayList<AnthologyTitle> getContentList() {
        ArrayList<AnthologyTitle> list = super.getSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY);
        return list != null ? list : new ArrayList<AnthologyTitle>();
    }

    /**
     * Special Accessor
     */
    @CallSuper
    public void putContentList(@NonNull final ArrayList<AnthologyTitle> list) {
        super.putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, list);
    }


    /**
     * Update author details from DB
     *
     * @param db Database connection
     */
    public void refreshAuthorList(@NonNull final CatalogueDBAdapter db) {
        ArrayList<Author> list = getAuthorList();
        for (Author a : list) {
            db.refreshAuthor(a);
        }
        putAuthorList(list);
    }

    /**
     * Cleanup thumbnails from underlying data
     */
    public void cleanupThumbnails() {
        ImageUtils.cleanupThumbnails(mBundle);
    }

    /**
     * Get the underlying raw data.
     * DO NOT UPDATE THIS! IT SHOULD BE USED FOR READING DATA ONLY.
     * 2018-09-29: so we clone it before.
     */
    @NonNull
    public Bundle getRawData() {
        return (Bundle) mBundle.clone();
    }

    /**
     * Build any special purpose validators/accessors
     */
    private void initValidatorsAndAccessors() {
        addValidator(UniqueId.KEY_TITLE, nonBlankValidator);
        addValidator(UniqueId.KEY_ANTHOLOGY_BITMASK, integerValidator);
        addValidator(UniqueId.KEY_BOOK_LIST_PRICE, blankOrFloatValidator);
        addValidator(UniqueId.KEY_BOOK_PAGES, blankOrIntegerValidator);

        /* Anthology needs special handling:
         *
         * bitmask! used for {@link DatabaseDefinitions#DOM_BOOK_ANTHOLOGY_BITMASK}
         *  00 = not an ant,
         *  01 = ant from one author
         *  10 = not an ant, multiple authors -> not in the wild, but could be Omnibus of a set of novels
         *  11 = ant from multiple authors
         *  So for now, the field should be 0,1,3
         *
         *  TODO: enumerator {@link AnthologyTitle.Type}
         */
        addAccessor(IS_ANTHOLOGY, new DataAccessor() {
            @NonNull
            @Override
            public Object get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                Integer bitmask = data.getInt(UniqueId.KEY_ANTHOLOGY_BITMASK);
                return bitmask != 0 ? "1" : "0";
            }

            /**
             * There is a shortcoming here: flipping the checkbox for an original value of "3"
             * sets the value to "2" which means "not an anthology" written by multiple authors
             * Of course if we re-think the value 2 being an Omnibus of a set of novels... then it would be ok.
             */
            @Override
            public void set(@NonNull final DataManager data,
                            @NonNull final Datum datum,
                            @NonNull final Bundle rawData,
                            @NonNull final Object value) {
                Integer bitmask = getInt(UniqueId.KEY_ANTHOLOGY_BITMASK);
                // Parse the string the CheckBox returns us (0 or 1)
                if (Datum.toBoolean(value)) {
                    bitmask |= 0x01;
                } else {
                    bitmask &= 0xFFFFFFFE;
                }
                putInt(UniqueId.KEY_ANTHOLOGY_BITMASK, bitmask);

            }

            @Override
            public boolean isPresent(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return rawData.containsKey(UniqueId.KEY_ANTHOLOGY_BITMASK);
            }

        });

        addAccessor(IS_READ, new DataAccessor() {
            @NonNull
            @Override
            public Object get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return 0 != data.getInt(UniqueId.KEY_BOOK_READ);
            }

            @Override
            public void set(@NonNull final DataManager data,
                            @NonNull final Datum datum,
                            @NonNull final Bundle rawData,
                            @NonNull final Object value) {
                putBoolean(UniqueId.KEY_BOOK_READ, Datum.toBoolean(value));

            }

            @Override
            public boolean isPresent(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return rawData.containsKey(UniqueId.KEY_BOOK_READ);
            }
        });

    }
}
