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
package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.datamanager.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * Represents the underlying data for a book.
 *
 * @author pjw
 */
public class BookData extends DataManager {

    /** Key for special field */
    public static final String IS_ANTHOLOGY = "+IsAnthology";
    /** Key for special field */
    private static final String BOOKSHELF_LIST = "+BookshelfList";
    /** Key for special field */
    private static final String BOOKSHELF_TEXT = "+BookshelfText";
    /** Row ID for book */
    private long mRowId;

    public BookData() {
        this(0, null);
    }

    public BookData(final long rowId) {
        this(rowId, null);
    }

    /**
     * Constructor
     *
     * @param bundle with book data (may be null)
     */
    public BookData(@Nullable final Bundle bundle) {
        this(0L, bundle);
    }

    /**
     * Constructor
     *
     * @param rowId     of book (may be 0 for new)
     * @param bundle    Bundle with book data (may be null)
     */
    public BookData(final long rowId, @Nullable final Bundle bundle) {
        mRowId = rowId;

        // Load from bundle or database
        if (bundle != null) {
            putAll(bundle);
        } else if (mRowId > 0) {
            reload();
        }
        // Create special validators
        initValidators();
    }

    /**
     * Erase everything in this instance and reset the special handlers
     *
     * @return self, for chaining
     */
    @Override
    @NonNull
    public DataManager clear() {
        super.clear();
        // Create special validators
        initValidators();
        return this;
    }

    //TODO: can we simplify this ? not just a 'string' but structured data with proper ID's
    public String getBookshelfList() {
        return getString(BOOKSHELF_LIST);
    }

    public void setBookshelfList(@NonNull final String encodedList) {
        putString(BOOKSHELF_LIST, encodedList);
    }

    /**
     * @return a csv formatted list of bookshelves
     */
    @NonNull
    public String getBookshelfText() {
        final String list = getString(BOOKSHELF_LIST);
        final List<String> items = ArrayUtils.decodeList(Bookshelf.SEPARATOR, list);
        if (items.size() == 0)
            return "";

        final StringBuilder text = new StringBuilder(items.get(0));
        for (int i = 1; i < items.size(); i++) {
            text.append(", ");
            text.append(items.get(i));
        }
        return text.toString();
    }


    /**
     * Accessor
     */
    public long getRowId() {
        return mRowId;
    }

    /**
     * Load the book details from the database
     */
    public void reload() {
        // If ID = 0, no details in DB
        if (getRowId() == 0)
            return;

        // Connect to DB and get cursor for book details
        CatalogueDBAdapter db = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        db.open();
        try {
            try (BooksCursor book = db.fetchBookById(getRowId())) {
                // Put all cursor fields in collection
                putAll(book);

                // Get author, series, bookshelf and anthology title lists
                setAuthorList(db.getBookAuthorList(getRowId()));
                setSeriesList(db.getBookSeriesList(getRowId()));
                setAnthologyTitles(db.getBookAnthologyTitleList(getRowId()));
                setBookshelfList(db.getBookshelvesByBookIdAsStringList(getRowId()));

            } catch (Exception e) {
                Logger.logError(e);
            }
        } finally {
            db.close();
        }
    }

