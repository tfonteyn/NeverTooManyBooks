/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License V3
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

package com.eleybourn.bookcatalogue.booklist;

import android.support.annotation.CallSuper;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.CompoundKey;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.properties.BooleanListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_BOOK;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_BOOKSHELF;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_DAY_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_DAY_READ;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_FORMAT;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_GENRE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOANED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOCATION;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MAX;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_PUBLISHED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_READ;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_PUBLISHER;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_RATING;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_READ_AND_UNREAD;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_SERIES;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_TITLE_LETTER;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_UPDATE_YEAR;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_PUBLISHED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_READ;

/**
 * Class representing a single level in the booklist hierarchy.
 *
 * @author Philip Warner
 */
public class BooklistGroup implements Serializable {
    private static final long serialVersionUID = 1012206875683862714L;

    private static final String PREF_SHOW_ALL_AUTHORS = "APP.ShowAllAuthors";
    private static final String PREF_SHOW_ALL_SERIES = "APP.ShowAllSeries";
    private static final String PREF_DISPLAY_FIRST_THEN_LAST_NAMES = "APP.DisplayFirstThenLast";

    private static final Map<Integer, String> mRowKindNames = new UniqueMap<>();
    static {
        mRowKindNames.put(ROW_KIND_AUTHOR, BookCatalogueApp.getResourceString(R.string.author));
        mRowKindNames.put(ROW_KIND_SERIES, BookCatalogueApp.getResourceString(R.string.series));
        mRowKindNames.put(ROW_KIND_GENRE, BookCatalogueApp.getResourceString(R.string.lbl_genre));
        mRowKindNames.put(ROW_KIND_PUBLISHER, BookCatalogueApp.getResourceString(R.string.lbl_publisher));
        mRowKindNames.put(ROW_KIND_READ_AND_UNREAD, BookCatalogueApp.getResourceString(R.string.read_amp_unread));
        mRowKindNames.put(ROW_KIND_LOANED, BookCatalogueApp.getResourceString(R.string.loaned));
        mRowKindNames.put(ROW_KIND_YEAR_PUBLISHED, BookCatalogueApp.getResourceString(R.string.publication_year));
        mRowKindNames.put(ROW_KIND_MONTH_PUBLISHED, BookCatalogueApp.getResourceString(R.string.publication_month));
        mRowKindNames.put(ROW_KIND_TITLE_LETTER, BookCatalogueApp.getResourceString(R.string.style_builtin_title_first_letter));
        mRowKindNames.put(ROW_KIND_YEAR_ADDED, BookCatalogueApp.getResourceString(R.string.added_year));
        mRowKindNames.put(ROW_KIND_MONTH_ADDED, BookCatalogueApp.getResourceString(R.string.added_month));
        mRowKindNames.put(ROW_KIND_DAY_ADDED, BookCatalogueApp.getResourceString(R.string.added_day));
        mRowKindNames.put(ROW_KIND_FORMAT, BookCatalogueApp.getResourceString(R.string.lbl_format));
        mRowKindNames.put(ROW_KIND_YEAR_READ, BookCatalogueApp.getResourceString(R.string.read_year));
        mRowKindNames.put(ROW_KIND_MONTH_READ, BookCatalogueApp.getResourceString(R.string.read_month));
        mRowKindNames.put(ROW_KIND_DAY_READ, BookCatalogueApp.getResourceString(R.string.read_day));
        mRowKindNames.put(ROW_KIND_LOCATION, BookCatalogueApp.getResourceString(R.string.lbl_location));
        mRowKindNames.put(ROW_KIND_LANGUAGE, BookCatalogueApp.getResourceString(R.string.lbl_language));
        mRowKindNames.put(ROW_KIND_UPDATE_DAY, BookCatalogueApp.getResourceString(R.string.update_day));
        mRowKindNames.put(ROW_KIND_UPDATE_MONTH, BookCatalogueApp.getResourceString(R.string.update_month));
        mRowKindNames.put(ROW_KIND_UPDATE_YEAR, BookCatalogueApp.getResourceString(R.string.update_year));
        mRowKindNames.put(ROW_KIND_RATING, BookCatalogueApp.getResourceString(R.string.lbl_rating));
        mRowKindNames.put(ROW_KIND_BOOKSHELF, BookCatalogueApp.getResourceString(R.string.lbl_bookshelf));
        mRowKindNames.put(ROW_KIND_BOOK, BookCatalogueApp.getResourceString(R.string.book));
        // NEWKIND: ROW_KIND_x

        // Sanity check
        for (int i = 0; i <= ROW_KIND_MAX; i++) {
            if (!mRowKindNames.containsKey(i))
                throw new IllegalArgumentException("Missing mRowKindNames for kind " + i);
        }
    }

