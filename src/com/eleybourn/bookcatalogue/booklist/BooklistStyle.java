/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.booklist;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.ListOfIntegerValuesProperty;
import com.eleybourn.bookcatalogue.properties.ListOfValuesProperty.ItemList;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.PropertyList;
import com.eleybourn.bookcatalogue.properties.StringProperty;
import com.eleybourn.bookcatalogue.utils.RTE.DeserializationException;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Represents a specific style of book list (eg. authors/series).
 * Individual {@link BooklistGroup} objects are added to a {@link BooklistStyle} in order
 * to describe the resulting list style.
 *
 * ENHANCE: Allow for style-based overrides of things currently stored in preferences
 * This should include thumbnail presence/size, book-in-each-series etc. as well as font sizes.
 *
 * How to add a new Group:
 *
 * 1. add it to {@link RowKinds} and update ROW_KIND_TOTAL
 *
 * 2. if necessary add new domain to {@link DatabaseDefinitions }
 *
 * 3. modify {@link BooklistBuilder#build} to add the necessary grouped/sorted domains
 *
 * 4. modify {@link BooksMultiTypeListHandler} ; If it is just a string field,
 * then use a {@link BooksMultiTypeListHandler.GenericStringHolder}, otherwise add a new holder.
 *
 *
 * Need to at least modify {@link BooksMultiTypeListHandler#newHolder}
 *
 * @author Philip Warner
 */
public class BooklistStyle implements Iterable<BooklistGroup>, Serializable, Parcelable {

    /** Extra book data to show at lowest level */
    public static final int EXTRAS_BOOKSHELVES = 1;
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_LOCATION = (1 << 1);
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_PUBLISHER = (1 << 3);
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_AUTHOR = (1 << 4);
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_THUMBNAIL = (1 << 5);
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_THUMBNAIL_LARGE = (1 << 6);
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_FORMAT = (1 << 7);
    /** the scaling for 'condensed' text */
    public static final float SCALE = 0.8f;
    public static final Integer SUMMARY_SHOW_COUNT = 1;
    public static final Integer SUMMARY_SHOW_LEVEL_1 = 1 << 1;
    public static final Integer SUMMARY_SHOW_LEVEL_2 = 1 << 2;
    public static final Integer SUMMARY_SHOW_ALL = 0xff;
    public static final Creator<BooklistStyle> CREATOR = new Creator<BooklistStyle>() {
        @Override
        public BooklistStyle createFromParcel(Parcel in) {
            return new BooklistStyle(in);
        }

        @Override
        public BooklistStyle[] newArray(int size) {
            return new BooklistStyle[size];
        }
    };
    static final int LIST_FONT_SIZE_USE_SMALLER = 2;
    private static final int LIST_FONT_SIZE_USE_DEFAULT = 0;
    private static final int LIST_FONT_SIZE_USE_NORMAL = 1;
    /** bitmask */
    private static final Integer SUMMARY_HIDE = 0;
    private static final long serialVersionUID = 6615877148246388549L;
    /** version field used in serialized data reading from file, see {@link #readObject} */
    private static final long realSerialVersion = 5;
    private static final String SFX_SHOW_AUTHOR = "ShowAuthor";
    private static final String SFX_SHOW_BOOKSHELVES = "ShowBookshelves";
    private static final String SFX_SHOW_FORMAT = "ShowFormat";
    private static final String SFX_SHOW_LOCATION = "ShowLocation";
    private static final String SFX_SHOW_PUBLISHER = "ShowPublisher";
    private static final String SFX_SHOW_THUMBNAILS = "ShowThumbnails";
    private static final String SFX_LARGE_THUMBNAILS = "LargeThumbnails";
    private static final String SFX_CONDENSED = "Condensed";
    private static final String SFX_SHOW_HEADER_INFO = "ShowHeaderInfo";
    /** Prefix for all prefs */
    private static final String TAG = "BookList";
    /** Prefix for all prefs */
    private static final String PREF_SHOW_EXTRAS_PREFIX = TAG + ".";
    /** Show header info in list */
    private static final String PREF_SHOW_HEADER_INFO = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_HEADER_INFO;

    /* Extra fields to show at the book level */
    /** Use condensed text, e.g. {@link #SCALE} */
    private static final String PREF_CONDENSED_TEXT = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_CONDENSED;
    /** Show thumbnail image for each book */
    private static final String PREF_SHOW_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_THUMBNAILS;
    /** Show large thumbnail if thumbnails are shown */
    private static final String PREF_LARGE_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_LARGE_THUMBNAILS;
    /** Show list of bookshelves for each book */
    private static final String PREF_SHOW_BOOKSHELVES = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_BOOKSHELVES;
    /** Show location for each book */
    private static final String PREF_SHOW_LOCATION = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_LOCATION;
    /** Show author for each book */
    private static final String PREF_SHOW_AUTHOR = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_AUTHOR;
    /** Show publisher for each book */
    private static final String PREF_SHOW_PUBLISHER = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_PUBLISHER;
    /** Show format for each book */
    private static final String PREF_SHOW_FORMAT = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_FORMAT;
    /** Support for 'Font Size' property */
    private static final ItemList<Integer> mListFontSizeListItems = new ItemList<>();
    /** Support for 'Show List Header Info' property */
    private static final ItemList<Integer> mShowHeaderInfoListItems = new ItemList<>();

    static {
        mListFontSizeListItems.add(null, R.string.use_default_setting);
        mListFontSizeListItems.add(LIST_FONT_SIZE_USE_NORMAL, R.string.blp_item_size_normal);
        mListFontSizeListItems.add(LIST_FONT_SIZE_USE_SMALLER, R.string.blp_item_size_smaller);

        mShowHeaderInfoListItems.add(null, R.string.use_default_setting);
        mShowHeaderInfoListItems.add(SUMMARY_HIDE, R.string.blp_summary_hide);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_COUNT, R.string.blp_summary_book_count);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_COUNT ^ SUMMARY_SHOW_LEVEL_1, R.string.blp_summary_first_level_and_book_count);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_ALL, R.string.blp_summary_show_all);
    }

    /** List of groups */
    @NonNull
    private final ArrayList<BooklistGroup> mGroups;

    /**
     * Row id of database row from which this object comes
     * A '0' is for an as yet unsaved user-style.
     * Always NEGATIVE (e.g. <0 ) for a build-in style
     */
    public long id = 0;

    /**
     * ID if string representing name of this style.
     * Used for standard system-defined styles
     * Always 0 for a user-defined style
     */
    @StringRes
    private int mNameStringId;
    /**
     * User-defined name of this style.
     */
    private transient StringProperty mNameProperty;
    /**
     * Legacy field needs to be kept for backward serialization compatibility,
     * replaced by {@link #mNameProperty}
     */
    @SuppressWarnings("unused")
    private String mName;

    /**
     * Options indicating this style was in the 'preferred' set when it was added to
     * its Styles collection. The value is not dynamically checked.
     */
    private boolean mIsPreferred;

    /** Show list using smaller text */
    private transient ListOfIntegerValuesProperty mListFontSize;
    /** Show list header info */
    private transient ListOfIntegerValuesProperty mShowHeaderInfo;

    /** Extra details to show on book rows */
    private transient BooleanProperty mExtraShowThumbnails;
    private transient BooleanProperty mExtraLargeThumbnails;
    private transient BooleanProperty mExtraShowBookshelves;
    private transient BooleanProperty mExtraShowLocation;
    private transient BooleanProperty mExtraShowAuthor;
    private transient BooleanProperty mExtraShowPublisher;
    private transient BooleanProperty mExtraShowFormat;

    /**
     * ENHANCE: https://github.com/eleybourn/Book-Catalogue/issues/686 - Filter by signed
     * ==> adding ISortableField and IFilterableField so that we (theoretically) just need to create a new
     * ==> object that implements one or both of these for each such field.
     *
     * Filters.
     */
    private transient BooleanProperty mFilterRead;
    private transient BooleanProperty mFilterSigned;
    private transient BooleanProperty mFilterAnthology;
    private transient BooleanProperty mFilterLoaned;


    /**
     * Constructor for system-defined styles.
     */
    BooklistStyle(final @IntRange(from = -100, to = -1) long id, final @StringRes int stringId) {
        this.id = id;
        mNameStringId = stringId;
        mGroups = new ArrayList<>();
        // init first, then set actual values afterwards
        initProperties();
    }

    /**
     * Constructor for user-defined styles.
     */
    public BooklistStyle(final @NonNull String name) {
        mNameStringId = 0;
        mGroups = new ArrayList<>();
        // init first, then set actual values afterwards
        initProperties();
        mNameProperty.setValue(name);
    }

    protected BooklistStyle(final @NonNull Parcel in) {
        id = in.readLong();
        mNameStringId = in.readInt();
        mGroups = new ArrayList<>();
        in.readList(mGroups, getClass().getClassLoader());
        mIsPreferred = in.readByte() != 0;
        // init first, then set their actual values afterwards
        initProperties();
        mNameProperty.setDefaultValue(in.readString());

        mListFontSize.readFromParcel(in);
        mShowHeaderInfo.readFromParcel(in);

        mExtraShowThumbnails.readFromParcel(in);
        mExtraLargeThumbnails.readFromParcel(in);
        mExtraShowBookshelves.readFromParcel(in);
        mExtraShowLocation.readFromParcel(in);
        mExtraShowAuthor.readFromParcel(in);
        mExtraShowPublisher.readFromParcel(in);
        mExtraShowFormat.readFromParcel(in);

        mFilterRead.readFromParcel(in);
        mFilterSigned.readFromParcel(in);
        mFilterAnthology.readFromParcel(in);
        mFilterLoaned.readFromParcel(in);
    }

    /**
     * Delete *ALL* styles from the database
     */
    public static void deleteAllStyles(final @NonNull CatalogueDBAdapter db) {
        db.deleteAllBooklistStyle();
    }

    @Override
    public void writeToParcel(final @NonNull Parcel dest, final int flags) {
        dest.writeLong(id);
        dest.writeInt(mNameStringId);
        dest.writeList(mGroups);
        dest.writeByte((byte) (mIsPreferred ? 1 : 0));
        // don't save the properties themselves, but save their actual values
        dest.writeString(mNameProperty.getValue());

        mListFontSize.writeToParcel(dest);
        mShowHeaderInfo.writeToParcel(dest);

        mExtraShowThumbnails.writeToParcel(dest);
        mExtraLargeThumbnails.writeToParcel(dest);
        mExtraShowBookshelves.writeToParcel(dest);
        mExtraShowLocation.writeToParcel(dest);
        mExtraShowAuthor.writeToParcel(dest);
        mExtraShowPublisher.writeToParcel(dest);
        mExtraShowFormat.writeToParcel(dest);

        mFilterRead.writeToParcel(dest);
        mFilterSigned.writeToParcel(dest);
        mFilterAnthology.writeToParcel(dest);
        mFilterLoaned.writeToParcel(dest);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    Boolean filterRead() {
        return mFilterRead.getResolvedValue();
    }

    Boolean filterSigned() {
        return mFilterSigned.getResolvedValue();
    }

    Boolean filterAnthology() {
        return mFilterAnthology.getResolvedValue();
    }

    Boolean filterLoaned() {
        return mFilterLoaned.getResolvedValue();
    }

    /**
     * Accessor for flag indicating style is among preferred styles.
     */
    public boolean isPreferred() {
        return mIsPreferred;
    }

    /**
     * Accessor for flag indicating style is among preferred styles.
     */
    public void setPreferred(final boolean isPreferred) {
        mIsPreferred = isPreferred;
    }

    /**
     * Accessor. Returns system name or user-defined name based on kind of style this object defines.
     */
    @NonNull
    public String getDisplayName() {
        String s = mNameProperty.getResolvedValue();

        if ((s == null) || s.isEmpty()) {
            return BookCatalogueApp.getResourceString(mNameStringId);
        } else {
            return s.trim();
        }
    }

    /**
     * Accessor. Sets user-defined name.
     */
    public void setName(final @NonNull String name) {
        mNameProperty.setValue(name);
        mNameStringId = 0;
    }

    /**
     * Accessor. Returns a standardised form of the style name. This name is unique.
     */
    @NonNull
    public String getCanonicalName() {
        if (isUserDefined())
            return id + "-u";
        else {
            String name = getDisplayName().toLowerCase();
            return name + "-s";
        }
    }

    void addGroup(final @NonNull BooklistGroup group) {
        mGroups.add(group);
    }

    /**
     * Add a group to this style below any already added groups.
     *
     * @param kind Kind of group to add.
     */
    void addGroup(final int kind) {
        BooklistGroup group = BooklistGroup.newInstance(kind);
        mGroups.add(group);
    }

    /**
     * Add a group to this style below any already added groups.
     *
     * @param kinds one or more Kind of groups to add.
     */
    void addGroups(final @NonNull int... kinds) {
        for (int kind : kinds) {
            BooklistGroup group = BooklistGroup.newInstance(kind);
            mGroups.add(group);
        }
    }

    /**
     * Remove a group from this style.
     *
     * @param kind Kind of group to add.
     *
     * @return removed group.
     */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    BooklistGroup removeGroup(final int kind) {
        BooklistGroup toRemove = null;
        for (BooklistGroup g : mGroups) {
            if (g.kind == kind) {
                toRemove = g;
                break;
            }
        }
        if (toRemove != null) {
            mGroups.remove(toRemove);
        }

        return toRemove;
    }

    /**
     * @return <tt>true</tt>if this style is user-defined.
     */
    public boolean isUserDefined() {
        return (mNameStringId == 0 || id > 0);
    }

    private void initProperties() {

        /* *****************************************************************************
         * GRP_GENERAL:
         ******************************************************************************/
        mNameProperty = new StringProperty("StyleName",
                PropertyGroup.GRP_GENERAL, R.string.name, "")
                .setRequireNonBlank(true)
                .setWeight(-100);

        mListFontSize = new ListOfIntegerValuesProperty(mListFontSizeListItems, PREF_CONDENSED_TEXT,
                PropertyGroup.GRP_GENERAL, R.string.blp_item_size, LIST_FONT_SIZE_USE_DEFAULT)
                .setPreferenceKey(PREF_CONDENSED_TEXT);

        mShowHeaderInfo = new ListOfIntegerValuesProperty(mShowHeaderInfoListItems, PREF_SHOW_HEADER_INFO,
                PropertyGroup.GRP_GENERAL, R.string.blp_summary, SUMMARY_SHOW_ALL)
                .setPreferenceKey(PREF_SHOW_HEADER_INFO);

        /* *****************************************************************************
         * GRP_THUMBNAILS:
         ******************************************************************************/
        mExtraShowThumbnails = new BooleanProperty("XThumbnails",
                PropertyGroup.GRP_THUMBNAILS, R.string.thumbnails_show, Boolean.TRUE)
                .setPreferenceKey(PREF_SHOW_THUMBNAILS)
                .setWeight(-100);

        mExtraLargeThumbnails = new BooleanProperty("XLargeThumbnails",
                PropertyGroup.GRP_THUMBNAILS, R.string.thumbnails_prefer_large, Boolean.FALSE)
                .setPreferenceKey(PREF_LARGE_THUMBNAILS)
                .setWeight(-99);

        /* *****************************************************************************
         * GRP_EXTRA_BOOK_DETAILS:
         ******************************************************************************/
        mExtraShowBookshelves = new BooleanProperty("XBookshelves",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.lbl_bookshelves_long, Boolean.FALSE)
                .setPreferenceKey(PREF_SHOW_BOOKSHELVES);

        mExtraShowLocation = new BooleanProperty("XLocation",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.lbl_location, Boolean.FALSE)
                .setPreferenceKey(PREF_SHOW_LOCATION);

        mExtraShowPublisher = new BooleanProperty("XPublisher",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.lbl_publisher, Boolean.FALSE)
                .setPreferenceKey(PREF_SHOW_PUBLISHER);

        mExtraShowFormat = new BooleanProperty("XFormat",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.lbl_format, Boolean.FALSE)
                .setPreferenceKey(PREF_SHOW_FORMAT);

        mExtraShowAuthor = new BooleanProperty("XAuthor",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.lbl_author, Boolean.FALSE)
                .setPreferenceKey(PREF_SHOW_AUTHOR);

        /* *****************************************************************************
         * GRP_FILTERS:
         ******************************************************************************/
        mFilterRead = new BooleanProperty("XReadFilter",
                PropertyGroup.GRP_FILTERS, R.string.booklist_filters_select_based_on_read_status, null)
                .setOptionLabels(R.string.booklist_filters_read, R.string.booklist_filters_unread);

        mFilterSigned = new BooleanProperty("XSignedFilter",
                PropertyGroup.GRP_FILTERS, R.string.booklist_filters_select_based_on_signed_status, null)
                .setOptionLabels(R.string.booklist_filters_signed_yes, R.string.booklist_filters_signed_no);

        mFilterAnthology = new BooleanProperty("XAnthologyFilter",
                PropertyGroup.GRP_FILTERS, R.string.booklist_filters_select_based_on_is_anthology_status, null)
                .setOptionLabels(R.string.booklist_filters_is_anthology_yes, R.string.booklist_filters_is_anthology_no);

        mFilterLoaned = new BooleanProperty("XLoanedFilter",
                PropertyGroup.GRP_FILTERS, R.string.booklist_filters_select_based_on_loaned_status, null)
                .setOptionLabels(R.string.booklist_filters_loaned_yes, R.string.booklist_filters_loaned_no);
    }

    /**
     * Get all of the properties of this Style and its groups.
     */
    @NonNull
    public PropertyList getProperties() {
        PropertyList list = new PropertyList();
        // essential property for user-defined styles 'name'
        list.add(mNameProperty);
        // properties that can be shown as extra information for each line in the book list
        list.add(mExtraShowThumbnails);
        list.add(mExtraLargeThumbnails);
        list.add(mExtraShowBookshelves);
        list.add(mExtraShowLocation);
        list.add(mExtraShowPublisher);
        list.add(mExtraShowFormat);
        list.add(mExtraShowAuthor);
        // smaller font size ?
        list.add(mListFontSize);
        // list header information shown
        list.add(mShowHeaderInfo);
        // filter the list according to these
        list.add(mFilterRead);
        list.add(mFilterSigned);
        list.add(mFilterAnthology);
        list.add(mFilterLoaned);

        // now for each group, add its specific properties to our list
        for (BooklistGroup group : mGroups) {
            group.getStyleProperties(list);
        }

        return list;
    }

    /**
     * Passed a PropertyList object, update the properties of this style
     * based on the values of the passed properties.
     */
    public void setProperties(final @NonNull PropertyList newProps) {
        PropertyList props = getProperties();
        for (Property newVal : newProps) {
            Property thisProp = props.get(newVal.getUniqueName());
            if (thisProp != null) {
                //noinspection unchecked
                thisProp.setValue(newVal);
            }
        }
    }

    /**
     * Passed a template style, copy the group structure to this style.
     */
    public void setGroups(final @NonNull BooklistStyle fromStyle) {
        PropertyList newProps = new PropertyList();

        // Save the current groups
        @SuppressLint("UseSparseArrays")
        Map<Integer, BooklistGroup> oldGroups = new HashMap<>();
        for (BooklistGroup g : this) {
            oldGroups.put(g.kind, g);
        }
        // Clear the current groups, and rebuild, reusing old values where possible
        mGroups.clear();
        for (BooklistGroup group : fromStyle) {
            BooklistGroup saved = oldGroups.get(group.kind);
            if (saved != null) {
                mGroups.add(saved);
            } else {
                group.getStyleProperties(newProps);
                this.addGroup(group.kind);
            }
        }
        // Copy any properties from new groups.
        this.setProperties(newProps);
    }

    /**
     * A quicker way of getting the status of all extra-fields in one go instead of implementing
     * individual getters for each.
     *
     * @return bitmask with the 'extra' fields that are in use (visible) for this style.
     */
    public int getExtraFieldsStatus() {
        int extras = 0;

        if (mExtraShowThumbnails.isTrue())
            extras |= EXTRAS_THUMBNAIL;

        if (mExtraLargeThumbnails.isTrue())
            extras |= EXTRAS_THUMBNAIL_LARGE;

        if (mExtraShowBookshelves.isTrue())
            extras |= EXTRAS_BOOKSHELVES;

        if (mExtraShowLocation.isTrue())
            extras |= EXTRAS_LOCATION;

        if (mExtraShowPublisher.isTrue())
            extras |= EXTRAS_PUBLISHER;

        if (mExtraShowFormat.isTrue())
            extras |= EXTRAS_FORMAT;

        if (mExtraShowAuthor.isTrue())
            extras |= EXTRAS_AUTHOR;

        return extras;
    }

    /**
     * Check if this style has the specified group
     */
    boolean hasKind(final int kind) {
        for (BooklistGroup g : mGroups) {
            if (g.kind == kind)
                return true;
        }
        return false;
    }

    /**
     * Get the group at the passed index.
     */
    @NonNull
    public BooklistGroup getGroupAt(final int index) {
        return mGroups.get(index);
    }

    /**
     * Get the number of groups in this style
     */
    public int size() {
        return mGroups.size();
    }

    public int getShowHeaderInfo() {
        return mShowHeaderInfo.getInt();
    }

    /**
     * Iterable support
     */
    @NonNull
    @Override
    public Iterator<BooklistGroup> iterator() {
        return mGroups.iterator();
    }

    public boolean isCondensed() {
        return mListFontSize.getInt() == LIST_FONT_SIZE_USE_SMALLER;
    }

    /**
     * Delete this style from the database
     */
    public void delete(final @NonNull CatalogueDBAdapter db) {
        if (id <= 0) {
            throw new IllegalArgumentException("Style is not stored in the database, can not be deleted");
        }
        db.deleteBooklistStyle(id);
    }

    /**
     * Convenience function to return a list of group names.
     */
    @NonNull
    String getGroupListDisplayNames() {
        StringBuilder groups = new StringBuilder();
        boolean first = true;
        for (BooklistGroup g : this) {
            if (first) {
                first = false;
            } else {
                groups.append(" / ");
            }
            groups.append(g.getName());
        }
        return groups.toString();
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(realSerialVersion);
        out.writeObject(mExtraShowThumbnails.getValue());
        out.writeObject(mExtraLargeThumbnails.getValue());
        out.writeObject(mExtraShowBookshelves.getValue());
        out.writeObject(mExtraShowLocation.getValue());
        out.writeObject(mExtraShowPublisher.getValue());
        out.writeObject(mExtraShowAuthor.getValue());
        out.writeObject(mFilterRead.getValue());
        // v1
        out.writeObject(mListFontSize.getValue());
        // v2
        out.writeObject(mNameProperty.getValue());
        // v3 / v4
        out.writeObject(mShowHeaderInfo.getValue());
        // v5
        out.writeObject(mExtraShowFormat.getValue());
        out.writeObject(mFilterSigned.getValue());
        out.writeObject(mFilterAnthology.getValue());
        out.writeObject(mFilterLoaned.getValue());
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // init first, then set all actual values afterwards
        initProperties();

        Object object = in.readObject();
        long version = 0;
        if (object instanceof Long) {
            // It's the version
            version = ((Long) object);
            // Get the next object
            object = in.readObject();
        } // else it's a pre-version object..
        // just use it
        mExtraShowThumbnails.setValue((Boolean) object);

        mExtraLargeThumbnails.setValue((Boolean) in.readObject());
        mExtraShowBookshelves.setValue((Boolean) in.readObject());
        mExtraShowLocation.setValue((Boolean) in.readObject());
        mExtraShowPublisher.setValue((Boolean) in.readObject());
        mExtraShowAuthor.setValue((Boolean) in.readObject());

        // v0..4 mFilterRead was an Integer.
        // v5+ it's a Boolean matching the other filters
        object = in.readObject();
        if (object instanceof Integer) {
            // v0..4
            Integer rf = (Integer) object;
//            public static final int FILTER_READ = 1;
//            public static final int FILTER_UNREAD = 2;
//            public static final int FILTER_READ_AND_UNREAD = 3;
            switch (rf) {
                case 1:
                    mFilterRead.setValue(Boolean.TRUE);
                    break;
                case 2:
                    mFilterRead.setValue(Boolean.FALSE);
                    break;
                case 3:
                    mFilterRead.setValue(null);
                    break;
            }
        } else {
            // v5
            mFilterRead.setValue((Boolean) object);
        }

        // v1 'condensed' was a Boolean.
        // v5+ it's an Integer and re-used for Font Size of which 'condensed' is just one option.
        Object tmpCondensed = in.readObject();
        if ((version > 0) && (version < 5)) {
            // up to and including v4: Boolean: null=='use-defaults', false='normal', true='condensed'
            if (tmpCondensed == null) {
                mListFontSize.setValue(LIST_FONT_SIZE_USE_DEFAULT);
            } else {
                mListFontSize.setValue((Boolean) tmpCondensed ? LIST_FONT_SIZE_USE_SMALLER : LIST_FONT_SIZE_USE_NORMAL);
            }
        } else if (version >= 5) {
            // starting v5: Integer
            mListFontSize.setValue((Integer) tmpCondensed);
        }

        // v2
        if (version > 1) {
            mNameProperty.setValue((String) in.readObject());
        } else {
            // v0/1, we had a simple String 'mName' which 'defaultReadObject' de-serialized.
            // transfer that value into our new mNameProperty.
            mNameProperty.setValue(mName);
        }

        // v3 Added mShowHeaderInfo as a Boolean
        if (version == 3) {
            Boolean isSet = (Boolean) in.readObject();
            if (isSet == null) {
                mShowHeaderInfo.setValue(null);
            } else {
                mShowHeaderInfo.setValue(isSet ? SUMMARY_SHOW_ALL : SUMMARY_HIDE);
            }
        }

        // v4 Changed mShowHeaderInfo from Boolean to Integer
        if (version > 3) {
            mShowHeaderInfo.setValue((Integer) in.readObject());
        }

        // v5
        if (version > 4) {
            mExtraShowFormat.setValue((Boolean) in.readObject());
            mFilterSigned.setValue((Boolean) in.readObject());
            mFilterAnthology.setValue((Boolean) in.readObject());
            mFilterLoaned.setValue((Boolean) in.readObject());
        }
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("SameParameterValue")
    void setFontSize(final @IntRange(from = LIST_FONT_SIZE_USE_DEFAULT, to = LIST_FONT_SIZE_USE_SMALLER) int size) {
        mListFontSize.setValue(size);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("SameParameterValue")
    void setShowAuthor(final boolean show) {
        mExtraShowAuthor.setValue(show);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("SameParameterValue")
    void setShowThumbnails(final boolean show) {
        mExtraShowThumbnails.setValue(show);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("SameParameterValue")
    void setReadFilter(final @NonNull Boolean value) {
        mFilterRead.setValue(value);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("unused")
    void setSignedFilter(final @NonNull Boolean value) {
        mFilterSigned.setValue(value);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("unused")
    void setAnthologyFilter(final @NonNull Boolean value) {
        mFilterAnthology.setValue(value);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("unused")
    void setLoanedFilter(final @NonNull Boolean value) {
        mFilterLoaned.setValue(value);
    }

    /**
     * Construct a deep clone of this object.
     */
    @NonNull
    BooklistStyle getClone() throws DeserializationException {
        BooklistStyle clone = SerializationUtils.cloneObject(this);
        clone.id = 0;
        return clone;
    }

    /**
     * Represents a collection of domains that make a unique key for a given group.
     *
     * @author Philip Warner
     */
    static class CompoundKey implements Parcelable {
        public static final Creator<CompoundKey> CREATOR = new Creator<CompoundKey>() {
            @Override
            public CompoundKey createFromParcel(Parcel in) {
                return new CompoundKey(in);
            }

            @Override
            public CompoundKey[] newArray(int size) {
                return new CompoundKey[size];
            }
        };
        /** Unique getPrefix used to represent a key in the hierarchy */
        @NonNull
        final String prefix;
        /** List of domains in key */
        @NonNull
        final DomainDefinition[] domains;

        /** Constructor */
        CompoundKey(final @NonNull String prefix, final @NonNull DomainDefinition... domains) {
            this.prefix = prefix;
            this.domains = domains;
        }

        CompoundKey(final @NonNull Parcel in) {
            prefix = in.readString();
            domains = in.createTypedArray(DomainDefinition.CREATOR);
        }

        @Override
        public void writeToParcel(final @NonNull Parcel dest, final int flags) {
            dest.writeString(prefix);
            dest.writeTypedArray(domains, flags);
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }
    }
}

