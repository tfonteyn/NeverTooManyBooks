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
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
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
public abstract class BooklistStyle
        implements ListStyle {

    /** The default expansion level for the groups. */
    public static final String PK_LEVELS_EXPANSION = "style.booklist.levels.default";

    /** What fields the user wants to see in the list header. */
    private static final String PK_HEADER = "style.booklist.header";
    /**
     * The spacing used for the group/level rows.
     * A value of {@code 0} means {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}.
     */
    private static final String PK_SCALE_GROUP_ROW = "style.booklist.group.height";

    /**
     * The uuid based SharedPreference name.
     * <p>
     * When set to the empty string, the global preferences will be used.
     */
    @NonNull
    protected final String mUuid;

    /**
     * Row id of database row from which this object comes.
     * A '0' is for an as yet unsaved user-style.
     * Always NEGATIVE (e.g. <0 ) for a build-in style
     */
    protected long mId;
    /**
     * The menu position of this style as sorted by the user.
     * Preferred styles will be at the top.
     * <p>
     * Stored in the database.
     */
    protected int mMenuPosition;
    /**
     * Is this style preferred by the user; i.e. should it be shown in the preferred-list.
     * <p>
     * Stored in the database.
     */
    protected boolean mIsPreferred;

    /** The default number of levels to expand the list tree to. */
    protected PInteger mExpansionLevel;
    /**
     * Show list header info.
     * <p>
     * Ideally this would use a simple int, but {@link MultiSelectListPreference} insists on a Set.
     */
    protected PBitmask mShowHeaderInfo;
    /** Text related settings. */
    protected TextScale mTextScale;
    /**
     * Should rows be shown using WRAP_CONTENT (false),
     * or as system minimum list-item height (true).
     */
    protected PBoolean mGroupRowHeight;

    /** Local override. */
    protected PBoolean mShowAuthorByGivenName;
    /** Local override. */
    protected PBoolean mSortAuthorByGivenName;

    /** Configuration for the groups in this style. */
    protected Groups mGroups;
    /** Configuration for the filters in this style. */
    protected Filters mFilters;
    /** Configuration for the fields shown on the Book level in the book list. */
    protected ListScreenBookFields mListScreenBookFields;
    /** Configuration for the fields shown on the Book details screen. */
    protected DetailScreenBookFields mDetailScreenBookFields;

    /** Cached backing preferences. */
    @SuppressWarnings("FieldNotUsedInToString")
    protected StyleSettings mStyleSettings;

    protected BooklistStyle(@NonNull final Context context,
                            @NonNull final String uuid) {
        mUuid = uuid;
        mStyleSettings = new StyleSettings(context, mUuid);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    protected BooklistStyle(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        this(App.getAppContext(), in.readString());

        mId = in.readLong();

        mIsPreferred = in.readByte() != 0;
        mMenuPosition = in.readInt();

        initPrefs(App.getAppContext());
        unparcelPrefs(in);

        // if adding fields, take care child classes stay in sync
    }

    /**
     * Construct a clone of this object with id==0, and a new uuid.
     *
     * @param context Current context
     *
     * @return cloned/new instance
     */
    @NonNull
    public UserStyle clone(@NonNull final Context context) {
        SanityCheck.requireValue(getUuid(), "mUuid");

        return clone(context, 0, UUID.randomUUID().toString());
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @NonNull
    public UserStyle clone(@NonNull final Context context,
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

        final UserStyle clone = new UserStyle(context, id, uuid, getLabel(context), parcel);
        parcel.recycle();

        return clone;
    }


    /**
     * Only ever init the Preferences if you have a valid UUID.
     *
     * @param context Current context
     */
    void initPrefs(@NonNull final Context context) {

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
    @NonNull
    public Map<String, PPref> getPreferences() {
        final Map<String, PPref> tmpMap = new HashMap<>();

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
        dest.writeString(mUuid);
        dest.writeLong(mId);

        dest.writeByte((byte) (mIsPreferred ? 1 : 0));
        dest.writeInt(mMenuPosition);

        parcelPrefs(dest);
    }

    private void parcelPrefs(@NonNull final Parcel dest) {
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

    void unparcelPrefs(@NonNull final Parcel in) {
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

    @Override
    public boolean isShowAuthorByGivenName(@NonNull final Context context) {
        return mShowAuthorByGivenName.isTrue(context);
    }

    @Override
    public boolean isSortAuthorByGivenName(@NonNull final Context context) {
        return mSortAuthorByGivenName.isTrue(context);
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