    /** The Row Kind of this group */
    public int kind;
    /** The domains represented by this group. Set at runtime by builder based on current group and outer groups */
    transient ArrayList<DomainDefinition> groupDomains;
    /** The domain used to display this group. Set at runtime by builder based on internal logic of builder */
    transient DomainDefinition displayDomain;
    /** Compound key of this group. Set at runtime by builder based on current group and outer groups */
    private transient CompoundKey mCompoundKey;

    /**
     * Constructor
     *
     */
    BooklistGroup(@IntRange(from=0, to=ROW_KIND_MAX) final int kind) {
        this.kind = kind;
    }

    /**
     * Return a list of all defined row kinds.
     */
    @NonNull
    static int[] getRowKinds() {
        int[] kinds = new int[mRowKindNames.size()];
        int pos = 0;
        for (Entry<Integer, String> e : mRowKindNames.entrySet()) {
            kinds[pos++] = e.getKey();
        }
        return kinds;
    }

    /**
     * Return a list of BooklistGroups, one for each defined row kind
     */
    @NonNull
    static List<BooklistGroup> getAllGroups() {
        List<BooklistGroup> list = new ArrayList<>();

        for (Entry<Integer, String> e : mRowKindNames.entrySet()) {
            final int kind = e.getKey();
            if (kind != ROW_KIND_BOOK)
                list.add(newGroup(kind));
        }
        return list;
    }

    /**
     * Create a new BooklistGroup of the specified kind, creating any more specific subclasses as necessary.
     *
     * @param kind Kind of group to create
     */
    @NonNull
    static BooklistGroup newGroup(final int kind) {
        switch (kind) {
            case ROW_KIND_AUTHOR:
                return new BooklistGroup.BooklistAuthorGroup();
            case ROW_KIND_SERIES:
                return new BooklistGroup.BooklistSeriesGroup();
            default:
                return new BooklistGroup(kind);
        }
    }

    /** Setter for compound key */
    void setKeyComponents(final @NonNull String prefix, final @NonNull DomainDefinition... domains) {
        mCompoundKey = new CompoundKey(prefix, domains);
    }

    /** Getter for compound key */
    @NonNull
    CompoundKey getCompoundKey() {
        return mCompoundKey;
    }

    @NonNull
    public String getName() {
        return mRowKindNames.get(kind);
    }

    public void getStyleProperties(final @NonNull Properties list) {
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     * @see Serializable
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     * @see Serializable
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
    }

