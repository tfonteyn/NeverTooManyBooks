/*
 * @Copyright 2019 HardBackNutter
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BooklistAdapter;
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
 * Represents a specific style of book list (e.g. authors/series).
 * Individual {@link BooklistGroup} objects are added to a {@link BooklistStyle} in order
 * to describe the resulting list style.
 * <p>
 * 2019-08-19: legacy style related code removed.
 * <p>
 * 2019-08-04: due to the move to a new package/directory structure, it is no longer
 * possible to import legacy (binary) styles.
 * <p>
 * 2018-12-20: the implementation no longer stores serialized blobs, neither in the database nor
 * in backup archives (but can still read them from archives/database upgrades).<br>
 * The database table now consists of a PK ID, and a UUID column.<br>
 * The UUID serves as the name of the SharedPreference which describes the style.<br>
 * Builtin styles are not stored in the database and (internally) use negative ID's and
 * a hardcoded UUID.<br>
 * Every setting in a style is backed by a {@link PPref} which handles the storage
 * of that setting.<br>
 * *All* style settings are private to a style, there is no inheritance of global settings.<br>
 * <p>
 * ENHANCE: re-introduce global inheritance ? But would that actually be used ?
 * <p>
 * <p>
 * How to add a new Group:
 * <ol>
 * <li>add it to {@link BooklistGroup.RowKind} and update ROW_KIND_MAX</li>
 * <li>if necessary add new domain to {@link DBDefinitions }</li>
 * <li>modify {@link BooklistBuilder#build} to add the necessary grouped/sorted domains</li>
 * <li>modify {@link BooklistAdapter} ; If it is just a string field,
 * then use a {@link BooklistAdapter} .GenericStringHolder}, otherwise add a new holder.</li>
 * </ol>
 * Need to at least modify {@link BooklistAdapter} #createHolder
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

    /** Extra book data to show at lowest level. (the bit numbers are not stored anywhere) */
    public static final int EXTRAS_BOOKSHELVES = 1;
    /** Extra book data to show at lowest level. */
    public static final int EXTRAS_LOCATION = 1 << 1;
    /** Extra book data to show at lowest level. */
    public static final int EXTRAS_FORMAT = 1 << 2;
    /** Extra book data to show at lowest level. */
    public static final int EXTRAS_PUBLISHER = 1 << 3;
    /** Extra book data to show at lowest level. */
    public static final int EXTRAS_PUB_DATE = 1 << 4;
    /** Extra book data to show at lowest level. */
    public static final int EXTRAS_AUTHOR = 1 << 5;
    /** Extra book data to show at lowest level. */
    public static final int EXTRAS_ISBN = 1 << 6;

    /** Mask for the extras that are fetched using {@link BooklistAdapter}.GetBookExtrasTask}. */
    public static final int EXTRAS_BY_TASK = EXTRAS_BOOKSHELVES
                                             | EXTRAS_LOCATION | EXTRAS_FORMAT
                                             | EXTRAS_PUBLISHER | EXTRAS_PUB_DATE
                                             | EXTRAS_AUTHOR | EXTRAS_ISBN;

    /** Extra book data to show at lowest level. */
    public static final int EXTRAS_THUMBNAIL = 1 << 8;

