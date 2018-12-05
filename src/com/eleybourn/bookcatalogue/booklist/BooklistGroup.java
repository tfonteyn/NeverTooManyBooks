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

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.CompoundKey;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.PropertyList;

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
public class BooklistGroup implements Serializable, Parcelable {
    public static final Parcelable.Creator<BooklistGroup> CREATOR = new Parcelable.Creator<BooklistGroup>() {
        @Override
        public BooklistGroup createFromParcel(final @NonNull Parcel in) {
            return new BooklistGroup(in);
        }

        @Override
        public BooklistGroup[] newArray(final int size) {
            return new BooklistGroup[size];
        }
    };
    private static final long serialVersionUID = 1012206875683862714L;
    /** The Row Kind of this group */
    public final int kind;
    /** The domains represented by this group. Set at runtime by builder based on current group and outer groups */
    @Nullable
    transient ArrayList<DomainDefinition> groupDomains;
    /** The domain used to display this group. Set at runtime by builder based on internal logic of builder */
    @Nullable
    transient DomainDefinition displayDomain;
    /** Compound key of this group. Set at runtime by builder based on current group and outer groups */
    private transient CompoundKey mCompoundKey;

    /**
     * Constructor
     */
    BooklistGroup(final int kind) {
        this.kind = kind;
    }

    BooklistGroup(final @NonNull Parcel in) {
        kind = in.readInt();
        displayDomain = in.readParcelable(getClass().getClassLoader());
        mCompoundKey = in.readParcelable(getClass().getClassLoader());
        groupDomains = new ArrayList<>();
        in.readList(groupDomains, getClass().getClassLoader());
    }

    /**
     * Return a list of BooklistGroups, one for each defined row kind
     */
    @NonNull
    static List<BooklistGroup> getAllGroups() {
        List<BooklistGroup> list = new ArrayList<>();
        //skip BOOK KIND
        for (int kind = 1; kind < RowKinds.size(); kind++) {
            list.add(newInstance(kind));
        }
        return list;
    }

    /**
     * Create a new BooklistGroup of the specified kind, creating any specific subclasses as necessary.
     *
     * @param kind Kind of group to create
     */
    @NonNull
    static BooklistGroup newInstance(final int kind) {
        switch (kind) {
            case ROW_KIND_AUTHOR:
                return new BooklistAuthorGroup();
            case ROW_KIND_SERIES:
                return new BooklistSeriesGroup();
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
        if (BuildConfig.DEBUG) {
            if (mCompoundKey == null) {
                throw new IllegalStateException("mCompoundKey was null");
            }
        }
        return mCompoundKey;
    }

    @NonNull
    public String getName() {
        return RowKinds.getName(kind);
    }

    /**
     * Get the Property objects that this group will contribute to a Style.
     */
    public void getStyleProperties(final @NonNull PropertyList /* in/out */ list) {
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

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final @NonNull Parcel dest, int flags) {
        dest.writeInt(kind);
        // TEST: displayDomain, mCompoundKey, groupDomains are marked as transient. Do we need to do this ?
        dest.writeParcelable(displayDomain, 0);
        dest.writeParcelable(mCompoundKey, 0);
        dest.writeList(groupDomains);
    }

    /**
     * Note to self: do not rename or move this class, deserialization will break.
     *
     * Specialized BooklistGroup representing an Series group. Includes extra attributes based
     * on preferences.
     *
     * @author Philip Warner
     */
    public static class BooklistSeriesGroup extends BooklistGroup implements Serializable, Parcelable {
        public static final Creator<BooklistSeriesGroup> CREATOR = new Creator<BooklistSeriesGroup>() {
            @Override
            public BooklistSeriesGroup createFromParcel(Parcel in) {
                return new BooklistSeriesGroup(in);
            }

            @Override
            public BooklistSeriesGroup[] newArray(int size) {
                return new BooklistSeriesGroup[size];
            }
        };

        public static final String PREF_SHOW_ALL_SERIES = BooklistStyle.TAG + "Group.Show.AllSeries";

        private static final long serialVersionUID = 9023218506278704155L;
        /** mAllSeries Parameter values and descriptions */
        private static final String kind = BookCatalogueApp.getResourceString(R.string.lbl_series);

        /** Show book under each series it appears in? */
        private transient BooleanProperty mAllSeries;

        BooklistSeriesGroup() {
            super(ROW_KIND_SERIES);
            initProperties();
            mAllSeries.setValue(null);
        }

        BooklistSeriesGroup(Parcel in) {
            super(in);
            initProperties();
            mAllSeries.readFromParcel(in);
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            mAllSeries.writeToParcel(dest);
        }

        /**
         * Create the properties objects; these are transient, so not created by deserialization,
         * and need to be created in constructors as well.
         */
        private void initProperties() {
            mAllSeries = new BooleanProperty(R.string.books_with_multiple_series,
                    PropertyGroup.GRP_SERIES)
                    .setPreferenceKey(PREF_SHOW_ALL_SERIES)
                    .setHint(R.string.hint_series_book_may_appear_more_than_once)
                    .setTrueLabel(R.string.books_with_multiple_show_book_under_each_1s, kind)
                    .setFalseLabel(R.string.books_with_multiple_show_book_under_primary_1s_only, kind);
        }

        boolean showAllSeries() {
            return mAllSeries.isTrue();
        }

        /**
         * Get the Property objects that this group will contribute to a Style.
         */
        @Override
        @CallSuper
        public void getStyleProperties(final @NonNull PropertyList list) {
            super.getStyleProperties(list);
            list.add(mAllSeries);
        }

        /**
         * Custom serialization support. The signature of this method should never be changed.
         *
         * @see Serializable
         */
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            // We use read/write Object so that NULL values are preserved
            out.writeObject(mAllSeries.getValue());
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
            mAllSeries.setValue((Boolean) in.readObject());
        }
    }

