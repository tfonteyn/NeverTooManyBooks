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
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksMultiTypeListHandler;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.prefs.PBoolean;
import com.eleybourn.bookcatalogue.booklist.filters.PBooleanFilter;
import com.eleybourn.bookcatalogue.booklist.prefs.PInt;
import com.eleybourn.bookcatalogue.booklist.prefs.PIntList;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.booklist.prefs.PString;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;


/**
 * Represents a specific style of book list (eg. authors/series).
 * Individual {@link BooklistGroup} objects are added to a {@link BooklistStyle} in order
 * to describe the resulting list style.
 *
 * 2018-12-20: the implementation no longer stores serialized blobs, neither in the database nor
 * in backup archives (but can still read them from archives).
 * The database table now consists of a PK id, and a UUID column
 * The UUID serves as the name of the SharedPreference which describes the style.
 * Builtin styles are not stored in the database, and (internally) use a UUID==null
 * and negative id's.
 * Every setting in a style is backed by a {@link PPref} which handles the storage of that setting.
 * *All* style settings are private to a style, there is no inheritance of global settings.
 * ENHANCE: re-introduce global inheritance ? But would that actually be used ?
 *
 * ENHANCE: when a style is deleted, the prefs are cleared. But the actual fine is not removed.
 * How to do this in a device independent manner?
 *
 * How to add a new Group:
 *
 * 1. add it to {@link BooklistGroup.RowKind} and update ROW_KIND_MAX
 *
 * 2. if necessary add new domain to {@link DatabaseDefinitions }
 *
 * 3. modify {@link BooklistBuilder#build} to add the necessary grouped/sorted domains
 *
 * 4. modify {@link BooksMultiTypeListHandler} ; If it is just a string field,
 * then use a {@link BooksMultiTypeListHandler.GenericStringHolder}, otherwise add a new holder.
 * Need to at least modify {@link BooksMultiTypeListHandler#createHolder}
 *
 * @author Philip Warner
 */
