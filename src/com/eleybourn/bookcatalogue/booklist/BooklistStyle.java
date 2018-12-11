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
import com.eleybourn.bookcatalogue.booklist.filters.Filter;
import com.eleybourn.bookcatalogue.booklist.filters.TrinaryFilter;
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
 * 1. add it to {@link RowKinds} and update ROW_KIND_MAX
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
public class BooklistStyle implements Serializable, Parcelable {

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

    /**
     * Extra book data to show at lowest level
     */
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

    /**
     * the amount of details to show in the header
     */
    private static final Integer SUMMARY_HIDE = 0;
    /** the amount of details to show in the header */
    public static final Integer SUMMARY_SHOW_COUNT = 1;
    /** the amount of details to show in the header */
    public static final Integer SUMMARY_SHOW_LEVEL_1 = 1 << 1;
    /** the amount of details to show in the header */
    public static final Integer SUMMARY_SHOW_LEVEL_2 = 1 << 2;
    /** the amount of details to show in the header */
    public static final Integer SUMMARY_SHOW_ALL = 0xff;

    /**
     * Scaling of text and images
     */
    @SuppressWarnings("WeakerAccess")
    public static final int SCALE_SIZE_USE_DEFAULT = 0;
    /** Scaling of text and images */
    public static final int SCALE_SIZE_NORMAL = 1;
    /** Scaling of text and images */
    public static final int SCALE_SIZE_SMALLER = 2;

    /**
     * Prefix for all prefs
     */
    static final String TAG = "BookList.Style.";
    /** Preferred styles / menu order */
    public static final String PREF_BL_STYLE_MENU_ITEMS = TAG + "Menu.Items";
    /** Use scaled text and images */
    public static final String PREF_BL_STYLE_SCALE_SIZE = TAG + "Scaling";
    /** Show header info in list */
    public static final String PREF_BL_STYLE_SHOW_HEADER_INFO = TAG + "Show.HeaderInfo";
    /** Show thumbnail image for each book */
    public static final String PREF_BL_STYLE_SHOW_THUMBNAILS = TAG + "Show.Thumbnails";
    /** Show large thumbnail if thumbnails are shown */
    public static final String PREF_BL_STYLE_SHOW_LARGE_THUMBNAILS = TAG + "Show.LargeThumbnails";
    /** Show list of bookshelves for each book */
    public static final String PREF_BL_STYLE_SHOW_BOOKSHELVES = TAG + "Show.Bookshelves";
    /** Show location for each book */
    public static final String PREF_BL_STYLE_SHOW_LOCATION = TAG + "Show.Location";
    /** Show author for each book */
    public static final String PREF_BL_STYLE_SHOW_AUTHOR = TAG + "Show.Author";
    /** Show publisher for each book */
    public static final String PREF_BL_STYLE_SHOW_PUBLISHER = TAG + "Show.Publisher";
    /** Show format for each book */
    @SuppressWarnings("WeakerAccess")
    public static final String PREF_BL_STYLE_SHOW_FORMAT = TAG + "Show.Format";
    /** Sorting Author by family (default) or given name. This is independent from the display format */
    @SuppressWarnings("WeakerAccess")
    public static final String PREF_BL_STYLE_SORT_AUTHOR_GIVEN_FIRST = "Sort.Author.GivenFirst";

    /** serialization id for plain class data */
    private static final long serialVersionUID = 6615877148246388549L;
    /** version field used in serialized data reading from file, see {@link #readObject} */
    private static final long realSerialVersion = 5;


    /** Support for 'Font Size' property */
    private static final ItemList<Integer> mListFontSizeListItems;
    /** Support for 'Show List Header Info' property */
    private static final ItemList<Integer> mShowHeaderInfoListItems = new ItemList<>();

