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
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
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
import java.util.List;

import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.RowKinds.ROW_KIND_SERIES;

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

    /** The Row Kind of this group */
    public final int kind;
    /** The domains represented by this group. Set at runtime by builder based on current group and outer groups */
    transient ArrayList<DomainDefinition> groupDomains;
    /** The domain used to display this group. Set at runtime by builder based on internal logic of builder */
    transient DomainDefinition displayDomain;
    /** Compound key of this group. Set at runtime by builder based on current group and outer groups */
    private transient CompoundKey mCompoundKey;

    /**
     * Constructor
     */
    BooklistGroup(final int kind) {
        this.kind = kind;
    }

    /**
     * Return a list of BooklistGroups, one for each defined row kind
     */
    @NonNull
    static List<BooklistGroup> getAllGroups() {
        List<BooklistGroup> list = new ArrayList<>();
        //skip BOOK KIND
        for (int kind = 1; kind < RowKinds.size(); kind++) {
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
        return RowKinds.getName(kind);
    }

    public void getStyleProperties(final @NonNull Properties list) {
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
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
            String kind = BookCatalogueApp.getResourceString(R.string.lbl_series);
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
         *
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
         *
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
            mGivenNameFirstItems.add(false, R.string.blp_format_author_name_family_first);
            mGivenNameFirstItems.add(true, R.string.blp_format_author_name_given_first);
        }

        static {
            String kind = BookCatalogueApp.getResourceString(R.string.lbl_author);
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
                    PropertyGroup.GRP_AUTHOR, R.string.blp_format_author_name)
                    .setPreferenceKey(PREF_DISPLAY_FIRST_THEN_LAST_NAMES);
        }

        /**
         * Custom serialization support. The signature of this method should never be changed.
         *
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
         *
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

