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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.properties.BooleanListProperty;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
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
 * Individual {@link BooklistGroup} objects are added to a style in order to describe
 * the resulting list style.
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
public class BooklistStyle implements Iterable<BooklistGroup>, Serializable {

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

    static final int FILTER_YES = 1;
    static final int FILTER_NO = 2;
    private static final int FILTER_EITHER = 3;

    private static final Integer SUMMARY_HIDE = 0;
    public static final Integer SUMMARY_SHOW_COUNT = 1;
    public static final Integer SUMMARY_SHOW_LEVEL_1 = 1 << 1;
    public static final Integer SUMMARY_SHOW_LEVEL_2 = 1 << 2;
    public static final Integer SUMMARY_SHOW_ALL = 0xff;

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

    /** the scaling for 'condensed' text */
    public static final float SCALE = 0.8f;

    /** Use condensed text, e.g. {@link #SCALE} */
    private static final String PREF_CONDENSED_TEXT = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_CONDENSED;

    // Extra fields to show at the book level

    /** Show list of bookshelves for each book */
    private static final String PREF_SHOW_BOOKSHELVES = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_BOOKSHELVES;
    /** Show location for each book */
    private static final String PREF_SHOW_LOCATION = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_LOCATION;
    /** Show author for each book */
    private static final String PREF_SHOW_AUTHOR = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_AUTHOR;
    /** Show publisher for each book */
    private static final String PREF_SHOW_PUBLISHER = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_PUBLISHER;
    /** Show thumbnail image for each book */
    private static final String PREF_SHOW_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_THUMBNAILS;
    /** Show large thumbnail if thumbnails are shown */
    private static final String PREF_LARGE_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_LARGE_THUMBNAILS;
    /** Show format for each book */
    private static final String PREF_SHOW_FORMAT = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_FORMAT;

    /** Support for 'Condensed' property */
    private static final ItemEntries<Boolean> mCondensedListItems = new ItemEntries<>();
    /** Support for 'Show List Header Info' property */
    private static final ItemEntries<Integer> mShowHeaderInfoListItems = new ItemEntries<>();

    /** Support for filter */
    private static final ItemEntries<Integer> mReadFilterListItems = new ItemEntries<>();
    /** Support for filter */
    private static final ItemEntries<Integer> mSignedFilterListItems = new ItemEntries<>();
    /** Support for filter */
    private static final ItemEntries<Integer> mAnthologyFilterListItems = new ItemEntries<>();
    /** Support for filter */
    private static final ItemEntries<Integer> mLoanedFilterListItems = new ItemEntries<>();

