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
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.datamanager.DataAccessor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.CheckListItem;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.dialogs.CheckListItemBase;

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

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_EDITION_BITMASK}
     * Type: Boolean
     */
    private static final String IS_FIRST_EDITION = "+IsFirstEdition";

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_EDITION_BITMASK}
     * Type: Boolean
     */
    private static final String IS_FIRST_IMPRESSION = "+IsFirstImpression";

    /**
     * Key for accessor to the underlying {@link UniqueId#KEY_BOOK_EDITION_BITMASK}
     * Type: Boolean
     */
    private static final String IS_BOOK_CLUB_EDITION = "+IsBookClubEdition";


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
                putTOC(db.getAnthologyTitleListByBook(bookId));
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
     *
     * @return a complete list of Bookshelves each reflecting the book being on that shelf or not
     */
    public ArrayList<CheckListItem<Bookshelf>> getEditableBookshelvesList(@NonNull final CatalogueDBAdapter db) {
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
     * TODO: use {@link DataAccessor}
     *
     * @return the list of bookshelves formatted as "shelf1, shelf2, shelf3, ...
     */
    public String getBookshelfListAsText() {
        List<String> list = new ArrayList<>();
        for (Bookshelf bookshelf : getBookshelfList()) {
            list.add(bookshelf.name);
        }
        return ArrayUtils.toDisplayString(list);
    }

    /**
     * Special Accessor
     */
    public void putBookshelfList(@NonNull final ArrayList<Bookshelf> list) {
        super.putSerializable(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
    }


    /**
     *
     * @return a complete list of Editions each reflecting the book being that edition
     */
    public ArrayList<CheckListItem<Integer>> getEditableEditionList() {
        //Logger.info(this,"edition: " + Integer.toBinaryString(getInt(UniqueId.KEY_BOOK_EDITION_BITMASK)));

        ArrayList<CheckListItem<Integer>> list = new ArrayList<>();
        list.add(new EditionCheckListItem(R.string.edition_first_edition, getBoolean(Book.IS_FIRST_EDITION)));
        list.add(new EditionCheckListItem(R.string.edition_first_impression, getBoolean(Book.IS_FIRST_IMPRESSION)));
        list.add(new EditionCheckListItem(R.string.edition_book_club_edition, getBoolean(Book.IS_BOOK_CLUB_EDITION)));
        return list;
    }

    /**
     *
     * @return a CSV list of editions for this book
     */
    public String getEditionListAsText() {
        List<String> list = new ArrayList<>();
        if (getBoolean(IS_FIRST_EDITION)) {
            list.add(BookCatalogueApp.getResourceString(R.string.edition_first_edition));
        }
        if (getBoolean(IS_FIRST_IMPRESSION)) {
            list.add(BookCatalogueApp.getResourceString(R.string.edition_first_impression));
        }
        if (getBoolean(IS_BOOK_CLUB_EDITION)) {
            list.add(BookCatalogueApp.getResourceString(R.string.edition_book_club_edition));
        }
        return ArrayUtils.toDisplayString(list);
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
    public void putAuthorList(@NonNull final ArrayList<Author> list) {
        super.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, list);
    }

    /**
     * Update author details from DB
     *
     * @param db Database connection
     */
    public void refreshAuthorList(@NonNull final CatalogueDBAdapter db) {
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
        ArrayList<Series> list = super.getSerializable(UniqueId.BKEY_SERIES_ARRAY);
        return list != null ? list : new ArrayList<Series>();
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
    public void putSeriesList(@NonNull final ArrayList<Series> list) {
        super.putSerializable(UniqueId.BKEY_SERIES_ARRAY, list);
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
    public ArrayList<AnthologyTitle> getTOC() {
        ArrayList<AnthologyTitle> list = super.getSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY);
        return list != null ? list : new ArrayList<AnthologyTitle>();
    }

    /**
     * Special Accessor
     */
    @CallSuper
    public void putTOC(@NonNull final ArrayList<AnthologyTitle> list) {
        super.putSerializable(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY, list);
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
        addValidator(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, integerValidator);
        addValidator(UniqueId.KEY_BOOK_EDITION_BITMASK, integerValidator);
        addValidator(UniqueId.KEY_BOOK_LIST_PRICE, blankOrFloatValidator);
        addValidator(UniqueId.KEY_BOOK_PAGES, blankOrIntegerValidator);

        addAccessor(IS_READ, new DataAccessor() {
            @NonNull
            @Override
            public Boolean get(@NonNull final DataManager data,
                               @NonNull final Datum datum,
                               @NonNull final Bundle rawData) {
                return data.getInt(UniqueId.KEY_BOOK_READ) != 0;
            }

            @Override
            public void set(@NonNull final DataManager data,
                            @NonNull final Datum datum,
                            @NonNull final Bundle rawData,
                            @NonNull final Object value) {
                data.putBoolean(UniqueId.KEY_BOOK_READ, Datum.toBoolean(value));

            }

            @Override
            public boolean isPresent(@NonNull final DataManager data,
                                     @NonNull final Datum datum,
                                     @NonNull final Bundle rawData) {
                return rawData.containsKey(UniqueId.KEY_BOOK_READ);
            }
        });

        /* This only handles the fact of being an anthology or not. Does not handle 'multiple authors' */
        addAccessor(IS_ANTHOLOGY, new DataAccessor() {
            @NonNull
            @Override
            public Boolean get(@NonNull final DataManager data,
                              @NonNull final Datum datum,
                              @NonNull final Bundle rawData) {
                Integer bitmask = data.getInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK);
                return (bitmask & DatabaseDefinitions.DOM_ANTHOLOGY) != 0;
            }

            @Override
            public void set(@NonNull final DataManager data,
                            @NonNull final Datum datum,
                            @NonNull final Bundle rawData,
                            @NonNull final Object value) {
                Integer bitmask = data.getInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK);
                // Parse the string the CheckBox returns us (0 or 1)
                if (Datum.toBoolean(value)) {
                    bitmask |= DatabaseDefinitions.DOM_ANTHOLOGY;
                } else {
                    bitmask &= ~DatabaseDefinitions.DOM_ANTHOLOGY;
                }
                data.putInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, bitmask);
            }

            @Override
            public boolean isPresent(@NonNull final DataManager data,
                                     @NonNull final Datum datum,
                                     @NonNull final Bundle rawData) {
                return rawData.containsKey(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK);
            }

        });

        addAccessor(IS_FIRST_EDITION, new DataAccessor() {
            @NonNull
            @Override
            public Boolean get(@NonNull final DataManager data,
                              @NonNull final Datum datum,
                              @NonNull final Bundle rawData) {
                Integer bitmask = data.getInt(UniqueId.KEY_BOOK_EDITION_BITMASK);
                return (bitmask & DatabaseDefinitions.DOM_EDITION_FIRST) != 0;
            }


            @Override
            public void set(@NonNull final DataManager data,
                            @NonNull final Datum datum,
                            @NonNull final Bundle rawData,
                            @NonNull final Object value) {
                Integer bitmask = data.getInt(UniqueId.KEY_BOOK_EDITION_BITMASK);
                // Parse the string the CheckBox returns us (0 or 1)
                if (Datum.toBoolean(value)) {
                    bitmask |= DatabaseDefinitions.DOM_EDITION_FIRST;
                } else {
                    bitmask &= ~DatabaseDefinitions.DOM_EDITION_FIRST;
                }
                data.putInt(UniqueId.KEY_BOOK_EDITION_BITMASK, bitmask);

            }

            @Override
            public boolean isPresent(@NonNull final DataManager data,
                                     @NonNull final Datum datum,
                                     @NonNull final Bundle rawData) {
                return rawData.containsKey(UniqueId.KEY_BOOK_EDITION_BITMASK);
            }
        });

        addAccessor(IS_FIRST_IMPRESSION, new DataAccessor() {
            @NonNull
            @Override
            public Boolean get(@NonNull final DataManager data,
                              @NonNull final Datum datum,
                              @NonNull final Bundle rawData) {
                Integer bitmask = data.getInt(UniqueId.KEY_BOOK_EDITION_BITMASK);
                return (bitmask & DatabaseDefinitions.DOM_EDITION_FIRST_IMPRESSION) != 0;
            }

            @Override
            public void set(@NonNull final DataManager data,
                            @NonNull final Datum datum,
                            @NonNull final Bundle rawData,
                            @NonNull final Object value) {
                Integer bitmask = data.getInt(UniqueId.KEY_BOOK_EDITION_BITMASK);
                // Parse the string the CheckBox returns us (0 or 1)
                if (Datum.toBoolean(value)) {
                    bitmask |= DatabaseDefinitions.DOM_EDITION_FIRST_IMPRESSION;
                } else {
                    bitmask &= ~DatabaseDefinitions.DOM_EDITION_FIRST_IMPRESSION;
                }
                data.putInt(UniqueId.KEY_BOOK_EDITION_BITMASK, bitmask);

            }

            @Override
            public boolean isPresent(@NonNull final DataManager data,
                                     @NonNull final Datum datum,
                                     @NonNull final Bundle rawData) {
                return rawData.containsKey(UniqueId.KEY_BOOK_EDITION_BITMASK);
            }
        });

        addAccessor(IS_BOOK_CLUB_EDITION, new DataAccessor() {
            @NonNull
            @Override
            public Boolean get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData) {
                Integer bitmask = data.getInt(UniqueId.KEY_BOOK_EDITION_BITMASK);
                return (bitmask & DatabaseDefinitions.DOM_EDITION_BOOK_CLUB) != 0;
            }

            @Override
            public void set(@NonNull final DataManager data,
                            @NonNull final Datum datum,
                            @NonNull final Bundle rawData,
                            @NonNull final Object value) {
                Integer bitmask = data.getInt(UniqueId.KEY_BOOK_EDITION_BITMASK);
                // Parse the string the CheckBox returns us (0 or 1)
                if (Datum.toBoolean(value)) {
                    bitmask |= DatabaseDefinitions.DOM_EDITION_BOOK_CLUB;
                } else {
                    bitmask &= ~DatabaseDefinitions.DOM_EDITION_BOOK_CLUB;
                }
                data.putInt(UniqueId.KEY_BOOK_EDITION_BITMASK, bitmask);

            }

            @Override
            public boolean isPresent(@NonNull final DataManager data,
                                     @NonNull final Datum datum,
                                     @NonNull final Bundle rawData) {
                return rawData.containsKey(UniqueId.KEY_BOOK_EDITION_BITMASK);
            }
        });
    }

    public static class EditionCheckListItem extends CheckListItemBase<Integer> {
        public EditionCheckListItem() {
        }

        EditionCheckListItem(@NonNull final Integer item, final boolean selected) {
            super(item, selected);
        }

        public String getLabel() {
            return BookCatalogueApp.getResourceString(getItem());
        }
    }

    public static class BookshelfCheckListItem extends CheckListItemBase<Bookshelf> {
        public BookshelfCheckListItem() {
        }

        BookshelfCheckListItem(@NonNull final Bookshelf item, final boolean selected) {
            super(item, selected);
        }

        public String getLabel() {
            return getItem().name;
        }
    }
}
