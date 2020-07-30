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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Combines a list of {@link Site} objects with the {@link Type}.
 */
public class SiteList
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<SiteList> CREATOR = new Creator<SiteList>() {
        @Override
        @NonNull
        public SiteList createFromParcel(@NonNull final Parcel in) {
            return new SiteList(in);
        }

        @Override
        @NonNull
        public SiteList[] newArray(final int size) {
            return new SiteList[size];
        }
    };

    /** Cached copy of the users preferred site order; one entry for each type of list. */
    private static final EnumMap<Type, SiteList> SITE_LIST_MAP = new EnumMap<>(Type.class);

    /** Type of this list. */
    @NonNull
    private final Type mType;

    /** The list. */
    @NonNull
    private ArrayList<Site> mList;

    /**
     * Constructor.
     *
     * @param type Type of this list.
     */
    public SiteList(@NonNull final Type type) {
        mType = type;
        mList = new ArrayList<>();
    }

    /**
     * Copy constructor.
     *
     * @param from object to copy
     */
    private SiteList(@NonNull final SiteList from) {
        mType = from.mType;
        mList = new ArrayList<>();
        for (Site site : from.mList) {
            mList.add(new Site(site));
        }
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private SiteList(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        mType = in.readParcelable(Type.class.getClassLoader());
        mList = new ArrayList<>();
        in.readTypedList(mList, Site.CREATOR);
    }

    /**
     * Get the (cached) list with user preferences for the data sites, ordered by reliability.
     * Includes enabled <strong>AND</strong> disabled sites.
     * <p>
     * This list will be a re-ordered clone/copy of the {@link Type#Data} list,
     * with the SAME Site objects
     *
     * @param context Current context
     *
     * @return the list
     */
    @NonNull
    static List<Site> getDataSitesByReliability(@NonNull final Context context) {
        final Locale systemLocale = LocaleUtils.getSystemLocale();
        final Locale userLocale = LocaleUtils.getUserLocale(context);
        return getList(context, systemLocale, userLocale, Type.Data)
                .reorder(SearchSites.DATA_RELIABILITY_ORDER)
                .getSites();
    }

    /**
     * Get the global search site list in the preferred order.
     * Includes enabled <strong>AND</strong> disabled sites.
     *
     * @param context      Current context
     * @param systemLocale device Locale <em>(passed in to allow mocking)</em>
     * @param userLocale   user locale <em>(passed in to allow mocking)</em>
     * @param type         type
     *
     * @return the list
     */
    @NonNull
    public static SiteList getList(@NonNull final Context context,
                                   @NonNull final Locale systemLocale,
                                   @NonNull final Locale userLocale,
                                   @NonNull final Type type) {
        // already loaded ?
        final SiteList list = SITE_LIST_MAP.get(type);
        if (list != null) {
            return new SiteList(list);
        }

        // create the list according to user preferences.
        final SiteList newList = SearchSites.createSiteList(systemLocale, userLocale, type);
        newList.loadPrefs(context);

        // cache the list for reuse
        SITE_LIST_MAP.put(type, newList);

        return new SiteList(newList);
    }

    /**
     * Reset a list back to the hardcoded defaults.
     *
     * @param context      Current context
     * @param systemLocale device Locale <em>(passed in to allow mocking)</em>
     * @param userLocale   user locale <em>(passed in to allow mocking)</em>
     * @param type         type
     *
     * @return the new list
     */
    public static SiteList resetList(@NonNull final Context context,
                                     @NonNull final Locale systemLocale,
                                     @NonNull final Locale userLocale,
                                     @NonNull final Type type) {

        // recreate the list with all defaults applied.
        final SiteList newList = SearchSites.createSiteList(systemLocale, userLocale, type);
        // overwrite stored user preferences
        newList.update(context);

        // cache the list for reuse; thereby overwriting the previously stored copy
        SITE_LIST_MAP.put(type, newList);

        return new SiteList(newList);
    }

    /**
     * Bring up an Alert to the user if the current list includes a site where registration
     * is beneficial/required.
     *
     * @param context      Current context
     * @param required     {@code true} if we <strong>must</strong> have access.
     *                     {@code false} if it would be beneficial.
     * @param callerSuffix String used to flag in preferences if we showed the alert from
     *                     that caller already or not.
     *
     * @return {@code true} if an alert is currently shown
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean promptToRegister(@NonNull final Context context,
                                    final boolean required,
                                    @NonNull final String callerSuffix) {
        boolean showingAlert = false;
        for (Site site : mList) {
            if (site.isEnabled()) {
                showingAlert |= site.getSearchEngine()
                                    .promptToRegister(context, required, callerSuffix);
            }
        }

        return showingAlert;
    }


    public void clear() {
        mList.clear();
    }

    public void add(@NonNull final Site site) {
        mList.add(site);
    }

    public void add(@SearchSites.EngineId final int id) {
        add(id, true);
    }

    public void add(@SearchSites.EngineId final int id,
                    final boolean enabled) {
        mList.add(new Site(id, mType, enabled));
    }

    public void addAll(@NonNull final SiteList newList) {
        mList.addAll(newList.mList);
    }

    /**
     * Get the sites (both enabled and disabled) in the preferred order.
     *
     * @return the list
     */
    @NonNull
    public List<Site> getSites() {
        return mList;
    }

    /**
     * Get the enabled sites list in the preferred order.
     *
     * @return the list
     */
    @NonNull
    public List<Site> getEnabledSites() {
        return mList.stream().filter(Site::isEnabled).collect(Collectors.toList());
    }

    /**
     * Reorder the given list based on user preferences.
     * The site objects are the <strong>same</strong> as in the original list.
     * <p>
     * The reordered list <strong>MAY</strong> be shorter then the original,
     * as sites from the original which are not present in the order string
     * are <strong>NOT</strong> added.
     * <p>
     * The internal {@link #mList} is NOT modified.
     *
     * @param order CSV string with site ID's
     *
     * @return ordered list
     */
    private SiteList reorder(@NonNull final String order) {
        final SiteList orderedList = new SiteList(mType);
        for (String idStr : order.split(",")) {
            final int id = Integer.parseInt(idStr);
            for (Site site : mList) {
                if (site.engineId == id) {
                    orderedList.add(site);
                    break;
                }
            }
        }
        return orderedList;
    }

    /**
     * Update the list.
     *
     * @param context Current context
     */
    public void update(@NonNull final Context context) {
        SITE_LIST_MAP.put(mType, this);
        savePrefs(context);
    }

    /**
     * Save the site settings and the order of the list.
     *
     * @param context Current context
     */
    private void savePrefs(@NonNull final Context context) {
        // Save the order of the given list (ID's) and the individual site settings to preferences.
        final SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(context)
                                                             .edit();
        final String order = Csv.join(mList, site -> {
            // store individual site settings
            site.saveToPrefs(ed);
            // and collect the id for the order string
            return String.valueOf(site.engineId);
        });
        ed.putString(mType.getOrderPreferenceKey(), order);
        ed.apply();
    }

    private void loadPrefs(@NonNull final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        for (Site site : mList) {
            site.loadFromPrefs(prefs);
        }
        final String order = prefs.getString(mType.getOrderPreferenceKey(), null);
        if (order != null) {
            // List as hardcoded.
            final ArrayList<Site> fullList = mList;
            // Reorder according to user preferences.
            mList = reorder(order).mList;

            if (mList.size() < fullList.size()) {
                // This is a fringe case: a new engine was added, and the user upgraded
                // this app. The stored order will lack the new engine.
                // Add any sites not added yet to the end of the list
                for (Site site : fullList) {
                    if (!mList.contains(site)) {
                        mList.add(site);
                    }
                }
                savePrefs(context);
            }
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(mType, flags);
        dest.writeTypedList(mList);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * The different types of configurable site lists we maintain.
     *
     * <strong>Note:</strong> the order of the enum values is used as the order
     * of the tabs in {@link SearchAdminActivity}.
     */
    public enum Type
            implements Parcelable {

        /** generic searches. */
        Data(R.string.lbl_books, "data"),
        /** Covers. */
        Covers(R.string.lbl_covers, "covers"),
        /** Alternative editions for a given isbn. */
        AltEditions(R.string.tab_lbl_alternative_editions, "alted");

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<Type> CREATOR = new Creator<Type>() {
            @Override
            @NonNull
            public Type createFromParcel(@NonNull final Parcel in) {
                return Type.values()[in.readInt()];
            }

            @Override
            @NonNull
            public Type[] newArray(final int size) {
                return new Type[size];
            }
        };

        /** Preferences prefix for individual site settings. */
        private static final String PREF_PREFIX = "search.site.";
        /** Preferences prefix for site order per type. */
        private static final String PREFS_ORDER_PREFIX = "search.siteOrder.";

        /** Log tag. */
        private static final String TAG = "SiteList.Type";

        /** User displayable name. */
        @StringRes
        private final int mNameResId;
        /** Internal name (for prefs). */
        private final String mTypeName;

        /**
         * Constructor.
         *
         * @param stringId for displaying the type name to the user
         * @param typeName for internal usage
         */
        Type(@StringRes final int stringId,
             @NonNull final String typeName) {
            mNameResId = stringId;
            mTypeName = typeName;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(this.ordinal());
        }

        @StringRes
        public int getLabelId() {
            return mNameResId;
        }

        @NonNull
        public String getBundleKey() {
            return TAG + ":" + mTypeName;
        }

        @NonNull
        String getSitePreferenceKey(@SearchSites.EngineId final int engineId) {
            //noinspection ConstantConditions
            return PREF_PREFIX + Site.getConfig(engineId).getPreferenceKey() + '.' + mTypeName;
        }

        @NonNull
        String getOrderPreferenceKey() {
            return PREFS_ORDER_PREFIX + mTypeName;
        }
    }
}
