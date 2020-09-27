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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.StringRes;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.BitmaskFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.BooleanFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.NotEmptyFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntList;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PString;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

/**
 * Represents a specific style of booklist (e.g. Authors/Series).
 * Individual {@link BooklistGroup} objects are added to a {@link BooklistStyle} in order
 * to describe the resulting list style.
 * <p>
 * The styles database table consists of a PK ID, and a UUID column.<br>
 * The UUID serves as the name of the SharedPreference which describes the style.<br>
 * Builtin styles use negative ID's and a hardcoded UUID.<br>
 * Every setting in a style is backed by a {@link PPref} which handles the storage
 * of that setting.<br>
 * <p>
 * ENHANCE: re-introduce global inheritance ? But would that actually be used ?
 * - style preferences, filters and book-details are based on PPrefs and are backed
 * by a global default.
 * - group preferences for groups defined in the style are also covered by PPrefs.
 * - group preferences for groups NOT defined, are fronted by a method in this class
 * that will return the global setting if the group is not present.
 * ... so it's a matter of extending the StyleBaseFragment and children (and group activity),
 * to allow editing global defaults.
 */
public class BooklistStyle
        implements Parcelable, Entity {

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

    /** default style when none is set yet. */
    public static final int DEFAULT_STYLE_ID = Builtin.AUTHOR_THEN_SERIES_ID;

    /**
     * the amount of details to show in the header.
     * NEVER change these values, they get stored in preferences.
     * <p>
     * not in use: 1 << 2
     */
    public static final int HEADER_SHOW_BOOK_COUNT = 1;
    /** the amount of details to show in the header. */
    public static final int HEADER_SHOW_STYLE_NAME = 1 << 3;
    /** the amount of details to show in the header. */
    public static final int HEADER_SHOW_FILTER = 1 << 4;

    /** Main style preferences. */
    public static final String pk_name = "style.booklist.name";


    /** The default expansion level for the groups. */
    public static final String pk_levels_expansion = "style.booklist.levels.default";


    /** Thumbnail Scaling. */
    static final int IMAGE_SCALE_0_NOT_DISPLAYED = 0;
    /** Thumbnail Scaling. */
    static final int IMAGE_SCALE_1_VERY_SMALL = 1;
    /** Thumbnail Scaling. */
    static final int IMAGE_SCALE_2_SMALL = 2;
    /** Thumbnail Scaling. */
    static final int IMAGE_SCALE_3_MEDIUM = 3;
    /** Thumbnail Scaling. */
    public static final int IMAGE_SCALE_DEFAULT = IMAGE_SCALE_3_MEDIUM;
    /** Thumbnail Scaling. */
    static final int IMAGE_SCALE_4_LARGE = 4;
    /** Thumbnail Scaling. */
    static final int IMAGE_SCALE_5_VERY_LARGE = 5;
    /** Thumbnail Scaling. */
    static final int IMAGE_SCALE_6_MAX = 6;

    private static final String pk_is_preferred = "style.booklist.preferred";

    private static final String pk_header = "style.booklist.header";

    /**
     * The spacing used for the group/level rows.
     * A value of {@code 0} means {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}.
     */
    private static final String pk_scale_group_row = "style.booklist.group.height";

    /**
     * Style unique name. This is a stored in our preference file (with the same name)
     * and is used for backup/restore purposes as the 'ID'.
     */
    private static final String PK_STYLE_UUID = "style.booklist.uuid";

    /** log tag. */
    private static final String TAG = "BooklistStyle";

    /**
     * A BooklistStyle <strong>UUID</strong>. This is used during the USE of a style.
     * <p>
     * <br>type: {@code String}
     */
    public static final String BKEY_STYLE_UUID = TAG + ":uuid";

    /**
     * A parcelled BooklistStyle. This should only be used during the EDITING of a style.
     * <p>
     * <br>type: {@link BooklistStyle}
     */
    public static final String BKEY_STYLE = TAG + ":style";

    /**
     * Styles related data was modified (or not).
     * This includes a Style being modified or deleted,
     * or the order of the preferred styles modified,
     * or the selected style changed,
     * or ...
     * ENHANCE: make this fine grained and reduce unneeded rebuilds
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_STYLE_MODIFIED = TAG + ":modified";

    /** the amount of details to show in the header. */
    private static final int HEADER_BITMASK_ALL =
            HEADER_SHOW_BOOK_COUNT
            | HEADER_SHOW_STYLE_NAME
            | HEADER_SHOW_FILTER;

    /**
     * Preference for the current default style UUID to use.
     * Stored in global shared preferences.
     */
    private static final String PREF_BL_STYLE_CURRENT_DEFAULT = "bookList.style.current";

    /**
     * Preferred styles / menu order.
     * Stored in global shared preferences as a CSV String of UUIDs.
     */
    private static final String PREF_BL_PREFERRED_STYLES = "bookList.style.preferred.order";

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
    private long mId;
    /**
     * Display name of this style.
     * Used for builtin styles.
     * Always {@code 0} for a user-defined style
     */
    @StringRes
    private int mNameResId;
    /**
     * Display name of this style.
     * Used for user-defined styles.
     * Encapsulated value is always {@code null} for a builtin style.
     */
    private PString mName;
    /**
     * Flag indicating this style was in the 'preferred' set when it was added to
     * its Styles collection.
     * This preference is stored with the user-defined style.
     * But all preferred (user *and* builtin) styles also stored as a single set
     * in the app-preferences.
     */
    private PBoolean mIsPreferred;

    /** The default number of levels to expand the list tree to. */
    private PInteger mExpansionLevel;

    /**
     * Show list header info.
     * <p>
     * Ideally this would use a simple int, but {@link MultiSelectListPreference} insists on a Set.
     */
    private PBitmask mShowHeaderInfo;

    /** Text related settings. */
    private TextStyle mTextStyle;

    private PBoolean mGroupRowScale;

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
    private DetailScreenBookFields mDetailScreenBookFields;

    @SuppressWarnings("FieldNotUsedInToString")
    private SharedPreferences mStylePrefs;

    /**
     * Global defaults constructor.
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
        initPrefs(context, false);
    }

    /**
     * Constructor for system-defined styles.
     *
     * @param context  Current context
     * @param id       a negative int
     * @param uuid     UUID for the builtin style.
     * @param nameId   the resource id for the name
     * @param groupIds a list of groups to attach to this style
     */
    private BooklistStyle(@NonNull final Context context,
                          @IntRange(from = -100, to = -1) final long id,
                          @NonNull final String uuid,
                          @StringRes final int nameId,
                          @NonNull final int... groupIds) {
        mId = id;
        mUuid = uuid;
        mNameResId = nameId;
        initPrefs(context, mNameResId == 0);
        for (@BooklistGroup.Id int groupId : groupIds) {
            mGroups.add(BooklistGroup.newInstance(context, groupId, this));
        }
    }

    /**
     * Constructor for user-defined styles.
     * <p>
     * Only used when styles are loaded from storage or imported from xml.
     * Real new styles are created by cloning an existing style.
     *
     * @param context Current context
     * @param id      the row id of the style
     * @param uuid    UUID of the style
     */
    public BooklistStyle(@NonNull final Context context,
                         final long id,
                         @NonNull final String uuid) {
        mId = id;
        mUuid = uuid;
        initPrefs(context, true);
    }

//    /**
//     * Copy constructor.
//     * The new style will have an id==0, and a new uuid.
//     *
//     * @param from object to copy
//     */
//    public BooklistStyle(@NonNull final Context context,
//                         @NonNull final BooklistStyle from) {
//        mId = 0;
//        mNameResId = 0;
//        mUuid = createUniqueName(context);
//        initPrefs(mNameResId == 0);
//        setName(from.getLabel(context));
//
//        mIsPreferred.set(from.mIsPreferred.get());
//        mDefaultExpansionLevel.set(from.mDefaultExpansionLevel.get());
//        mFontScale.set(from.mFontScale.get());
//        mThumbnailScale.set(from.mThumbnailScale.get());
//        mShowHeaderInfo.set(from.mShowHeaderInfo.get());
//        mShowAuthorByGivenNameFirst.set(from.mShowAuthorByGivenNameFirst.get());
//        mSortAuthorByGivenNameFirst.set(from.mSortAuthorByGivenNameFirst.get());
//
//        for (BooklistGroup group : from.getGroups()) {
//            mStyleGroups.add(new BooklistGroup(group);
//
//        }
//
//        for (final Map.Entry<String, PBoolean> entry : from.mAllBookDetailFields.entrySet()) {
//            //noinspection ConstantConditions
//            mAllBookDetailFields.get(entry.getKey()).set(entry.getValue().get());
//        }
//
//        //: copy filters
////        for (final Map.Entry<String, Filter> entry : from.mFilters.entrySet()) {
////            //noinspection ConstantConditions
////            mFilters.get(entry.getKey()).
////        }
//    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private BooklistStyle(@NonNull final Parcel in) {
        mId = in.readLong();
        // will be 0 for user defined styles
        mNameResId = in.readInt();
        //noinspection ConstantConditions
        mUuid = in.readString();

        // now we have a valid uuid and name resId, we can init the preferences.
        initPrefs(App.getAppContext(), mNameResId == 0);

        // continue to restore from the parcel.
        mName.set(in);

        mIsPreferred.set(in);
        mExpansionLevel.set(in);
        mGroupRowScale.set(in);
        mTextStyle.set(in);
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
     * The new id will be 0, and the uuid will be newly generated.
     *
     * @param context Current context
     * @param name    for the new style
     * @param in      Parcel to construct the object from
     */
    private BooklistStyle(@NonNull final Context context,
                          @NonNull final String name,
                          @NonNull final Parcel in) {
        // skip mId
        in.readLong();
        // skip mNameResId
        in.readInt();
        // skip mUuid
        in.readString();

        // instead use these new identifiers:
        mId = 0;
        mNameResId = 0;
        mUuid = UUID.randomUUID().toString();

        // manually store the new UUID (this will automatically initialise a new xml file)
        context.getSharedPreferences(mUuid, Context.MODE_PRIVATE)
               .edit().putString(PK_STYLE_UUID, mUuid).apply();

        // only init the prefs once we have a valid mId, mUuid and mNameResId
        initPrefs(context, true);

        // skip, and set the name
        mName.set(in);
        mName.set(name);

        // further prefs can be set from the parcel as normal.
        mIsPreferred.set(in);
        mExpansionLevel.set(in);
        mGroupRowScale.set(in);
        mTextStyle.set(in);
        mShowHeaderInfo.set(in);
        mShowAuthorByGivenName.set(in);
        mSortAuthorByGivenName.set(in);

        mGroups.set(in);
        mFilters.set(in);

        mListScreenBookFields.set(in);
        mDetailScreenBookFields.set(in);
    }

    /**
     * Get the specified style. If not found, the default style will be returned.
     *
     * @param context Current context
     * @param db      Database Access
     * @param uuid    UUID of the style to get.
     *
     * @return the style, or the default style if not found
     */
    @NonNull
    public static BooklistStyle getStyleOrDefault(@NonNull final Context context,
                                                  @NonNull final DAO db,
                                                  @NonNull final String uuid) {
        final BooklistStyle style = getStyle(context, db, uuid);
        if (style != null) {
            return style;
        }
        // fall back to the user default.
        return getDefault(context, db);
    }

    /**
     * Get the <strong>user</strong> default style.
     *
     * @param context Current context
     * @param db      Database Access
     *
     * @return the style.
     */
    @NonNull
    public static BooklistStyle getDefault(@NonNull final Context context,
                                           @NonNull final DAO db) {

        // read the global user default, or if not present the hardcoded default.
        final String uuid = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getString(PREF_BL_STYLE_CURRENT_DEFAULT,
                                                        Builtin.DEFAULT_STYLE_UUID);

        final BooklistStyle style = getStyle(context, db, uuid);
        if (style != null) {
            return style;
        }
        // fall back to the builtin default.
        return Builtin.getDefault(context);
    }

    /**
     * Get the specified style; {@code null} if not found.
     *
     * @param context Current context
     * @param db      Database Access
     * @param uuid    UUID of the style to get.
     *
     * @return the style, or {@code null} if not found
     */
    @Nullable
    private static BooklistStyle getStyle(@NonNull final Context context,
                                          @NonNull final DAO db,
                                          @NonNull final String uuid) {
        // Check Builtin first
        final BooklistStyle style = Builtin.getStyle(context, uuid);
        if (style != null) {
            return style;
        }

        // User defined ? or null if not found
        return StyleDAO.getStyle(context, db, uuid);
    }

    /**
     * Get an ordered Map with all the styles (user/builtin).
     * The preferred styles are at the front of the list.
     *
     * @param context Current context
     * @param db      Database Access
     * @param all     if {@code true} then also return the non-preferred styles
     *
     * @return LinkedHashMap, key: uuid, value: style
     */
    @NonNull
    public static Map<String, BooklistStyle> getStyles(@NonNull final Context context,
                                                       @NonNull final DAO db,
                                                       final boolean all) {
        // Create a NEW list! i.e. the style objects are shared (as they should)
        // but do not modify the original list.
        final Map<String, BooklistStyle> allStyles = new LinkedHashMap<>();
        // add the user defined styles
        allStyles.putAll(StyleDAO.getStyles(context, db));
        // and builtin styles
        allStyles.putAll(Builtin.getStyles(context));

        // filter, so this list only has the preferred ones.
        final Map<String, BooklistStyle> styles =
                MenuOrder.filterPreferredStyles(context, allStyles);

        // but if we want all, add the missing styles to the end of the list
        if (all && !styles.equals(allStyles)) {
            for (BooklistStyle style : allStyles.values()) {
                if (!styles.containsKey(style.getUuid())) {
                    styles.put(style.getUuid(), style);
                }
            }
        }
        return styles;
    }

    /**
     * store the current style as the global default one.
     *
     * @param preferences Global preferences
     * @param uuid        style to set
     */
    public static void setDefault(@NonNull final SharedPreferences preferences,
                                  @NonNull final String uuid) {
        preferences.edit().putString(PREF_BL_STYLE_CURRENT_DEFAULT, uuid).apply();
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
        // sanity check
        if (mUuid.isEmpty()) {
            throw new IllegalArgumentException(ErrorMsg.EMPTY_UUID);
        }

        //TODO: revisit... this is to complicated/inefficient.
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();

        parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);

        BooklistStyle clone = new BooklistStyle(context, getLabel(context), parcel);
        parcel.recycle();

        return clone;
    }

    /**
     * Only ever init the Preferences if you have a valid UUID.
     *
     * @param context       Current context
     * @param isUserDefined flag
     */
    private void initPrefs(@NonNull final Context context,
                           final boolean isUserDefined) {

        final SharedPreferences globalSharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        if (!mUuid.isEmpty()) {
            mStylePrefs = context.getSharedPreferences(mUuid, Context.MODE_PRIVATE);
        } else {
            mStylePrefs = globalSharedPreferences;
        }

        mName = new PString(mStylePrefs, isUserDefined, pk_name);

        mIsPreferred = new PBoolean(mStylePrefs, isUserDefined, pk_is_preferred);

        mExpansionLevel = new PInteger(mStylePrefs, isUserDefined, pk_levels_expansion, 1);

        mShowHeaderInfo = new PBitmask(mStylePrefs, isUserDefined, pk_header,
                                       HEADER_BITMASK_ALL, HEADER_BITMASK_ALL);

        mGroupRowScale = new PBoolean(mStylePrefs, isUserDefined, pk_scale_group_row, true);

        mTextStyle = new TextStyle(mStylePrefs, isUserDefined);

        mShowAuthorByGivenName = new PBoolean(mStylePrefs, isUserDefined,
                                              Prefs.pk_show_author_name_given_first);

        mSortAuthorByGivenName = new PBoolean(mStylePrefs, isUserDefined,
                                              Prefs.pk_sort_author_name_given_first);

        // all groups in this style
        mGroups = new Groups(context, this);
        // all filters
        mFilters = new Filters(mStylePrefs, isUserDefined);
        // all optional details for books.
        mListScreenBookFields = new ListScreenBookFields(mStylePrefs, isUserDefined);
        mDetailScreenBookFields = new DetailScreenBookFields(context, mStylePrefs, isUserDefined);
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

        mName.writeToParcel(dest);

        mIsPreferred.writeToParcel(dest);
        mExpansionLevel.writeToParcel(dest);
        mGroupRowScale.writeToParcel(dest);
        mTextStyle.writeToParcel(dest);
        mShowHeaderInfo.writeToParcel(dest);
        mShowAuthorByGivenName.writeToParcel(dest);
        mSortAuthorByGivenName.writeToParcel(dest);

        mGroups.writeToParcel(dest);
        mFilters.writeToParcel(dest);

        mListScreenBookFields.writeToParcel(dest);
        mDetailScreenBookFields.writeToParcel(dest);
    }

    /**
     * Get the UUID for this style.
     *
     * @return the UUID
     */
    @NonNull
    public String getUuid() {
        return mUuid;
    }

    @NonNull
    SharedPreferences getStyleSharedPreferences() {
        return mStylePrefs;
    }

    /**
     * Convenience/clarity method: check if this style represents global settings.
     *
     * @return {@code true} if global
     */
    public boolean isGlobal() {
        return mUuid.isEmpty();
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
    @NonNull
    public String getLabel(@NonNull final Context context) {
        if (mNameResId != 0) {
            return context.getString(mNameResId);
        } else {
            return mName.getValue(context);
        }
    }

    /**
     * Check if this is a user defined style.
     *
     * @return flag
     */
    public boolean isUserDefined() {
        return mNameResId == 0;
    }


    /**
     * Check if this is a user preferred style.
     *
     * @param context Current context
     *
     * @return flag
     */
    public boolean isPreferred(@NonNull final Context context) {
        return mIsPreferred.isTrue(context);
    }

    /**
     * Set this style as a user preferred style.
     *
     * @param isPreferred flag
     */
    public void setPreferred(final boolean isPreferred) {
        mIsPreferred.set(isPreferred);
    }


    /**
     * Get all of the preferences of this Style and its groups/filters.
     *
     * @param all {@code false} for only the 'flat' Preferences
     *            {@code true} add also the groups/filters...
     *
     * @return unordered map with all preferences for this style
     */
    @NonNull
    public Map<String, PPref> getPreferences(final boolean all) {
        final Map<String, PPref> map = new HashMap<>();
        map.put(mName.getKey(), mName);

        map.put(mIsPreferred.getKey(), mIsPreferred);
        map.put(mExpansionLevel.getKey(), mExpansionLevel);
        map.put(mGroupRowScale.getKey(), mGroupRowScale);
        map.put(mShowHeaderInfo.getKey(), mShowHeaderInfo);

        map.put(mShowAuthorByGivenName.getKey(), mShowAuthorByGivenName);
        map.put(mSortAuthorByGivenName.getKey(), mSortAuthorByGivenName);

        map.put(mGroups.getKey(), mGroups);

        mTextStyle.addToMap(map);

        mListScreenBookFields.addToMap(map);
        mDetailScreenBookFields.addToMap(map);

        if (all) {
            mGroups.addToMap(map);
            mFilters.addToMap(map);
        }
        return map;
    }

    /**
     * update the preferences of this style based on the values of the passed preferences.
     * Preferences we don't have will be not be added.
     *
     * @param context  Current context
     * @param newPrefs to apply
     */
    public void updatePreferences(@NonNull final Context context,
                                  @NonNull final Map<String, PPref> newPrefs) {
        final SharedPreferences.Editor ed = mStylePrefs.edit();
        updatePreferences(context, ed, newPrefs);
        ed.apply();
    }

    /**
     * update the preferences of this style based on the values of the passed preferences.
     * Preferences we don't have will be not be added.
     *
     * @param context  Current context
     * @param newPrefs to apply
     */
    private void updatePreferences(@NonNull final Context context,
                                   @NonNull final SharedPreferences.Editor ed,
                                   @NonNull final Map<String, PPref> newPrefs) {
        final Map<String, PPref> currentPreferences = getPreferences(true);

        for (PPref p : newPrefs.values()) {
            // do we have this Preference ?
            final PPref ourPPref = currentPreferences.get(p.getKey());
            if (ourPPref != null) {
                // if we do, update our value
                //noinspection unchecked
                ourPPref.set(ed, p.getValue(context));
            }
        }
    }


    /**
     * Check if the style wants the specified header to be displayed.
     *
     * @param context    Current context
     * @param headerMask to check
     *
     * @return {@code true} if the header should be shown
     */
    public boolean isShowHeader(@NonNull final Context context,
                                @ListHeaderOption final int headerMask) {
        return (mShowHeaderInfo.getValue(context) & headerMask) != 0;
    }

    /**
     * Get the default visible level for the list.
     * i.e. the level which will be visible but not expanded.
     * i.o.w. the top-level where items above will be expanded/visible,
     * and items below will be hidden.
     *
     * @param context Current context
     *
     * @return level
     */
    @IntRange(from = 1)
    public int getTopLevel(@NonNull final Context context) {
        // limit to the amount of groups!
        int level = mExpansionLevel.getValue(context);
        if (level > mGroups.size()) {
            level = mGroups.size();
        }
        return level;
    }

    /**
     * Get the group row <strong>height</strong> to be applied to
     * the {@link android.view.ViewGroup.LayoutParams}.
     *
     * @param context Current context
     *
     * @return group row height value in pixels
     */
    int getGroupRowHeight(@NonNull final Context context) {
        if (mGroupRowScale.getValue(context)) {
            return AttrUtils.getDimen(context, R.attr.listPreferredItemHeightSmall);
        } else {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
    }


    /**
     * Get the text style.
     *
     * @return TextStyle
     */
    public TextStyle getTextStyle() {
        return mTextStyle;
    }

    /**
     * Get the Groups style object.
     *
     * @return the Groups object
     */
    @NonNull
    public Groups getGroups() {
        return mGroups;
    }

    /**
     * Get the Filters style object.
     *
     * @return the Filters object
     */
    @NonNull
    public Filters getFilters() {
        return mFilters;
    }

    /**
     * Get the ListScreenBookFields style object.
     *
     * @return the ListScreenBookFields object
     */
    @NonNull
    public ListScreenBookFields getListScreenBookFields() {
        return mListScreenBookFields;
    }

    /**
     * Get the DetailScreenBookFields style object.
     *
     * @return the DetailScreenBookFields object
     */
    @NonNull
    public DetailScreenBookFields getDetailScreenBookFields() {
        return mDetailScreenBookFields;
    }


    /**
     * Wrapper that gets the preference from {@link BooklistGroup.SeriesBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Series.
     */
    boolean isShowBooksUnderEachSeries(@NonNull final Context context) {
        final BooklistGroup.SeriesBooklistGroup group = (BooklistGroup.SeriesBooklistGroup)
                (mGroups.getGroupById(BooklistGroup.SERIES));
        if (group != null) {
            return group.showBooksUnderEach(context);
        } else {
            // return the global default.
            return BooklistGroup.SeriesBooklistGroup.showBooksUnderEachDefault(context);
        }
    }

    /**
     * Wrapper that gets the preference from {@link BooklistGroup.PublisherBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Publishers.
     */
    boolean isShowBooksUnderEachPublisher(@NonNull final Context context) {
        final BooklistGroup.PublisherBooklistGroup group = (BooklistGroup.PublisherBooklistGroup)
                (mGroups.getGroupById(BooklistGroup.PUBLISHER));
        if (group != null) {
            return group.showBooksUnderEach(context);
        } else {
            // return the global default.
            return BooklistGroup.PublisherBooklistGroup.showBooksUnderEachDefault(context);
        }
    }

    /**
     * Wrapper that gets the preference from {@link BooklistGroup.AuthorBooklistGroup}
     * if we have it this group, or from the global default if not.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Authors
     */
    boolean isShowBooksUnderEachAuthor(@NonNull final Context context) {
        final BooklistGroup.AuthorBooklistGroup group = (BooklistGroup.AuthorBooklistGroup)
                (mGroups.getGroupById(BooklistGroup.AUTHOR));
        if (group != null) {
            return group.showBooksUnderEach(context);
        } else {
            // return the global default.
            return BooklistGroup.AuthorBooklistGroup.showBooksUnderEachDefault(context);
        }
    }

    /**
     * Whether the user prefers the Author names displayed by Given names, or by Family name first.
     *
     * @param context Current context
     *
     * @return {@code true} when Given names should come first
     */
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
    boolean isSortAuthorByGivenName(@NonNull final Context context) {
        return mSortAuthorByGivenName.isTrue(context);
    }

    /**
     * Wrapper that gets the getPrimaryType flag from the
     * {@link BooklistGroup.AuthorBooklistGroup} if we have it, or from the global default.
     *
     * @param context Current context
     *
     * @return the type of author we consider the primary author
     */
    int getPrimaryAuthorType(@NonNull final Context context) {
        final BooklistGroup.AuthorBooklistGroup group = (BooklistGroup.AuthorBooklistGroup)
                (mGroups.getGroupById(BooklistGroup.AUTHOR));
        if (group != null) {
            return group.getPrimaryType(context);
        } else {
            // return the global default.
            return BooklistGroup.AuthorBooklistGroup.getPrimaryTypeGlobalDefault(context);
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
               + "id=" + mId
               + "\nuuid=`" + mUuid + '`'
               + "\nmNameResId=`" + (mNameResId != 0 ? App.getAppContext().getString(mNameResId)
                                                     : 0) + '`'
               + "\nmName=`" + mName + '`'
               + "\nmIsPreferred=" + mIsPreferred
               + "\nmExpansionLevel=" + mExpansionLevel

               + "\nmTextStyle=" + mTextStyle
               + "\nmGroupRowScale=" + mGroupRowScale

               + "\nmShowHeaderInfo=" + mShowHeaderInfo

               + "\nmShowAuthorByGivenNameFirst=" + mShowAuthorByGivenName
               + "\nmSortAuthorByGivenNameFirst=" + mSortAuthorByGivenName

               + "\nmStyleGroups=" + mGroups
               + "\nmFilters=\n" + mFilters
               + "\nmListScreenBookFields=" + mListScreenBookFields
               + "\nmDetailScreenBookFields=" + mDetailScreenBookFields
               + '}';
    }

    @IntDef(flag = true, value = {HEADER_SHOW_BOOK_COUNT,
                                  HEADER_SHOW_STYLE_NAME,
                                  HEADER_SHOW_FILTER})
    @Retention(RetentionPolicy.SOURCE)
    private @interface ListHeaderOption {

    }

    @IntDef({IMAGE_SCALE_0_NOT_DISPLAYED,
             IMAGE_SCALE_1_VERY_SMALL, IMAGE_SCALE_2_SMALL, IMAGE_SCALE_3_MEDIUM,
             IMAGE_SCALE_4_LARGE, IMAGE_SCALE_5_VERY_LARGE, IMAGE_SCALE_6_MAX})
    @Retention(RetentionPolicy.SOURCE)
    @interface CoverScale {

    }


    /**
     * Encapsulate the list of {@code BooklistGroup} with backend storage in a preference,
     * and all related data/logic.
     */
    public static class Groups
            extends PIntList {

        /** Style group preferences. */
        public static final String pk_style_groups = "style.booklist.groups";

        /** All groups; ordered. Reminder: the underlying PIntList is only storing the id. */
        private final Map<Integer, BooklistGroup> mGroupMap = new LinkedHashMap<>();

        /**
         * Constructor.
         *
         * @param context Current context
         * @param style   the style
         */
        Groups(@NonNull final Context context,
               @NonNull final BooklistStyle style) {
            super(style.getStyleSharedPreferences(), style.isUserDefined(), pk_style_groups);

            // load the group ID's from the SharedPreference and populates the Group object list.
            mGroupMap.clear();
            for (@BooklistGroup.Id int id : getValue(context)) {
                mGroupMap.put(id, BooklistGroup.newInstance(context, id, style));
            }
        }

        /**
         * Get all groups assigned to this style.
         *
         * @return group list
         */
        @NonNull
        public ArrayList<BooklistGroup> getGroupList() {
            return new ArrayList<>(mGroupMap.values());
        }

        /**
         * Check if the given group is present, using the given group id.
         *
         * @param id group id
         *
         * @return {@code true} if present
         */
        public boolean contains(@BooklistGroup.Id final int id) {
            return mGroupMap.containsKey(id);
        }

        /**
         * Get the group for the given id.
         * <p>
         * Dev note: we want this call to ALWAYS return a valid group.
         * We had (have?) a bug in the past:
         * <p>
         * at BooklistStyle.getGroupById(BooklistStyle.java:1152)
         * at BooklistAdapter.onCreateViewHolder(BooklistAdapter.java:247)
         * at BooklistAdapter.onCreateViewHolder(BooklistAdapter.java:96)
         * <p>
         * the STYLE is the wrong one...
         * 2020-09-11: java.lang.IllegalArgumentException: Group was NULL: id=14
         * 14 is READ_YEAR
         * but the style dumped was "Books - Author, Series"
         * so it's the STYLE itself which was wrong...
         * TEST: We're using newListCursor everywhere now.
         * Seems 'get' -> existing cursor, with link to builder with link to style
         * while elsewhere we already have a new builder/style.
         *
         * @param id to get
         *
         * @return group
         *
         * @throws IllegalArgumentException on bug
         */
        @NonNull
        BooklistGroup getGroupByIdOrCrash(final int id) {
            final BooklistGroup group = mGroupMap.get(id);
            if (group == null) {
                // Don't use a Objects.requireNonNull() ... message is evaluated before null test.
                throw new IllegalArgumentException(
                        "Group was NULL: id=" + id + ", " + this.toString());
            }
            return group;
        }

        /**
         * Get the group for the given id.
         *
         * @param id to get
         *
         * @return group, or {@code null} if not present.
         */
        @Nullable
        BooklistGroup getGroupById(@BooklistGroup.Id final int id) {
            return mGroupMap.get(id);
        }

        /**
         * Get the group at the given level.
         *
         * @param level to get
         *
         * @return group
         */
        @NonNull
        BooklistGroup getGroupByLevel(@IntRange(from = 1) final int level) {
            // can throw IndexOutOfBoundsException only if we have a bug passing an illegal level.
            return (BooklistGroup) mGroupMap.values().toArray()[level - 1];
        }

        /**
         * Get the number of groups in this style.
         *
         * @return the number of groups
         */
        public int size() {
            return mGroupMap.size();
        }

        /**
         * Convenience method for use in the Preferences screen.
         * Get the summary text for the in-use group names.
         *
         * @param context Current context
         *
         * @return summary text
         */
        @NonNull
        public String getSummaryText(@NonNull final Context context) {
            return mGroupMap.values().stream()
                            .map(element -> element.getLabel(context))
                            .collect(Collectors.joining(", "));
        }

        /**
         * Add a new group to the end of the list.
         *
         * @param group to add
         */
        public void add(@NonNull final BooklistGroup group) {
            mGroupMap.put(group.getId(), group);
            super.add(group.getId());
        }

        @Override
        public void add(@NonNull final Integer element) {
            // we need the actual group to add it to mGroups
            throw new IllegalStateException("use add(BooklistGroup) instead");
        }

        /**
         * Remove the given group.
         *
         * @param id of group to remove
         */
        @Override
        public void remove(@BooklistGroup.Id @NonNull final Integer id) {
            mGroupMap.remove(id);
            super.remove(id);
        }

        /**
         * Add all entries to the given map.
         *
         * @param map to add to
         */
        void addToMap(@NonNull final Map<String, PPref> map) {
            // for each group used by the style, add its specific preferences to our list
            for (BooklistGroup group : getGroupList()) {
                map.putAll(group.getPreferences());
            }
        }

        /**
         * Set the <strong>value</strong> from the Parcel.
         *
         * @param in parcel to read from
         */
        @Override
        public void set(@NonNull final Parcel in) {
            mGroupMap.clear();
            super.clear();

            final List<BooklistGroup> list = new ArrayList<>();
            in.readList(list, getClass().getClassLoader());
            // (faster) equivalent of add(@NonNull final BooklistGroup group)
            // but split in adding the group and...
            for (BooklistGroup group : list) {
                mGroupMap.put(group.getId(), group);
            }
            // storing the ID's in SharedPreference.
            this.set(new ArrayList<>(mGroupMap.keySet()));
        }

        /**
         * Write the <strong>value</strong> to the Parcel.
         *
         * @param dest parcel to write to
         */
        @Override
        public void writeToParcel(@NonNull final Parcel dest) {
            dest.writeList(new ArrayList<>(mGroupMap.values()));
        }

        @Override
        @NonNull
        public String toString() {
            return "Groups{" + super.toString()
                   + "mGroups=" + mGroupMap
                   + '}';
        }
    }

    /**
     * Encapsulate Filters and all related data/logic.
     */
    public static class Filters {

        /** Booklist Filter - ListPreference. */
        static final String pk_filter_isbn = "style.booklist.filter.isbn";
        /** Booklist Filter - ListPreference. */
        static final String pk_filter_read = "style.booklist.filter.read";
        /** Booklist Filter - ListPreference. */
        static final String pk_filter_signed = "style.booklist.filter.signed";
        /** Booklist Filter - ListPreference. */
        static final String pk_filter_loaned = "style.booklist.filter.loaned";
        /** Booklist Filter - ListPreference. */
        static final String pk_filter_anthology = "style.booklist.filter.anthology";
        /** Booklist Filter - MultiSelectListPreference. */
        static final String pk_filter_editions = "style.booklist.filter.editions";

        /**
         * All filters in an <strong>ordered</strong> map.
         */
        private final Map<String, Filter<?>> mFilters = new LinkedHashMap<>();

        /**
         * Constructor.
         *
         * @param stylePrefs    the SharedPreferences for the style
         * @param isUserDefined flag
         */
        public Filters(@NonNull final SharedPreferences stylePrefs,
                       final boolean isUserDefined) {

            mFilters.put(pk_filter_read,
                         new BooleanFilter(stylePrefs, isUserDefined, R.string.lbl_read,
                                           pk_filter_read,
                                           DBDefinitions.TBL_BOOKS,
                                           DBDefinitions.KEY_READ));

            mFilters.put(pk_filter_signed,
                         new BooleanFilter(stylePrefs, isUserDefined, R.string.lbl_signed,
                                           pk_filter_signed,
                                           DBDefinitions.TBL_BOOKS,
                                           DBDefinitions.KEY_SIGNED));

            mFilters.put(pk_filter_anthology,
                         new BooleanFilter(stylePrefs, isUserDefined, R.string.lbl_anthology,
                                           pk_filter_anthology,
                                           DBDefinitions.TBL_BOOKS,
                                           DBDefinitions.KEY_TOC_BITMASK));

            mFilters.put(pk_filter_loaned,
                         new BooleanFilter(stylePrefs, isUserDefined, R.string.lbl_loaned,
                                           pk_filter_loaned,
                                           DBDefinitions.TBL_BOOKS,
                                           DBDefinitions.KEY_LOANEE));

            mFilters.put(pk_filter_editions,
                         new BitmaskFilter(stylePrefs, isUserDefined, R.string.lbl_edition,
                                           pk_filter_editions, 0, Book.Edition.BITMASK_ALL,
                                           DBDefinitions.TBL_BOOKS,
                                           DBDefinitions.KEY_EDITION_BITMASK));

            mFilters.put(pk_filter_isbn,
                         new NotEmptyFilter(stylePrefs, isUserDefined, R.string.lbl_isbn,
                                            pk_filter_isbn,
                                            DBDefinitions.TBL_BOOKS,
                                            DBDefinitions.KEY_ISBN));
        }

        /**
         * Get the list of <strong>active and non-active</strong> Filters.
         *
         * @return list
         */
        @NonNull
        public Collection<Filter<?>> getAll() {
            return mFilters.values();
        }

        /**
         * Get the list of <strong>active</strong> Filters.
         *
         * @param context Current context
         *
         * @return list
         */
        @NonNull
        public Collection<Filter<?>> getActiveFilters(@NonNull final Context context) {
            return mFilters.values()
                           .stream()
                           .filter(f -> f.isActive(context))
                           .collect(Collectors.toList());
        }

        /**
         * Used by built-in styles only. Set by user via preferences screen.
         */
        @SuppressWarnings("SameParameterValue")
        private void setFilter(@Key @NonNull final String key,
                               final boolean value) {
            //noinspection ConstantConditions
            ((BooleanFilter) mFilters.get(key)).set(value);
        }


        @NonNull
        private List<String> getLabels(@NonNull final Context context,
                                       final boolean all) {

            return mFilters.values().stream()
                           .filter(f -> f.isActive(context) || all)
                           .map(f -> f.getLabel(context))
                           .sorted()
                           .collect(Collectors.toList());
        }

        /**
         * Convenience method for use in the Preferences screen.
         * Get the list of in-use filter names in a human readable format.
         *
         * @param context Current context
         * @param all     {@code true} to get all, {@code false} for only the active filters
         *
         * @return summary text
         */
        public String getSummaryText(@NonNull final Context context,
                                     final boolean all) {

            final List<String> labels = getLabels(context, all);
            if (labels.isEmpty()) {
                return context.getString(R.string.none);
            } else {
                return TextUtils.join(", ", labels);
            }
        }

        /**
         * Add all filters (both active and non-active) to the given map.
         *
         * @param map to add to
         */
        void addToMap(@NonNull final Map<String, PPref> map) {
            for (Filter<?> filter : mFilters.values()) {
                map.put(filter.getKey(), (PPref) filter);
            }
        }

        /**
         * Set the <strong>value</strong> from the Parcel.
         *
         * @param in parcel to read from
         */
        public void set(@NonNull final Parcel in) {
            mFilters.clear();
            // the collection is ordered, so we don't need the keys.
            for (Filter<?> filter : mFilters.values()) {
                filter.set(in);
            }
        }

        /**
         * Write the <strong>value</strong> to the Parcel.
         *
         * @param dest parcel to write to
         */
        public void writeToParcel(@NonNull final Parcel dest) {
            // the collection is ordered, so we don't write the keys.
            for (Filter<?> filter : mFilters.values()) {
                filter.writeToParcel(dest);
            }
        }

        @NonNull
        @Override
        public String toString() {
            return "Filters{"
                   + "mFilters=" + mFilters
                   + '}';
        }

        @StringDef({pk_filter_isbn,
                    pk_filter_read,
                    pk_filter_signed,
                    pk_filter_loaned,
                    pk_filter_anthology,
                    pk_filter_editions})
        @Retention(RetentionPolicy.SOURCE)
        @interface Key {

        }
    }

    /**
     * Encapsulate Font Scale and all related data/logic.
     */
    public static class TextStyle {

        /** <strong>ALL</strong> text. */
        public static final String pk_scale_font = "style.booklist.scale.font";

        /**
         * Text Scaling.
         * NEVER change these values, they get stored in preferences.
         * The book title in the list is by default 'medium' (see styles.xml)
         * Other elements are always 1 size 'less' than the title.
         */
        static final int FONT_SCALE_0_VERY_SMALL = 0;
        /** Text Scaling. */
        static final int FONT_SCALE_1_SMALL = 1;
        /** Text Scaling. This is the default. */
        static final int FONT_SCALE_2_MEDIUM = 2;
        /** Text Scaling. */
        static final int FONT_SCALE_3_LARGE = 3;
        /** Text Scaling. */
        static final int FONT_SCALE_4_VERY_LARGE = 4;

        /** Relative size of list text/images. */
        private final PInteger mFontScale;

        /**
         * Constructor.
         *
         * @param stylePrefs    the SharedPreferences for the style
         * @param isUserDefined flag
         */
        TextStyle(@NonNull final SharedPreferences stylePrefs,
                  final boolean isUserDefined) {
            mFontScale = new PInteger(stylePrefs, isUserDefined,
                                      pk_scale_font, FONT_SCALE_2_MEDIUM);
        }

        /**
         * Get the scaling factor to apply to the View padding if text is scaled.
         *
         * @param context Current context
         *
         * @return scale factor
         */
        float getPaddingFactor(@NonNull final Context context) {
            final TypedArray ta = context
                    .getResources().obtainTypedArray(R.array.bob_text_padding_in_percent);
            try {
                return ta.getFloat(mFontScale.getValue(context), FONT_SCALE_2_MEDIUM);
            } finally {
                ta.recycle();
            }
        }

        /**
         * Get the text <strong>size in SP units</strong> to apply.
         *
         * @param context Current context
         *
         * @return sp units
         */
        float getFontSizeInSpUnits(@NonNull final Context context) {
            final TypedArray ta = context
                    .getResources().obtainTypedArray(R.array.bob_text_size_in_sp);
            try {
                return ta.getFloat(mFontScale.getValue(context), FONT_SCALE_2_MEDIUM);
            } finally {
                ta.recycle();
            }
        }

        /**
         * Convenience method for use in the Preferences screen.
         * Get the summary text for the scale factor to be used.
         *
         * @param context Current context
         *
         * @return summary text
         */
        public String getFontScaleSummaryText(@NonNull final Context context) {
            return context.getResources().getStringArray(R.array.pe_bob_text_scale)
                    [mFontScale.getValue(context)];
        }

        /**
         * Check if the current setting is the default.
         *
         * @param context Current context
         *
         * @return {@code true} if this is the default
         */
        boolean isDefaultScale(@NonNull final Context context) {
            return mFontScale.getValue(context) == FONT_SCALE_2_MEDIUM;
        }

        /**
         * Used by built-in styles only. Set by user via preferences screen.
         *
         * @param scale id
         */
        @SuppressWarnings("SameParameterValue")
        void setFontScale(@FontScale final int scale) {
            mFontScale.set(scale);
        }

        /**
         * Add all entries to the given map.
         *
         * @param map to add to
         */
        void addToMap(@NonNull final Map<String, PPref> map) {
            map.put(mFontScale.getKey(), mFontScale);
        }

        /**
         * Set the <strong>value</strong> from the Parcel.
         *
         * @param in parcel to read from
         */
        void set(@NonNull final Parcel in) {
            mFontScale.set(in);
        }

        /**
         * Write the <strong>value</strong> to the Parcel.
         *
         * @param dest parcel to write to
         */
        void writeToParcel(@NonNull final Parcel dest) {
            mFontScale.writeToParcel(dest);
        }

        @NonNull
        @Override
        public String toString() {
            return "TextStyle{"
                   + "mFontScale=" + mFontScale
                   + '}';
        }

        @IntDef({FONT_SCALE_0_VERY_SMALL,
                 FONT_SCALE_1_SMALL,
                 FONT_SCALE_2_MEDIUM,
                 FONT_SCALE_3_LARGE,
                 FONT_SCALE_4_VERY_LARGE})
        @Retention(RetentionPolicy.SOURCE)
        @interface FontScale {

        }
    }


    public abstract static class BookFields {

        /**
         * All fields (domains) that are optionally shown on the Book level,
         * in an <strong>ordered</strong> map.
         */
        final Map<String, PBoolean> mFields = new LinkedHashMap<>();

        /**
         * Check if the given book-detail field should be displayed.
         *
         * @param context     Current context
         * @param preferences the <strong>GLOBAL</strong> preferences
         * @param key         to check
         *
         * @return {@code true} if in use
         */
        public boolean isShowField(@NonNull final Context context,
                                   @NonNull final SharedPreferences preferences,
                                   @ListScreenBookFields.Key @NonNull final String key) {

            // Disabled in the Global style overrules the local style
            if (!preferences.getBoolean(key, true)) {
                return false;
            }

            if (mFields.containsKey(key)) {
                final PBoolean value = mFields.get(key);
                return value != null && value.isTrue(context);
            }
            return false;
        }

        /**
         * Used by built-in styles only. Set by user via preferences screen.
         *
         * @param key  for the field
         * @param show value to set
         */
        void setShowField(@ListScreenBookFields.Key @NonNull final String key,
                          final boolean show) {
            //noinspection ConstantConditions
            mFields.get(key).set(show);
        }

        void addToMap(@NonNull final Map<String, PPref> map) {
            for (PBoolean field : mFields.values()) {
                map.put(field.getKey(), field);
            }
        }

        /**
         * Set the <strong>value</strong> from the Parcel.
         *
         * @param in parcel to read from
         */
        public void set(@NonNull final Parcel in) {
            mFields.clear();
            // the collection is ordered, so we don't need the keys.
            for (PBoolean field : mFields.values()) {
                field.set(in);
            }
        }

        /**
         * Write the <strong>value</strong> to the Parcel.
         *
         * @param dest parcel to write to
         */
        public void writeToParcel(@NonNull final Parcel dest) {
            // the collection is ordered, so we don't write the keys.
            for (PBoolean field : mFields.values()) {
                field.writeToParcel(dest);
            }
        }
    }

    /** Encapsulate the Book fields which can be shown on the Book-list screen. */
    public static class ListScreenBookFields
            extends BookFields {

        /** Show the cover image (front only) for each book on the list screen. */
        public static final String pk_covers = "style.booklist.show.thumbnails";
        /** Show author for each book. */
        public static final String pk_author = "style.booklist.show.author";
        /** Show publisher for each book. */
        public static final String pk_publisher = "style.booklist.show.publisher";
        /** Show publication date for each book. */
        public static final String pk_pub_date = "style.booklist.show.publication.date";
        /** Show format for each book. */
        public static final String pk_format = "style.booklist.show.format";
        /** Show location for each book. */
        public static final String pk_location = "style.booklist.show.location";
        /** Show rating for each book. */
        public static final String pk_rating = "style.booklist.show.rating";
        /** Show list of bookshelves for each book. */
        public static final String pk_bookshelves = "style.booklist.show.bookshelves";
        /** Show ISBN for each book. */
        @SuppressWarnings("WeakerAccess")
        public static final String pk_isbn = "style.booklist.show.isbn";

        /** Thumbnails in the list view. Only used when {@link #pk_covers} is set. */
        public static final String pk_cover_scale = "style.booklist.scale.thumbnails";

        /** Scale factor to apply for thumbnails. */
        private final PInteger mThumbnailScale;

        /**
         * Constructor.
         *
         * @param stylePrefs    the SharedPreferences for the style
         * @param isUserDefined flag
         */
        ListScreenBookFields(@NonNull final SharedPreferences stylePrefs,
                             final boolean isUserDefined) {

            mThumbnailScale = new PInteger(stylePrefs, isUserDefined, pk_cover_scale,
                                           IMAGE_SCALE_DEFAULT);

            mFields.put(pk_covers,
                        new PBoolean(stylePrefs, isUserDefined, pk_covers, true));

            mFields.put(pk_author,
                        new PBoolean(stylePrefs, isUserDefined, pk_author));

            mFields.put(pk_publisher,
                        new PBoolean(stylePrefs, isUserDefined, pk_publisher));

            mFields.put(pk_pub_date,
                        new PBoolean(stylePrefs, isUserDefined, pk_pub_date));

            mFields.put(pk_isbn,
                        new PBoolean(stylePrefs, isUserDefined, pk_isbn));

            mFields.put(pk_format,
                        new PBoolean(stylePrefs, isUserDefined, pk_format));

            mFields.put(pk_location,
                        new PBoolean(stylePrefs, isUserDefined, pk_location));

            mFields.put(pk_rating,
                        new PBoolean(stylePrefs, isUserDefined, pk_rating));

            mFields.put(pk_bookshelves,
                        new PBoolean(stylePrefs, isUserDefined, pk_bookshelves));
        }

        /**
         * Get the scale <strong>identifier</strong> for the thumbnail size preferred.
         *
         * @param context Current context
         *
         * @return scale id
         */
        @CoverScale
        int getCoverScale(@NonNull final Context context) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            if (isShowField(context, prefs, ListScreenBookFields.pk_covers)) {
                return mThumbnailScale.getValue(context);
            }
            return IMAGE_SCALE_0_NOT_DISPLAYED;
        }

        /**
         * Convenience method for use in the Preferences screen.
         * Get the summary text for the cover scale factor.
         *
         * @param context Current context
         *
         * @return summary text
         */
        public String getCoverScaleSummaryText(@NonNull final Context context) {
            final int scale = getCoverScale(context);
            return context.getResources().getStringArray(R.array.pe_bob_thumbnail_scale)[scale];
        }

        /**
         * Get the list of in-use book-detail-field names in a human readable format.
         * This is used to set the summary of the PreferenceScreen.
         * <p>
         * Dev. note: don't micro-optimize this method with a map which would use more memory...
         *
         * @param context Current context
         *
         * @return list of labels, can be empty, but never {@code null}
         */
        @NonNull
        private List<String> getLabels(@NonNull final Context context) {
            final List<String> labels = new ArrayList<>();

            //noinspection ConstantConditions
            if (mFields.get(pk_covers).isTrue(context)) {
                labels.add(context.getString(R.string.lbl_covers));
            }
            //noinspection ConstantConditions
            if (mFields.get(pk_author).isTrue(context)) {
                labels.add(context.getString(R.string.lbl_author));
            }
            //noinspection ConstantConditions
            if (mFields.get(pk_publisher).isTrue(context)) {
                labels.add(context.getString(R.string.lbl_publisher));
            }
            //noinspection ConstantConditions
            if (mFields.get(pk_pub_date).isTrue(context)) {
                labels.add(context.getString(R.string.lbl_date_published));
            }
            //noinspection ConstantConditions
            if (mFields.get(pk_isbn).isTrue(context)) {
                labels.add(context.getString(R.string.lbl_isbn));
            }
            //noinspection ConstantConditions
            if (mFields.get(pk_format).isTrue(context)) {
                labels.add(context.getString(R.string.lbl_format));
            }
            //noinspection ConstantConditions
            if (mFields.get(pk_location).isTrue(context)) {
                labels.add(context.getString(R.string.lbl_location));
            }
            //noinspection ConstantConditions
            if (mFields.get(pk_rating).isTrue(context)) {
                labels.add(context.getString(R.string.lbl_rating));
            }
            //noinspection ConstantConditions
            if (mFields.get(pk_bookshelves).isTrue(context)) {
                labels.add(context.getString(R.string.lbl_bookshelves_long));
            }

            Collections.sort(labels);
            return labels;
        }

        /**
         * Convenience method for use in the Preferences screen.
         * Get the summary text for the book fields to show in lists.
         *
         * @param context Current context
         *
         * @return summary text
         */
        public String getSummaryText(@NonNull final Context context) {
            final List<String> labels = getLabels(context);
            if (labels.isEmpty()) {
                return context.getString(R.string.none);
            } else {
                return TextUtils.join(", ", labels);
            }
        }

        /**
         * Add all filters (both active and non-active) to the given map.
         *
         * @param map to add to
         */
        void addToMap(@NonNull final Map<String, PPref> map) {
            super.addToMap(map);
            map.put(mThumbnailScale.getKey(), mThumbnailScale);
        }

        /**
         * Set the <strong>value</strong> from the Parcel.
         *
         * @param in parcel to read from
         */
        public void set(@NonNull final Parcel in) {
            super.set(in);
            mThumbnailScale.set(in);
        }

        /**
         * Write the <strong>value</strong> to the Parcel.
         *
         * @param dest parcel to write to
         */
        public void writeToParcel(@NonNull final Parcel dest) {
            super.writeToParcel(dest);
            mThumbnailScale.writeToParcel(dest);
        }

        @NonNull
        @Override
        public String toString() {
            return "ListScreenBookFields{"
                   + "mThumbnailScale=" + mThumbnailScale
                   + ", mFields=" + mFields
                   + '}';
        }

        @StringDef({pk_covers,
                    pk_author,
                    pk_publisher,
                    pk_pub_date,
                    pk_isbn,
                    pk_format,
                    pk_location,
                    pk_rating,
                    pk_bookshelves})
        @Retention(RetentionPolicy.SOURCE)
        @interface Key {

        }
    }

    /** Encapsulate the Book fields which can be shown on the Book-details screen. */
    public static class DetailScreenBookFields
            extends BookFields {

        /** Show the cover images (front/back) for each book on the details screen. */
        public static final String[] pk_cover = new String[]{
                "style.details.show.thumbnail.0",
                "style.details.show.thumbnail.1"
        };

        /**
         * Constructor.
         *
         * @param context       Current context
         * @param stylePrefs    the SharedPreferences for the style
         * @param isUserDefined flag
         */
        DetailScreenBookFields(final Context context,
                               @NonNull final SharedPreferences stylePrefs,
                               final boolean isUserDefined) {

            final SharedPreferences globalPrefs =
                    PreferenceManager.getDefaultSharedPreferences(context);

            for (int cIdx = 0; cIdx < 2; cIdx++) {
                mFields.put(pk_cover[cIdx],
                            new PBoolean(stylePrefs, isUserDefined, pk_cover[cIdx],
                                         DBDefinitions.isCoverUsed(globalPrefs, cIdx)));
            }
        }

        /**
         * Convenience method to check if a cover (front/back) should be
         * show on the <strong>details</strong> screen.
         *
         * @param context     Current context
         * @param preferences the <strong>GLOBAL</strong> preferences
         * @param cIdx        0..n image index
         *
         * @return {@code true} if in use
         */
        public boolean isShowCover(@NonNull final Context context,
                                   @NonNull final SharedPreferences preferences,
                                   @IntRange(from = 0, to = 1) final int cIdx) {
            return isShowField(context, preferences, pk_cover[cIdx]);
        }
    }


    /** Utility class encapsulating database access and internal in-memory cache list. */
    public static final class StyleDAO {

        /**
         * We keep a cache of User styles in memory as it's to costly to keep
         * re-creating {@link BooklistStyle} objects.
         * Pre-loaded on first access.
         * Re-loaded when the Locale changes.
         * <p>
         * Key: uuid of style.
         */
        private static final Map<String, BooklistStyle> S_USER_STYLES = new LinkedHashMap<>();

        private StyleDAO() {
        }

        public static void clearCache() {
            S_USER_STYLES.clear();
        }

        /**
         * Save the given style to the database / cached list.
         * <p>
         * if an insert fails, the style retains id==0.
         *
         * @param db    Database Access
         * @param style to save
         */
        public static void updateOrInsert(@NonNull final DAO db,
                                          @NonNull final BooklistStyle style) {

            if (!style.isUserDefined()) {
                throw new IllegalArgumentException("Builtin Style cannot be saved to database");
            }

            // sanity check
            if (style.getUuid().isEmpty()) {
                throw new IllegalArgumentException(ErrorMsg.EMPTY_UUID);
            }

            // check if the style already exists.
            final long existingId = db.getStyleIdByUuid(style.getUuid());
            if (existingId == 0) {
                if (db.insert(style) > 0) {
                    S_USER_STYLES.put(style.getUuid(), style);
                }
            } else {
                style.setId(existingId);
                S_USER_STYLES.put(style.getUuid(), style);
            }
        }

        /**
         * Update the given style.
         *
         * @param style to update
         */
        public static void update(@NonNull final BooklistStyle style) {
            if (style.getId() == 0 || !style.isUserDefined()) {
                throw new IllegalArgumentException("Only an existing user Style can be updated");
            }
            // Note there is no database access needed here
            S_USER_STYLES.put(style.getUuid(), style);
        }

        /**
         * Delete the given style from the database / cached list.
         *
         * @param context Current context
         * @param db      Database Access
         * @param style   to delete
         */
        public static void delete(@NonNull final Context context,
                                  @NonNull final DAO db,
                                  @NonNull final BooklistStyle style) {

            // cannot delete a builtin or a 'new' style(id==0)
            if (style.getId() == 0 || !style.isUserDefined()) {
                throw new IllegalArgumentException("Builtin Style cannot be deleted");
            }
            // sanity check, cannot delete the global style settings.
            if (style.getUuid().isEmpty()) {
                throw new IllegalArgumentException("Global Style cannot be deleted");
            }

            S_USER_STYLES.remove(style.getUuid());
            db.delete(style);

            if (Build.VERSION.SDK_INT >= 24) {
                context.deleteSharedPreferences(style.getUuid());
            } else {
                context.getSharedPreferences(style.getUuid(), Context.MODE_PRIVATE)
                       .edit().clear().apply();
            }
        }

        public static void discard(@NonNull final Context context,
                                   @NonNull final BooklistStyle style) {
            // can ONLY discard a new style
            if (style.getId() != 0) {
                throw new IllegalArgumentException("Can only discard a new style");
            }
            if (Build.VERSION.SDK_INT >= 24) {
                context.deleteSharedPreferences(style.getUuid());
            } else {
                context.getSharedPreferences(style.getUuid(), Context.MODE_PRIVATE)
                       .edit().clear().apply();
            }
        }

        /**
         * Get the user-defined Styles from the database.
         *
         * @param context Current context
         * @param db      Database Access
         *
         * @return an ordered unmodifiableMap of BooklistStyle
         */
        @NonNull
        public static Map<String, BooklistStyle> getStyles(@NonNull final Context context,
                                                           @NonNull final DAO db) {
            if (S_USER_STYLES.isEmpty()) {
                S_USER_STYLES.putAll(db.getUserStyles(context));
            }
            return Collections.unmodifiableMap(S_USER_STYLES);
        }

        @Nullable
        static BooklistStyle getStyle(@NonNull final Context context,
                                      @NonNull final DAO db,
                                      @NonNull final String uuid) {
            return getStyles(context, db).get(uuid);
        }
    }

    /** Utility class encapsulating the menu-order methods. */
    public static final class MenuOrder {

        /**
         * Get the UUIDs of the preferred styles from user preferences.
         *
         * @param context Current context
         *
         * @return set of UUIDs
         */
        @NonNull
        private static Set<String> get(@NonNull final Context context) {
            final Set<String> uuidSet = new LinkedHashSet<>();
            final String itemsStr = PreferenceManager.getDefaultSharedPreferences(context)
                                                     .getString(PREF_BL_PREFERRED_STYLES, null);

            if (itemsStr != null && !itemsStr.isEmpty()) {
                final String[] entries = itemsStr.split(",");
                for (String entry : entries) {
                    if (entry != null && !entry.isEmpty()) {
                        uuidSet.add(entry);
                    }
                }
            }
            return uuidSet;
        }

        /**
         * Internal single-point of writing the preferred styles menu order.
         *
         * @param context Current context
         * @param uuidSet a set of style UUIDs
         */
        private static void set(@NonNull final Context context,
                                @NonNull final Collection<String> uuidSet) {
            PreferenceManager.getDefaultSharedPreferences(context)
                             .edit()
                             .putString(PREF_BL_PREFERRED_STYLES, TextUtils.join(",", uuidSet))
                             .apply();
        }

        /**
         * Save the preferred style menu list.
         * <p>
         * This list contains the ID's for user-defined *AND* system-styles.
         *
         * @param context Current context
         * @param styles  full list of preferred styles to save 'in order'
         */
        public static void save(@NonNull final Context context,
                                @NonNull final Collection<BooklistStyle> styles) {

            final Collection<String> list = styles
                    .stream()
                    .filter(style -> style.isPreferred(context))
                    .map(BooklistStyle::getUuid)
                    .collect(Collectors.toList());

            set(context, list);
        }

        /**
         * Add a style (its uuid) to the menu list of preferred styles.
         *
         * @param context Current context
         * @param style   to add.
         */
        public static void addPreferredStyle(@NonNull final Context context,
                                             @NonNull final BooklistStyle style) {
            final Set<String> list = get(context);
            list.add(style.getUuid());
            set(context, list);
        }

        /**
         * Filter the specified styles so it contains only the preferred styles.
         * If none were preferred, returns the incoming list.
         *
         * @param context   Current context
         * @param allStyles a list of styles
         *
         * @return ordered list.
         */
        @NonNull
        static Map<String, BooklistStyle> filterPreferredStyles(
                @NonNull final Context context,
                @NonNull final Map<String, BooklistStyle> allStyles) {

            final Map<String, BooklistStyle> resultingStyles = new LinkedHashMap<>();

            // first check the saved and ordered list
            for (String uuid : get(context)) {
                final BooklistStyle style = allStyles.get(uuid);
                if (style != null) {
                    // catch mismatches in any imported bad-data.
                    style.setPreferred(true);
                    // and add to results
                    resultingStyles.put(uuid, style);
                }
            }
            // now check for styles marked preferred, but not in the menu list,
            // again to catch mismatches in any imported bad-data.
            for (BooklistStyle style : allStyles.values()) {
                if (style.isPreferred(context) && !resultingStyles.containsKey(style.getUuid())) {
                    resultingStyles.put(style.getUuid(), style);
                }
            }

            // Return the ones we found.
            if (!resultingStyles.isEmpty()) {
                return resultingStyles;
            } else {
                // If none found, return what we were given.
                return allStyles;
            }
        }
    }

    /**
     * Collection of system-defined booklist styles.
     * <p>
     * The UUID's should never be changed.
     */
    public static final class Builtin {

        // NEWTHINGS: BooklistStyle. Make sure to update the max id when adding a style!
        // and make sure a row is added to the database styles table.
        // next max is -20
        public static final int MAX_ID = -19;

        /**
         * Note the hardcoded negative ID's.
         * These id/uuid's should NEVER be changed as they will
         * get stored in preferences and serialized. Take care not to add duplicates.
         */
        private static final int AUTHOR_THEN_SERIES_ID = -1;
        private static final String AUTHOR_THEN_SERIES_UUID
                = "6a82c4c0-48f1-4130-8a62-bbf478ffe184";

        private static final String DEFAULT_STYLE_UUID = AUTHOR_THEN_SERIES_UUID;

        private static final int UNREAD_AUTHOR_THEN_SERIES_ID = -2;
        private static final String UNREAD_AUTHOR_THEN_SERIES_UUID
                = "f479e979-c43f-4b0b-9c5b-6942964749df";

        private static final int COMPACT_ID = -3;
        private static final String COMPACT_UUID
                = "5e4c3137-a05f-4c4c-853a-bd1dacb6cd16";

        private static final int TITLE_FIRST_LETTER_ID = -4;
        private static final String TITLE_FIRST_LETTER_UUID
                = "16b4ecdf-edef-4bf2-a682-23f7230446c8";

        private static final int SERIES_ID = -5;
        private static final String SERIES_UUID
                = "ad55ebc3-f79d-4cc2-a27d-f06ff0bf2335";

        private static final int GENRE_ID = -6;
        private static final String GENRE_UUID
                = "edc5c178-60f0-40e7-9674-e08445b6c942";

        private static final int LENDING_ID = -7;
        private static final String LENDING_UUID
                = "e4f1c364-2cbe-467e-a0c1-3ae71bd56fa3";

        private static final int READ_AND_UNREAD_ID = -8;
        private static final String READ_AND_UNREAD_UUID
                = "e3678890-7785-4870-9213-333a68293a49";

        private static final int PUBLICATION_DATA_ID = -9;
        private static final String PUBLICATION_DATA_UUID
                = "182f5d3c-8fd7-4f3a-b5b0-0c93551d1796";

        private static final int DATE_ADDED_ID = -10;
        private static final String DATE_ADDED_UUID
                = "95d7afc0-a70a-4f1f-8d77-aa7ebc60e521";

        private static final int DATE_ACQUIRED_ID = -11;
        private static final String DATE_ACQUIRED_UUID
                = "b3255b1f-5b07-4b3e-9700-96c0f8f35a58";

        private static final int AUTHOR_AND_YEAR_ID = -12;
        private static final String AUTHOR_AND_YEAR_UUID
                = "7c9ad91e-df7c-415a-a205-cdfabff5465d";

        private static final int FORMAT_ID = -13;
        private static final String FORMAT_UUID
                = "bdc43f17-2a95-42ef-b0f8-c750ef920f28";

        private static final int DATE_READ_ID = -14;
        private static final String DATE_READ_UUID
                = "034fe547-879b-4fa0-997a-28d769ba5a84";

        private static final int LOCATION_ID = -15;
        private static final String LOCATION_UUID
                = "e21a90c9-5150-49ee-a204-0cab301fc5a1";

        private static final int LANGUAGE_ID = -16;
        private static final String LANGUAGE_UUID
                = "00379d95-6cb2-40e6-8c3b-f8278f34750a";

        private static final int RATING_ID = -17;
        private static final String RATING_UUID
                = "20a2ebdf-81a7-4eca-a3a9-7275062b907a";

        private static final int BOOKSHELF_ID = -18;
        private static final String BOOKSHELF_UUID
                = "999d383e-6e76-416a-86f9-960c729aa718";

        private static final int DATE_LAST_UPDATE_ID = -19;
        private static final String DATE_LAST_UPDATE_UUID
                = "427a0da5-0779-44b6-89e9-82772e5ad5ef";
        /**
         * Use the NEGATIVE builtin style id to get the UUID for it. Element 0 is not used.
         * NEVER change the order.
         */
        private static final String[] ID_UUID = {
                "",
                AUTHOR_THEN_SERIES_UUID,
                UNREAD_AUTHOR_THEN_SERIES_UUID,
                COMPACT_UUID,
                TITLE_FIRST_LETTER_UUID,
                SERIES_UUID,

                GENRE_UUID,
                LENDING_UUID,
                READ_AND_UNREAD_UUID,
                PUBLICATION_DATA_UUID,
                DATE_ADDED_UUID,

                DATE_ACQUIRED_UUID,
                AUTHOR_AND_YEAR_UUID,
                FORMAT_UUID,
                DATE_READ_UUID,
                LOCATION_UUID,

                LANGUAGE_UUID,
                RATING_UUID,
                BOOKSHELF_UUID,
                DATE_LAST_UPDATE_UUID,
                };
        /**
         * We keep a cache of Builtin styles in memory as it's to costly to keep
         * re-creating {@link BooklistStyle} objects.
         * Pre-loaded on first access.
         * Re-loaded when the Locale changes.
         * <p>
         * Key: uuid of style.
         */
        private static final Map<String, BooklistStyle> S_BUILTIN_STYLES = new LinkedHashMap<>();
        /**
         * Hardcoded initial/default style. Avoids having the create the full set of styles just
         * to load the default one. Created on first access in {@link #getDefault}.
         */
        private static BooklistStyle sDefaultStyle;

        private Builtin() {
        }

        /**
         * Get all builtin styles.
         *
         * @param context Current context
         *
         * @return an ordered unmodifiableMap of all builtin styles.
         */
        @SuppressWarnings("SameReturnValue")
        @NonNull
        static Map<String, BooklistStyle> getStyles(@NonNull final Context context) {

            if (S_BUILTIN_STYLES.isEmpty()) {
                create(context);
            }
            return Collections.unmodifiableMap(S_BUILTIN_STYLES);
        }

        @NonNull
        public static BooklistStyle getDefault(@NonNull final Context context) {
            if (sDefaultStyle == null) {
                sDefaultStyle = new BooklistStyle(context,
                                                  AUTHOR_THEN_SERIES_ID,
                                                  AUTHOR_THEN_SERIES_UUID,
                                                  R.string.style_builtin_author_series,
                                                  BooklistGroup.AUTHOR,
                                                  BooklistGroup.SERIES);
            }
            return sDefaultStyle;
        }

        @NonNull
        public static String getUuidById(final int id) {
            return ID_UUID[id];
        }

        @Nullable
        static BooklistStyle getStyle(@NonNull final Context context,
                                      @NonNull final String uuid) {
            return getStyles(context).get(uuid);
        }

        private static void create(@NonNull final Context context) {

            BooklistStyle style;

            // Author/Series
            style = getDefault(context);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Unread
            style = new BooklistStyle(context,
                                      UNREAD_AUTHOR_THEN_SERIES_ID,
                                      UNREAD_AUTHOR_THEN_SERIES_UUID,
                                      R.string.style_builtin_unread,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);
            style.getFilters().setFilter(Filters.pk_filter_read, false);

            // Compact
            style = new BooklistStyle(context,
                                      COMPACT_ID,
                                      COMPACT_UUID,
                                      R.string.style_builtin_compact,
                                      BooklistGroup.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);
            style.getTextStyle().setFontScale(TextStyle.FONT_SCALE_1_SMALL);
            style.getListScreenBookFields()
                 .setShowField(ListScreenBookFields.pk_covers, false);

            // Title
            style = new BooklistStyle(context,
                                      TITLE_FIRST_LETTER_ID,
                                      TITLE_FIRST_LETTER_UUID,
                                      R.string.style_builtin_first_letter_book_title,
                                      BooklistGroup.BOOK_TITLE_LETTER);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Series
            style = new BooklistStyle(context,
                                      SERIES_ID,
                                      SERIES_UUID,
                                      R.string.style_builtin_series,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Genre
            style = new BooklistStyle(context,
                                      GENRE_ID,
                                      GENRE_UUID,
                                      R.string.style_builtin_genre,
                                      BooklistGroup.GENRE,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Lending
            style = new BooklistStyle(context,
                                      LENDING_ID,
                                      LENDING_UUID,
                                      R.string.style_builtin_loaned,
                                      BooklistGroup.ON_LOAN,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Read & Unread
            style = new BooklistStyle(context,
                                      READ_AND_UNREAD_ID,
                                      READ_AND_UNREAD_UUID,
                                      R.string.style_builtin_read_and_unread,
                                      BooklistGroup.READ_STATUS,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Publication date
            style = new BooklistStyle(context,
                                      PUBLICATION_DATA_ID,
                                      PUBLICATION_DATA_UUID,
                                      R.string.style_builtin_publication_date,
                                      BooklistGroup.DATE_PUBLISHED_YEAR,
                                      BooklistGroup.DATE_PUBLISHED_MONTH,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Added date
            style = new BooklistStyle(context,
                                      DATE_ADDED_ID,
                                      DATE_ADDED_UUID,
                                      R.string.style_builtin_added_date,
                                      BooklistGroup.DATE_ADDED_YEAR,
                                      BooklistGroup.DATE_ADDED_MONTH,
                                      BooklistGroup.DATE_ADDED_DAY,
                                      BooklistGroup.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Acquired date
            style = new BooklistStyle(context,
                                      DATE_ACQUIRED_ID,
                                      DATE_ACQUIRED_UUID,
                                      R.string.style_builtin_acquired_date,
                                      BooklistGroup.DATE_ACQUIRED_YEAR,
                                      BooklistGroup.DATE_ACQUIRED_MONTH,
                                      BooklistGroup.DATE_ACQUIRED_DAY,
                                      BooklistGroup.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Author/Publication date
            style = new BooklistStyle(context,
                                      AUTHOR_AND_YEAR_ID,
                                      AUTHOR_AND_YEAR_UUID,
                                      R.string.style_builtin_author_year,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.DATE_PUBLISHED_YEAR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Format
            style = new BooklistStyle(context,
                                      FORMAT_ID,
                                      FORMAT_UUID,
                                      R.string.style_builtin_format,
                                      BooklistGroup.FORMAT);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Read date
            style = new BooklistStyle(context,
                                      DATE_READ_ID,
                                      DATE_READ_UUID,
                                      R.string.style_builtin_read_date,
                                      BooklistGroup.DATE_READ_YEAR,
                                      BooklistGroup.DATE_READ_MONTH,
                                      BooklistGroup.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);
            style.getFilters().setFilter(Filters.pk_filter_read, true);

            // Location
            style = new BooklistStyle(context,
                                      LOCATION_ID,
                                      LOCATION_UUID,
                                      R.string.style_builtin_location,
                                      BooklistGroup.LOCATION,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Location
            style = new BooklistStyle(context,
                                      LANGUAGE_ID,
                                      LANGUAGE_UUID,
                                      R.string.style_builtin_language,
                                      BooklistGroup.LANGUAGE,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Rating
            style = new BooklistStyle(context,
                                      RATING_ID,
                                      RATING_UUID,
                                      R.string.style_builtin_rating,
                                      BooklistGroup.RATING,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Bookshelf
            style = new BooklistStyle(context,
                                      BOOKSHELF_ID,
                                      BOOKSHELF_UUID,
                                      R.string.style_builtin_bookshelf,
                                      BooklistGroup.BOOKSHELF,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Update date
            style = new BooklistStyle(context,
                                      DATE_LAST_UPDATE_ID,
                                      DATE_LAST_UPDATE_UUID,
                                      R.string.style_builtin_update_date,
                                      BooklistGroup.DATE_LAST_UPDATE_YEAR,
                                      BooklistGroup.DATE_LAST_UPDATE_MONTH,
                                      BooklistGroup.DATE_LAST_UPDATE_DAY);
            S_BUILTIN_STYLES.put(style.getUuid(), style);
            style.getListScreenBookFields()
                 .setShowField(ListScreenBookFields.pk_author, true);

            // NEWTHINGS: BooklistStyle: add a new builtin style
        }
    }
}