//    /** the amount of details to show in the header. */
//    public static final Integer SUMMARY_HIDE = 0;
    /** the amount of details to show in the header. */
    public static final Integer SUMMARY_SHOW_COUNT = 1;
    /** the amount of details to show in the header. */
    @SuppressWarnings("WeakerAccess")
    public static final Integer SUMMARY_SHOW_LEVEL_1 = 1 << 1;
    /** the amount of details to show in the header. */
    @SuppressWarnings("WeakerAccess")
    public static final Integer SUMMARY_SHOW_LEVEL_2 = 1 << 2;
    /** the amount of details to show in the header. */
    public static final Integer SUMMARY_SHOW_ALL =
            SUMMARY_SHOW_COUNT | SUMMARY_SHOW_LEVEL_1 | SUMMARY_SHOW_LEVEL_2;

    /** Text Scaling. */
    public static final int TEXT_SCALE_SMALL = 1;
    /** Text Scaling. */
    public static final int TEXT_SCALE_MEDIUM = 2;
    /** Text Scaling. */
    @SuppressWarnings("WeakerAccess")
    public static final int TEXT_SCALE_LARGE = 3;

    /** Preference for the current default style UUID to use. */
    public static final String PREF_BL_STYLE_CURRENT_DEFAULT = "BookList.Style.Current";
    /**
     * Preferred styles / menu order.
     * Stored in global shared preferences as a CSV String of UUIDs.
     */
    public static final String PREF_BL_PREFERRED_STYLES = "BookList.Style.Preferred.Order";
    /**
     * Unique name. This is a stored in our preference file (with the same name)
     * and is used for backup/restore purposes as the 'ID'.
     * <p>
     * (this is not a PPref, as we'd need the uuid to store the uuid....)
     */
    private static final String PREF_STYLE_UUID = "BookList.Style.uuid";

    /**
     * Row id of database row from which this object comes.
     * A '0' is for an as yet unsaved user-style.
     * Always NEGATIVE (e.g. <0 ) for a build-in style
     */
    private long mId;

    /**
     * The uuid based SharedPreference name.
     */
    @NonNull
    private String mUuid;

    /**
     * id if string representing name of this style.
     * Used for standard system-defined styles.
     * Always 0 for a user-defined style
     */
    @StringRes
    private int mNameResId;

    /**
     * Display name of this style.
     * Used for user-defined styles.
     * Encapsulated value always {@code null} for a builtin style.
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

    /**
     * Relative size of list text/images.
     * ==1 being 'normal' size
     */
    private PInteger mScaleFontSize;

    /** Scale factor to apply for thumbnails. */
    private PInteger mThumbnailScale;

    /**
     * Show list header info.
     * <p>
     * Ideally this would use a simple int, but {@link MultiSelectListPreference} insists on a Set.
     */
    private PBitmask mShowHeaderInfo;
    /** Sorting. */
    private PBoolean mSortAuthorGivenNameFirst;

    /** All groups in this style. */
    private PStyleGroups mStyleGroups;

    /** Fetch the extras by using a task or not. */
    private PBoolean mFetchExtrasByTask;

    /**
     * All extra fields in an <strong>ordered</strong> map.
     * <p>
     * The key in the Map is the actual preference key.
     */
    private Map<String, PBoolean> mAllExtras;

    /**
     * All filters in an <strong>ordered</strong> map.
     * <p>
     * The key in the Map is the actual preference key.
     */
    private Map<String, Filter> mAllFilters;


    /**
     * Constructor for system-defined styles.
     *
     * @param id     a negative int
     * @param uuid   the hardcoded UUID for the builtin style.
     * @param nameId the resource id for the name
     * @param kinds  a list of group kinds to attach to this style
     */
    private BooklistStyle(@IntRange(from = -100, to = -1) final long id,
                          @NonNull final String uuid,
                          @StringRes final int nameId,
                          @NonNull final int... kinds) {

        mId = id;
        mUuid = uuid;
        mNameResId = nameId;
        initPrefs();
        for (int kind : kinds) {
            mStyleGroups.add(BooklistGroup.newInstance(kind, mUuid, isUserDefined()));
        }
    }

    /**
     * Constructor for user-defined styles.
     * <p>
     * Only used when styles are loaded from storage / importing from xml.
     * Real new styles are created by cloning an existing style.
     *
     * @param id   the row id of the style
     * @param uuid the UUID of the style
     */
    public BooklistStyle(final long id,
                         @NonNull final String uuid) {
        mId = id;
        mUuid = uuid;
        initPrefs();
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private BooklistStyle(@NonNull final Parcel in) {
        this(in, false, null);
    }

    /**
     * Custom Parcelable constructor which allows cloning/new.
     *
     * @param in      Parcel to construct the object from
     * @param isNew   when set to true, partially override the incoming data so we get
     *                a 'new' object but with the settings from the Parcel.
     *                The new id will be 0, and the uuid will be newly generated.
     * @param context Current context
     *                will be {@code null} when doNew==false !
     */
    private BooklistStyle(@NonNull final Parcel in,
                          final boolean isNew,
                          @Nullable final Context context) {
        mId = in.readLong();
        mNameResId = in.readInt();
        //noinspection ConstantConditions
        mUuid = in.readString();
        if (isNew) {
            mUuid = createUniqueName();
        }

        // only init the prefs once we have a valid uuid
        initPrefs();
        mName.set(in);

        // create new clone ?
        if (isNew) {
            // get a copy of the name first
            //noinspection ConstantConditions
            setName(getLabel(context));
            // now reset the other identifiers.
            mId = 0;
            mNameResId = 0;
        }

        mIsPreferred.set(in);
        mScaleFontSize.set(in);
        mThumbnailScale.set(in);
        mShowHeaderInfo.set(in);
        mSortAuthorGivenNameFirst.set(in);
        mStyleGroups.set(in);

        mFetchExtrasByTask.set(in);
        for (PBoolean extra : mAllExtras.values()) {
            extra.set(in);
        }
        for (Filter filter : mAllFilters.values()) {
            filter.set(in);
        }
    }

    /**
     * Get the global default style, or if that fails, the builtin default style..
     *
     * @param db Database Access
     *
     * @return the style.
     */
    @NonNull
    public static BooklistStyle getDefaultStyle(@NonNull final DAO db) {

        // read the global user default, or if not present the hardcoded default.
        String uuid = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                       .getString(PREF_BL_STYLE_CURRENT_DEFAULT,
                                                  Builtin.DEFAULT_STYLE_UUID);
        // hard-coded default ?
        if (Builtin.DEFAULT_STYLE_UUID.equals(uuid)) {
            return Builtin.DEFAULT;
        }

        // check that the style really/still exists!
        BooklistStyle style = Helper.getStyles(db, true).get(uuid);

        if (style == null) {
            return Builtin.DEFAULT;
        }
        return style;
    }

    /**
     * Get the specified style.
     *
     * @param db   Database Access
     * @param uuid of the style to get.
     *
     * @return the style, or if not found, the default style.
     */
    @NonNull
    public static BooklistStyle getStyle(@NonNull final DAO db,
                                         @NonNull final String uuid) {
        BooklistStyle style = Helper.getUserStyles(db).get(uuid);
        if (style != null) {
            return style;
        }

        style = Builtin.getStyles().get(uuid);
        if (style != null) {
            return style;
        }

        return getDefaultStyle(db);
    }

    /**
     * Used in migration/import. Convert the style name to a uuid.
     * <strong>Note:</strong> only the current Locale is used. Importing any styles
     * saved with a different Locale might trigger a false negative.
     *
     * @param context Current context
     * @param name    of the style
     *
     * @return style uuid
     */
    @NonNull
    public static BooklistStyle getStyle(@NonNull final Context context,
                                         @NonNull final String name) {

        // try user-defined first - users can clone a builtin style and use the identical name.
        try (DAO db = new DAO()) {
            for (BooklistStyle style : Helper.getUserStyles(db).values()) {
                if (style.getLabel(context).equals(name)) {
                    return style;
                }
            }
        }

        // check builtin.
        for (BooklistStyle style : Builtin.getStyles().values()) {
            if (style.getLabel(context).equals(name)) {
                return style;
            }
        }

        // not found...
        return Builtin.DEFAULT;
    }


    /**
     * @return the SharedPreference
     */
    @NonNull
    private SharedPreferences getPrefs() {
        return App.getAppContext().getSharedPreferences(mUuid, Context.MODE_PRIVATE);
    }

    /**
     * Only ever init the Preferences if you have a valid UUID.
     */
    private void initPrefs() {

        mName = new PString(Prefs.pk_bob_style_name, mUuid, isUserDefined());

        mStyleGroups = new PStyleGroups(mUuid, isUserDefined());

        mIsPreferred = new PBoolean(Prefs.pk_bob_preferred_style, mUuid, isUserDefined());

        mSortAuthorGivenNameFirst = new PBoolean(Prefs.pk_bob_sort_author_name, mUuid,
                                                 isUserDefined());

        mShowHeaderInfo = new PBitmask(Prefs.pk_bob_header, mUuid, isUserDefined(),
                                       SUMMARY_SHOW_ALL);

        mScaleFontSize = new PInteger(Prefs.pk_bob_text_size, mUuid, isUserDefined(),
                                      TEXT_SCALE_MEDIUM);

        mThumbnailScale = new PInteger(Prefs.pk_bob_cover_size, mUuid, isUserDefined(),
                                       ImageUtils.SCALE_MEDIUM);

        mFetchExtrasByTask = new PBoolean(Prefs.pk_bob_use_task_for_extras, mUuid, isUserDefined(),
                                          true);

        // all extra details for book-rows.
        mAllExtras = new LinkedHashMap<>();

        mAllExtras.put(Prefs.pk_bob_show_thumbnails,
                       new PBoolean(Prefs.pk_bob_show_thumbnails, mUuid, isUserDefined(), true));

        mAllExtras.put(Prefs.pk_bob_show_bookshelves,
                       new PBoolean(Prefs.pk_bob_show_bookshelves, mUuid, isUserDefined()));

        mAllExtras.put(Prefs.pk_bob_show_location,
                       new PBoolean(Prefs.pk_bob_show_location, mUuid, isUserDefined()));

        mAllExtras.put(Prefs.pk_bob_show_author,
                       new PBoolean(Prefs.pk_bob_show_author, mUuid, isUserDefined()));

        mAllExtras.put(Prefs.pk_bob_show_publisher,
                       new PBoolean(Prefs.pk_bob_show_publisher, mUuid, isUserDefined()));

        mAllExtras.put(Prefs.pk_bob_show_pub_date,
                       new PBoolean(Prefs.pk_bob_show_pub_date, mUuid, isUserDefined()));

        mAllExtras.put(Prefs.pk_bob_show_isbn,
                       new PBoolean(Prefs.pk_bob_show_isbn, mUuid, isUserDefined()));

        mAllExtras.put(Prefs.pk_bob_show_format,
                       new PBoolean(Prefs.pk_bob_show_format, mUuid, isUserDefined()));

        // all filters
        mAllFilters = new LinkedHashMap<>();

        mAllFilters.put(Prefs.pk_bob_filter_read,
                        new BooleanFilter(R.string.lbl_read,
                                          Prefs.pk_bob_filter_read,
                                          mUuid, isUserDefined(),
                                          DBDefinitions.TBL_BOOKS,
                                          DBDefinitions.DOM_BOOK_READ));

        mAllFilters.put(Prefs.pk_bob_filter_signed,
                        new BooleanFilter(R.string.lbl_signed,
                                          Prefs.pk_bob_filter_signed,
                                          mUuid, isUserDefined(),
                                          DBDefinitions.TBL_BOOKS,
                                          DBDefinitions.DOM_BOOK_SIGNED));

        mAllFilters.put(Prefs.pk_bob_filter_anthology,
                        new BooleanFilter(R.string.lbl_anthology,
                                          Prefs.pk_bob_filter_anthology,
                                          mUuid, isUserDefined(),
                                          DBDefinitions.TBL_BOOKS,
                                          DBDefinitions.DOM_BOOK_TOC_BITMASK));

        mAllFilters.put(Prefs.pk_bob_filter_loaned,
                        new BooleanFilter(R.string.lbl_loaned,
                                          Prefs.pk_bob_filter_loaned,
                                          mUuid, isUserDefined(),
                                          DBDefinitions.TBL_BOOKS,
                                          DBDefinitions.DOM_LOANEE));

        mAllFilters.put(Prefs.pk_bob_filter_editions,
                        new BitmaskFilter(R.string.lbl_edition,
                                          Prefs.pk_bob_filter_editions,
                                          mUuid, isUserDefined(),
                                          DBDefinitions.TBL_BOOKS,
                                          DBDefinitions.DOM_BOOK_EDITION_BITMASK));
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
        mScaleFontSize.writeToParcel(dest);
        mThumbnailScale.writeToParcel(dest);
        mShowHeaderInfo.writeToParcel(dest);
        mSortAuthorGivenNameFirst.writeToParcel(dest);
        mStyleGroups.writeToParcel(dest);

        mFetchExtrasByTask.writeToParcel(dest);
        for (PBoolean extra : mAllExtras.values()) {
            extra.writeToParcel(dest);
        }
        for (Filter filter : mAllFilters.values()) {
            filter.writeToParcel(dest);
        }
    }

    /**
     * create + set the UUID.
     *
     * @return the UUID
     */
    @NonNull
    private String createUniqueName() {
        mUuid = UUID.randomUUID().toString();
        getPrefs().edit().putString(PREF_STYLE_UUID, mUuid).apply();
        return mUuid;
    }

    /**
     * @return the UUID
     */
    @NonNull
    public String getUuid() {
        return mUuid;
    }

    /**
     * Accessor.
     * Positive ID's: user-defined styles
     * Negative ID's: builtin styles
     * 0: a user-defined style which has not been saved yet
     */
    @Override
    public long getId() {
        return mId;
    }

    public void setId(final long id) {
        mId = id;
    }

    @Override
    public String getLabel() {
        throw new IllegalStateException("Use getLabel(Context)");
    }

    /**
     * @param context Current context
     *
     * @return the system name or user-defined name based on kind of style this object defines.
     */
    @NonNull
    public String getLabel(@NonNull final Context context) {
        if (mNameResId != 0) {
            return context.getString(mNameResId);
        } else {
            return mName.get();
        }
    }

    /**
     * Set both the internal name and the display-name.
     *
     * @param name to set
     */
    private void setName(@NonNull final String name) {
        mName.set(name);
    }

    /**
     * @return {@code true} if this style is user defined.
     */
    public boolean isUserDefined() {
        return mNameResId == 0;
    }

    /**
     * @return {@code true} if the style is among preferred styles.
     */
    public boolean isPreferred() {
        return mIsPreferred.isTrue();
    }

    /**
     * @param isPreferred set to {@code true} if the style should become a preferred style.
     */
    public void setPreferred(final boolean isPreferred) {
        mIsPreferred.set(isPreferred);
    }

    /**
     * store the current style as the global default one.
     */
    public void setDefault() {
        PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                         .edit().putString(PREF_BL_STYLE_CURRENT_DEFAULT, mUuid)
                         .apply();
    }

    /**
     * Get all of the preferences of this Style and its groups/filters.
     *
     * @param all {@code false} for only the 'flat' Preferences
     *            {@code true} add also the groups/filters...
     *
     * @return map with all preferences for this style
     */
    @NonNull
    public Map<String, PPref> getPreferences(final boolean all) {
        @SuppressLint("UseSparseArrays")
        Map<String, PPref> map = new HashMap<>();
        // essential property for user-defined styles 'name'
        map.put(mName.getKey(), mName);

        // is a preferred style
        map.put(mIsPreferred.getKey(), mIsPreferred);
        // relative scaling of fonts
        map.put(mScaleFontSize.getKey(), mScaleFontSize);
        // size of thumbnails to use.
        map.put(mThumbnailScale.getKey(), mThumbnailScale);
        // list header information shown
        map.put(mShowHeaderInfo.getKey(), mShowHeaderInfo);

        // properties that can be shown as extra information for each line in the book list
        for (PBoolean extra : mAllExtras.values()) {
            map.put(extra.getKey(), extra);
        }
        map.put(mFetchExtrasByTask.getKey(), mFetchExtrasByTask);

        // sorting
        map.put(mSortAuthorGivenNameFirst.getKey(), mSortAuthorGivenNameFirst);

        // the groups that are used by the style
        map.put(mStyleGroups.getKey(), mStyleGroups);

        if (all) {
            // all filters (both active and non-active)
            for (Filter filter : mAllFilters.values()) {
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
     */
    public void updatePreferences(@NonNull final Map<String, PPref> newPrefs) {
        SharedPreferences.Editor ed = getPrefs().edit();
        updatePreferences(ed, newPrefs);
        ed.apply();
    }

    /**
     * update the preferences of this style based on the values of the passed preferences.
     * Preferences we don't have will be not be added.
     */
    private void updatePreferences(@NonNull final SharedPreferences.Editor ed,
                                   @NonNull final Map<String, PPref> newPrefs) {
        Map<String, PPref> currentPreferences = getPreferences(true);

        for (PPref p : newPrefs.values()) {
            // do we have this Preference ?
            PPref ourPPref = currentPreferences.get(p.getKey());
            if (ourPPref != null) {
                // if we do, then update our value
                //noinspection unchecked
                ourPPref.set(ed, p.get());
            }
        }
    }

    /**
     * Whether the user prefers the book details (extras) to be fetched in the background,
     * or immediately.
     *
     * @return {@code true} when a background task should be used.
     */
    public boolean extrasByTask() {
        return mFetchExtrasByTask.isTrue();
    }

    /**
     * Whether the user prefers the Author names sorted by Given names, or by Family name first.
     *
     * @return {@code true} when Given names should come first
     */
    boolean sortAuthorByGiven() {
        return mSortAuthorGivenNameFirst.isTrue();
    }

    public int getShowHeaderInfo() {
        return mShowHeaderInfo.get();
    }

    /**
     * Check if the style can show the passed level.
     *
     * @param level to check, 1-based.
     *
     * @return {@code true} if this style can show the desired level
     */
    public boolean hasHeaderForLevel(@IntRange(from = 1, to = 2) final int level) {
        switch (level) {
            case 1:
                return (mShowHeaderInfo.get() & BooklistStyle.SUMMARY_SHOW_LEVEL_1) != 0;
            case 2:
                return (mShowHeaderInfo.get() & BooklistStyle.SUMMARY_SHOW_LEVEL_2) != 0;

            default:
                // paranoia
                return false;
        }
    }

    /**
     * Get the scaling factor to apply to text size if needed.
     *
     * @return scale
     */
    public float getScaleFactor() {
        switch (mScaleFontSize.get()) {
            case TEXT_SCALE_LARGE:
                return 1.2f;
            case TEXT_SCALE_SMALL:
                return 0.8f;
            case TEXT_SCALE_MEDIUM:
            default:
                return 1.0f;
        }
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     */
    @SuppressWarnings("SameParameterValue")
    private void setScale(
            @IntRange(from = TEXT_SCALE_SMALL, to = TEXT_SCALE_LARGE) final int size) {
        mScaleFontSize.set(size);
    }

    /**
     * Get the scaling factor to apply to images, or zero if images should not be shown.
     *
     * @return scale
     */
    public int getThumbnailScaleFactor() {
        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_thumbnails).isFalse()) {
            return 0;
        }

        return mThumbnailScale.get();
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     */
    @SuppressWarnings("SameParameterValue")
    private void setShowAuthor(final boolean show) {
        //noinspection ConstantConditions
        mAllExtras.get(Prefs.pk_bob_show_author).set(show);
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     */
    @SuppressWarnings("SameParameterValue")
    private void setShowThumbnails(final boolean show) {
        //noinspection ConstantConditions
        mAllExtras.get(Prefs.pk_bob_show_thumbnails).set(show);
    }

    /**
     * @return all groups assigned to this style.
     */
    @NonNull
    public List<BooklistGroup> getGroups() {
        return mStyleGroups.getGroups();
    }

    /**
     * Passed a template style, copy the group structure to this style.
     */
    @SuppressWarnings("unused")
    public void setGroups(@NonNull final BooklistStyle source) {

        // Save the current groups
        Map<Integer, BooklistGroup> currentGroups = new LinkedHashMap<>();
        for (BooklistGroup group : mStyleGroups.getGroups()) {
            currentGroups.put(group.getKind(), group);
        }

        // we'll collect the new Preferences to add here
        Map<String, PPref> allGroupsPreferences = new LinkedHashMap<>();

        // Clear the current groups, and rebuild, reusing old values where possible
        mStyleGroups.clear();
        for (BooklistGroup newGroup : source.getGroups()) {
            BooklistGroup current = currentGroups.get(newGroup.getKind());
            // if we don't have the new one...
            if (current == null) {
                // copy the groups PPrefs locally
                allGroupsPreferences.putAll(newGroup.getPreferences());
                // and add a new instance of that group
                mStyleGroups.add(BooklistGroup.newInstance(newGroup.getKind(),
                                                           mUuid, isUserDefined()));
            } else {
                // otherwise, just re-add our (old) current group.
                mStyleGroups.add(current);
            }
        }

        // Lastly, copy any Preference values from the new groups.
        updatePreferences(allGroupsPreferences);
    }

    /**
     * Add an already existing instance.
     *
     * @param group to add
     */
    public void addGroup(@NonNull final BooklistGroup group) {
        mStyleGroups.add(group);
    }

    /**
     * Remove a group from this style.
     *
     * @param group kind to remove.
     */
    public void removeGroup(final int group) {
        mStyleGroups.remove(group);
    }

    /**
     * @param context Current context
     *
     * @return a list of in-use group names in a human readable format.
     */
    @NonNull
    public String getGroupLabels(@NonNull final Context context) {
        return Csv.join(", ", mStyleGroups.getGroups(), element -> element.getName(context));
    }

    /**
     * @return {@code true} if this style has the specified group.
     */
    public boolean hasGroupKind(
            @IntRange(from = 0, to = BooklistGroup.RowKind.ROW_KIND_MAX) final int kind) {
        return mStyleGroups.getGroupKinds().contains(kind);
    }

    /**
     * @return the group at the passed index.
     */
    @NonNull
    BooklistGroup getGroupAt(final int index) {
        return mStyleGroups.getGroups().get(index);
    }

    /**
     * @return the group kind at the passed index.
     */
    int getGroupKindAt(final int index) {
        return mStyleGroups.getGroupKindAt(index);
    }

    /**
     * @return the number of groups in this style.
     */
    public int groupCount() {
        return mStyleGroups.size();
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     */
    @SuppressWarnings("SameParameterValue")
    private void setFilter(@NonNull final String key,
                           final boolean value) {
        //noinspection ConstantConditions
        ((BooleanFilter) mAllFilters.get(key)).set(value);
    }

    /**
     * @return Filters (active and non-active)
     */
    @NonNull
    public Collection<Filter> getFilters() {
        return mAllFilters.values();
    }

    /**
     * Convenience method for use in the Preferences screen.
     *
     * @param all {@code true} to get all, {@code false} for only the active filters
     *
     * @return the list of in-use filter names in a human readable format.
     */
    public List<String> getFilterLabels(@NonNull final Context context,
                                        final boolean all) {
        List<String> labels = new ArrayList<>();
        for (Filter filter : mAllFilters.values()) {
            if (filter.isActive() || all) {
                labels.add(filter.getLabel(context));
            }
        }
        Collections.sort(labels);
        return labels;
    }

    /**
     * Get the list of in-use extra-field names in a human readable format.
     * This is used to set the summary of the PreferenceScreen.
     *
     * @return list of labels, can be empty, but never {@code null}
     */
    @NonNull
    public List<String> getExtraFieldsLabels(@NonNull final Context context) {
        List<String> labels = new ArrayList<>();

        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_thumbnails).isTrue()) {
            labels.add(context.getString(R.string.pt_bob_thumbnails_show));
        }
        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_bookshelves).isTrue()) {
            labels.add(context.getString(R.string.lbl_bookshelves));
        }
        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_location).isTrue()) {
            labels.add(context.getString(R.string.lbl_location));
        }
        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_author).isTrue()) {
            labels.add(context.getString(R.string.lbl_author));
        }
        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_publisher).isTrue()) {
            labels.add(context.getString(R.string.lbl_publisher));
        }
        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_pub_date).isTrue()) {
            labels.add(context.getString(R.string.lbl_date_published));
        }
        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_isbn).isTrue()) {
            labels.add(context.getString(R.string.lbl_isbn));
        }
        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_format).isTrue()) {
            labels.add(context.getString(R.string.lbl_format));
        }

        Collections.sort(labels);
        return labels;
    }

    /**
     * A quicker way of getting the status of all extra-fields in one go instead of implementing
     * individual getters for each.
     *
     * @return bitmask with the 'extra' fields that are in use (visible) for this style.
     */
    public int getExtraFieldsStatus() {
        int extras = 0;

        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_thumbnails).isTrue()) {
            extras |= EXTRAS_THUMBNAIL;
        }

        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_bookshelves).isTrue()) {
            extras |= EXTRAS_BOOKSHELVES;
        }

        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_location).isTrue()) {
            extras |= EXTRAS_LOCATION;
        }

        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_publisher).isTrue()) {
            extras |= EXTRAS_PUBLISHER;
        }

        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_pub_date).isTrue()) {
            extras |= EXTRAS_PUB_DATE;
        }

        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_isbn).isTrue()) {
            extras |= EXTRAS_ISBN;
        }

        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_format).isTrue()) {
            extras |= EXTRAS_FORMAT;
        }

        //noinspection ConstantConditions
        if (mAllExtras.get(Prefs.pk_bob_show_author).isTrue()) {
            extras |= EXTRAS_AUTHOR;
        }

        return extras;
    }

    /**
     * Construct a clone of this object with id==0, and a new uuid.
     * <p>
     * TODO: have a think... don't use Parceling, but simply copy the prefs + db entry.
     *
     * @param context Current context
     */
    @NonNull
    public BooklistStyle clone(@NonNull final Context context) {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();

        parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        BooklistStyle clone = new BooklistStyle(parcel, true, context);
        parcel.recycle();

        return clone;
    }

    /**
     * Save the style to the database. This is now limited to the UUID.
     * All actual settings reside in a dedicated SharedPreference file.
     * <p>
     * if an insert fails, the style retains id==0.
     *
     * @param db Database Access
     */
    public void insert(@NonNull final DAO db) {
        if (!isUserDefined()) {
            throw new IllegalStateException("Builtin Style cannot be saved to database");
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
     * @param db Database Access
     */
    public void delete(@NonNull final DAO db) {
        // cannot delete a builtin or a 'new' style(id==0)
        if (mId == 0 || !isUserDefined()) {
            throw new IllegalArgumentException("Builtin Style cannot be deleted");
        }

        Helper.S_USER_STYLES.remove(mUuid);
        db.deleteStyle(mUuid);

        if (Build.VERSION.SDK_INT >= 24) {
            App.getAppContext().deleteSharedPreferences(mUuid);
        } else {
            getPrefs().edit().clear().apply();
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

        // should never happen, famous last words...
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
               + "\nmScaleFontSize=" + mScaleFontSize
               + "\nmShowHeaderInfo=" + mShowHeaderInfo
               + "\nmSortAuthorGivenNameFirst=" + mSortAuthorGivenNameFirst
               + "\nmThumbnailScale=" + mThumbnailScale
               + "\nmStyleGroups=" + mStyleGroups

               + "\nmFetchExtrasByTask=" + mFetchExtrasByTask
               + "\nmAllExtras=" + mAllExtras
               + "\nmAllFilters=\n" + mAllFilters
               + '}';
    }

    /**
     * Check if a particular 'Extra' field, or a particular group (the display-domain), is in use.
     *
     * @param key to check
     *
     * @return {@code true} if in use
     */
    public boolean isUsed(@NonNull final String key) {
        if (mAllExtras.containsKey(key)) {
            //noinspection ConstantConditions
            return App.isUsed(key) && mAllExtras.get(key).isTrue();

        } else {
            for (BooklistGroup group : getGroups()) {
                if (group.getDisplayDomain().getName().equals(key)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Wrapper that gets the showAuthorGivenNameFirst flag from the Author group if we have it,
     * or from the global defaults.
     *
     * @param context Current context
     *
     * @return {@code true} if we want "given-names last-name" formatted authors.
     */
    public boolean showAuthorGivenNameFirst(@NonNull final Context context) {
        if (hasGroupKind(BooklistGroup.RowKind.AUTHOR)) {
            BooklistGroup.BooklistAuthorGroup authorGroup =
                    (BooklistGroup.BooklistAuthorGroup)
                            (mStyleGroups.getGroupForKind(BooklistGroup.RowKind.AUTHOR));
            if (authorGroup != null) {
                return authorGroup.showAuthorGivenNameFirst();
            }
        }
        // return the global default.
        return BooklistGroup.BooklistAuthorGroup.globalShowGivenNameFirst(context);
    }

    /**
     * Fronts an {@code ArrayList<BooklistGroup>} with backend storage in a preference.
     */
    private static class PStyleGroups
            extends PIntList {

        private final ArrayList<BooklistGroup> mGroups = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param uuid          the UUID of the style
         * @param isUserDefined Flag to indicate this is a user style or a builtin style
         */
        PStyleGroups(@NonNull final String uuid,
                     final boolean isUserDefined) {
            super(Prefs.pk_bob_groups, uuid, isUserDefined);

            // load the group ID's from the SharedPreference and populates the Group object list.
            mGroups.clear();
            for (int kind : get()) {
                mGroups.add(BooklistGroup.newInstance(kind, uuid, isUserDefined));
            }
        }

        @NonNull
        List<BooklistGroup> getGroups() {
            return mGroups;
        }

        @Nullable
        BooklistGroup getGroupForKind(@SuppressWarnings("SameParameterValue") final int kind) {
            for (BooklistGroup group : mGroups) {
                if (group.getKind() == kind) {
                    return group;
                }
            }
            return null;
        }

        @NonNull
        List<Integer> getGroupKinds() {
            return get();
        }

        int getGroupKindAt(final int index) {
            //noinspection ConstantConditions
            return (int) get().toArray()[index];
        }

        int size() {
            return get().size();
        }

        @Override
        public void set(@NonNull final Parcel in) {
            clear();
            in.readList(mGroups, getClass().getClassLoader());
            writeGroupIds();
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
         * @param group to add
         */
        void add(@NonNull final BooklistGroup group) {
            mGroups.add(group);
            super.add(group.getKind());
        }

        /**
         * We need the *kind* of group to remove (and NOT the group itself),
         * so we can (optionally) replace it with a new (different) copy.
         *
         * @param kind of group to remove
         */
        void remove(final int kind) {
            Iterator<BooklistGroup> it = mGroups.iterator();
            while (it.hasNext()) {
                int groupKind = it.next().getKind();
                if (groupKind == kind) {
                    it.remove();
                    super.remove(groupKind);
                }
            }
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest) {
            dest.writeList(mGroups);
        }

        @Override
        public void clear() {
            mGroups.clear();
            super.clear();
        }

        @Override
        public void add(@NonNull final Integer element) {
            throw new IllegalStateException("use add(BooklistGroup) instead");
        }

        @Override
        public void remove(@NonNull final Integer element) {
            throw new IllegalStateException("use remove(BooklistGroup) instead");
        }

        /**
         * Store the kind of all groups in SharedPreference.
         */
        private void writeGroupIds() {
            List<Integer> list = new ArrayList<>();
            for (BooklistGroup group : mGroups) {
                list.add(group.getKind());
            }
            set(list);
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

        public static void reload() {
            S_USER_STYLES.clear();
        }

        /**
         * Get the user-defined Styles from the database.
         *
         * @param db Database Access
         *
         * @return ordered map of BooklistStyle
         */
        @NonNull
        public static Map<String, BooklistStyle> getUserStyles(@NonNull final DAO db) {
            if (S_USER_STYLES.size() == 0) {
                S_USER_STYLES.putAll(db.getUserStyles());
            }
            return S_USER_STYLES;
        }

        /**
         * Get an ordered Map with all the styles.
         * The preferred styles are at the front of the list.
         *
         * @param db  Database Access
         * @param all if {@code true} then also return the non-preferred styles
         *
         * @return ordered list
         */
        @NonNull
        public static Map<String, BooklistStyle> getStyles(@NonNull final DAO db,
                                                           final boolean all) {
            // Get all styles: user
            Map<String, BooklistStyle> allStyles = getUserStyles(db);
            // Get all styles: builtin
            allStyles.putAll(Builtin.getStyles());

            // filter, so the list only shows the preferred ones.
            Map<String, BooklistStyle> styles = filterPreferredStyles(allStyles);

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
         * @param allStyles a list of styles
         *
         * @return ordered list.
         */
        @NonNull
        private static Map<String, BooklistStyle> filterPreferredStyles(
                @NonNull final Map<String, BooklistStyle> allStyles) {

            Map<String, BooklistStyle> resultingStyles = new LinkedHashMap<>();

            // first check the saved and ordered list
            for (String uuid : getMenuOrder()) {
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
                if (style.isPreferred() && !resultingStyles.containsKey(style.getUuid())) {
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
         * @return set of UUIDs
         */
        @NonNull
        private static Set<String> getMenuOrder() {
            Set<String> uuidSet = new LinkedHashSet<>();
            String itemsStr = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
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
         * @param uuidSet of style UUIDs
         */
        private static void setMenuOrder(@NonNull final Set<String> uuidSet) {
            PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                             .edit()
                             .putString(PREF_BL_PREFERRED_STYLES, TextUtils.join(",", uuidSet))
                             .apply();
        }

        /**
         * Add a style (its uuid) to the menu list of preferred styles.
         *
         * @param style to add.
         */
        public static void addPreferredStyle(@NonNull final BooklistStyle style) {
            Set<String> list = getMenuOrder();
            list.add(style.getUuid());
            setMenuOrder(list);
        }

        /**
         * Save the preferred style menu list.
         * <p>
         * This list contains the ID's for user-defined *AND* system-styles.
         *
         * @param styles full list of preferred styles to save 'in order'
         */
        public static void saveMenuOrder(@NonNull final List<BooklistStyle> styles) {
            Set<String> list = new LinkedHashSet<>();
            for (BooklistStyle style : styles) {
                if (style.isPreferred()) {
                    list.add(style.getUuid());
                }
            }
            setMenuOrder(list);
        }
    }

    /**
     * Collection of system-defined Book List styles.
     * <p>
     * The UUID's should never be changed.
     */
    public static final class Builtin {

        // NEWKIND: BooklistStyle. Make sure to update the max id when adding a style!
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
        /**
         * Hardcoded initial/default style. Avoids having the create the full set of styles just
         * to load the default one.
         */
        public static final BooklistStyle DEFAULT =
                new BooklistStyle(AUTHOR_THEN_SERIES_ID,
                                  AUTHOR_THEN_SERIES_UUID,
                                  R.string.style_builtin_author_series,
                                  BooklistGroup.RowKind.AUTHOR,
                                  BooklistGroup.RowKind.SERIES);
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

        private Builtin() {
        }

        /**
         * Static method to get all builtin styles.
         * <p>
         * <strong>Note:</strong> Do NOT call this in static initialization of application.
         * This method requires the application context to be present.
         *
         * @return a collection of all builtin styles.
         */
        @NonNull
        private static Map<String, BooklistStyle> getStyles() {

            if (S_BUILTIN_STYLES.size() == 0) {
                create();
            }
            return S_BUILTIN_STYLES;
        }

        private static void create() {
            BooklistStyle style;

            // Author/Series
            S_BUILTIN_STYLES.put(DEFAULT.getUuid(), DEFAULT);

            // Unread
            style = new BooklistStyle(UNREAD_AUTHOR_THEN_SERIES_ID,
                                      UNREAD_AUTHOR_THEN_SERIES_UUID,
                                      R.string.style_builtin_unread,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);
            style.setFilter(Prefs.pk_bob_filter_read, false);

            // Compact
            style = new BooklistStyle(COMPACT_ID,
                                      COMPACT_UUID,
                                      R.string.style_builtin_compact,
                                      BooklistGroup.RowKind.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);
            style.setScale(TEXT_SCALE_SMALL);
            style.setShowThumbnails(false);

            // Title
            style = new BooklistStyle(TITLE_FIRST_LETTER_ID,
                                      TITLE_FIRST_LETTER_UUID,
                                      R.string.style_builtin_title_first_letter,
                                      BooklistGroup.RowKind.TITLE_LETTER);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Series
            style = new BooklistStyle(SERIES_ID,
                                      SERIES_UUID,
                                      R.string.style_builtin_series,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Genre
            style = new BooklistStyle(GENRE_ID,
                                      GENRE_UUID,
                                      R.string.style_builtin_genre,
                                      BooklistGroup.RowKind.GENRE,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Loaned
            style = new BooklistStyle(LENDING_ID,
                                      LENDING_UUID,
                                      R.string.style_builtin_loaned,
                                      BooklistGroup.RowKind.LOANED,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Read & Unread
            style = new BooklistStyle(READ_AND_UNREAD_ID,
                                      READ_AND_UNREAD_UUID,
                                      R.string.style_builtin_read_and_unread,
                                      BooklistGroup.RowKind.READ_STATUS,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Publication date
            style = new BooklistStyle(PUBLICATION_DATA_ID,
                                      PUBLICATION_DATA_UUID,
                                      R.string.style_builtin_publication_date,
                                      BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                      BooklistGroup.RowKind.DATE_PUBLISHED_MONTH,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Added date
            style = new BooklistStyle(DATE_ADDED_ID,
                                      DATE_ADDED_UUID,
                                      R.string.style_builtin_added_date,
                                      BooklistGroup.RowKind.DATE_ADDED_YEAR,
                                      BooklistGroup.RowKind.DATE_ADDED_MONTH,
                                      BooklistGroup.RowKind.DATE_ADDED_DAY,
                                      BooklistGroup.RowKind.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Acquired date
            style = new BooklistStyle(DATE_ACQUIRED_ID,
                                      DATE_ACQUIRED_UUID,
                                      R.string.style_builtin_acquired_date,
                                      BooklistGroup.RowKind.DATE_ACQUIRED_YEAR,
                                      BooklistGroup.RowKind.DATE_ACQUIRED_MONTH,
                                      BooklistGroup.RowKind.DATE_ACQUIRED_DAY,
                                      BooklistGroup.RowKind.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Author/Publication date
            style = new BooklistStyle(AUTHOR_AND_YEAR_ID,
                                      AUTHOR_AND_YEAR_UUID,
                                      R.string.style_builtin_author_year,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.DATE_PUBLISHED_YEAR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Format
            style = new BooklistStyle(FORMAT_ID,
                                      FORMAT_UUID,
                                      R.string.style_builtin_format,
                                      BooklistGroup.RowKind.FORMAT);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Read date
            style = new BooklistStyle(DATE_READ_ID,
                                      DATE_READ_UUID,
                                      R.string.style_builtin_read_date,
                                      BooklistGroup.RowKind.DATE_READ_YEAR,
                                      BooklistGroup.RowKind.DATE_READ_MONTH,
                                      BooklistGroup.RowKind.AUTHOR);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Location
            style = new BooklistStyle(LOCATION_ID,
                                      LOCATION_UUID,
                                      R.string.style_builtin_location,
                                      BooklistGroup.RowKind.LOCATION,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Location
            style = new BooklistStyle(LANGUAGE_ID,
                                      LANGUAGE_UUID,
                                      R.string.style_builtin_language,
                                      BooklistGroup.RowKind.LANGUAGE,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Rating
            style = new BooklistStyle(RATING_ID,
                                      RATING_UUID,
                                      R.string.style_builtin_rating,
                                      BooklistGroup.RowKind.RATING,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Bookshelf
            style = new BooklistStyle(BOOKSHELF_ID,
                                      BOOKSHELF_UUID,
                                      R.string.style_builtin_bookshelf,
                                      BooklistGroup.RowKind.BOOKSHELF,
                                      BooklistGroup.RowKind.AUTHOR,
                                      BooklistGroup.RowKind.SERIES);
            S_BUILTIN_STYLES.put(style.getUuid(), style);

            // Update date
            style = new BooklistStyle(DATE_LAST_UPDATE_ID,
                                      DATE_LAST_UPDATE_UUID,
                                      R.string.style_builtin_update_date,
                                      BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR,
                                      BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH,
                                      BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY);
            S_BUILTIN_STYLES.put(style.getUuid(), style);
            style.setShowAuthor(true);

            // NEWKIND: BooklistStyle
        }
    }
}