public class BooklistStyle
    implements Serializable, Parcelable {

    public static final Creator<BooklistStyle> CREATOR = new Creator<BooklistStyle>() {
        @Override
        public BooklistStyle createFromParcel(@NonNull final Parcel source) {
            return new BooklistStyle(source);
        }

        @Override
        public BooklistStyle[] newArray(final int size) {
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
    public static final int EXTRAS_FORMAT = (1 << 2);
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_PUBLISHER = (1 << 3);
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_AUTHOR = (1 << 4);
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_THUMBNAIL = (1 << 5);
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_THUMBNAIL_LARGE = (1 << 6);


    /**
     * the amount of details to show in the header
     */
    @SuppressWarnings("WeakerAccess")
    public static final Integer SUMMARY_HIDE = 0;
    /** the amount of details to show in the header */
    public static final Integer SUMMARY_SHOW_COUNT = 1;
    /** the amount of details to show in the header */
    public static final Integer SUMMARY_SHOW_LEVEL_1 = 1 << 1;
    /** the amount of details to show in the header */
    public static final Integer SUMMARY_SHOW_LEVEL_2 = 1 << 2;
    /** the amount of details to show in the header */
    public static final Integer SUMMARY_SHOW_ALL = 0xff;

    /** Scaling of text and images */
    public static final int SCALE_SIZE_NORMAL = 1;
    /** Scaling of text and images */
    public static final int SCALE_SIZE_SMALLER = 2;
    /** Scaling of text and images */
    public static final int SCALE_SIZE_LARGER = 3;
    /**
     * Preferred styles / menu order. Stored in global shared preferences
     */
    public static final String PREF_BL_PREFERRED_STYLES = "BookList.Style.Preferred.Order";
    /** version field used in serialized data reading from file, see {@link #readObject} */
    static final long realSerialVersion = 5;
    /**
     * Unique name. This is a stored in our preference file (with the same name)
     * and is used for backup/restore purposes as the 'id'.
     *
     * (this is not a PPref, as we'd need the uuid to store the uuid....)
     */
    private static final String PREF_STYLE_UUID = "BookList.Style.uuid";
    /** serialization id for plain class data */
    private static final long serialVersionUID = 6615877148246388549L;

    /**
     * Row id of database row from which this object comes
     * A '0' is for an as yet unsaved user-style.
     * Always NEGATIVE (e.g. <0 ) for a build-in style
     */
    public long id;

    /**
     * the unique uuid based SharedPreference name
     */
    @Nullable
    public String uuid;

    /**
     * ID if string representing name of this style.
     * Used for standard system-defined styles
     * Always 0 for a user-defined style
     */
    @StringRes
    private int mNameResId;

    /**
     * Display name of this style.
     *
     * Used for user-defined styles.
     * encapsulated value always null for a builtin style.
     */
    private transient PString mDisplayNamePref;

    /**
     * Legacy field needs to be kept for backward serialization compatibility,
     * replaced by {@link #mDisplayNamePref}
     */
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private String mName;

    /**
     * Legacy field needs to be kept for backward serialization compatibility,
     * replaced by {@link #mStyleGroups}
     *
     * Will be converted to the new one during de-serialization, and then null'd
     * Note to self: a 'List' will NOT be deserialize'd, must be the original ArrayList
     */
    @SuppressWarnings("FieldCanBeLocal")
    private ArrayList<BooklistGroup> mGroups;

    /**
     * Options indicating this style was in the 'preferred' set when it was added to
     * its Styles collection. The value is not stored with the Style.
     * Instead all preferred (user *and* builtin) styles are stored as a single set
     * in the app-preferences.
     */
    private boolean mIsPreferred;

    /** Relative size of list text/images. */
    private transient PInt mScaleSize;
    /** Show list header info. */
    private transient PInt mShowHeaderInfo;
    /** Sorting */
    private transient PBoolean mSortAuthor;
    /** Extra details to show on book rows */
    private transient PBoolean mExtraShowThumbnails;
    private transient PBoolean mExtraLargeThumbnails;
    private transient PBoolean mExtraShowBookshelves;
    private transient PBoolean mExtraShowLocation;
    private transient PBoolean mExtraShowAuthor;
    private transient PBoolean mExtraShowPublisher;
    private transient PBoolean mExtraShowFormat;

    /**
     * All groups in this style
     */
    private transient PStyleGroups mStyleGroups;

    /**
     * All filters.
     */
    private transient Map<String, PBooleanFilter> mFilters;

    private transient PBooleanFilter mFilterRead;
    private transient PBooleanFilter mFilterSigned;
    private transient PBooleanFilter mFilterAnthology;
    private transient PBooleanFilter mFilterLoaned;

    /**
     * Constructor for system-defined styles.
     *
     * @param id     a negative int
     * @param nameId the resource id for the name
     * @param kinds  a list of group kinds to attach to this style
     */
    BooklistStyle(final @IntRange(from = -100, to = -1) long id,
                  @StringRes final int nameId,
                  @NonNull final int... kinds) {

        this.id = id;
        mNameResId = nameId;
        initPrefs();
        for (int kind : kinds) {
            mStyleGroups.add(BooklistGroup.newInstance(kind, null));
        }
    }

    /**
     * Constructor for user-defined styles.
     *
     * Only used when styles are loaded from storage.
     * Real new styles are created by cloning an existing style.
     */
    public BooklistStyle(final long id,
                         @NonNull final String uuid) {
        this.id = id;
        this.uuid = uuid;
        initPrefs();
    }

    /**
     * Standard Parcelable constructor
     */
    protected BooklistStyle(@NonNull final Parcel in) {
        this(in, false);
    }

    /**
     * Custom Parcelable constructor which allows cloning/new
     *
     * @param in    Parcel to read the object from
     * @param doNew when set to true, partially override the incoming data so we get
     *              a 'new' object but with the settings from the Parcel.
     */
    protected BooklistStyle(@NonNull final Parcel in,
                            final boolean doNew) {
        id = in.readLong();
        mNameResId = in.readInt();
        mIsPreferred = in.readByte() != 0;
        uuid = in.readString();
        if (doNew) {
            uuid = createUniqueName();
        }

        // only init the prefs once we have a valid uuid
        initPrefs();

        mDisplayNamePref.set(uuid, in);
        mName = mDisplayNamePref.get(uuid);

        // create new clone ?
        if (doNew) {
            // get a copy of the name first
            setName(this.getDisplayName());
            // now reset the other identifiers.
            id = 0;
            mNameResId = 0;
        }

        mScaleSize.set(uuid, in);
        mShowHeaderInfo.set(uuid, in);

        mStyleGroups.set(uuid, in);

        mExtraShowThumbnails.set(uuid, in);
        mExtraLargeThumbnails.set(uuid, in);
        mExtraShowBookshelves.set(uuid, in);
        mExtraShowLocation.set(uuid, in);
        mExtraShowAuthor.set(uuid, in);
        mExtraShowPublisher.set(uuid, in);
        mExtraShowFormat.set(uuid, in);

        mSortAuthor.set(uuid, in);

        mFilterRead.set(uuid, in);
        mFilterSigned.set(uuid, in);
        mFilterAnthology.set(uuid, in);
        mFilterLoaned.set(uuid, in);
    }

    /**
     * Delete *ALL* styles from the database
     */
    @SuppressWarnings("unused")
    public static void deleteAllStyles(@NonNull final CatalogueDBAdapter db) {
        db.deleteAllBooklistStyle();
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    private String createUniqueName() {
        uuid = UUID.randomUUID().toString();
        Prefs.getPrefs(uuid).edit().putString(PREF_STYLE_UUID, uuid).apply();
        return uuid;
    }

    private void initPrefs() {

        mDisplayNamePref = new PString(R.string.pk_bob_style_name);

        mStyleGroups = new PStyleGroups(R.string.pk_bob_groups);

        mScaleSize = new PInt(R.string.pk_bob_item_size);
        mShowHeaderInfo = new PInt(R.string.pk_bob_header);

        mSortAuthor = new PBoolean(R.string.pk_bob_sort_author_name);

        mExtraShowThumbnails = new PBoolean(R.string.pk_bob_thumbnails_show);
        mExtraLargeThumbnails = new PBoolean(R.string.pk_bob_thumbnails_show_large);
        mExtraShowBookshelves = new PBoolean(R.string.pk_bob_show_bookshelves);
        mExtraShowLocation = new PBoolean(R.string.pk_bob_show_location);
        mExtraShowAuthor = new PBoolean(R.string.pk_bob_show_author);
        mExtraShowPublisher = new PBoolean(R.string.pk_bob_show_publisher);
        mExtraShowFormat = new PBoolean(R.string.pk_bob_show_format);

        mFilters = new LinkedHashMap<>();

        mFilterRead = new PBooleanFilter(R.string.pk_bob_filter_read,
                                         DatabaseDefinitions.TBL_BOOKS,
                                         DatabaseDefinitions.DOM_BOOK_READ);
        mFilters.put(mFilterRead.getKey(), mFilterRead);

        mFilterSigned = new PBooleanFilter(R.string.pk_bob_filter_signed,
                                           DatabaseDefinitions.TBL_BOOKS,
                                           DatabaseDefinitions.DOM_BOOK_SIGNED);
        mFilters.put(mFilterSigned.getKey(), mFilterSigned);

        mFilterAnthology = new PBooleanFilter(R.string.pk_bob_filter_anthology,
                                              DatabaseDefinitions.TBL_BOOKS,
                                              DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK);
        mFilters.put(mFilterAnthology.getKey(), mFilterAnthology);

        mFilterLoaned = new PBooleanFilter(R.string.pk_bob_filter_loaned,
                                           DatabaseDefinitions.TBL_BOOKS,
                                           DatabaseDefinitions.DOM_LOANED_TO);
        mFilters.put(mFilterLoaned.getKey(), mFilterLoaned);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(id);
        dest.writeInt(mNameResId);
        dest.writeByte((byte) (mIsPreferred ? 1 : 0));
        dest.writeString(uuid);

        mDisplayNamePref.writeToParcel(uuid, dest);

        mScaleSize.writeToParcel(uuid, dest);
        mShowHeaderInfo.writeToParcel(uuid, dest);

        mStyleGroups.writeToParcel(uuid, dest);

        mExtraShowThumbnails.writeToParcel(uuid, dest);
        mExtraLargeThumbnails.writeToParcel(uuid, dest);
        mExtraShowBookshelves.writeToParcel(uuid, dest);
        mExtraShowLocation.writeToParcel(uuid, dest);
        mExtraShowAuthor.writeToParcel(uuid, dest);
        mExtraShowPublisher.writeToParcel(uuid, dest);
        mExtraShowFormat.writeToParcel(uuid, dest);

        mSortAuthor.writeToParcel(uuid, dest);

        mFilterRead.writeToParcel(uuid, dest);
        mFilterSigned.writeToParcel(uuid, dest);
        mFilterAnthology.writeToParcel(uuid, dest);
        mFilterLoaned.writeToParcel(uuid, dest);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
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

    /**
     * @return the system name or user-defined name based on kind of style this object defines.
     */
    @NonNull
    public String getDisplayName() {
        if (mNameResId != 0) {
            return BookCatalogueApp.getResourceString(mNameResId);
        } else {
            return mDisplayNamePref.get(uuid);
        }
    }

    private void setName(final String name) {
        mName = name;
        mDisplayNamePref.set(uuid, name);
    }

    /**
     * @return <tt>true</tt> if this style is user-defined.
     */
    public boolean isUserDefined() {
        return (mNameResId == 0 || id > 0);
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
     * Get all of the preferences of this Style and its groups.
     */
    @NonNull
    public Map<String, PPref> getPPrefs() {
        @SuppressLint("UseSparseArrays")
        Map<String, PPref> map = new HashMap<>();
        // essential property for user-defined styles 'name'
        map.put(mDisplayNamePref.getKey(), mDisplayNamePref);

        // relative scaling of font and images
        map.put(mScaleSize.getKey(), mScaleSize);
        // list header information shown
        map.put(mShowHeaderInfo.getKey(), mShowHeaderInfo);

        // properties that can be shown as extra information for each line in the book list
        map.put(mExtraShowThumbnails.getKey(), mExtraShowThumbnails);
        map.put(mExtraLargeThumbnails.getKey(), mExtraLargeThumbnails);
        map.put(mExtraShowBookshelves.getKey(), mExtraShowBookshelves);
        map.put(mExtraShowLocation.getKey(), mExtraShowLocation);
        map.put(mExtraShowPublisher.getKey(), mExtraShowPublisher);
        map.put(mExtraShowFormat.getKey(), mExtraShowFormat);
        map.put(mExtraShowAuthor.getKey(), mExtraShowAuthor);

        // sorting
        map.put(mSortAuthor.getKey(), mSortAuthor);
        // all filters (active or not)
        map.putAll(mFilters);
        // the groups that are used by the style
        map.put(mStyleGroups.getKey(), mStyleGroups);

        // for each group used by the style, add its specific preferences to our list
        for (BooklistGroup group : mStyleGroups.getGroups()) {
            map.putAll(group.getStylePPrefs());
        }

        return map;
    }

    /**
     * update the preferences of this style based on the values of the passed preferences.
     * Preferences we don't have will be not be added.
     */
    void updatePPref(@NonNull final Map<String, PPref> pPrefs) {
        Map<String, PPref> currentPPrefs = getPPrefs();

        for (PPref p : pPrefs.values()) {
            // do we have this PPref ?
            PPref ourPPref = currentPPrefs.get(p.getKey());
            // yes we do...
            if (ourPPref != null) {
                // ... update our value
                //noinspection unchecked
                ourPPref.set(uuid, p.get(uuid));
            }
        }
    }

    boolean sortAuthorByGiven() {
        return mSortAuthor.isTrue(uuid);
    }

    public int getShowHeaderInfo() {
        return mShowHeaderInfo.get(uuid);
    }

    public float getScaleSize() {
        switch (mScaleSize.get(uuid)) {
            case SCALE_SIZE_NORMAL:
                return 1.0f;
            case SCALE_SIZE_SMALLER:
                return 0.8f;
            case SCALE_SIZE_LARGER:
                return 1.2f;
            default:
                return SCALE_SIZE_NORMAL;
        }
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("SameParameterValue")
    void setScaleSize(final @IntRange(from = SCALE_SIZE_NORMAL, to = SCALE_SIZE_LARGER) int size) {
        mScaleSize.set(uuid, size);
    }

    /**
     * A quicker way of getting the status of all extra-fields in one go instead of implementing
     * individual getters for each.
     *
     * @return bitmask with the 'extra' fields that are in use (visible) for this style.
     */
    public int getExtraFieldsStatus() {
        int extras = 0;

        if (mExtraShowThumbnails.isTrue(uuid)) {
            extras |= EXTRAS_THUMBNAIL;
        }

        if (mExtraLargeThumbnails.isTrue(uuid)) {
            extras |= EXTRAS_THUMBNAIL_LARGE;
        }

        if (mExtraShowBookshelves.isTrue(uuid)) {
            extras |= EXTRAS_BOOKSHELVES;
        }

        if (mExtraShowLocation.isTrue(uuid)) {
            extras |= EXTRAS_LOCATION;
        }

        if (mExtraShowPublisher.isTrue(uuid)) {
            extras |= EXTRAS_PUBLISHER;
        }

        if (mExtraShowFormat.isTrue(uuid)) {
            extras |= EXTRAS_FORMAT;
        }

        if (mExtraShowAuthor.isTrue(uuid)) {
            extras |= EXTRAS_AUTHOR;
        }

        return extras;
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("SameParameterValue")
    void setShowAuthor(final boolean show) {
        mExtraShowAuthor.set(uuid, show);
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("SameParameterValue")
    void setShowThumbnails(final boolean show) {
        mExtraShowThumbnails.set(uuid, show);
    }

    @NonNull
    public List<BooklistGroup> getGroups() {
        return mStyleGroups.getGroups();
    }

    /**
     * Passed a template style, copy the group structure to this style.
     */
    public void setGroups(@NonNull final BooklistStyle source) {

        // Save the current groups
        Map<Integer, BooklistGroup> currentGroups = new LinkedHashMap<>();
        for (BooklistGroup group : mStyleGroups.getGroups()) {
            currentGroups.put(group.getKind(), group);
        }

        // we'll collect the new PPrefs to add here
        Map<String, PPref> newPPrefs = new LinkedHashMap<>();

        // Clear the current groups, and rebuild, reusing old values where possible
        mStyleGroups.clear();
        for (BooklistGroup newGroup : source.getGroups()) {
            BooklistGroup current = currentGroups.get(newGroup.getKind());
            // if we don't have the new one...
            if (current == null) {
                // copy the groups PPrefs locally
                newPPrefs.putAll(newGroup.getStylePPrefs());
                // and add a new instance of that group
                mStyleGroups.add(BooklistGroup.newInstance(newGroup.getKind(), uuid));
            } else {
                // otherwise, just re-add our (old) current group.
                mStyleGroups.add(current);
            }
        }

        // Lastly, copy any Preference values from new groups.
        updatePPref(newPPrefs);
    }

    /**
     * Add an already existing instance
     *
     * @param group to add
     */
    void addGroup(@NonNull final BooklistGroup group) {
        mStyleGroups.add(group);
    }

    /**
     * Remove a group from this style.
     *
     * @param group kind to remove.
     */
    void removeGroup(final int group) {
        mStyleGroups.remove(group);
    }

    /**
     * Convenience function to return a list of group names in a human readable format
     */
    @NonNull
    public String getGroupListDisplayNames() {
        return Utils.toDisplayString(mStyleGroups.getGroups());
    }

    /**
     * Check if this style has the specified group
     */
    boolean hasGroupKind(final @IntRange(from = 0, to = BooklistGroup.RowKind.ROW_KIND_MAX) int kind) {
        return mStyleGroups.getGroupKinds().contains(kind);
    }

    /**
     * Get the group at the passed index.
     */
    @NonNull
    BooklistGroup getGroupAt(final int index) {
        return mStyleGroups.getGroupAt(index);
    }

    /**
     * Get the group kind at the passed index.
     */
    @NonNull
    public int getGroupKindAt(final int index) {
        return mStyleGroups.getGroupKindAt(index);
    }

    /**
     * Get the number of groups in this style
     */
    public int groupCount() {
        return mStyleGroups.size();
    }

    @NonNull
    public Map<String, PBooleanFilter> getFilters() {
        return mFilters;
    }

    /**
     * Used by built-in styles only
     */
    @SuppressWarnings("SameParameterValue")
    void setFilter(@NonNull final Integer key,
                   final boolean value) {
        mFilters.get(BookCatalogueApp.getResourceString(key)).set(uuid, value);
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void writeObject(ObjectOutputStream out)
        throws IOException {
        out.defaultWriteObject();
        // version must use writeObject
        out.writeObject(realSerialVersion);
        // uuid is done by defaultWriteObject, so next up is the name
        out.writeObject(mDisplayNamePref.get(uuid));

        out.writeInt(mScaleSize.get(uuid));
        out.writeInt(mShowHeaderInfo.get(uuid));

        out.writeBoolean(mExtraShowThumbnails.get(uuid));
        out.writeBoolean(mExtraLargeThumbnails.get(uuid));
        out.writeBoolean(mExtraShowBookshelves.get(uuid));
        out.writeBoolean(mExtraShowLocation.get(uuid));
        out.writeBoolean(mExtraShowPublisher.get(uuid));
        out.writeBoolean(mExtraShowAuthor.get(uuid));
        out.writeBoolean(mExtraShowFormat.get(uuid));

        out.writeBoolean(mSortAuthor.get(uuid));

        out.writeInt(mFilterRead.get(uuid));
        out.writeInt(mFilterSigned.get(uuid));
        out.writeInt(mFilterAnthology.get(uuid));
        out.writeInt(mFilterLoaned.get(uuid));
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        Object object = in.readObject();
        long version = 0;
        if (object instanceof Long) {
            // It's the version
            version = ((Long) object);
            if (version > 4) {
                readObjectPostVersion4(version, in);
                return;
            }
            // Get the next object
            object = in.readObject();
        } // else it's a pre-version object, just use it

        // pre-v5 we did not have a UUID, create one so the prefs file will be written.
        uuid = createUniqueName();
        initPrefs();

        SharedPreferences.Editor ed = Prefs.getPrefs(uuid).edit();
        mExtraShowThumbnails.set(ed, (Boolean) object);

        mExtraLargeThumbnails.set(ed, (Boolean) in.readObject());
        mExtraShowBookshelves.set(ed, (Boolean) in.readObject());
        mExtraShowLocation.set(ed, (Boolean) in.readObject());
        mExtraShowPublisher.set(ed, (Boolean) in.readObject());
        mExtraShowAuthor.set(ed, (Boolean) in.readObject());
        mFilterRead.set(ed, (Integer) in.readObject());

        // v1 'condensed' was a Boolean.
        Object tmpCondensed = in.readObject();
        // up to and including v4: Boolean: null=='use-defaults', false='normal', true='condensed'
        if (tmpCondensed == null) {
            mScaleSize.set(ed, SCALE_SIZE_NORMAL);
        } else {
            mScaleSize.set(ed, (Boolean) tmpCondensed ? SCALE_SIZE_SMALLER : SCALE_SIZE_NORMAL);
        }

        // v2
        if (version > 1) {
            mName = ((String) in.readObject());
            mDisplayNamePref.set(ed, mName);
        }

        // v3 Added mShowHeaderInfo as a Boolean
        if (version == 3) {
            Boolean isSet = (Boolean) in.readObject();
            if (isSet != null) {
                mShowHeaderInfo.set(ed, isSet ? SUMMARY_SHOW_ALL : SUMMARY_HIDE);
            }
        }

        // v4 Changed mShowHeaderInfo from Boolean to Integer
        if (version > 3) {
            Integer i = ((Integer) in.readObject());
            if (i != null) {
                mShowHeaderInfo.set(ed, i);
            }
        }

        // base class de-serialized the groups, convert them
        for (BooklistGroup group : mGroups) {
            mStyleGroups.add(group);
        }
        mGroups = null;
        ed.apply();
    }

    private void readObjectPostVersion4(@SuppressWarnings("unused") final long version,
                                        final ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        initPrefs();

        SharedPreferences.Editor ed = Prefs.getPrefs(uuid).edit();
        mDisplayNamePref.set(ed, (String) in.readObject());

        mScaleSize.set(ed, in.readInt());
        mShowHeaderInfo.set(ed, in.readInt());

        mExtraShowThumbnails.set(ed, in.readBoolean());
        mExtraLargeThumbnails.set(ed, in.readBoolean());
        mExtraShowBookshelves.set(ed, in.readBoolean());
        mExtraShowLocation.set(ed, in.readBoolean());
        mExtraShowPublisher.set(ed, in.readBoolean());
        mExtraShowAuthor.set(ed, in.readBoolean());
        mExtraShowFormat.set(ed, in.readBoolean());

        mSortAuthor.set(ed, in.readBoolean());

        mFilterRead.set(ed, in.readInt());
        mFilterSigned.set(ed, in.readInt());
        mFilterAnthology.set(ed, in.readInt());
        mFilterLoaned.set(ed, in.readInt());
        ed.apply();
    }

    /**
     * Construct a clone of this object.
     * The clone is committed! (written to a new pref file, and stored in the database)
     */
    @NonNull
    public BooklistStyle getClone(@NonNull final CatalogueDBAdapter db) {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();

        parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        BooklistStyle clone = new BooklistStyle(parcel, true);
        parcel.recycle();

        clone.save(db);

        return clone;
    }

    /**
     * if an insert fails, the style retains id==0
     */
    public void save(@NonNull final CatalogueDBAdapter db) {
        // negative id == builtin style
        if (id < 0) {
            throw new IllegalArgumentException(
                "Builtin Style is not stored in the database, can not be saved");
        }

        // check if the style already exists. This is easy as we have a UUID.
        long existingId = db.getBooklistStyleIdByUuid(uuid);
        if (existingId == 0) {
            long newId = db.insertBooklistStyle(this);
            if (newId > 0) {
                id = newId;
            }
        } else {
            // force-update the id.
            id = existingId;
            db.updateBooklistStyle(this);
        }
    }

    /**
     * Delete this style
     */
    public void delete(@NonNull final CatalogueDBAdapter db) {
        // cannot delete a builtin or a 'new' style(id==0)
        if (id <= 0) {
            throw new IllegalArgumentException(
                "Style is not stored in the database, can not be deleted");
        }
        db.deleteBooklistStyle(id);
        Prefs.getPrefs(uuid).edit().clear().apply();
    }

    @Override
    public String toString() {
        return "\nBooklistStyle{" +
            "id=" + id +
            "\nuuid=`" + uuid + '`' +
            "\nmNameResId=" + mNameResId +
            "\nmDisplayNamePref=" + mDisplayNamePref +
            "\nmName=`" + mName + '`' +
            "\nmIsPreferred=" + mIsPreferred +
            "\nmScaleSize=" + mScaleSize +
            "\nmShowHeaderInfo=" + mShowHeaderInfo +
            "\nmSortAuthor=" + mSortAuthor +
            "\nmExtraShowThumbnails=" + mExtraShowThumbnails +
            "\nmExtraLargeThumbnails=" + mExtraLargeThumbnails +
            "\nmExtraShowBookshelves=" + mExtraShowBookshelves +
            "\nmExtraShowLocation=" + mExtraShowLocation +
            "\nmExtraShowAuthor=" + mExtraShowAuthor +
            "\nmExtraShowPublisher=" + mExtraShowPublisher +
            "\nmExtraShowFormat=" + mExtraShowFormat +

            "\nmFilters=\n" + mFilters +
            "\nmStyleGroups=\n" + mStyleGroups +
            '}';
    }

    /**
     * fronts an ArrayList<BooklistGroup> with backend storage of the 'kind' in a preference file.
     *
     * Not static as it uses the {@link #uuid}
     */
    private class PStyleGroups
        extends PIntList {

        private final ArrayList<BooklistGroup> mGroups = new ArrayList<>();

        PStyleGroups(final int key) {
            super(key);
            loadGroups();
        }

        /**
         * load the group id's from the SharedPreference and populates the Group object list
         */
        private void loadGroups() {
            mGroups.clear();
            for (int kind : get(uuid)) {
                mGroups.add(BooklistGroup.newInstance(kind, uuid));
            }
        }

        /**
         * Walk the list of Groups, and store their kind in SharedPreference
         */
        private void writeGroupIds() {
            List<Integer> list = new ArrayList<>();
            for (BooklistGroup group : mGroups) {
                list.add(group.getKind());
            }
            set(uuid, list);
        }

        @NonNull
        public List<BooklistGroup> getGroups() {
            return mGroups;
        }

        BooklistGroup getGroupAt(final int index) {
            return mGroups.get(index);
        }

        @NonNull
        List<Integer> getGroupKinds() {
            return this.get(uuid);
        }

        int getGroupKindAt(final int index) {
            return (int) this.get(uuid).toArray()[index];
        }

        public void clear() {
            mGroups.clear();
            super.clear(uuid);
        }

        public int size() {
            return get(uuid).size();
        }

        @Override
        public void set(@Nullable final String uuid,
                        @NonNull final Parcel in) {
            this.clear();
            in.readList(mGroups, getClass().getClassLoader());
            writeGroupIds();
        }

        @Override
        public void writeToParcel(@Nullable final String uuid,
                                  @NonNull final Parcel dest) {
            dest.writeList(mGroups);
        }

        public void add(@NonNull final BooklistGroup group) {
            mGroups.add(group);
            super.add(uuid, group.getKind());
        }

        @Override
        public void add(@Nullable final String uuid,
                        @NonNull final Integer value) {
            throw new IllegalStateException("use add(BooklistGroup) instead");
        }

        /**
         * We need the *kind* of group to remove, so we can (optionally) replace it
         * with a new (different) copy.
         *
         * @param kind of group to remove
         */
        public void remove(final int kind) {
            Iterator<BooklistGroup> it = mGroups.iterator();
            while (it.hasNext()) {
                int groupKind = it.next().getKind();
                if (groupKind == kind) {
                    it.remove();
                    super.remove(uuid, groupKind);
                }
            }
        }

        @Override
        public void remove(@Nullable final String uuid,
                           @NonNull final Integer value) {
            throw new IllegalStateException("use remove(BooklistGroup) instead");
        }

        @Override
        public String toString() {
            return "PStyleGroups{" +
                "mGroups=" + mGroups +
                ", nonPersistedValue=" + nonPersistedValue +
                ", defaultValue=" + defaultValue +
                '}';
        }
    }
}

