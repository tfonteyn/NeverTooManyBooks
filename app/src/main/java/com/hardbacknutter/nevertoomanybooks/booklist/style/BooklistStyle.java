/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.preference.MultiSelectListPreference;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.BookshelfBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.Groups;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.PublisherBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.SeriesBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PString;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Represents a specific style of booklist (e.g. Authors/Series).<br>
 * Individual {@link BooklistGroup} objects are added to a {@link BooklistStyle} in order
 * to describe the resulting list style.
 * <p>
 * The styles database table consists of a PK ID, and a UUID column and internal managed settings
 * <br>
 * The UUID serves as the name of the SharedPreference which describes the style.<br>
 * Builtin styles use negative ID's and a hardcoded UUID.<br>
 * Every user setting in a style is backed by a {@link PPref} which handles the storage
 * of that setting.<br>
 * <p>
 * ENHANCE: re-introduce global inheritance ? But would that actually be used ?
 * <ul>
 * <li>style preferences, filters and book-details are based on PPrefs and are backed
 * by a global default.</li>
 * <li>group preferences for groups defined in the style are also covered by PPrefs.</li>
 * <li>group preferences for groups NOT defined, are fronted by a method in this class
 * that will return the global setting if the group is not present.</li>
 * </ul>
 * ... so it's a matter of extending the StyleBaseFragment and children (and group activity),
 * to allow editing global defaults.
 */