    /**
     * Static definitions of the kinds of rows that can be displayed and summarized.
     * Adding new row types needs to involve changes to:
     *
     * - {@link BooklistBuilder} (to build the correct SQL)
     * - {@link BooksMultiTypeListHandler} (to know what to do with the new type)
     *
     * @author Philip Warner
     */
    public static final class RowKinds {
        public static final int ROW_KIND_BOOK = 0;              // Supported
        public static final int ROW_KIND_AUTHOR = 1;            // Supported
        public static final int ROW_KIND_SERIES = 2;            // Supported
        public static final int ROW_KIND_GENRE = 3;             // Supported
        public static final int ROW_KIND_PUBLISHER = 4;         // Supported
        public static final int ROW_KIND_READ_AND_UNREAD = 5;   // Supported
        public static final int ROW_KIND_LOANED = 6;            // Supported
        public static final int ROW_KIND_YEAR_PUBLISHED = 7;    // Supported
        public static final int ROW_KIND_MONTH_PUBLISHED = 8;   // Supported
        public static final int ROW_KIND_TITLE_LETTER = 9;      // Supported
        public static final int ROW_KIND_YEAR_ADDED = 10;       // Supported
        public static final int ROW_KIND_MONTH_ADDED = 11;      // Supported
        public static final int ROW_KIND_DAY_ADDED = 12;        // Supported
        public static final int ROW_KIND_FORMAT = 13;           // Supported
        public static final int ROW_KIND_YEAR_READ = 14;        // Supported
        public static final int ROW_KIND_MONTH_READ = 15;       // Supported
        public static final int ROW_KIND_DAY_READ = 16;         // Supported
        public static final int ROW_KIND_LOCATION = 17;         // Supported
        public static final int ROW_KIND_LANGUAGE = 18;         // Supported
        public static final int ROW_KIND_UPDATE_YEAR = 19;      // Supported
        public static final int ROW_KIND_UPDATE_MONTH = 20;     // Supported
        public static final int ROW_KIND_UPDATE_DAY = 21;       // Supported
        public static final int ROW_KIND_RATING = 22;           // Supported
        public static final int ROW_KIND_BOOKSHELF = 23;        // Supported
        // NEWKIND: ROW_KIND_x
        public static final int ROW_KIND_MAX = 23;                // **** NOTE **** ALWAYS update after adding a row kind...
    }

    /**
     * Subclass of HashMap with an add(...) method that ensures values are unique.
     *
     * @param <K> Type of Key values
     * @param <V> Type of data values
     *
     * @author pjw
     */
    private static class UniqueMap<K, V> extends HashMap<K, V> {
        private static final long serialVersionUID = 1L;

        /**
         * @param key   Key for new value
         * @param value Data for new value
         *
         * @throws IllegalArgumentException if key already stored
         */
        @Override
        @NonNull
        @CallSuper
        public V put(final @NonNull K key, final @NonNull V value) {
            if (super.put(key, value) != null) {
                throw new IllegalArgumentException("Map already contains key value" + key);
            }
            return value;
        }
    }

    /**
     * Specialized BooklistGroup representing an Series group. Includes extra attributes based
     * on preferences.
     *
     * @author Philip Warner
     */
    public static class BooklistSeriesGroup extends BooklistGroup /* implements Parcelable */ {
        private static final long serialVersionUID = 9023218506278704155L;
        /** mAllSeries Parameter values and descriptions */
        private static final ItemEntries<Boolean> mAllSeriesItems = new ItemEntries<>();

        static {
            String kind = BookCatalogueApp.getResourceString(R.string.series);
            mAllSeriesItems.add(null, R.string.use_default_setting);
            mAllSeriesItems.add(false, R.string.books_with_multiple_show_book_under_primary_1s_only, kind);
            mAllSeriesItems.add(true, R.string.books_with_multiple_show_book_under_each_1s, kind);
        }

        /** Show book under each series it appears in? */
        transient BooleanListProperty mAllSeries;

        BooklistSeriesGroup() {
            super(ROW_KIND_SERIES);
            initProperties();
            mAllSeries.set((Boolean) null);
        }

        private void initProperties() {
            mAllSeries = new BooleanListProperty(mAllSeriesItems, "AllSeries",
                    PropertyGroup.GRP_SERIES, R.string.books_with_multiple_series)
                    .setPreferenceKey(PREF_SHOW_ALL_SERIES)
                    .setDefaultValue(false)
                    .setHint(R.string.hint_series_book_may_appear_more_than_once);
        }

        /**
         * Custom serialization support. The signature of this method should never be changed.
         * @see Serializable
         */
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            // We use read/write Object so that NULL values are preserved
            out.writeObject(mAllSeries.get());
        }