    /** Support for filter */
    private static final ItemList<Integer> mReadFilterListItems = new ItemList<>();
    /** Support for filter */
    private static final ItemList<Integer> mSignedFilterListItems = new ItemList<>();
    /** Support for filter */
    private static final ItemList<Integer> mAnthologyFilterListItems = new ItemList<>();
    /** Support for filter */
    private static final ItemList<Integer> mLoanedFilterListItems = new ItemList<>();

    static {
        mListFontSizeListItems = new ItemList<>();
        mListFontSizeListItems.add(null, R.string.use_default_setting);
        mListFontSizeListItems.add(SCALE_SIZE_NORMAL, R.string.blp_item_size_normal);
        mListFontSizeListItems.add(SCALE_SIZE_SMALLER, R.string.blp_item_size_smaller);

        mShowHeaderInfoListItems.add(null, R.string.use_default_setting);
        mShowHeaderInfoListItems.add(SUMMARY_HIDE, R.string.blp_summary_hide);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_COUNT, R.string.blp_summary_book_count);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_COUNT ^ SUMMARY_SHOW_LEVEL_1, R.string.blp_summary_first_level_and_book_count);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_ALL, R.string.blp_summary_show_all);

        mReadFilterListItems.add(null, R.string.use_default_setting);
        mReadFilterListItems.add(TrinaryFilter.FILTER_NOT_USED, R.string.all_books);
        mReadFilterListItems.add(TrinaryFilter.FILTER_NO, R.string.booklist_filters_unread);
        mReadFilterListItems.add(TrinaryFilter.FILTER_YES, R.string.booklist_filters_read);

        mSignedFilterListItems.add(null, R.string.use_default_setting);
        mSignedFilterListItems.add(TrinaryFilter.FILTER_NOT_USED, R.string.all_books);
        mSignedFilterListItems.add(TrinaryFilter.FILTER_NO, R.string.booklist_filters_signed_no);
        mSignedFilterListItems.add(TrinaryFilter.FILTER_YES, R.string.booklist_filters_signed_yes);

        mAnthologyFilterListItems.add(null, R.string.use_default_setting);
        mAnthologyFilterListItems.add(TrinaryFilter.FILTER_NOT_USED, R.string.all_books);
        mAnthologyFilterListItems.add(TrinaryFilter.FILTER_NO, R.string.booklist_filters_is_anthology_no);
        mAnthologyFilterListItems.add(TrinaryFilter.FILTER_YES, R.string.booklist_filters_is_anthology_yes);

        mLoanedFilterListItems.add(null, R.string.use_default_setting);
        mLoanedFilterListItems.add(TrinaryFilter.FILTER_NOT_USED, R.string.all_books);
        mLoanedFilterListItems.add(TrinaryFilter.FILTER_NO, R.string.booklist_filters_loaned_no);
        mLoanedFilterListItems.add(TrinaryFilter.FILTER_YES, R.string.booklist_filters_loaned_yes);
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
    private transient ListOfIntegerValuesProperty mScaleSize;
    /** Show list header info */
    private transient ListOfIntegerValuesProperty mShowHeaderInfo;

    /** Sorting */
    private transient BooleanProperty mSortAuthor;

    /** Extra details to show on book rows */
    private transient BooleanProperty mExtraShowThumbnails;
    private transient BooleanProperty mExtraLargeThumbnails;
    private transient BooleanProperty mExtraShowBookshelves;
    private transient BooleanProperty mExtraShowLocation;
    private transient BooleanProperty mExtraShowAuthor;
    private transient BooleanProperty mExtraShowPublisher;
    private transient BooleanProperty mExtraShowFormat;

    private transient ArrayList<Filter> mFilters;
    private transient TrinaryFilter mFilterRead;
    private transient TrinaryFilter mFilterSigned;
    private transient TrinaryFilter mFilterAnthology;
    private transient TrinaryFilter mFilterLoaned;

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
        mNameProperty.readFromParcel(in);

        mScaleSize.readFromParcel(in);
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

        mSortAuthor.readFromParcel(in);
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

        mNameProperty.writeToParcel(dest);

        mScaleSize.writeToParcel(dest);
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

        mSortAuthor.writeToParcel(dest);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    boolean sortAuthorByGiven() {
        return mSortAuthor.isTrue();
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
        return s.isEmpty() ? BookCatalogueApp.getResourceString(mNameStringId) : s;
    }

    /**
     * Accessor. Sets user-defined name.
     */
    public void setName(final @NonNull String name) {
        mNameProperty.setValue(name);
        mNameStringId = 0;
    }

    /**
     * Accessor.
     * Positive id's: user-defined styles
     * Negative id's: builtin styles
     * 0: a user-defined style which has not been saved yet
     */
    public long getId() {
        return id;
    }

    @NonNull
    public ArrayList<BooklistGroup> getGroups() {
        return mGroups;
    }

    /**
     * Passed a template style, copy the group structure to this style.
     */
    public void setGroups(final @NonNull BooklistStyle fromStyle) {
        PropertyList newProps = new PropertyList();

        // Save the current groups
        @SuppressLint("UseSparseArrays")
        Map<Integer, BooklistGroup> oldGroups = new HashMap<>();
        for (BooklistGroup group : this.getGroups()) {
            oldGroups.put(group.getRowKind().kind, group);
        }
        // Clear the current groups, and rebuild, reusing old values where possible
        mGroups.clear();
        for (BooklistGroup group : fromStyle.getGroups()) {
            BooklistGroup saved = oldGroups.get(group.getRowKind().kind);
            if (saved != null) {
                mGroups.add(saved);
            } else {
                group.getStyleProperties(newProps);
                this.addGroup(group.getRowKind().kind);
            }
        }
        // Copy any properties from new groups.
        this.setProperties(newProps);
    }

    void addGroup(final @NonNull BooklistGroup group) {
        mGroups.add(group);
    }

    /**
     * Add a group to this style below any already added groups.
     *
     * @param kind of group to add.
     */
    void addGroup(final @IntRange(from = 0, to = RowKinds.ROW_KIND_MAX) int kind) {
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
     * @param kind of group to remove from.
     *
     * @return removed group.
     */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    BooklistGroup removeGroup(final @IntRange(from = 0, to = RowKinds.ROW_KIND_MAX) int kind) {
        BooklistGroup toRemove = null;
        for (BooklistGroup group : mGroups) {
            if (group.getRowKind().kind == kind) {
                toRemove = group;
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
        /* the name for user-defined styles */
        mNameProperty = new StringProperty(R.string.name,
                PropertyGroup.GRP_GENERAL, "")
                .setRequireNonBlank(true)
                .setWeight(-100);

        mScaleSize = new ListOfIntegerValuesProperty(R.string.blp_item_size,
                PropertyGroup.GRP_GENERAL, SCALE_SIZE_USE_DEFAULT, mListFontSizeListItems)
                .setPreferenceKey(PREF_BL_STYLE_SCALE_SIZE);

        mShowHeaderInfo = new ListOfIntegerValuesProperty(R.string.blp_summary,
                PropertyGroup.GRP_GENERAL, SUMMARY_SHOW_ALL, mShowHeaderInfoListItems)
                .setPreferenceKey(PREF_BL_STYLE_SHOW_HEADER_INFO);

        /* *****************************************************************************
         * GRP_AUTHOR:
         ******************************************************************************/
        mSortAuthor = new BooleanProperty(R.string.blp_sort_author_name,
                PropertyGroup.GRP_AUTHOR)
                .setPreferenceKey(PREF_BL_STYLE_SORT_AUTHOR_GIVEN_FIRST)
                .setOptionLabels(R.string.blp_format_author_name_given_first,
                        R.string.blp_format_author_name_family_first);

        /* *****************************************************************************
         * GRP_THUMBNAILS:
         ******************************************************************************/
        mExtraShowThumbnails = new BooleanProperty(R.string.thumbnails_show,
                PropertyGroup.GRP_THUMBNAILS, Boolean.TRUE)
                .setPreferenceKey(PREF_BL_STYLE_SHOW_THUMBNAILS)
                .setWeight(-100);

        mExtraLargeThumbnails = new BooleanProperty(R.string.thumbnails_prefer_large,
                PropertyGroup.GRP_THUMBNAILS)
                .setPreferenceKey(PREF_BL_STYLE_SHOW_LARGE_THUMBNAILS)
                .setWeight(-99);

        /* *****************************************************************************
         * GRP_EXTRA_BOOK_DETAILS:
         ******************************************************************************/
        mExtraShowBookshelves = new BooleanProperty(R.string.lbl_bookshelves_long,
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS)
                .setPreferenceKey(PREF_BL_STYLE_SHOW_BOOKSHELVES);

        mExtraShowLocation = new BooleanProperty(R.string.lbl_location,
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS)
                .setPreferenceKey(PREF_BL_STYLE_SHOW_LOCATION);

        mExtraShowPublisher = new BooleanProperty(R.string.lbl_publisher,
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS)
                .setPreferenceKey(PREF_BL_STYLE_SHOW_PUBLISHER);

        mExtraShowFormat = new BooleanProperty(R.string.lbl_format,
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS)
                .setPreferenceKey(PREF_BL_STYLE_SHOW_FORMAT);

        mExtraShowAuthor = new BooleanProperty(R.string.lbl_author,
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS)
                .setPreferenceKey(PREF_BL_STYLE_SHOW_AUTHOR);

        /* *****************************************************************************
         * GRP_FILTERS:
         ******************************************************************************/
        mFilters = new ArrayList<>();

        mFilterRead = new TrinaryFilter(R.string.booklist_filters_select_based_on_read_status,
                PropertyGroup.GRP_FILTERS, TrinaryFilter.FILTER_NOT_USED, mReadFilterListItems);
        mFilterRead.setDomain(DatabaseDefinitions.TBL_BOOKS, DatabaseDefinitions.DOM_BOOK_READ);
        mFilters.add(mFilterRead);

        mFilterSigned = new TrinaryFilter(R.string.booklist_filters_select_based_on_signed_status,
                PropertyGroup.GRP_FILTERS, TrinaryFilter.FILTER_NOT_USED, mSignedFilterListItems);
        mFilterSigned.setDomain(DatabaseDefinitions.TBL_BOOKS, DatabaseDefinitions.DOM_BOOK_SIGNED);
        mFilters.add(mFilterSigned);

        mFilterAnthology = new TrinaryFilter(R.string.booklist_filters_select_based_on_is_anthology_status,
                PropertyGroup.GRP_FILTERS, TrinaryFilter.FILTER_NOT_USED, mAnthologyFilterListItems);
        mFilterAnthology.setDomain(DatabaseDefinitions.TBL_BOOKS, DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK);
        mFilters.add(mFilterAnthology);

        mFilterLoaned = new TrinaryFilter(R.string.booklist_filters_select_based_on_loaned_status,
                PropertyGroup.GRP_FILTERS, TrinaryFilter.FILTER_NOT_USED, mLoanedFilterListItems);
        mFilterLoaned.setDomain(DatabaseDefinitions.TBL_BOOKS, DatabaseDefinitions.DOM_LOANED_TO);
        mFilters.add(mFilterLoaned);
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
        list.add(mScaleSize);
        // list header information shown
        list.add(mShowHeaderInfo);
        // sorting
        list.add(mSortAuthor);
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
            Property thisProp = props.get(newVal.getUniqueId());
            if (thisProp != null) {
                //noinspection unchecked
                thisProp.setValue(newVal.getValue());
            }
        }
    }

    /**
     * A quicker way of getting the status of all extra-fields in one go instead of implementing
     * individual getters for each.
     *
     * @return bitmask with the 'extra' fields that are in use (visible) for this style.
     */
    public int getExtraFieldsStatus() {
        int extras = 0;

        if (mExtraShowThumbnails.isTrue()) {
            extras |= EXTRAS_THUMBNAIL;
        }

        if (mExtraLargeThumbnails.isTrue()) {
            extras |= EXTRAS_THUMBNAIL_LARGE;
        }

        if (mExtraShowBookshelves.isTrue()) {
            extras |= EXTRAS_BOOKSHELVES;
        }

        if (mExtraShowLocation.isTrue()) {
            extras |= EXTRAS_LOCATION;
        }

        if (mExtraShowPublisher.isTrue()) {
            extras |= EXTRAS_PUBLISHER;
        }

        if (mExtraShowFormat.isTrue()) {
            extras |= EXTRAS_FORMAT;
        }

        if (mExtraShowAuthor.isTrue()) {
            extras |= EXTRAS_AUTHOR;
        }

        return extras;
    }

    /**
     * Check if this style has the specified group
     */
    boolean hasKind(final @IntRange(from = 0, to = RowKinds.ROW_KIND_MAX) int kind) {
        for (BooklistGroup group : mGroups) {
            if (group.getRowKind().kind == kind) {
                return true;
            }
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
        return mShowHeaderInfo.getResolvedValue();
    }

    public float getScaleSize() {
        switch (mScaleSize.getResolvedValue()) {
            case SCALE_SIZE_NORMAL:
                return 1.0f;
            case SCALE_SIZE_SMALLER:
                return 0.8f;

            default:
                return SCALE_SIZE_NORMAL;
        }
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("SameParameterValue")
    void setScaleSize(final @IntRange(from = SCALE_SIZE_USE_DEFAULT, to = SCALE_SIZE_SMALLER) int size) {
        mScaleSize.setValue(size);
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
        for (BooklistGroup group : this.getGroups()) {
            if (first) {
                first = false;
            } else {
                groups.append(" / ");
            }
            groups.append(group.getRowKind().getName());
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
        out.writeObject(mScaleSize.getValue());
        // v2
        out.writeObject(mNameProperty.getValue());
        // v3 / v4
        out.writeObject(mShowHeaderInfo.getValue());
        // v5
        out.writeObject(mExtraShowFormat.getValue());

        out.writeObject(mFilterSigned.getValue());
        out.writeObject(mFilterAnthology.getValue());
        out.writeObject(mFilterLoaned.getValue());

        out.writeObject(mSortAuthor.getValue());
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
        mFilterRead.setValue((Integer) in.readObject());

        // v1 'condensed' was a Boolean.
        // v5+ it's an Integer and re-used for Font Size of which 'condensed' is just one option.
        Object tmpCondensed = in.readObject();
        if ((version > 0) && (version < 5)) {
            // up to and including v4: Boolean: null=='use-defaults', false='normal', true='condensed'
            if (tmpCondensed == null) {
                mScaleSize.setValue(SCALE_SIZE_USE_DEFAULT);
            } else {
                mScaleSize.setValue((Boolean) tmpCondensed ? SCALE_SIZE_SMALLER : SCALE_SIZE_NORMAL);
            }
        } else if (version >= 5) {
            // starting v5: Integer
            mScaleSize.setValue((Integer) tmpCondensed);
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

            mFilterSigned.setValue((Integer) in.readObject());
            mFilterAnthology.setValue((Integer) in.readObject());
            mFilterLoaned.setValue((Integer) in.readObject());

            mSortAuthor.setValue((Boolean) in.readObject());
        }
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
    void setReadFilter(final @NonNull Integer value) {
        mFilterRead.setValue(value);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("unused")
    void setSignedFilter(final @NonNull Integer value) {
        mFilterSigned.setValue(value);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("unused")
    void setAnthologyFilter(final @NonNull Integer value) {
        mFilterAnthology.setValue(value);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("unused")
    void setLoanedFilter(final @NonNull Integer value) {
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

    public ArrayList<Filter> getFilters() {
        return mFilters;
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
        /** Unique prefix used to represent a key in the hierarchy */
        @NonNull
        final String prefix;
        /** List of domains in key */
        final DomainDefinition[] domains;

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