public class BooklistStyle
        implements ListStyle {

    /** {@link Parcelable}. */
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

    /** Main style preferences. */
    public static final String PK_STYLE_NAME = "style.booklist.name";

    /** The default expansion level for the groups. */
    public static final String PK_LEVELS_EXPANSION = "style.booklist.levels.default";

    /** What fields the user wants to see in the list header. */
    private static final String PK_HEADER = "style.booklist.header";
    /**
     * The spacing used for the group/level rows.
     * A value of {@code 0} means {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}.
     */
    private static final String PK_SCALE_GROUP_ROW = "style.booklist.group.height";
    /** Style unique name. */
    private static final String PK_STYLE_UUID = "style.booklist.uuid";
    /** log tag. */
    private static final String TAG = "BooklistStyle";

    /**
     * The uuid based SharedPreference name.
     * <p>
     * When set to the empty string, the global preferences will be used.
     */
    @NonNull
    private final String mUuid;
    /**
     * Display name of this style.
     * Used for builtin styles.
     * Always {@code 0} for a user-defined style
     */
    @StringRes
    private final int mNameResId;
    /**
     * Row id of database row from which this object comes.
     * A '0' is for an as yet unsaved user-style.
     * Always NEGATIVE (e.g. <0 ) for a build-in style
     */
    private long mId;
    /**
     * The menu position of this style as sorted by the user.
     * Preferred styles will be at the top.
     * <p>
     * Stored in the database.
     */
    private int mMenuPosition;
    /**
     * Display name of this style.
     * Used for user-defined styles.
     * Encapsulated value is always {@code null} for a builtin style.
     */
    private PString mName;
    /**
     * Is this style preferred by the user; i.e. should it be shown in the preferred-list.
     * <p>
     * Stored in the database.
     */
    private boolean mIsPreferred;
    /** The default number of levels to expand the list tree to. */
    private PInteger mExpansionLevel;
    /**
     * Show list header info.
     * <p>
     * Ideally this would use a simple int, but {@link MultiSelectListPreference} insists on a Set.
     */
    private PBitmask mShowHeaderInfo;
    /** Text related settings. */
    private TextScale mTextScale;
    /**
     * Should rows be shown using WRAP_CONTENT (false),
     * or as system minimum list-item height (true).
     */
    private PBoolean mGroupRowHeight;
    /** Local override. */
    private PBoolean mShowAuthorByGivenName;
    /** Local override. */
    private PBoolean mSortAuthorByGivenName;
    /** Configuration for the groups in this style. */
    private Groups mGroups;
    /** Configuration for the filters in this style. */
    private Filters mFilters;
    /** Configuration for the fields shown on the Book level in the book list. */
    private ListScreenBookFields mListScreenBookFields;
    /** Configuration for the fields shown on the Book details screen. */
    private DetailScreenBookFields mDetailScreenBookFields;
    /** Cached backing preferences. */
    @SuppressWarnings("FieldNotUsedInToString")
    private StyleSettings mStyleSettings;

    /**
     * Constructor for <strong>Global defaults</strong>.
     *
     * @param context Current context
     */
    public BooklistStyle(@NonNull final Context context) {
        // negative == builtin; MIN_VALUE because why not....
        mId = Integer.MIN_VALUE;
        // empty indicates global
        mUuid = "";
        // must have a name res id to indicate it's not a user defined style.
        mNameResId = android.R.string.untitled;
        initPrefs(context);
    }

    /**
     * Constructor for <strong>builtin styles</strong>.
     *
     * @param context      Current context
     * @param id           a negative int
     * @param uuid         UUID for the builtin style.
     * @param nameId       the resource id for the name
     * @param isPreferred  flag
     * @param menuPosition to set
     * @param groupIds     a list of groups to attach to this style
     */
    BooklistStyle(@NonNull final Context context,
                  @IntRange(from = StyleDAO.Builtin.MAX_ID, to = -1) final long id,
                  @NonNull final String uuid,
                  @StringRes final int nameId,
                  final boolean isPreferred,
                  final int menuPosition,
                  @NonNull final int... groupIds) {
        mId = id;
        mUuid = uuid;
        mNameResId = nameId;
        mIsPreferred = isPreferred;
        mMenuPosition = menuPosition;
        initPrefs(context);

        for (@BooklistGroup.Id final int groupId : groupIds) {
            mGroups.add(BooklistGroup.newInstance(context, groupId, this));
        }
    }

    /**
     * Constructor for styles <strong>imported from xml</strong>.
     *
     * @param context Current context
     * @param uuid    UUID of the style
     */
    public BooklistStyle(@NonNull final Context context,
                         @NonNull final String uuid) {
        mId = 0;
        mUuid = uuid;
        mNameResId = 0;
        initPrefs(context);
    }

    /**
     * Constructor for styles <strong>loaded from database</strong>.
     *
     * @param context    Current context
     * @param dataHolder with data
     */
    public BooklistStyle(@NonNull final Context context,
                         @NonNull final DataHolder dataHolder) {
        mId = dataHolder.getLong(DBDefinitions.KEY_PK_ID);
        mUuid = dataHolder.getString(DBDefinitions.KEY_UUID);
        mNameResId = 0;

        mIsPreferred = dataHolder.getBoolean(DBDefinitions.KEY_STYLE_IS_PREFERRED);
        mMenuPosition = dataHolder.getInt(DBDefinitions.KEY_STYLE_MENU_POSITION);

        initPrefs(context);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private BooklistStyle(@NonNull final Parcel in) {
        mId = in.readLong();
        mNameResId = in.readInt();
        //noinspection ConstantConditions
        mUuid = in.readString();
        mIsPreferred = in.readByte() != 0;
        mMenuPosition = in.readInt();

        // We have a valid uuid and name resId,
        // and have read the basic (database stored) settings.
        // Now init the preferences.
        initPrefs(App.getAppContext());

        // continue to restore from the parcel.
        mName.set(in);

        mExpansionLevel.set(in);
        mGroupRowHeight.set(in);
        mTextScale.set(in);
        mShowHeaderInfo.set(in);
        mShowAuthorByGivenName.set(in);
        mSortAuthorByGivenName.set(in);

        mGroups.set(in);

        mFilters.set(in);
        mListScreenBookFields.set(in);
        mDetailScreenBookFields.set(in);
    }

    /**
     * Custom Parcelable constructor: create a new object but with the settings from the Parcel.
     * <p>
     * The id and uuid are passed in to allow testing,
     * see {@link #clone(Context)}.
     *
     * @param context Current context
     * @param id      for the new style
     * @param uuid    for the new style
     * @param name    for the new style
     * @param in      Parcel to construct the object from
     */
    private BooklistStyle(@NonNull final Context context,
                          final long id,
                          @NonNull final String uuid,
                          @NonNull final String name,
                          @NonNull final Parcel in) {
        // skip these and use new values

        // skip mId
        in.readLong();
        mId = id;
        // skip mNameResId
        in.readInt();
        mNameResId = 0;
        // skip mUuid
        in.readString();
        mUuid = uuid;
        // Manually store the new UUID.
        // This will initialise a new xml file.
        // It's not strictly needed (we'll never read it) but handy to have
        // it stored inside the file for debugging.
        context.getSharedPreferences(mUuid, Context.MODE_PRIVATE)
               .edit().putString(PK_STYLE_UUID, mUuid).apply();

        // continue with basic settings
        mIsPreferred = in.readByte() != 0;
        mMenuPosition = in.readInt();

        // We have a valid uuid and name resId,
        // and have read the basic (database stored) settings.
        // Now init the preferences.
        initPrefs(context);

        // skip, and set the new name
        mName.set(in);
        mName.set(name);

        // further prefs can be set from the parcel as normal.
        mExpansionLevel.set(in);
        mGroupRowHeight.set(in);
        mTextScale.set(in);
        mShowHeaderInfo.set(in);
        mShowAuthorByGivenName.set(in);
        mSortAuthorByGivenName.set(in);

        mGroups.set(in);

        mFilters.set(in);
        mListScreenBookFields.set(in);
        mDetailScreenBookFields.set(in);
    }

    public static boolean isBuiltin(@NonNull final String uuid) {
        return StyleDAO.Builtin.isBuiltin(uuid);
    }

    /**
     * Construct a clone of this object with id==0, and a new uuid.
     *
     * @param context Current context
     *
     * @return cloned/new instance
     */
    @NonNull
    public BooklistStyle clone(@NonNull final Context context) {
        SanityCheck.requireValue(mUuid, "mUuid");

        return clone(context, 0, UUID.randomUUID().toString());
    }

    @VisibleForTesting
    @NonNull
    public BooklistStyle clone(@NonNull final Context context,
                               final long id,
                               @NonNull final String uuid) {

        //TODO: revisit... this is to complicated/inefficient.
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        final byte[] bytes = parcel.marshall();
        parcel.recycle();

        parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);

        final BooklistStyle clone = new BooklistStyle(context, id, uuid, getLabel(context), parcel);
        parcel.recycle();

        return clone;
    }

    /**
     * Only ever init the Preferences if you have a valid UUID.
     *
     * @param context Current context
     */
    private void initPrefs(@NonNull final Context context) {

        mStyleSettings = new StyleSettings(context, mUuid);

        mName = new PString(this, PK_STYLE_NAME);

        mExpansionLevel = new PInteger(this, PK_LEVELS_EXPANSION, 1);

        mShowHeaderInfo = new PBitmask(this, PK_HEADER, HEADER_BITMASK_ALL, HEADER_BITMASK_ALL);

        mGroupRowHeight = new PBoolean(this, PK_SCALE_GROUP_ROW, true);

        mTextScale = new TextScale(this);

        mShowAuthorByGivenName = new PBoolean(this, Prefs.pk_show_author_name_given_first);
        mSortAuthorByGivenName = new PBoolean(this, Prefs.pk_sort_author_name_given_first);

        mGroups = new Groups(context, this);
        mFilters = new Filters(this);
        mListScreenBookFields = new ListScreenBookFields(this);
        mDetailScreenBookFields = new DetailScreenBookFields(context, this);
    }

    /**
     * Get all of the preferences of this Style and its groups/filters.
     *
     * @return unordered map with all preferences for this style
     */
    @Override
    @NonNull
    public Map<String, PPref> getPreferences() {
        final Map<String, PPref> tmpMap = new HashMap<>();
        tmpMap.put(mName.getKey(), mName);

        tmpMap.put(mExpansionLevel.getKey(), mExpansionLevel);
        tmpMap.put(mGroupRowHeight.getKey(), mGroupRowHeight);
        tmpMap.put(mShowHeaderInfo.getKey(), mShowHeaderInfo);

        tmpMap.put(mShowAuthorByGivenName.getKey(), mShowAuthorByGivenName);
        tmpMap.put(mSortAuthorByGivenName.getKey(), mSortAuthorByGivenName);

        tmpMap.put(mGroups.getKey(), mGroups);

        mTextScale.addToMap(tmpMap);

        mListScreenBookFields.addToMap(tmpMap);
        mDetailScreenBookFields.addToMap(tmpMap);

        mGroups.addToMap(tmpMap);
        mFilters.addToMap(tmpMap);

        return tmpMap;
    }

    @Override
    @NonNull
    public StyleSettings getSettings() {
        return mStyleSettings;
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeInt(mNameResId);
        dest.writeString(mUuid);
        dest.writeByte((byte) (mIsPreferred ? 1 : 0));
        dest.writeInt(mMenuPosition);

        // now the user preferences themselves
        mName.writeToParcel(dest);

        mExpansionLevel.writeToParcel(dest);
        mGroupRowHeight.writeToParcel(dest);
        mTextScale.writeToParcel(dest);
        mShowHeaderInfo.writeToParcel(dest);
        mShowAuthorByGivenName.writeToParcel(dest);
        mSortAuthorByGivenName.writeToParcel(dest);

        mGroups.writeToParcel(dest);

        mFilters.writeToParcel(dest);
        mListScreenBookFields.writeToParcel(dest);
        mDetailScreenBookFields.writeToParcel(dest);
    }

    @Override
    @NonNull
    public String getUuid() {
        return mUuid;
    }



    /**
     * Accessor.
     * <ul>
     *      <li>Positive ID's: user-defined styles</li>
     *      <li>Negative ID's: builtin styles</li>
     *      <li>0: a user-defined style which has not been saved yet</li>
     * </ul>
     */
    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    /**
     * Get the user displayable name for this style.
     *
     * @param context Current context
     *
     * @return the system name or user-defined name
     */
    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        if (mNameResId != 0) {
            return context.getString(mNameResId);
        } else {
            return mName.getValue(context);
        }
    }

    @Override
    public boolean isUserDefined() {
        return mNameResId == 0;
    }

    @Override
    public boolean isBuiltin() {
        return mNameResId != 0;
    }

    @Override
    public int getMenuPosition() {
        return mMenuPosition;
    }

    @Override
    public void setMenuPosition(final int menuPosition) {
        mMenuPosition = menuPosition;
    }


    @Override
    public boolean isPreferred() {
        return mIsPreferred;
    }

    @Override
    public void setPreferred(final boolean isPreferred) {
        mIsPreferred = isPreferred;
    }

    @Override
    public boolean isShowHeader(@NonNull final Context context,
                                @ListHeaderOption final int headerMask) {
        return (mShowHeaderInfo.getValue(context) & headerMask) != 0;
    }

    @Override
    @IntRange(from = 1)
    public int getTopLevel(@NonNull final Context context) {
        // limit to the amount of groups!
        int level = mExpansionLevel.getValue(context);
        if (level > mGroups.size()) {
            level = mGroups.size();
        }
        return level;
    }

    @Override
    public int getGroupRowHeight(@NonNull final Context context) {
        if (mGroupRowHeight.getValue(context)) {
            return AttrUtils.getDimensionPixelSize(context, R.attr.listPreferredItemHeightSmall);
        } else {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
    }


    /**
     * Get the text style.
     *
     * @return TextScale
     */
    @Override
    public TextScale getTextScale() {
        return mTextScale;
    }

    /**
     * Get the Groups style object.
     *
     * @return the Groups object
     */
    @Override
    @NonNull
    public Groups getGroups() {
        return mGroups;
    }

    /**
     * Get the Filters style object.
     *
     * @return the Filters object
     */
    @Override
    @NonNull
    public Filters getFilters() {
        return mFilters;
    }

    /**
     * Get the ListScreenBookFields style object.
     *
     * @return the ListScreenBookFields object
     */
    @Override
    @NonNull
    public ListScreenBookFields getListScreenBookFields() {
        return mListScreenBookFields;
    }

    /**
     * Get the DetailScreenBookFields style object.
     *
     * @return the DetailScreenBookFields object
     */
    @Override
    @NonNull
    public DetailScreenBookFields getDetailScreenBookFields() {
        return mDetailScreenBookFields;
    }


    /**
     * Wrapper that gets the preference from {@link SeriesBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Series.
     */
    @Override
    public boolean isShowBooksUnderEachSeries(@NonNull final Context context) {
        final SeriesBooklistGroup group = (SeriesBooklistGroup)
                (mGroups.getGroupById(BooklistGroup.SERIES));
        if (group != null) {
            return group.showBooksUnderEach(context);
        } else {
            // return the global default.
            return SeriesBooklistGroup.showBooksUnderEachDefault(context);
        }
    }

    /**
     * Wrapper that gets the preference from {@link PublisherBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Publishers.
     */
    @Override
    public boolean isShowBooksUnderEachPublisher(@NonNull final Context context) {
        final PublisherBooklistGroup group = (PublisherBooklistGroup)
                (mGroups.getGroupById(BooklistGroup.PUBLISHER));
        if (group != null) {
            return group.showBooksUnderEach(context);
        } else {
            // return the global default.
            return PublisherBooklistGroup.showBooksUnderEachDefault(context);
        }
    }

    /**
     * Wrapper that gets the preference from {@link BookshelfBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Publishers.
     */
    @Override
    public boolean isShowBooksUnderEachBookshelf(@NonNull final Context context) {
        final BookshelfBooklistGroup group = (BookshelfBooklistGroup)
                (mGroups.getGroupById(BooklistGroup.BOOKSHELF));
        if (group != null) {
            return group.showBooksUnderEach(context);
        } else {
            // return the global default.
            return BookshelfBooklistGroup.showBooksUnderEachDefault(context);
        }
    }

    /**
     * Wrapper that gets the preference from {@link AuthorBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Authors
     */
    @Override
    public boolean isShowBooksUnderEachAuthor(@NonNull final Context context) {
        final AuthorBooklistGroup group = (AuthorBooklistGroup)
                (mGroups.getGroupById(BooklistGroup.AUTHOR));
        if (group != null) {
            return group.showBooksUnderEach(context);
        } else {
            // return the global default.
            return AuthorBooklistGroup.showBooksUnderEachDefault(context);
        }
    }

    /**
     * Whether the user prefers the Author names displayed by Given names, or by Family name first.
     *
     * @param context Current context
     *
     * @return {@code true} when Given names should come first
     */
    @Override
    public boolean isShowAuthorByGivenName(@NonNull final Context context) {
        return mShowAuthorByGivenName.isTrue(context);
    }

    /**
     * Whether the user prefers the Author names sorted by Given names, or by Family name first.
     *
     * @param context Current context
     *
     * @return {@code true} when Given names should come first
     */
    @Override
    public boolean isSortAuthorByGivenName(@NonNull final Context context) {
        return mSortAuthorByGivenName.isTrue(context);
    }

    /**
     * Wrapper that gets the getPrimaryType flag from the
     * {@link AuthorBooklistGroup} if we have it, or from the global default.
     *
     * @param context Current context
     *
     * @return the type of author we consider the primary author
     */
    @Override
    public int getPrimaryAuthorType(@NonNull final Context context) {
        final AuthorBooklistGroup group = (AuthorBooklistGroup)
                (mGroups.getGroupById(BooklistGroup.AUTHOR));
        if (group != null) {
            return group.getPrimaryType(context);
        } else {
            // return the global default.
            return AuthorBooklistGroup.getPrimaryTypeGlobalDefault(context);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUuid);
    }

    /**
     * Equality.
     * <p>
     * - it's the same Object duh..
     * - the uuid is the same (and not empty).
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final BooklistStyle that = (BooklistStyle) obj;

        if (mUuid.isEmpty() || that.mUuid.isEmpty()) {
            return false;
        }

        // ignore case because paranoia (import)
        return mUuid.equalsIgnoreCase(that.mUuid);
    }

    @Override
    @NonNull
    public String toString() {
        return "\nBooklistStyle{"
               + "mId=" + mId
               + "\nmUuid=`" + mUuid + '`'
               + "\nmMenuPosition=" + mMenuPosition
               + "\nmNameResId=`" + (mNameResId != 0
                                     ? App.getAppContext().getString(mNameResId) : 0) + '`'
               + "\nmName=`" + mName + '`'
               + "\nmIsPreferred=" + mIsPreferred
               + "\nmExpansionLevel=" + mExpansionLevel

               + "\nmTextScale=" + mTextScale
               + "\nmGroupRowScale=" + mGroupRowHeight

               + "\nmShowHeaderInfo=" + mShowHeaderInfo

               + "\nmShowAuthorByGivenNameFirst=" + mShowAuthorByGivenName
               + "\nmSortAuthorByGivenNameFirst=" + mSortAuthorByGivenName

               + "\nmStyleGroups=" + mGroups
               + "\nmFilters=\n" + mFilters
               + "\nmListScreenBookFields=" + mListScreenBookFields
               + "\nmDetailScreenBookFields=" + mDetailScreenBookFields
               + '}';
    }

}
