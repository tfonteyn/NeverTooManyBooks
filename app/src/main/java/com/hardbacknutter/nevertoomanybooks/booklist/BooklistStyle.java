/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.BitmaskFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.BooleanFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBitmask;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PBoolean;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntList;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PInteger;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PPref;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PString;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;

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
    public static final Creator<BooklistStyle> CREATOR =
            new Creator<BooklistStyle>() {
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
     */
    public static final int HEADER_SHOW_BOOK_COUNT = 1;
    /** the amount of details to show in the header. */
    @SuppressWarnings("WeakerAccess")
    public static final int HEADER_SHOW_LEVEL_1 = 1 << 1;
    /** the amount of details to show in the header. */
    @SuppressWarnings("WeakerAccess")
    public static final int HEADER_SHOW_LEVEL_2 = 1 << 2;
    /** the amount of details to show in the header. */
    public static final int HEADER_SHOW_STYLE_NAME = 1 << 3;
    /** the amount of details to show in the header. */
    public static final int HEADER_SHOW_FILTER = 1 << 4;
    public static final int[] HEADER_LEVELS = {HEADER_SHOW_LEVEL_1, HEADER_SHOW_LEVEL_2};
    /**
     * Text Scaling.
     * NEVER change these values, they get stored in preferences.
     * <p>
     * For reference:
     * {@code
     * <dimen name="text_size_large_material">22sp</dimen>
     * <dimen name="text_size_medium_material">18sp</dimen>
     * <dimen name="text_size_small_material">14sp</dimen>
     * }
     * The book title in the list is by default 'medium' (see styles.xml)
     * Other elements are always 1 size 'less' then the title.
     */
    static final int FONT_SCALE_SMALL = 0;
    /** Text Scaling. */
    static final int FONT_SCALE_MEDIUM = 1;
    /** Text Scaling. */
    static final int FONT_SCALE_LARGE = 2;

    /** the amount of details to show in the header. */
    private static final int HEADER_SHOW_ALL =
            HEADER_SHOW_BOOK_COUNT
            | HEADER_SHOW_LEVEL_1 | HEADER_SHOW_LEVEL_2
            | HEADER_SHOW_STYLE_NAME
            | HEADER_SHOW_FILTER;

    /** Prefix for all style preferences. */
    private static final String PREFS_PREFIX = "bookList.style.";
    /** Preference for the current default style UUID to use. */
    private static final String PREF_BL_STYLE_CURRENT_DEFAULT = PREFS_PREFIX + "current";

    /**
     * Preferred styles / menu order.
     * Stored in global shared preferences as a CSV String of UUIDs.
     */
    private static final String PREF_BL_PREFERRED_STYLES = PREFS_PREFIX + "preferred.order";

    /**
     * Row id of database row from which this object comes.
     * A '0' is for an as yet unsaved user-style.
     * Always NEGATIVE (e.g. <0 ) for a build-in style
     */
    private long mId;

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
    private PInteger mDefaultExpansionLevel;

    /**
     * Show list header info.
     * <p>
     * Ideally this would use a simple int, but {@link MultiSelectListPreference} insists on a Set.
     */
    private PBitmask mShowHeaderInfo;

    /**
     * Relative size of list text/images.
     * ==1 being 'normal' size
     */
    private PInteger mFontScale;

    /** Scale factor to apply for thumbnails. */
    private PInteger mThumbnailScale;

    /** Local override. */
    private PBoolean mShowAuthorByGivenNameFirst;
    /** Local override. */
    private PBoolean mSortAuthorByGivenNameFirst;

    /** All groups in this style. */
    private PStyleGroups mStyleGroups;

    /**
     * All fields(domains) that are optionally shown on the Book level,
     * in an <strong>ordered</strong> map.
     * <p>
     * <strong>IMPORTANT:</strong> The key in the Map is the DOMAIN name
     * e.g. DBDefinitions.KEY_AUTHOR_FORMATTED, DBDefinitions.KEY_FORMAT ...
     */
    private Map<String, PBoolean> mAllBookDetailFields;

    /**
     * All filters in an <strong>ordered</strong> map.
     * <p>
     * <strong>IMPORTANT:</strong> The key in the Map is the actual preference key itself.
     */
    private Map<String, Filter> mFilters;

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
            mStyleGroups.add(context, BooklistGroup.newInstance(groupId, this));
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
        initPrefs(context, mNameResId == 0);
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
        mDefaultExpansionLevel.set(in);
        mFontScale.set(in);
        mThumbnailScale.set(in);
        mShowHeaderInfo.set(in);
        mShowAuthorByGivenNameFirst.set(in);
        mSortAuthorByGivenNameFirst.set(in);
        mStyleGroups.set(in);

        // the collection is ordered, so we don't need the keys.
        for (PBoolean bookDetailField : mAllBookDetailFields.values()) {
            bookDetailField.set(in);
        }
        // the collection is ordered, so we don't need the keys.
        for (Filter filter : mFilters.values()) {
            filter.set(in);
        }
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
               .edit().putString(Prefs.PK_STYLE_UUID, mUuid).apply();

        // only init the prefs once we have a valid mId, mUuid and mNameResId
        initPrefs(context, true);

        // skip, and set the name
        mName.set(in);
        mName.set(name);

        // further prefs can be set from the parcel as normal.
        mIsPreferred.set(in);
        mDefaultExpansionLevel.set(in);
        mFontScale.set(in);
        mThumbnailScale.set(in);
        mShowHeaderInfo.set(in);
        mShowAuthorByGivenNameFirst.set(in);
        mSortAuthorByGivenNameFirst.set(in);
        mStyleGroups.set(in);

        // the collection is ordered, so we don't need the keys.
        for (PBoolean bookDetailField : mAllBookDetailFields.values()) {
            bookDetailField.set(in);
        }
        // the collection is ordered, so we don't need the keys.
        for (Filter filter : mFilters.values()) {
            filter.set(in);
        }
    }

    /**
     * Get the global default style, or if that fails, the builtin default style..
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
        String uuid = PreferenceManager.getDefaultSharedPreferences(context)
                                       .getString(PREF_BL_STYLE_CURRENT_DEFAULT,
                                                  Builtin.DEFAULT_STYLE_UUID);
        // Builtin ?
        BooklistStyle style = Builtin.getByUuid(context, uuid);
        if (style != null) {
            return style;
        }

        // User defined ?
        style = Helper.getStyles(context, db, true).get(uuid);
        if (style != null) {
            return style;
        }

        // give up
        return Builtin.getDefault(context);
    }

    /**
     * Get the specified style.
     *
     * @param context Current context
     * @param db      Database Access
     * @param uuid    UUID of the style
     *
     * @return the style, <strong>or the default style if not found</strong>
     */
    @NonNull
    public static BooklistStyle getStyle(@NonNull final Context context,
                                         @NonNull final DAO db,
                                         @NonNull final String uuid) {
        BooklistStyle style = Helper.getUserStyles(context, db).get(uuid);
        if (style != null) {
            return style;
        }

        style = Builtin.getStyles(context).get(uuid);
        if (style != null) {
            return style;
        }

        return getDefault(context, db);
    }

    /**
     * Get the specified style.
     *
     * @param context Current context
     * @param db      Database Access
     * @param id      of the style to get.
     *
     * @return style, <strong>or {@code null} if not found</strong>
     */
    @SuppressWarnings("unused")
    @Nullable
    public static BooklistStyle getStyle(@NonNull final Context context,
                                         @NonNull final DAO db,
                                         final long id) {
        if (id == 0) {
            return null;
        }

        for (BooklistStyle style : Helper.getUserStyles(context, db).values()) {
            if (style.getId() == id) {
                return style;
            }
        }

        // check builtin.
        for (BooklistStyle style : Builtin.getStyles(context).values()) {
            if (style.getId() == id) {
                return style;
            }
        }

        // not found...
        return null;
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
            throw new IllegalStateException("mUuid.isEmpty()");
        }

        //FIXME: revisit... this is to complicated/inefficient.
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

        mName = new PString(Prefs.pk_style_name, mUuid, isUserDefined);

        mIsPreferred = new PBoolean(Prefs.pk_style_is_preferred, mUuid, isUserDefined);

        mDefaultExpansionLevel = new PInteger(Prefs.pk_style_levels_expansion, mUuid,
                                              isUserDefined, 1);

        mShowHeaderInfo = new PBitmask(Prefs.pk_style_header, mUuid,
                                       isUserDefined, HEADER_SHOW_ALL);

        mFontScale = new PInteger(Prefs.pk_style_scale_font, mUuid,
                                  isUserDefined, FONT_SCALE_MEDIUM);

        mThumbnailScale = new PInteger(Prefs.pk_style_scale_thumbnail, mUuid,
                                       isUserDefined, ImageUtils.SCALE_MEDIUM);


        mShowAuthorByGivenNameFirst = new PBoolean(Prefs.pk_show_author_name_given_first, mUuid,
                                                   isUserDefined);

        mSortAuthorByGivenNameFirst = new PBoolean(Prefs.pk_sort_author_name_given_first, mUuid,
                                                   isUserDefined);

        // all groups in this style
        mStyleGroups = new PStyleGroups(context, this);

        // all optional details for book-rows.
        mAllBookDetailFields = new LinkedHashMap<>();

        mAllBookDetailFields.put(DBDefinitions.KEY_THUMBNAIL,
                                 new PBoolean(Prefs.pk_style_book_show_thumbnails,
                                              mUuid, isUserDefined,
                                              true));

        mAllBookDetailFields.put(DBDefinitions.KEY_BOOKSHELF_NAME,
                                 new PBoolean(Prefs.pk_style_book_show_bookshelves,
                                              mUuid, isUserDefined));

        mAllBookDetailFields.put(DBDefinitions.KEY_LOCATION,
                                 new PBoolean(Prefs.pk_style_book_show_location,
                                              mUuid, isUserDefined));

        mAllBookDetailFields.put(DBDefinitions.KEY_AUTHOR_FORMATTED,
                                 new PBoolean(Prefs.pk_style_book_show_author,
                                              mUuid, isUserDefined));

        mAllBookDetailFields.put(DBDefinitions.KEY_PUBLISHER,
                                 new PBoolean(Prefs.pk_style_book_show_publisher,
                                              mUuid, isUserDefined));

        mAllBookDetailFields.put(DBDefinitions.KEY_DATE_PUBLISHED,
                                 new PBoolean(Prefs.pk_style_book_show_pub_date,
                                              mUuid, isUserDefined));

        mAllBookDetailFields.put(DBDefinitions.KEY_ISBN,
                                 new PBoolean(Prefs.pk_style_book_show_isbn,
                                              mUuid, isUserDefined));

        mAllBookDetailFields.put(DBDefinitions.KEY_FORMAT,
                                 new PBoolean(Prefs.pk_style_book_show_format,
                                              mUuid, isUserDefined));

        // all filters
        mFilters = new LinkedHashMap<>();

        mFilters.put(Prefs.pk_style_filter_read,
                     new BooleanFilter(R.string.lbl_read,
                                       Prefs.pk_style_filter_read,
                                       mUuid, isUserDefined,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_READ));

        mFilters.put(Prefs.pk_style_filter_signed,
                     new BooleanFilter(R.string.lbl_signed,
                                       Prefs.pk_style_filter_signed,
                                       mUuid, isUserDefined,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_SIGNED));

        mFilters.put(Prefs.pk_style_filter_anthology,
                     new BooleanFilter(R.string.lbl_anthology,
                                       Prefs.pk_style_filter_anthology,
                                       mUuid, isUserDefined,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_TOC_BITMASK));

        mFilters.put(Prefs.pk_style_filter_loaned,
                     new BooleanFilter(R.string.lbl_loaned,
                                       Prefs.pk_style_filter_loaned,
                                       mUuid, isUserDefined,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_LOANEE));

        mFilters.put(Prefs.pk_style_filter_editions,
                     new BitmaskFilter(R.string.lbl_edition,
                                       Prefs.pk_style_filter_editions,
                                       mUuid, isUserDefined,
                                       DBDefinitions.TBL_BOOKS,
                                       DBDefinitions.KEY_EDITION_BITMASK));
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
        mDefaultExpansionLevel.writeToParcel(dest);
        mFontScale.writeToParcel(dest);
        mThumbnailScale.writeToParcel(dest);
        mShowHeaderInfo.writeToParcel(dest);
        mShowAuthorByGivenNameFirst.writeToParcel(dest);
        mSortAuthorByGivenNameFirst.writeToParcel(dest);
        mStyleGroups.writeToParcel(dest);

        // the collection is ordered, so we don't write the keys.
        for (PBoolean bookDetailField : mAllBookDetailFields.values()) {
            bookDetailField.writeToParcel(dest);
        }
        // the collection is ordered, so we don't write the keys.
        for (Filter filter : mFilters.values()) {
            filter.writeToParcel(dest);
        }
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
     * store the current style as the global default one.
     *
     * @param context Current context
     */
    public void setDefault(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().putString(PREF_BL_STYLE_CURRENT_DEFAULT, mUuid)
                         .apply();
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
        Map<String, PPref> map = new HashMap<>();
        map.put(mName.getKey(), mName);

        map.put(mIsPreferred.getKey(), mIsPreferred);
        map.put(mDefaultExpansionLevel.getKey(), mDefaultExpansionLevel);
        map.put(mFontScale.getKey(), mFontScale);
        map.put(mThumbnailScale.getKey(), mThumbnailScale);
        map.put(mShowHeaderInfo.getKey(), mShowHeaderInfo);

        map.put(mShowAuthorByGivenNameFirst.getKey(), mShowAuthorByGivenNameFirst);
        map.put(mSortAuthorByGivenNameFirst.getKey(), mSortAuthorByGivenNameFirst);

        map.put(mStyleGroups.getKey(), mStyleGroups);

        for (PBoolean bookDetailField : mAllBookDetailFields.values()) {
            map.put(bookDetailField.getKey(), bookDetailField);
        }

        if (all) {
            // all filters (both active and non-active)
            for (Filter filter : mFilters.values()) {
                map.put(filter.getKey(), (PPref) filter);
            }

            // for each group used by the style, add its specific preferences to our list
            for (BooklistGroup group : mStyleGroups.getGroups()) {
                map.putAll(group.getPreferences());
            }
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
        SharedPreferences.Editor ed = context.getSharedPreferences(mUuid, Context.MODE_PRIVATE)
                                             .edit();
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
        Map<String, PPref> currentPreferences = getPreferences(true);

        for (PPref p : newPrefs.values()) {
            // do we have this Preference ?
            PPref ourPPref = currentPreferences.get(p.getKey());
            if (ourPPref != null) {
                // if we do, then update our value
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
    public boolean showHeader(@NonNull final Context context,
                              @ListHeaderOption final int headerMask) {
        return (mShowHeaderInfo.getValue(context) & headerMask) != 0;
    }

    /**
     * Get the scaling factor to apply to the View padding if text is scaled.
     *
     * @param context Current context
     *
     * @return scale factor
     */
    float getTextPaddingFactor(@NonNull final Context context) {
        switch (mFontScale.getValue(context)) {
            case FONT_SCALE_LARGE:
                return 1.22f;
            case FONT_SCALE_SMALL:
                return 0.77f;
            case FONT_SCALE_MEDIUM:
            default:
                return 1.0f;
        }
    }

    /**
     * Get the text size in SP units to apply.
     * <p>
     * With the header set to two lines, the toolbar fully visible,
     * on a full-HD (1920-1080 pixels) we get:
     * <ul>
     *      <li>32sp: 10 lines; or 2 books</li>
     *      <li>28sp: 11 lines</li>
     *      <li>24sp: 12 lines</li>
     *      <li>18sp: 13 lines; or 5-6 books</li>
     *      <li>14sp: 19 lines; or 7 books</li>
     * </ul>
     *
     * @param context Current context
     *
     * @return sp units
     */
    float getTextSpUnits(@NonNull final Context context) {
        switch (mFontScale.getValue(context)) {
            case FONT_SCALE_LARGE:
                return 32;
            case FONT_SCALE_SMALL:
                return 14;
            case FONT_SCALE_MEDIUM:
            default:
                return 18;
        }
    }

    /**
     * Get the scale <strong>identifier</strong> for the text size preferred.
     *
     * @param context Current context
     *
     * @return scale id
     */
    @FontScale
    public int getTextScale(@NonNull final Context context) {
        return mFontScale.getValue(context);
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     *
     * @param scale id
     */
    @SuppressWarnings("SameParameterValue")
    private void setTextScale(@FontScale final int scale) {
        mFontScale.set(scale);
    }

    /**
     * Get the scale <strong>identifier</strong> for the thumbnail size preferred.
     *
     * @param context Current context
     *
     * @return scale id
     */
    @ImageUtils.Scale
    public int getThumbnailScale(@NonNull final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (isBookDetailUsed(context, prefs, DBDefinitions.KEY_THUMBNAIL)) {
            return mThumbnailScale.getValue(context);
        }
        return ImageUtils.SCALE_NOT_DISPLAYED;
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     *
     * @param key  for the field
     * @param show value to set
     */
    private void setShowBookDetailField(@NonNull final String key,
                                        final boolean show) {
        //noinspection ConstantConditions
        mAllBookDetailFields.get(key).set(show);
    }

    /**
     * Add an already existing instance.
     *
     * @param context Current context
     * @param group   to add
     */
    public void addGroup(@NonNull final Context context,
                         @NonNull final BooklistGroup group) {
        mStyleGroups.add(context, group);
    }

    /**
     * Remove a group from this style.
     *
     * @param context Current context
     * @param group   to remove.
     */
    public void removeGroup(@NonNull final Context context,
                            @NonNull final BooklistGroup group) {
        mStyleGroups.remove(context, group.getId());
    }


    /**
     * Get the number of groups in this style.
     *
     * @return the number of groups
     */
    public int getGroupCount() {
        return mStyleGroups.size();
    }

    /**
     * Get all groups assigned to this style.
     *
     * @return group list
     */
    @NonNull
    public List<BooklistGroup> getGroups() {
        return mStyleGroups.getGroups();
    }

    /**
     * Get a list of in-use group names in a human readable format.
     *
     * @param context Current context
     *
     * @return list
     */
    @NonNull
    public String getGroupLabels(@NonNull final Context context) {
        return Csv.join(mStyleGroups.getGroups(), element -> element.getLabel(context));
    }

    /**
     * Check if the given group is present, using the given group id.
     *
     * @param id group id
     *
     * @return {@code true} if present
     */
    public boolean containsGroup(@BooklistGroup.Id final int id) {
        return mStyleGroups.contains(id);
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
        return mStyleGroups.getGroupById(id);
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
        return mStyleGroups.getGroupByLevel(level);
    }


    /**
     * Used by built-in styles only. Set by user via preferences screen.
     */
    @SuppressWarnings("SameParameterValue")
    private void setFilter(@NonNull final String key,
                           final boolean value) {
        //noinspection ConstantConditions
        ((BooleanFilter) mFilters.get(key)).set(value);
    }

    /**
     * Get the list of <strong>active</strong> Filters.
     *
     * @param context Current context
     *
     * @return list
     */
    @NonNull
    public Collection<Filter> getActiveFilters(@NonNull final Context context) {
        Collection<Filter> activeFilters = new ArrayList<>();
        for (Filter filter : mFilters.values()) {
            if (filter.isActive(context)) {
                activeFilters.add(filter);
            }
        }
        return activeFilters;
    }

    /**
     * Convenience method for use in the Preferences screen.
     *
     * @param context Current context
     * @param all     {@code true} to get all, {@code false} for only the active filters
     *
     * @return the list of in-use filter names in a human readable format.
     */
    public List<String> getFilterLabels(@NonNull final Context context,
                                        final boolean all) {
        List<String> labels = new ArrayList<>();
        for (Filter filter : mFilters.values()) {
            if (filter.isActive(context) || all) {
                labels.add(filter.getLabel(context));
            }
        }
        Collections.sort(labels);
        return labels;
    }

    /**
     * Get the list of in-use book-detail-field names in a human readable format.
     * This is used to set the summary of the PreferenceScreen.
     *
     * @param context Current context
     *
     * @return list of labels, can be empty, but never {@code null}
     */
    @NonNull
    public List<String> getBookDetailsFieldLabels(@NonNull final Context context) {
        List<String> labels = new ArrayList<>();

        //noinspection ConstantConditions
        if (mAllBookDetailFields.get(DBDefinitions.KEY_THUMBNAIL).isTrue(context)) {
            labels.add(context.getString(R.string.pt_bob_thumbnails_show));
        }
        //noinspection ConstantConditions
        if (mAllBookDetailFields.get(DBDefinitions.KEY_BOOKSHELF_NAME).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_bookshelves));
        }
        //noinspection ConstantConditions
        if (mAllBookDetailFields.get(DBDefinitions.KEY_LOCATION).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_location));
        }
        //noinspection ConstantConditions
        if (mAllBookDetailFields.get(DBDefinitions.KEY_AUTHOR_FORMATTED).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_author));
        }
        //noinspection ConstantConditions
        if (mAllBookDetailFields.get(DBDefinitions.KEY_PUBLISHER).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_publisher));
        }
        //noinspection ConstantConditions
        if (mAllBookDetailFields.get(DBDefinitions.KEY_DATE_PUBLISHED).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_date_published));
        }
        //noinspection ConstantConditions
        if (mAllBookDetailFields.get(DBDefinitions.KEY_ISBN).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_isbn));
        }
        //noinspection ConstantConditions
        if (mAllBookDetailFields.get(DBDefinitions.KEY_FORMAT).isTrue(context)) {
            labels.add(context.getString(R.string.lbl_format));
        }

        Collections.sort(labels);
        return labels;
    }

    /**
     * Save the style to the database. This is now limited to the UUID.
     * All actual settings reside in a dedicated SharedPreference file.
     * <p>
     * if an insert fails, the style retains id==0.
     *
     * @param db Database Access
     */
    public void save(@NonNull final DAO db) {
        if (!isUserDefined()) {
            throw new IllegalStateException("Builtin Style cannot be saved to database");
        }
        // sanity check
        if (mUuid.isEmpty()) {
            throw new IllegalStateException("mUuid.isEmpty()");
        }

        // check if the style already exists.
        long existingId = db.getStyleIdByUuid(mUuid);
        if (existingId == 0) {
            if (db.insertStyle(this) > 0) {
                Helper.S_USER_STYLES.put(getUuid(), this);
            }
        } else {
            // force-update the id.
            mId = existingId;
        }
    }

    /**
     * Delete this style.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public void delete(@NonNull final Context context,
                       @NonNull final DAO db) {

        // cannot delete a builtin or a 'new' style(id==0)
        if (mId == 0 || !isUserDefined()) {
            throw new IllegalArgumentException("Builtin Style cannot be deleted");
        }
        // sanity check, cannot delete the global style settings.
        if (mUuid.isEmpty()) {
            throw new IllegalStateException("Global Style cannot be deleted");
        }

        Helper.S_USER_STYLES.remove(mUuid);
        db.deleteStyle(mId);

        if (Build.VERSION.SDK_INT >= 24) {
            context.deleteSharedPreferences(mUuid);
        } else {
            context.getSharedPreferences(mUuid, Context.MODE_PRIVATE).edit().clear().apply();
        }
    }

    public void discard(@NonNull final Context context) {
        // can ONLY discard a new style
        if (mId != 0) {
            throw new IllegalArgumentException("can only discard a new style");
        }
        if (Build.VERSION.SDK_INT >= 24) {
            context.deleteSharedPreferences(mUuid);
        } else {
            context.getSharedPreferences(mUuid, Context.MODE_PRIVATE).edit().clear().apply();
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
     * - the uuid is the same.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        BooklistStyle that = (BooklistStyle) obj;

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
               + "\nmNameResId=" + mNameResId
               + "\nmName=" + mName
               + "\nmIsPreferred=" + mIsPreferred
               + "\nmDefaultExpansionLevel=" + mDefaultExpansionLevel
               + "\nmFontScale=" + mFontScale
               + "\nmShowHeaderInfo=" + mShowHeaderInfo
               + "\nmShowAuthorByGivenNameFirst=" + mShowAuthorByGivenNameFirst
               + "\nmSortAuthorByGivenNameFirst=" + mSortAuthorByGivenNameFirst
               + "\nmThumbnailScale=" + mThumbnailScale
               + "\nmStyleGroups=" + mStyleGroups

               + "\nmAllBookDetailFields=" + mAllBookDetailFields
               + "\nmFilters=\n" + mFilters
               + '}';
    }


    /**
     * Check if the given book-detail field is in use.
     *
     * @param context Current context
     * @param key     to check
     *
     * @return {@code true} if in use
     */
    public boolean isBookDetailUsed(@NonNull final Context context,
                                    @NonNull final SharedPreferences sharedPreferences,
                                    @NonNull final String key) {
        // global overrules local
        if (!DBDefinitions.isUsed(sharedPreferences, key)) {
            return false;
        }

        if (mAllBookDetailFields.containsKey(key)) {
            PBoolean value = mAllBookDetailFields.get(key);
            return value != null && value.isTrue(context);
        }
        return false;
    }

    /**
     * Wrapper that gets the getPrimaryType flag from the
     * {@link BooklistGroup.BooklistAuthorGroup} if we have it, or from the global default.
     *
     * @param context Current context
     *
     * @return the type of author we consider the primary author
     */
    int getPrimaryAuthorType(@NonNull final Context context) {
        BooklistGroup.BooklistAuthorGroup group = (BooklistGroup.BooklistAuthorGroup)
                (mStyleGroups.getGroupById(BooklistGroup.AUTHOR));
        if (group != null) {
            return group.getPrimaryType(context);
        } else {
            // return the global default.
            return BooklistGroup.BooklistAuthorGroup.getPrimaryTypeGlobalDefault(context);
        }
    }

    /**
     * Wrapper that gets the isShowBooksUnderEachAuthor flag from the
     * {@link BooklistGroup.BooklistAuthorGroup} if we have it, or from the global default.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Authors
     */
    boolean isShowBooksUnderEachAuthor(@NonNull final Context context) {
        BooklistGroup.BooklistAuthorGroup group = (BooklistGroup.BooklistAuthorGroup)
                (mStyleGroups.getGroupById(BooklistGroup.AUTHOR));
        if (group != null) {
            return group.showBooksUnderEachAuthor(context);
        } else {
            // return the global default.
            return BooklistGroup.BooklistAuthorGroup.showBooksUnderEachAuthorGlobalDefault(context);
        }
    }

    /**
     * Wrapper that gets the isShowBooksUnderEachSeries flag from the
     * {@link BooklistGroup.BooklistSeriesGroup} if we have it, or from the global default.
     *
     * @param context Current context
     *
     * @return {@code true} if we want to show a book under each of its Series.
     */
    boolean isShowBooksUnderEachSeries(@NonNull final Context context) {
        BooklistGroup.BooklistSeriesGroup group = (BooklistGroup.BooklistSeriesGroup)
                (mStyleGroups.getGroupById(BooklistGroup.SERIES));
        if (group != null) {
            return group.showBooksUnderEachSeries(context);
        } else {
            // return the global default.
            return BooklistGroup.BooklistSeriesGroup.showBooksUnderEachSeriesGlobalDefault(context);
        }
    }

    /**
     * Whether the user prefers the Author names displayed by Given names, or by Family name first.
     *
     * @param context Current context
     *
     * @return {@code true} when Given names should come first
     */
    public boolean isShowAuthorByGivenNameFirst(@NonNull final Context context) {
        return mShowAuthorByGivenNameFirst.isTrue(context);
    }

    /**
     * Whether the user prefers the Author names sorted by Given names, or by Family name first.
     *
     * @param context Current context
     *
     * @return {@code true} when Given names should come first
     */
    boolean isSortAuthorByGivenNameFirst(@NonNull final Context context) {
        return mSortAuthorByGivenNameFirst.isTrue(context);
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
        int level = mDefaultExpansionLevel.getValue(context);
        if (level > mStyleGroups.size()) {
            level = mStyleGroups.size();
        }
        return level;
    }

    public void updateHelper() {
        Helper.S_USER_STYLES.put(mUuid, this);
    }

    @IntDef(flag = true, value = {HEADER_SHOW_BOOK_COUNT,
                                  HEADER_SHOW_LEVEL_1, HEADER_SHOW_LEVEL_2,
                                  HEADER_SHOW_STYLE_NAME,
                                  HEADER_SHOW_FILTER})
    @Retention(RetentionPolicy.SOURCE)
    private @interface ListHeaderOption {

    }

    @IntDef({FONT_SCALE_SMALL, FONT_SCALE_MEDIUM, FONT_SCALE_LARGE})
    @Retention(RetentionPolicy.SOURCE)
    @interface FontScale {

    }

    /**
     * Fronts an {@code ArrayList<BooklistGroup>} with backend storage in a preference.
     */
    private static class PStyleGroups
            extends PIntList {

        /** All groups; ordered. Reminder: the underlying PIntList is only storing the id. */
        private final Map<Integer, BooklistGroup> mGroups = new LinkedHashMap<>();

        /**
         * Constructor.
         *
         * @param context Current context
         * @param style   the style
         */
        PStyleGroups(@NonNull final Context context,
                     @NonNull final BooklistStyle style) {
            super(Prefs.pk_style_groups, style.getUuid(), style.isUserDefined());

            // load the group ID's from the SharedPreference and populates the Group object list.
            mGroups.clear();
            for (@BooklistGroup.Id int id : getValue(context)) {
                mGroups.put(id, BooklistGroup.newInstance(id, style));
            }
        }

        @NonNull
        List<BooklistGroup> getGroups() {
            return new ArrayList<>(mGroups.values());
        }

        /**
         * Check if the given group is present, using the given group id.
         *
         * @param id group id
         *
         * @return {@code true} if present
         */
        boolean contains(@BooklistGroup.Id final int id) {
            return mGroups.containsKey(id);
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
            return mGroups.get(id);
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
            return (BooklistGroup) mGroups.values().toArray()[level - 1];
        }

        int size() {
            return mGroups.size();
        }

        @Override
        @NonNull
        public String toString() {
            return "PStyleGroups{" + super.toString()
                   + ", mGroups=" + mGroups
                   + '}';
        }

        /**
         * Add a new group to the end of the list.
         *
         * @param context Current context
         * @param group   to add
         */
        void add(@NonNull final Context context,
                 @NonNull final BooklistGroup group) {
            mGroups.put(group.getId(), group);
            super.add(context, group.getId());
        }

        @Override
        public void add(@NonNull final Context context,
                        @NonNull final Integer element) {
            // we need the actual group to add it to mGroups
            throw new IllegalStateException("use add(BooklistGroup) instead");
        }

        /**
         * Remove the given group.
         *
         * @param context Current context
         * @param id      of group to remove
         */
        public void remove(@NonNull final Context context,
                           @BooklistGroup.Id @NonNull final Integer id) {
            mGroups.remove(id);
            super.remove(context, id);
        }


        /**
         * Set the <strong>value</strong> from the Parcel.
         *
         * @param in parcel to read from
         */
        @Override
        public void set(@NonNull final Parcel in) {
            clear(App.getAppContext());
            List<BooklistGroup> list = new ArrayList<>();
            in.readList(list, getClass().getClassLoader());
            // (faster) equivalent of add(@NonNull final BooklistGroup group)
            // but split in adding the group and...
            for (BooklistGroup group : list) {
                mGroups.put(group.getId(), group);
            }
            // storing the ids in SharedPreference.
            this.set(new ArrayList<>(mGroups.keySet()));
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest) {
            dest.writeList(new ArrayList<>(mGroups.values()));
        }

        @Override
        public void clear(@NonNull final Context context) {
            mGroups.clear();
            super.clear(context);
        }
    }

    public static final class Helper {

        /**
         * We keep a cache of User styles in memory as it's to costly to keep
         * re-creating {@link BooklistStyle} objects.
         * Pre-loaded on first access.
         * Re-loaded when the Locale changes.
         * <p>
         * Key: uuid of style.
         */
        private static final Map<String, BooklistStyle> S_USER_STYLES = new LinkedHashMap<>();

        private Helper() {
        }

        public static void clear() {
            S_USER_STYLES.clear();
        }

        /**
         * Get the user-defined Styles from the database.
         *
         * @param context Current context
         * @param db      Database Access
         *
         * @return ordered map of BooklistStyle
         */
        @NonNull
        public static Map<String, BooklistStyle> getUserStyles(@NonNull final Context context,
                                                               @NonNull final DAO db) {
            if (S_USER_STYLES.isEmpty()) {
                S_USER_STYLES.putAll(db.getUserStyles(context));
            }
            return S_USER_STYLES;
        }

        /**
         * Get an ordered Map with all the styles.
         * The preferred styles are at the front of the list.
         *
         * @param context Current context
         * @param db      Database Access
         * @param all     if {@code true} then also return the non-preferred styles
         *
         * @return ordered list
         */
        @NonNull
        public static Map<String, BooklistStyle> getStyles(@NonNull final Context context,
                                                           @NonNull final DAO db,
                                                           final boolean all) {
            // Get all styles: user
            Map<String, BooklistStyle> allStyles = getUserStyles(context, db);
            // Get all styles: builtin
            allStyles.putAll(Builtin.getStyles(context));

            // filter, so the list only shows the preferred ones.
            Map<String, BooklistStyle> styles = filterPreferredStyles(context, allStyles);

            // but if we want all, add the missing styles to the end of the list
            if (all) {
                if (!styles.equals(allStyles)) {
                    for (BooklistStyle style : allStyles.values()) {
                        if (!styles.containsKey(style.getUuid())) {
                            styles.put(style.getUuid(), style);
                        }
                    }
                }
            }
            return styles;
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
        private static Map<String, BooklistStyle> filterPreferredStyles(
                @NonNull final Context context,
                @NonNull final Map<String, BooklistStyle> allStyles) {

            Map<String, BooklistStyle> resultingStyles = new LinkedHashMap<>();

            // first check the saved and ordered list
            for (String uuid : getMenuOrder(context)) {
                BooklistStyle style = allStyles.get(uuid);
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

        /**
         * Get the UUIDs of the preferred styles from user preferences.
         *
         * @param context Current context
         *
         * @return set of UUIDs
         */
        @NonNull
        private static Set<String> getMenuOrder(@NonNull final Context context) {
            Set<String> uuidSet = new LinkedHashSet<>();
            String itemsStr = PreferenceManager.getDefaultSharedPreferences(context)
                                               .getString(PREF_BL_PREFERRED_STYLES, null);

            if (itemsStr != null && !itemsStr.isEmpty()) {
                String[] entries = itemsStr.split(",");
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
        private static void setMenuOrder(@NonNull final Context context,
                                         @NonNull final Iterable<String> uuidSet) {
            PreferenceManager.getDefaultSharedPreferences(context)
                             .edit()
                             .putString(PREF_BL_PREFERRED_STYLES, TextUtils.join(",", uuidSet))
                             .apply();
        }

        /**
         * Add a style (its uuid) to the menu list of preferred styles.
         *
         * @param context Current context
         * @param style   to add.
         */
        public static void addPreferredStyle(@NonNull final Context context,
                                             @NonNull final BooklistStyle style) {
            Set<String> list = getMenuOrder(context);
            list.add(style.getUuid());
            setMenuOrder(context, list);
        }

        /**
         * Save the preferred style menu list.
         * <p>
         * This list contains the ID's for user-defined *AND* system-styles.
         *
         * @param context Current context
         * @param styles  full list of preferred styles to save 'in order'
         */
        public static void saveMenuOrder(@NonNull final Context context,
                                         @NonNull final Iterable<BooklistStyle> styles) {
            Collection<String> list = new LinkedHashSet<>();
            for (BooklistStyle style : styles) {
                if (style.isPreferred(context)) {
                    list.add(style.getUuid());
                }
            }
            setMenuOrder(context, list);
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
         * Note the hardcoded negative ID's. These number should never be changed as they will
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
        /** Use the NEGATIVE builtin style id to get the UUID for it. Element 0 is not used. */
        public static final String[] ID_UUID = {
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
         * Static method to get all builtin styles.
         * <p>
         * <strong>Note:</strong> Do NOT call this in static initialization of application.
         * This method requires the application context to be present.
         *
         * @param context Current context
         *
         * @return a collection of all builtin styles.
         */
        @SuppressWarnings("SameReturnValue")
        @NonNull
        private static Map<String, BooklistStyle> getStyles(@NonNull final Context context) {

            if (S_BUILTIN_STYLES.isEmpty()) {
                create(context);
            }
            return S_BUILTIN_STYLES;
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

        @Nullable
        static BooklistStyle getByUuid(@NonNull final Context context,
                                       @NonNull final String uuid) {
            return getStyles(context).get(uuid);
        }

        private static void create(@NonNull final Context context) {

            // Author/Series
            BooklistStyle style = getDefault(context);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Unread
            style = new BooklistStyle(context,
                                      UNREAD_AUTHOR_THEN_SERIES_ID,
                                      UNREAD_AUTHOR_THEN_SERIES_UUID,
                                      R.string.style_builtin_unread,
                                      BooklistGroup.AUTHOR,
                                      BooklistGroup.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);
            style.setFilter(Prefs.pk_style_filter_read, false);

            // Compact
            style = new BooklistStyle(context,
                                      COMPACT_ID,
                                      COMPACT_UUID,
                                      R.string.style_builtin_compact,
                                      BooklistGroup.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);
            style.setTextScale(FONT_SCALE_SMALL);
            style.setShowBookDetailField(DBDefinitions.KEY_THUMBNAIL, false);

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

            // Loaned
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
            style.setFilter(Prefs.pk_style_filter_read, true);

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
            style.setShowBookDetailField(DBDefinitions.KEY_AUTHOR_FORMATTED, true);

            // NEWTHINGS: BooklistStyle: add a new builtin style
        }
    }
}