    static {
        mCondensedListItems.add(null, R.string.use_default_setting);
        mCondensedListItems.add(false, R.string.blp_item_size_normal);
        mCondensedListItems.add(true, R.string.blp_item_size_smaller);

        mShowHeaderInfoListItems.add(null, R.string.use_default_setting);
        mShowHeaderInfoListItems.add(SUMMARY_HIDE, R.string.blp_summary_hide);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_COUNT, R.string.blp_summary_book_count);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_COUNT ^ SUMMARY_SHOW_LEVEL_1, R.string.blp_summary_first_level_and_book_count);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_ALL, R.string.blp_summary_show_all);

        mReadFilterListItems.add(FILTER_NO, R.string.booklist_filters_unread);
        mReadFilterListItems.add(FILTER_YES, R.string.booklist_filters_read);
        mReadFilterListItems.add(FILTER_EITHER, R.string.all_books);

        mSignedFilterListItems.add(FILTER_NO, R.string.booklist_filters_signed_no);
        mSignedFilterListItems.add(FILTER_YES, R.string.booklist_filters_signed_yes);
        mSignedFilterListItems.add(FILTER_EITHER, R.string.all_books);

        mAnthologyFilterListItems.add(FILTER_NO, R.string.booklist_filters_is_anthology_no);
        mAnthologyFilterListItems.add(FILTER_YES, R.string.booklist_filters_is_anthology_yes);
        mAnthologyFilterListItems.add(FILTER_EITHER, R.string.all_books);

        mLoanedFilterListItems.add(FILTER_NO, R.string.booklist_filters_loaned_no);
        mLoanedFilterListItems.add(FILTER_YES, R.string.booklist_filters_loaned_yes);
        mLoanedFilterListItems.add(FILTER_EITHER, R.string.all_books);
    }

    /** List of groups */
    @NonNull
    private final ArrayList<BooklistGroup> mGroups;
    /** ID if string representing name of this style. Used for standard system-defined styles */
    @StringRes
    private int mNameStringId;
    /** User-defined name of this style. */
    private transient StringProperty mNameProperty;

    /** Row id of database row from which this object comes, always 0 for a build-in style */
    public long id = 0;

    /** Extra details to show on book rows */
    private transient BooleanProperty mXtraShowThumbnails;
    private transient BooleanProperty mXtraLargeThumbnails;
    private transient BooleanProperty mXtraShowBookshelves;
    private transient BooleanProperty mXtraShowLocation;
    private transient BooleanProperty mXtraShowPublisher;
    private transient BooleanProperty mXtraShowFormat;
    private transient BooleanProperty mXtraShowAuthor;

    /** filters
     * ENHANCE: https://github.com/eleybourn/Book-Catalogue/issues/686 - Filter by signed
     * ==> adding ISortableField and IFilterableField so that we (theoretically) just need to create a new
     * ==> object that implements one or both of these for each such field.
     */
    private transient IntegerListProperty mXtraReadFilter;
    private transient IntegerListProperty mXtraSignedFilter;
    private transient IntegerListProperty mXtraAnthologyFilter;
    private transient IntegerListProperty mXtraLoanedFilter;

    /** Show list using smaller text */
    private transient BooleanListProperty mCondensed;
    /** Show list header info */
    private transient IntegerListProperty mShowHeaderInfo;

    /**
     * Options indicating this style was in the 'preferred' set when it was added to its Styles collection
     * The value is not dynamically checked.
     */
    private boolean mIsPreferred;

    /**
     * Constructor for system-defined styles.
     */
    BooklistStyle(final @StringRes int stringId) {
        mNameStringId = stringId;
        mGroups = new ArrayList<>();
        initProperties();
        mNameProperty.set((String) null);
    }

    /**
     * Constructor for user-defined styles.
     */
    public BooklistStyle(final @NonNull String name) {
        initProperties();
        mNameStringId = 0;
        mGroups = new ArrayList<>();
        mNameProperty.set(name);
    }

    public int getReadFilter() {
        return mXtraReadFilter.getInt();
    }
    public int getSignedFilter() {
        return mXtraSignedFilter.getInt();
    }
    public int getAnthologyFilter() {
        return mXtraAnthologyFilter.getInt();
    }
    public int getLoanedFilter() {
        return mXtraLoanedFilter.getInt();
    }

    /* the set Filter methods are meant for the build-in Styles */
    public void setReadFilter(final @NonNull Integer v) {
        mXtraReadFilter.set(v);
    }
    @SuppressWarnings("unused")
    public void setSignedFilter(final @NonNull Integer v) {
        mXtraSignedFilter.set(v);
    }
    @SuppressWarnings("unused")
    public void setAnthologyFilter(final @NonNull Integer v) {
        mXtraAnthologyFilter.set(v);
    }
    @SuppressWarnings("unused")
    public void setLoanedFilter(final @NonNull Integer v) {
        mXtraLoanedFilter.set(v);
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
        mNameProperty.set(name);
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

    public void addGroup(final @NonNull BooklistGroup group) {
        mGroups.add(group);
    }

    /**
     * Add a group to this style below any already added groups.
     *
     * @param kind Kind of group to add.
     */
    public void addGroup(final int kind) {
        BooklistGroup group = BooklistGroup.newGroup(kind);
        mGroups.add(group);
    }

    /**
     * Add a group to this style below any already added groups.
     *
     * @param kinds one or more Kind of groups to add.
     */
    public void addGroups(final @NonNull int... kinds) {
        for (int kind : kinds) {
            BooklistGroup group = BooklistGroup.newGroup(kind);
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
    public BooklistGroup removeGroup(final int kind) {
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
        return (mNameStringId == 0 || id != 0);
    }

    private void initProperties() {
        mXtraShowThumbnails = new BooleanProperty("XThumbnails",
                PropertyGroup.GRP_THUMBNAILS,R.string.thumbnails_show)
                .setPreferenceKey(PREF_SHOW_THUMBNAILS)
                .setDefaultValue(true)
                .setWeight(-100);

        mXtraLargeThumbnails = new BooleanProperty("XLargeThumbnails",
                PropertyGroup.GRP_THUMBNAILS, R.string.thumbnails_prefer_large)
                .setPreferenceKey(PREF_LARGE_THUMBNAILS)
                .setWeight(-99);

        mXtraShowBookshelves = new BooleanProperty("XBookshelves",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.lbl_bookshelves_long)
                .setPreferenceKey(PREF_SHOW_BOOKSHELVES);

        mXtraShowLocation = new BooleanProperty("XLocation",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.lbl_location)
                .setPreferenceKey(PREF_SHOW_LOCATION);

        mXtraShowPublisher = new BooleanProperty("XPublisher",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.lbl_publisher)
                .setPreferenceKey(PREF_SHOW_PUBLISHER);

        mXtraShowFormat = new BooleanProperty("XFormat",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS, R.string.lbl_format)
                .setPreferenceKey(PREF_SHOW_FORMAT);

        mXtraShowAuthor = new BooleanProperty("XAuthor",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS,R.string.lbl_author)
                .setPreferenceKey(PREF_SHOW_AUTHOR);

        mNameProperty = new StringProperty("StyleName",
                PropertyGroup.GRP_GENERAL, R.string.name)
                .setRequireNonBlank(true)
                .setWeight(-100);

        mCondensed = new BooleanListProperty(mCondensedListItems, PREF_CONDENSED_TEXT,
                PropertyGroup.GRP_GENERAL, R.string.blp_item_size)
                .setPreferenceKey(PREF_CONDENSED_TEXT);

        mShowHeaderInfo = new IntegerListProperty(mShowHeaderInfoListItems, PREF_SHOW_HEADER_INFO,
                PropertyGroup.GRP_GENERAL, R.string.blp_summary)
                .setPreferenceKey(PREF_SHOW_HEADER_INFO)
                .setDefaultValue(SUMMARY_SHOW_ALL);

        mXtraReadFilter = new IntegerListProperty(mReadFilterListItems, "XReadUnreadAll", /* keep name for compat */
                PropertyGroup.GRP_EXTRA_FILTERS, R.string.booklist_filters_select_based_on_read_status)
                .setDefaultValue(FILTER_EITHER);

        mXtraSignedFilter = new IntegerListProperty(mSignedFilterListItems, "XSignedFilter",
                PropertyGroup.GRP_EXTRA_FILTERS, R.string.booklist_filters_select_based_on_signed_status)
                .setDefaultValue(FILTER_EITHER);

        mXtraAnthologyFilter = new IntegerListProperty(mAnthologyFilterListItems, "XAnthologyFilter",
                PropertyGroup.GRP_EXTRA_FILTERS, R.string.booklist_filters_select_based_on_is_anthology_status)
                .setDefaultValue(FILTER_EITHER);

        mXtraLoanedFilter = new IntegerListProperty(mLoanedFilterListItems, "XLoanedFilter",
                PropertyGroup.GRP_EXTRA_FILTERS, R.string.booklist_filters_select_based_on_loaned_status)
                .setDefaultValue(FILTER_EITHER);
    }

    /**
     * Get all of the properties of this Style and its groups.
     */
    @NonNull
    public Properties getProperties() {
        Properties props = new Properties()
                .add(mXtraShowThumbnails)
                .add(mXtraLargeThumbnails)

                .add(mXtraShowBookshelves)
                .add(mXtraShowLocation)
                .add(mXtraShowPublisher)
                .add(mXtraShowFormat)
                .add(mXtraShowAuthor)

                .add(mCondensed)
                .add(mNameProperty)
                .add(mShowHeaderInfo)

                .add(mXtraReadFilter)
                .add(mXtraSignedFilter)
                .add(mXtraAnthologyFilter)
                .add(mXtraLoanedFilter);

        for (BooklistGroup g : mGroups) {
            g.getStyleProperties(props);
        }

        return props;
    }

    /**
     * Passed a Properties object, update the properties of this style
     * based on the values of the passed properties.
     */
    public void setProperties(final @NonNull Properties newProps) {
        Properties props = getProperties();
        for (Property newVal : newProps) {
            Property thisProp = props.get(newVal.getUniqueName());
            if (thisProp != null) {
                thisProp.set(newVal);
            }
        }
    }

    /**
     * Passed a template style, copy the group structure to this style.
     */
    public void setGroups(final @NonNull BooklistStyle fromStyle) {
        Properties newProps = new Properties();

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

        if (mXtraShowThumbnails.isTrue())
            extras |= EXTRAS_THUMBNAIL;

        if (mXtraLargeThumbnails.isTrue())
            extras |= EXTRAS_THUMBNAIL_LARGE;

        if (mXtraShowBookshelves.isTrue())
            extras |= EXTRAS_BOOKSHELVES;

        if (mXtraShowLocation.isTrue())
            extras |= EXTRAS_LOCATION;

        if (mXtraShowPublisher.isTrue())
            extras |= EXTRAS_PUBLISHER;

        if (mXtraShowFormat.isTrue())
            extras |= EXTRAS_FORMAT;

        if (mXtraShowAuthor.isTrue())
            extras |= EXTRAS_AUTHOR;

        return extras;
    }

    /**
     * Check if ths style has the specified group
     */
    public boolean hasKind(final int kind) {
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

    @SuppressWarnings("SameParameterValue")
    void setCondensed(final boolean condensed) {
        mCondensed.set(condensed);
    }
    @SuppressWarnings("SameParameterValue")
    void setShowAuthor(final boolean show) {
        mXtraShowAuthor.set(show);
    }

    @SuppressWarnings("SameParameterValue")
    void setShowThumbnails(final boolean show) {
        mXtraShowThumbnails.set(show);
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

    /**
     * Custom serialization support. The signature of this method should never be changed.
     * @see Serializable
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(realSerialVersion); // always write latest
        out.writeObject(mXtraShowThumbnails.get());
        out.writeObject(mXtraLargeThumbnails.get());
        out.writeObject(mXtraShowBookshelves.get());
        out.writeObject(mXtraShowLocation.get());
        out.writeObject(mXtraShowPublisher.get());
        out.writeObject(mXtraShowAuthor.get());
        out.writeObject(mXtraReadFilter.get());
        out.writeObject(mCondensed.get());
        out.writeObject(mNameProperty.get());
        out.writeObject(mShowHeaderInfo.get());
        // added in v4
        out.writeObject(mXtraShowFormat.get());
        out.writeObject(mXtraSignedFilter.get());
        out.writeObject(mXtraAnthologyFilter.get());
        out.writeObject(mXtraLoanedFilter.get());

    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     * @see Serializable
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
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
        mXtraShowThumbnails.set((Boolean) object);

        mXtraLargeThumbnails.set((Boolean) in.readObject());
        mXtraShowBookshelves.set((Boolean) in.readObject());
        mXtraShowLocation.set((Boolean) in.readObject());
        mXtraShowPublisher.set((Boolean) in.readObject());
        mXtraShowAuthor.set((Boolean) in.readObject());
        mXtraReadFilter.set((Integer) in.readObject());
        if (version > 0) {
            mCondensed.set((Boolean) in.readObject());
        }
        if (version > 1) {
            mNameProperty.set((String) in.readObject());
        } else {
            mNameProperty.set("Name");
        }
        // Added mShowHeaderInfo as a Boolean in version 3
        if (version == 3) {
            Boolean isSet = (Boolean) in.readObject();
            if (isSet == null) {
                mShowHeaderInfo.set((Integer) null);
            } else {
                mShowHeaderInfo.set(isSet ? SUMMARY_SHOW_ALL : SUMMARY_HIDE);
            }
        }
        // Changed mShowHeaderInfo from Boolean to Integer in version 4
        if (version > 3) {
            mShowHeaderInfo.set((Integer) in.readObject());
        }
        // added in v4
        if (version > 4) {
            mXtraShowFormat.set((Boolean) in.readObject());

            mXtraSignedFilter.set((Integer) in.readObject());
            mXtraAnthologyFilter.set((Integer) in.readObject());
            mXtraLoanedFilter.set((Integer) in.readObject());
        }
    }

    public boolean isCondensed() {
        return mCondensed.isTrue();
    }

    /**
     * Delete this style from the database
     */
    public void delete(final @NonNull CatalogueDBAdapter db) {
        if (id == 0) {
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
     * Construct a deep clone of this object.
     */
    @NonNull
    public BooklistStyle getClone() throws DeserializationException {
        BooklistStyle clone = SerializationUtils.cloneObject(this);
        clone.id = 0;
        return clone;
    }

    /**
     * Represents a collection of domains that make a unique key for a given group.
     *
     * @author Philip Warner
     */
    static class CompoundKey {
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
    }
}