    /**
     * Note to self: do not rename or move this class, deserialization will break.
     *
     * Specialized BooklistGroup representing an Author group. Includes extra attributes based
     * on preferences.
     *
     * @author Philip Warner
     */
    public static class BooklistAuthorGroup extends BooklistGroup implements Serializable, Parcelable {
        public static final Creator<BooklistAuthorGroup> CREATOR = new Creator<BooklistAuthorGroup>() {
            @Override
            public BooklistAuthorGroup createFromParcel(Parcel in) {
                return new BooklistAuthorGroup(in);
            }

            @Override
            public BooklistAuthorGroup[] newArray(int size) {
                return new BooklistAuthorGroup[size];
            }
        };
        public static final String PREF_SHOW_ALL_AUTHORS = BooklistStyle.TAG + "Group.Show.AllAuthors";
        public static final String PREF_DISPLAY_FIRST_THEN_LAST_NAMES = BooklistStyle.TAG + "Group.Show.AllAuthors.DisplayFirstThenLast";
        private static final long serialVersionUID = -1984868877792780113L;

        private static final String kind = BookCatalogueApp.getResourceString(R.string.lbl_author);

        /** Support for 'Show Given Name' property */
        private transient BooleanProperty mGivenName;
        /** Support for 'Show All Authors of Book' property */
        private transient BooleanProperty mAllAuthors;

        BooklistAuthorGroup() {
            super(ROW_KIND_AUTHOR);
            initProperties();
        }

        BooklistAuthorGroup(final @NonNull Parcel in) {
            super(in);
            initProperties();
            mAllAuthors.readFromParcel(in);
            mGivenName.readFromParcel(in);
        }

        @Override
        public void writeToParcel(final @NonNull Parcel dest, final int flags) {
            super.writeToParcel(dest, flags);
            mAllAuthors.writeToParcel(dest);
            mGivenName.writeToParcel(dest);
        }

        /**
         * Create the properties objects; these are transient, so not created by deserialization,
         * and need to be created in constructors as well.
         */
        private void initProperties() {
            mAllAuthors = new BooleanProperty(R.string.books_with_multiple_authors,
                    PropertyGroup.GRP_AUTHOR)
                    .setPreferenceKey(PREF_SHOW_ALL_AUTHORS)
                    .setHint(R.string.hint_authors_book_may_appear_more_than_once)
                    .setTrueLabel(R.string.books_with_multiple_show_book_under_each_1s, kind)
                    .setFalseLabel(R.string.books_with_multiple_show_book_under_primary_1s_only, kind);

            mGivenName = new BooleanProperty(R.string.blp_format_author_name, PropertyGroup.GRP_AUTHOR, Boolean.TRUE)
                    .setPreferenceKey(PREF_DISPLAY_FIRST_THEN_LAST_NAMES)
                    .setOptionLabels(R.string.blp_format_author_name_given_first, R.string.blp_format_author_name_family_first);
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
        public void getStyleProperties(final @NonNull PropertyList /* in/out */ list) {
            super.getStyleProperties(list);
            list.add(mAllAuthors);
            list.add(mGivenName);
        }

        /**
         * Custom serialization support. The signature of this method should never be changed.
         *
         * @see Serializable
         */
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            // We use read/write Object so that NULL values are preserved
            out.writeObject(mAllAuthors.getValue());
            out.writeObject(mGivenName.getValue());
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
            mAllAuthors.setValue((Boolean) in.readObject());
            mGivenName.setValue((Boolean) in.readObject());
        }
    }
}

