/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.preference.MultiSelectListPreference;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.filters.Filters;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.AuthorBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BookshelfBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.Groups;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.PublisherBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.SeriesBooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.booklist.style.prefs.PPref;
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
 * <p>
 * <p>
 * Storing the settings: client -- style -- PPref -- StyleSetting -- SharedPreferences
 * <ul>
 *     <li>the style is the holder of the PPrefs</li>
 *     <li>StyleSetting is a translation layer between type-safe PPrefs, and...</li>
 *     <li>SharedPreferences which stores some settings type-safe,
 *     and others as String or StringSet values which depends on the
 *     androidx.preference framework.</li>
 * </ul>
 */
public abstract class BooklistStyle
        implements ListStyle {

    /** The default expansion level for the groups. */
    public static final String PK_LEVELS_EXPANSION = "style.booklist.levels.default";
    /** What fields the user wants to see in the list header. */
    public static final String PK_LIST_HEADER = "style.booklist.header";
    /**
     * The spacing used for the group/level rows.
     * A value of {@code 0} means {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}.
     */
    private static final String PK_SCALE_GROUP_ROW = "style.booklist.group.height";

    /** Cached backing preferences. */
    @SuppressWarnings("FieldNotUsedInToString")
    final StylePersistenceLayer mPersistenceLayer;

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
    long mId;
    /**
     * The menu position of this style as sorted by the user.
     * Preferred styles will be at the top.
     * <p>
     * Stored in the database; not used by the global style.
     */
    int mMenuPosition;
    /**
     * Is this style preferred by the user; i.e. should it be shown in the preferred-list.
     * <p>
     * Stored in the database; not used by the global style.
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
     * or as system "?attr/listPreferredItemHeightSmall" (true).
     */
    PBoolean mUseGroupRowPreferredHeight;
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
    private int mListPreferredItemHeightSmall;

    /**
     * Base constructor.
     *
     * @param context      Current context
     * @param uuid         a full UUID string, or an empty string to represent the global style.
     * @param isPersistent flag
     */
    BooklistStyle(@NonNull final Context context,
                  @NonNull final String uuid,
                  final boolean isPersistent) {
        mUuid = uuid;
        mPersistenceLayer = new StyleSharedPreferences(context, mUuid, isPersistent);
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
        SanityCheck.requireValue(mUuid, "mUuid");
        // A cloned style is *always* a UserStyle/persistent regardless of the original
        // being a UserStyle or BuiltinStyle.
        return new UserStyle(context, this, 0, UUID.randomUUID().toString());
    }

    /**
     * Only ever init the Preferences if you have a valid UUID.
     *
     * @param isPersistent flag
     */
    void initPrefs(final boolean isPersistent) {

        mShowAuthorByGivenName = new PBoolean(isPersistent, mPersistenceLayer,
                                              Prefs.pk_show_author_name_given_first);
        mSortAuthorByGivenName = new PBoolean(isPersistent, mPersistenceLayer,
                                              Prefs.pk_sort_author_name_given_first);

        mExpansionLevel = new PInteger(isPersistent, mPersistenceLayer,
                                       PK_LEVELS_EXPANSION, 1);
        mShowHeaderInfo = new PBitmask(isPersistent, mPersistenceLayer,
                                       PK_LIST_HEADER, HEADER_BITMASK_ALL, HEADER_BITMASK_ALL);

        mUseGroupRowPreferredHeight = new PBoolean(isPersistent, mPersistenceLayer,
                                                   PK_SCALE_GROUP_ROW, true);

        mTextScale = new TextScale(isPersistent, mPersistenceLayer);

        mListScreenBookFields = new ListScreenBookFields(isPersistent, mPersistenceLayer);
        mDetailScreenBookFields = new DetailScreenBookFields(isPersistent, mPersistenceLayer);

        mFilters = new Filters(isPersistent, mPersistenceLayer);
        mGroups = new Groups(isPersistent, this);
    }

    @Override
    public boolean isShowAuthorByGivenName() {
        return mShowAuthorByGivenName.isTrue();
    }

    @Override
    public boolean isSortAuthorByGivenName() {
        return mSortAuthorByGivenName.isTrue();
    }

    @Override
    public boolean isShowHeader(@ListHeaderOption final int headerMask) {
        return (mShowHeaderInfo.getValue() & headerMask) != 0;
    }

    @Override
    public int getGroupRowHeight(@NonNull final Context context) {
        if (mUseGroupRowPreferredHeight.getValue()) {
            if (mListPreferredItemHeightSmall == 0) {
                mListPreferredItemHeightSmall = AttrUtils
                        .getDimensionPixelSize(context, R.attr.listPreferredItemHeightSmall);
            }
            return mListPreferredItemHeightSmall;
        } else {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
    }

    @Override
    @IntRange(from = 1)
    public int getTopLevel() {
        // limit to the amount of groups!
        int level = mExpansionLevel.getValue();
        if (level > mGroups.size()) {
            level = mGroups.size();
        }
        return level;
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

    @Override
    @NonNull
    public Groups getGroups() {
        return mGroups;
    }

    @Override
    @NonNull
    public Filters getFilters() {
        return mFilters;
    }

    @Override
    public TextScale getTextScale() {
        return mTextScale;
    }

    @Override
    @NonNull
    public ListScreenBookFields getListScreenBookFields() {
        return mListScreenBookFields;
    }

    @Override
    @NonNull
    public DetailScreenBookFields getDetailScreenBookFields() {
        return mDetailScreenBookFields;
    }


    /**
     * Wrapper that gets the preference from {@link SeriesBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @return {@code true} if we want to show a book under each of its Series.
     */
    @Override
    public boolean isShowBooksUnderEachSeries() {
        final SeriesBooklistGroup group = (SeriesBooklistGroup)
                (getGroups().getGroupById(BooklistGroup.SERIES));
        if (group != null) {
            return group.showBooksUnderEach();
        } else {
            // return the global default.
            return SeriesBooklistGroup.showBooksUnderEachDefault();
        }
    }

    /**
     * Wrapper that gets the preference from {@link PublisherBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @return {@code true} if we want to show a book under each of its Publishers.
     */
    @Override
    public boolean isShowBooksUnderEachPublisher() {
        final PublisherBooklistGroup group = (PublisherBooklistGroup)
                (getGroups().getGroupById(BooklistGroup.PUBLISHER));
        if (group != null) {
            return group.showBooksUnderEach();
        } else {
            // return the global default.
            return PublisherBooklistGroup.showBooksUnderEachDefault();
        }
    }

    /**
     * Wrapper that gets the preference from {@link BookshelfBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @return {@code true} if we want to show a book under each of its Publishers.
     */
    @Override
    public boolean isShowBooksUnderEachBookshelf() {
        final BookshelfBooklistGroup group = (BookshelfBooklistGroup)
                (getGroups().getGroupById(BooklistGroup.BOOKSHELF));
        if (group != null) {
            return group.showBooksUnderEach();
        } else {
            // return the global default.
            return BookshelfBooklistGroup.showBooksUnderEachDefault();
        }
    }

    /**
     * Wrapper that gets the preference from {@link AuthorBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @return {@code true} if we want to show a book under each of its Authors
     */
    @Override
    public boolean isShowBooksUnderEachAuthor() {
        final AuthorBooklistGroup group = (AuthorBooklistGroup)
                (getGroups().getGroupById(BooklistGroup.AUTHOR));
        if (group != null) {
            return group.showBooksUnderEach();
        } else {
            // return the global default.
            return AuthorBooklistGroup.showBooksUnderEachDefault();
        }
    }

    /**
     * Wrapper that gets the getPrimaryType flag from the
     * {@link AuthorBooklistGroup} if we have it, or from the global default.
     *
     * @return the type of author we consider the primary author
     */
    @Override
    public int getPrimaryAuthorType() {
        final AuthorBooklistGroup group = (AuthorBooklistGroup)
                (getGroups().getGroupById(BooklistGroup.AUTHOR));
        if (group != null) {
            return group.getPrimaryType();
        } else {
            // return the global default.
            return AuthorBooklistGroup.getPrimaryTypeGlobalDefault();
        }
    }

    @NonNull
    public Map<String, PPref<?>> getRawPreferences() {
        final Map<String, PPref<?>> map = new HashMap<>();

        map.put(mExpansionLevel.getKey(), mExpansionLevel);
        map.put(mUseGroupRowPreferredHeight.getKey(), mUseGroupRowPreferredHeight);
        map.put(mShowHeaderInfo.getKey(), mShowHeaderInfo);

        map.put(mShowAuthorByGivenName.getKey(), mShowAuthorByGivenName);
        map.put(mSortAuthorByGivenName.getKey(), mSortAuthorByGivenName);

        map.putAll(mTextScale.getRawPreferences());
        map.putAll(mListScreenBookFields.getRawPreferences());
        map.putAll(mDetailScreenBookFields.getRawPreferences());

        map.putAll(mFilters.getRawPreferences());
        map.putAll(mGroups.getRawPreferences());

        return map;
    }

    @NonNull
    public StylePersistenceLayer getPersistenceLayer() {
        return mPersistenceLayer;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BooklistStyle style = (BooklistStyle) o;
        return mId == style.mId
               && mUuid.equals(style.mUuid)
               && mMenuPosition == style.mMenuPosition
               && mIsPreferred == style.mIsPreferred
               && Objects.equals(mShowAuthorByGivenName, style.mShowAuthorByGivenName)
               && Objects.equals(mSortAuthorByGivenName, style.mSortAuthorByGivenName)
               && Objects.equals(mExpansionLevel, style.mExpansionLevel)
               && Objects.equals(mShowHeaderInfo, style.mShowHeaderInfo)
               && Objects.equals(mUseGroupRowPreferredHeight, style.mUseGroupRowPreferredHeight)
               && Objects.equals(mTextScale, style.mTextScale)
               && Objects.equals(mListScreenBookFields, style.mListScreenBookFields)
               && Objects.equals(mDetailScreenBookFields, style.mDetailScreenBookFields)
               && Objects.equals(mGroups, style.mGroups)
               && Objects.equals(mFilters, style.mFilters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mUuid, mMenuPosition, mIsPreferred,
                            mShowAuthorByGivenName, mSortAuthorByGivenName,
                            mExpansionLevel, mShowHeaderInfo, mUseGroupRowPreferredHeight,
                            mTextScale, mListScreenBookFields, mDetailScreenBookFields,
                            mGroups, mFilters);
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistStyle{"
               + "mId=" + mId
               + ", mUuid=`" + mUuid + '`'
               + ", mIsPreferred=" + mIsPreferred
               + ", mMenuPosition=" + mMenuPosition

               + ", mShowAuthorByGivenName=" + mShowAuthorByGivenName
               + ", mSortAuthorByGivenName=" + mSortAuthorByGivenName

               + ", mExpansionLevel=" + mExpansionLevel
               + ", mGroupsUseListPreferredHeight=" + mUseGroupRowPreferredHeight
               + ", mListPreferredItemHeightSmall=" + mListPreferredItemHeightSmall
               + ", mShowHeaderInfo=" + mShowHeaderInfo

               + ", mTextScale=" + mTextScale

               + ", mListScreenBookFields=" + mListScreenBookFields
               + ", mDetailScreenBookFields=" + mDetailScreenBookFields

               + ", mFilters=" + mFilters
               + ", mGroups=" + mGroups
               + '}';
    }

}
