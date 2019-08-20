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
import android.os.Parcel;
import android.os.Parcelable;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
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
 * The database table now consists of a PK id, and a UUID column.<br>
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
    public static final int EXTRAS_THUMBNAIL = 0x100;

    /**
     * the amount of details to show in the header.
     */
    public static final Integer SUMMARY_HIDE = 0;
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
    private static final int TEXT_SCALE_LARGE = 3;

    /**
     * Unique name. This is a stored in our preference file (with the same name)
     * and is used for backup/restore purposes as the 'id'.
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
     * ID if string representing name of this style.
     * Used for standard system-defined styles
     * Always 0 for a user-defined style
     */
    @StringRes
    private int mNameResId;

    /**
     * Display name of this style.
     * <p>
     * Used for user-defined styles.
     * encapsulated value always {@code null} for a builtin style.
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

    /** Use normal or large thumbnails. */
    private PInteger mThumbnailScale;

    /**
     * Show list header info.
     * <p>
     * Ideally this would use a simple int, but {@link MultiSelectListPreference} insists on a Set.
     */
    private PBitmask mShowHeaderInfo;
    /** Sorting. */
    private PBoolean mSortAuthorGivenNameFirst;

    /** Show a thumbnail on each book row in the list. */
    private PBoolean mExtraShowThumbnails;
    /** Extra info to show on each book row in the list. */
    private PBoolean mExtraShowBookshelves;
    private PBoolean mExtraShowLocation;
    private PBoolean mExtraShowAuthor;
    private PBoolean mExtraShowPublisher;
    private PBoolean mExtraShowPubDate;
    private PBoolean mExtraShowIsbn;
    private PBoolean mExtraShowFormat;
    /** Fetch the extras using ot task or not. */
    private PBoolean mExtrasByTask;

    /** All groups in this style. */
    private PStyleGroups mStyleGroups;

    /**
     * All filters.
     * <p>
     * The key in the Map is the actual preference key.
     */
    private Map<String, Filter> mFilters;

    @SuppressWarnings("FieldNotUsedInToString")
    private BooleanFilter mFilterRead;
    @SuppressWarnings("FieldNotUsedInToString")
    private BooleanFilter mFilterSigned;
    @SuppressWarnings("FieldNotUsedInToString")
    private BooleanFilter mFilterAnthology;
    @SuppressWarnings("FieldNotUsedInToString")
    private BooleanFilter mFilterLoaned;
    @SuppressWarnings("FieldNotUsedInToString")
    private BitmaskFilter mFilterEdition;

    /**
     * Constructor for system-defined styles.
     *
     * @param id     a negative int
     * @param uuid   the hardcoded UUID for the builtin style.
     * @param nameId the resource id for the name
     * @param kinds  a list of group kinds to attach to this style
     */
    BooklistStyle(@IntRange(from = -100, to = -1) final long id,
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
     * Only used when styles are loaded from storage.
     * Real new styles are created by cloning an existing style.
     *
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

        mStyleGroups.set(in);

        mExtraShowThumbnails.set(in);
        mExtraShowBookshelves.set(in);
        mExtraShowLocation.set(in);
        mExtraShowAuthor.set(in);
        mExtraShowPublisher.set(in);
        mExtraShowPubDate.set(in);
        mExtraShowIsbn.set(in);
        mExtraShowFormat.set(in);

        mExtrasByTask.set(in);

        mSortAuthorGivenNameFirst.set(in);

        mFilterRead.set(in);
        mFilterSigned.set(in);
        mFilterAnthology.set(in);
        mFilterLoaned.set(in);
        mFilterEdition.set(in);
    }

    /**
     * @return the SharedPreference
     */
    @NonNull
    private SharedPreferences getPrefs() {
        return App.getAppContext().getSharedPreferences(mUuid, Context.MODE_PRIVATE);
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

        // all extra details for book-rows.
        mExtraShowThumbnails = new PBoolean(Prefs.pk_bob_show_thumbnails, mUuid, isUserDefined(),
                                            true);
        mExtraShowBookshelves = new PBoolean(Prefs.pk_bob_show_bookshelves, mUuid, isUserDefined());
        mExtraShowLocation = new PBoolean(Prefs.pk_bob_show_location, mUuid, isUserDefined());
        mExtraShowAuthor = new PBoolean(Prefs.pk_bob_show_author, mUuid, isUserDefined());
        mExtraShowPublisher = new PBoolean(Prefs.pk_bob_show_publisher, mUuid, isUserDefined());
        mExtraShowPubDate = new PBoolean(Prefs.pk_bob_show_pub_date, mUuid, isUserDefined());
        mExtraShowIsbn = new PBoolean(Prefs.pk_bob_show_isbn, mUuid, isUserDefined());
        mExtraShowFormat = new PBoolean(Prefs.pk_bob_show_format, mUuid, isUserDefined());

        mExtrasByTask = new PBoolean(Prefs.pk_bob_use_task_for_extras, mUuid, isUserDefined(),
                                     true);

        // all filters
        mFilters = new LinkedHashMap<>();

        mFilterRead = new BooleanFilter(R.string.lbl_read,
                                        Prefs.pk_bob_filter_read,
                                        mUuid, isUserDefined(),
                                        DBDefinitions.TBL_BOOKS,
                                        DBDefinitions.DOM_BOOK_READ);
        mFilters.put(mFilterRead.getKey(), mFilterRead);

        mFilterSigned = new BooleanFilter(R.string.lbl_signed,
                                          Prefs.pk_bob_filter_signed,
                                          mUuid, isUserDefined(),
                                          DBDefinitions.TBL_BOOKS,
                                          DBDefinitions.DOM_BOOK_SIGNED);
        mFilters.put(mFilterSigned.getKey(), mFilterSigned);

        mFilterAnthology = new BooleanFilter(R.string.lbl_anthology,
                                             Prefs.pk_bob_filter_anthology,
                                             mUuid, isUserDefined(),
                                             DBDefinitions.TBL_BOOKS,
                                             DBDefinitions.DOM_BOOK_TOC_BITMASK);
        mFilters.put(mFilterAnthology.getKey(), mFilterAnthology);

        mFilterLoaned = new BooleanFilter(R.string.lbl_loaned,
                                          Prefs.pk_bob_filter_loaned,
                                          mUuid, isUserDefined(),
                                          DBDefinitions.TBL_BOOKS,
                                          DBDefinitions.DOM_LOANEE);
        mFilters.put(mFilterLoaned.getKey(), mFilterLoaned);

        mFilterEdition = new BitmaskFilter(R.string.lbl_edition,
                                           Prefs.pk_bob_filter_editions,
                                           mUuid, isUserDefined(),
                                           DBDefinitions.TBL_BOOKS,
                                           DBDefinitions.DOM_BOOK_EDITION_BITMASK);

        mFilters.put(mFilterEdition.getKey(), mFilterEdition);
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

        mStyleGroups.writeToParcel(dest);

        mExtraShowThumbnails.writeToParcel(dest);
        mExtraShowBookshelves.writeToParcel(dest);
        mExtraShowLocation.writeToParcel(dest);
        mExtraShowAuthor.writeToParcel(dest);
        mExtraShowPublisher.writeToParcel(dest);
        mExtraShowPubDate.writeToParcel(dest);
        mExtraShowIsbn.writeToParcel(dest);
        mExtraShowFormat.writeToParcel(dest);

        mExtrasByTask.writeToParcel(dest);

        mSortAuthorGivenNameFirst.writeToParcel(dest);

        mFilterRead.writeToParcel(dest);
        mFilterSigned.writeToParcel(dest);
        mFilterAnthology.writeToParcel(dest);
        mFilterLoaned.writeToParcel(dest);
        mFilterEdition.writeToParcel(dest);
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
                         .edit().putString(BooklistStyles.PREF_BL_STYLE_CURRENT_DEFAULT, mUuid)
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
        map.put(mExtraShowThumbnails.getKey(), mExtraShowThumbnails);
        map.put(mExtraShowBookshelves.getKey(), mExtraShowBookshelves);
        map.put(mExtraShowLocation.getKey(), mExtraShowLocation);
        map.put(mExtraShowAuthor.getKey(), mExtraShowAuthor);
        map.put(mExtraShowPublisher.getKey(), mExtraShowPublisher);
        map.put(mExtraShowPubDate.getKey(), mExtraShowPubDate);
        map.put(mExtraShowIsbn.getKey(), mExtraShowIsbn);
        map.put(mExtraShowFormat.getKey(), mExtraShowFormat);

        map.put(mExtrasByTask.getKey(), mExtrasByTask);

        // sorting
        map.put(mSortAuthorGivenNameFirst.getKey(), mSortAuthorGivenNameFirst);

        // the groups that are used by the style
        map.put(mStyleGroups.getKey(), mStyleGroups);

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
        return mExtrasByTask.isTrue();
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
            case TEXT_SCALE_SMALL:
                return 0.8f;
            case TEXT_SCALE_MEDIUM:
                return 1.0f;
            case TEXT_SCALE_LARGE:
                return 1.2f;
            default:
                return 1.0f;
        }
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     */
    @SuppressWarnings("SameParameterValue")
    void setScaleFactor(@IntRange(from = TEXT_SCALE_SMALL, to = TEXT_SCALE_LARGE) final int size) {
        mScaleFontSize.set(size);
    }

    /**
     * Get the scaling factor to apply to images, or zero if images should not be shown.
     *
     * @return scale
     */
    public int getThumbnailScaleFactor() {
        if (mExtraShowThumbnails.isFalse()) {
            return 0;
        }

        return mThumbnailScale.get();
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

        if (mExtraShowBookshelves.isTrue()) {
            extras |= EXTRAS_BOOKSHELVES;
        }

        if (mExtraShowLocation.isTrue()) {
            extras |= EXTRAS_LOCATION;
        }

        if (mExtraShowPublisher.isTrue()) {
            extras |= EXTRAS_PUBLISHER;
        }

        if (mExtraShowPubDate.isTrue()) {
            extras |= EXTRAS_PUB_DATE;
        }

        if (mExtraShowIsbn.isTrue()) {
            extras |= EXTRAS_ISBN;
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
     * Used by built-in styles only. Set by user via preferences screen.
     */
    @SuppressWarnings("SameParameterValue")
    void setShowAuthor(final boolean show) {
        mExtraShowAuthor.set(show);
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     */
    @SuppressWarnings("SameParameterValue")
    void setShowThumbnails(final boolean show) {
        mExtraShowThumbnails.set(show);
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
     * @return Filters (active and non-active)
     */
    @NonNull
    public Collection<Filter> getFilters() {
        return mFilters.values();
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
        for (Filter filter : mFilters.values()) {
            if (filter.isActive() || all) {
                labels.add(filter.getLabel(context));
            }
        }
        Collections.sort(labels);
        return labels;
    }

    /**
     * Used by built-in styles only. Set by user via preferences screen.
     */
    @SuppressWarnings("SameParameterValue")
    void setFilter(@NonNull final String key,
                   final boolean value) {
        //noinspection ConstantConditions
        ((BooleanFilter) mFilters.get(key)).set(value);
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
     * <p>
     * if an insert fails, the style retains id==0.
     */
    public void save(@NonNull final DAO db) {
        if (!isUserDefined()) {
            throw new IllegalStateException("Builtin Style cannot be saved to database");
        }

        // check if the style already exists.
        long existingId = db.getBooklistStyleIdByUuid(mUuid);
        if (existingId == 0) {
            db.insertBooklistStyle(this);
        } else {
            // force-update the id.
            mId = existingId;
        }
    }

    /**
     * Delete this style.
     */
    public void delete(@NonNull final DAO db) {
        // cannot delete a builtin or a 'new' style(id==0)
        if (mId == 0 || !isUserDefined()) {
            throw new IllegalArgumentException("Builtin Style cannot be deleted");
        }

        db.deleteBooklistStyle(mId);
        // ENHANCE: API: 24 -> App.getAppContext().deleteSharedPreferences(mUuid);
        getPrefs().edit().clear().apply();
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
               + "\nmExtraShowThumbnails=" + mExtraShowThumbnails
               + "\nmThumbnailScale=" + mThumbnailScale
               + "\nmExtrasByTask=" + mExtrasByTask
               + "\nmExtraShowBookshelves=" + mExtraShowBookshelves
               + "\nmExtraShowLocation=" + mExtraShowLocation
               + "\nmExtraShowAuthor=" + mExtraShowAuthor
               + "\nmExtraShowPublisher=" + mExtraShowPublisher
               + "\nmExtraShowPubDate=" + mExtraShowPubDate
               + "\nmExtraShowIsbn=" + mExtraShowIsbn
               + "\nmExtraShowFormat=" + mExtraShowFormat
               + "\nmStyleGroups=" + mStyleGroups
               + "\nmFilters=\n" + mFilters
               + '}';
    }

    public boolean isUsed(@NonNull final String key) {
        switch (key) {
            case UniqueId.BKEY_IMAGE:
                return App.isUsed(key) && mExtraShowThumbnails.isTrue();

            case DBDefinitions.KEY_AUTHOR_FORMATTED:
                return App.isUsed(key) && mExtraShowAuthor.isTrue();

            case DBDefinitions.KEY_BOOKSHELF:
                return App.isUsed(key) && mExtraShowBookshelves.isTrue();

            case DBDefinitions.KEY_FORMAT:
                return App.isUsed(key) && mExtraShowFormat.isTrue();

            case DBDefinitions.KEY_ISBN:
                return App.isUsed(key) && mExtraShowIsbn.isTrue();

            case DBDefinitions.KEY_LOCATION:
                return App.isUsed(key) && mExtraShowLocation.isTrue();

            case DBDefinitions.KEY_PUBLISHER:
                return App.isUsed(key) && mExtraShowPublisher.isTrue();

            case DBDefinitions.KEY_DATE_PUBLISHED:
                return App.isUsed(key) && mExtraShowPubDate.isTrue();

            default:
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
        BooklistGroup getGroupForKind(final int kind) {
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
         * Add a new element to the end of the list.
         *
         * @param list  current list to add the group to, can be {@code null} or empty.
         * @param group to add
         *
         * @return updated list string
         */
        @NonNull
        String add(@NonNull final SharedPreferences.Editor ed,
                   @Nullable final String list,
                   @NonNull final BooklistGroup group) {
            mGroups.add(group);
            return super.add(ed, list, group.getKind());
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
         * Walk the list of Groups, and store their kind in SharedPreference.
         */
        private void writeGroupIds() {
            List<Integer> list = new ArrayList<>();
            for (BooklistGroup group : mGroups) {
                list.add(group.getKind());
            }
            set(list);
        }
    }
}