    /**
     * Special Accessor
     */
    public void setAuthorList(@NonNull final ArrayList<Author> list) {
        putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, list);
    }

    /**
     * Special Accessor
     */
    public void setSeriesList(@NonNull final ArrayList<Series> list) {
        putSerializable(UniqueId.BKEY_SERIES_ARRAY, list);
    }

    /**
     * Special Accessor.
     *
     * Build a formatted string for author list.
     */
    @Nullable
    public String getAuthorTextShort() {
        String newText;
        List<Author> list = getAuthors();
        if (list.size() == 0) {
            return null;
        } else {
            newText = list.get(0).getDisplayName();
            if (list.size() > 1) {
                newText += " " + BookCatalogueApp.getResourceString(R.string.and_others);
            }
            return newText;
        }
    }

    /**
     * Utility routine to get an anthology title list from a data manager
     *
     * @return List of anthology titles
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public ArrayList<AnthologyTitle> getAnthologyTitles() {
        ArrayList<AnthologyTitle> list = (ArrayList<AnthologyTitle>) getSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY);
        return list != null ? list : new ArrayList<AnthologyTitle>();
    }

//    /**
//     * Special Accessor.
//     *
//     * Build a formatted string for series list.
//     */
//    public String getSeriesTextShort() {
//        String newText;
//        ArrayList<Series> list = getSeries();
//        if (list.size() == 0) {
//            newText = null;
//        } else {
//            newText = list.get(0).getDisplayName();
//            if (list.size() > 1) {
//                newText += " " + BookCatalogueApp.getResourceString(R.string.and_others);
//            }
//        }
//        return newText;
//    }

    /**
     * Special Accessor
     */
    public void setAnthologyTitles(@NonNull final ArrayList<AnthologyTitle> list) {
        putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, list);
    }

    /**
     * Utility routine to get an author list from a data manager
     *
     * @return List of authors
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public ArrayList<Author> getAuthors() {
        ArrayList<Author> list = (ArrayList<Author>) getSerializable(UniqueId.BKEY_AUTHOR_ARRAY);
        return list != null ? list : new ArrayList<Author>();
    }

    /**
     * Utility routine to get an series list from a data manager
     *
     * @return List of series
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public ArrayList<Series> getSeries() {
        ArrayList<Series> list = (ArrayList<Series>) getSerializable(UniqueId.BKEY_SERIES_ARRAY);
        return list != null ? list : new ArrayList<Series>();
    }

    /**
     * Convenience Accessor
     */
    public boolean isRead() {
        return getInt(UniqueId.KEY_BOOK_READ) != 0;
    }

    /**
     * Convenience Accessor
     */
    public boolean isSigned() {
        return getInt(UniqueId.KEY_BOOK_SIGNED) != 0;
    }

    /**
     * Update author details from DB
     *
     * @param db Database connection
     */
    public void refreshAuthorList(@NonNull final CatalogueDBAdapter db) {
        ArrayList<Author> list = getAuthors();
        for (Author a : list) {
            db.refreshAuthor(a);
        }
        setAuthorList(list);
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
     * Build any special purpose validators
     */
    private void initValidators() {
        addValidator(UniqueId.KEY_TITLE, nonBlankValidator);
        addValidator(UniqueId.KEY_ANTHOLOGY_MASK, integerValidator);
        addValidator(UniqueId.KEY_BOOK_LIST_PRICE, blankOrFloatValidator);
        addValidator(UniqueId.KEY_BOOK_PAGES, blankOrIntegerValidator);

        /* Anthology needs special handling, and we use a formatter to do this. If the original
         * value was 0 or 1, then setting/clearing it here should just set the new value to 0 or 1.
         * However...if if the original value was 2, then we want setting/clearing to alternate
         * between 2 and 0, not 1 and 0.
         * So, despite if being a checkbox, we use an integerValidator and use a special formatter.
         * We also store it in the tag field so that it is automatically serialized with the
         * activity. */
        addAccessor(IS_ANTHOLOGY, new DataAccessor() {
            @Override
            public Object get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                Integer mask = data.getInt(UniqueId.KEY_ANTHOLOGY_MASK);
                return mask != 0 ? "1" : "0";
            }

            @Override
            public void set(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData, @NonNull final Object value) {
                Integer mask = getInt(UniqueId.KEY_ANTHOLOGY_MASK);
                // Parse the string the CheckBox returns us (0 or 1)
                if (Datum.toBoolean(value)) {
                    mask |= 1;
                } else {
                    mask &= 0xFFFFFFFE;
                }
                putInt(UniqueId.KEY_ANTHOLOGY_MASK, mask);

            }

            @Override
            public boolean isPresent(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return rawData.containsKey(UniqueId.KEY_ANTHOLOGY_MASK);
            }

        });

        /* Make a csv formatted list of bookshelves */
        addAccessor(BOOKSHELF_TEXT, new DataAccessor() {
            @Override
            public Object get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return getBookshelfText();
            }

            @Override
            public void set(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData, @NonNull final Object value) {
                throw new IllegalStateException("Bookshelf Text can not be set");
            }

            @Override
            public boolean isPresent(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return !getBookshelfText().isEmpty();
            }
        });

        // Whenever the row ID is written, make sure mRowId is updated.
        addAccessor(UniqueId.KEY_ID, new DataAccessor() {
            @Override
            public Object get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return Datum.toLong(rawData.get(datum.getKey()));
            }

            @Override
            public void set(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData, @NonNull final Object value) {
                rawData.putLong(datum.getKey(), Datum.toLong(value));
                mRowId = rawData.getLong(datum.getKey());
            }

            @Override
            public boolean isPresent(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                return true;
            }
        });
    }
}
