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
    private final String mUuid;
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
    int mMenuPosition;

    /**
     * Is this style preferred by the user; i.e. should it be shown in the preferred-list.
     * <p>
     * Stored in the database.
     */
    boolean mIsPreferred;

    /** Local override. */
    PBoolean mShowAuthorByGivenName;

    /** Local override. */
    PBoolean mSortAuthorByGivenName;

    /** The default number of levels to expand the list tree to. */
    PInteger mExpansionLevel;
    /**
     * Show list header info.
     * <p>
     * Ideally this would use a simple int, but {@link MultiSelectListPreference} insists on a Set.
     */
    PBitmask mShowHeaderInfo;

    /**
     * Should rows be shown using WRAP_CONTENT (false),
     * or as system minimum list-item height (true).
     */
    PBoolean mGroupsUseListPreferredHeight;

    /** Text related settings. */
    TextScale mTextScale;

    /** Configuration for the groups in this style. */
    Groups mGroups;

    /** Configuration for the filters in this style. */
    Filters mFilters;

    /** Configuration for the fields shown on the Book level in the book list. */
    ListScreenBookFields mListScreenBookFields;

    /** Configuration for the fields shown on the Book details screen. */
    DetailScreenBookFields mDetailScreenBookFields;

    /** Cached backing preferences. */
    @SuppressWarnings("FieldNotUsedInToString")
    StyleSharedPreferences mStyleSharedPreferences;

    protected BooklistStyle(@NonNull final Context context,
                            @NonNull final String uuid) {
        mUuid = uuid;
        mStyleSharedPreferences = new StyleSharedPreferences(context, mUuid);
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    protected BooklistStyle(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        this(App.getAppContext(),
             // the uuid
             in.readString());

        mId = in.readLong();

        mIsPreferred = in.readByte() != 0;
        mMenuPosition = in.readInt();

        initPrefs(App.getAppContext());

        mShowAuthorByGivenName.set(in);
        mSortAuthorByGivenName.set(in);

        mExpansionLevel.set(in);
        mGroupsUseListPreferredHeight.set(in);
        mShowHeaderInfo.set(in);

        mTextScale.set(in);

        mListScreenBookFields.set(in);
        mDetailScreenBookFields.set(in);

        mGroups.set(in);
        mFilters.set(in);
    }

    /**
     * Construct a clone of this object with id==0, and a new uuid.
     *
     * @param context Current context
     *
     * @return cloned/new instance
     */
    @NonNull
    @Override
    public UserStyle clone(@NonNull final Context context) {
        SanityCheck.requireValue(getUuid(), "mUuid");

        return clone(context, 0, UUID.randomUUID().toString());
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @NonNull
    public UserStyle clone(@NonNull final Context context,
                           final long id,
                           @NonNull final String uuid) {

        return new UserStyle(context, this, id, uuid);
    }

    /**
     * Only ever init the Preferences if you have a valid UUID.
     *
     * @param context Current context
     */
    void initPrefs(@NonNull final Context context) {
        mShowAuthorByGivenName = new PBoolean(this, Prefs.pk_show_author_name_given_first);
        mSortAuthorByGivenName = new PBoolean(this, Prefs.pk_sort_author_name_given_first);
        mExpansionLevel = new PInteger(this, PK_LEVELS_EXPANSION, 1);
        mShowHeaderInfo = new PBitmask(this, PK_HEADER, HEADER_BITMASK_ALL, HEADER_BITMASK_ALL);
        mGroupsUseListPreferredHeight = new PBoolean(this, PK_SCALE_GROUP_ROW, true);

        mTextScale = new TextScale(this);

        mListScreenBookFields = new ListScreenBookFields(this);
        mDetailScreenBookFields = new DetailScreenBookFields(context, this);

        mGroups = new Groups(context, this);
        mFilters = new Filters(this);

    }

    @Override
    public boolean isShowAuthorByGivenName(@NonNull final Context context) {
        return mShowAuthorByGivenName.isTrue(context);
    }

    @Override
    public boolean isSortAuthorByGivenName(@NonNull final Context context) {
        return mSortAuthorByGivenName.isTrue(context);
    }

    @Override
    public boolean isShowHeader(@NonNull final Context context,
                                @ListHeaderOption final int headerMask) {
        return (mShowHeaderInfo.getValue(context) & headerMask) != 0;
    }

    @Override
    public int getGroupRowHeight(@NonNull final Context context) {
        if (mGroupsUseListPreferredHeight.getValue(context)) {
            return AttrUtils.getDimensionPixelSize(context, R.attr.listPreferredItemHeightSmall);
        } else {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
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

    @NonNull
    public StyleSharedPreferences getSettings() {
        return mStyleSharedPreferences;
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

        mShowAuthorByGivenName.writeToParcel(dest);
        mSortAuthorByGivenName.writeToParcel(dest);

        mExpansionLevel.writeToParcel(dest);
        mGroupsUseListPreferredHeight.writeToParcel(dest);
        mShowHeaderInfo.writeToParcel(dest);

        mTextScale.writeToParcel(dest);

        mListScreenBookFields.writeToParcel(dest);
        mDetailScreenBookFields.writeToParcel(dest);

        mGroups.writeToParcel(dest);
        mFilters.writeToParcel(dest);
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

    @Override
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
     * Get the text style.
     *
     * @return TextScale
     */
    @Override
    public TextScale getTextScale() {
        return mTextScale;
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
                (getGroups().getGroupById(BooklistGroup.SERIES));
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
                (getGroups().getGroupById(BooklistGroup.PUBLISHER));
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
                (getGroups().getGroupById(BooklistGroup.BOOKSHELF));
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
                (getGroups().getGroupById(BooklistGroup.AUTHOR));
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
                (getGroups().getGroupById(BooklistGroup.AUTHOR));
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

//    @Override
//    @NonNull
//    public String toString() {
//        return "\nBooklistStyle{"
//               + "mId=" + mId
//               + "\nmUuid=`" + mUuid + '`'
//               + "\nmMenuPosition=" + mMenuPosition
//               + "\nmIsPreferred=" + mIsPreferred
//               + "\nmExpansionLevel=" + mExpansionLevel
//
//               + "\nmTextScale=" + mTextScale
//               + "\nmGroupRowScale=" + mGroupRowHeight
//
//               + "\nmShowHeaderInfo=" + mShowHeaderInfo
//
//               + "\nmShowAuthorByGivenNameFirst=" + mShowAuthorByGivenName
//               + "\nmSortAuthorByGivenNameFirst=" + mSortAuthorByGivenName
//
//               + "\nmStyleGroups=" + mGroups
//               + "\nmFilters=\n" + mFilters
//               + "\nmListScreenBookFields=" + mListScreenBookFields
//               + "\nmDetailScreenBookFields=" + mDetailScreenBookFields
//               + '}';
//    }

}