        /**
         * We need to set the name resource ID for the properties since these may change across versions.
         *
         * Custom serialization support. The signature of this method should never be changed.
         * @see Serializable
         */
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            initProperties();
            // We use read/write Object so that NULL values are preserved
            mAllSeries.set((Boolean) in.readObject());
        }

        boolean showAllSeries() {
            return mAllSeries.isTrue();
        }

        @Override
        @CallSuper
        public void getStyleProperties(final @NonNull Properties list) {
            super.getStyleProperties(list);
            list.add(mAllSeries);
        }
    }

    /**
     * Specialized BooklistGroup representing an Author group. Includes extra attributes based
     * on preferences.
     *
     * @author Philip Warner
     */
    public static class BooklistAuthorGroup extends BooklistGroup {
        private static final long serialVersionUID = -1984868877792780113L;
        private static final ItemEntries<Boolean> mGivenNameFirstItems = new ItemEntries<>();
        private static final ItemEntries<Boolean> mAllAuthorsItems = new ItemEntries<>();

        static {
            mGivenNameFirstItems.add(null, R.string.use_default_setting);
            mGivenNameFirstItems.add(false, R.string.family_name_first_eg);
            mGivenNameFirstItems.add(true, R.string.given_name_first_eg);
        }

        static {
            String kind = BookCatalogueApp.getResourceString(R.string.author);
            mAllAuthorsItems.add(null, R.string.use_default_setting);
            mAllAuthorsItems.add(false, R.string.books_with_multiple_show_book_under_primary_1s_only, kind);
            mAllAuthorsItems.add(true, R.string.books_with_multiple_show_book_under_each_1s, kind);
        }

        /** Support for 'Show Given Name' property */
        transient BooleanListProperty mGivenName;
        /** Support for 'Show All Authors of Book' property */
        transient BooleanListProperty mAllAuthors;

        BooklistAuthorGroup() {
            super(ROW_KIND_AUTHOR);
            initProperties();
            mAllAuthors.set((Boolean) null);
            mGivenName.set((Boolean) null);
        }

        /**
         * Create the properties objects; these are transient, so not created by deserialization,
         * and need to be created in constructors as well.
         */
        private void initProperties() {
            mAllAuthors = new BooleanListProperty(mAllAuthorsItems, "AllAuthors",
                    PropertyGroup.GRP_AUTHOR, R.string.books_with_multiple_authors)
                    .setPreferenceKey(PREF_SHOW_ALL_AUTHORS)
                    .setHint(R.string.hint_authors_book_may_appear_more_than_once);

            mGivenName = new BooleanListProperty(mGivenNameFirstItems, "GivenName",
                    PropertyGroup.GRP_AUTHOR, R.string.format_of_author_names)
                    .setPreferenceKey(PREF_DISPLAY_FIRST_THEN_LAST_NAMES);
        }

        /**
         * Custom serialization support. The signature of this method should never be changed.
         * @see Serializable
         */
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            // We use read/write Object so that NULL values are preserved
            out.writeObject(mAllAuthors.get());
            out.writeObject(mGivenName.get());
        }

        /**
         * We need to set the name resource ID for the properties since these may change across versions.
         *
         * Custom serialization support. The signature of this method should never be changed.
         * @see Serializable
         */
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            initProperties();
            // We use read/write Object so that NULL values are preserved
            mAllAuthors.set((Boolean) in.readObject());
            mGivenName.set((Boolean) in.readObject());
        }

        boolean showAllAuthors() {
            return mAllAuthors.isTrue();
        }

        boolean showGivenName() {
            return mGivenName.isTrue();
        }

        /**
         * Get the Property objects that this group will contribute to a Style.
         */
        @Override
        @CallSuper
        public void getStyleProperties(final @NonNull Properties list) {
            super.getStyleProperties(list);

            list.add(mAllAuthors);
            list.add(mGivenName);
        }

    }
}

