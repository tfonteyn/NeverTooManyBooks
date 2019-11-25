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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

/**
 * Combines a list of {@link Site} objects with the {@link ListType}.
 */
public class SiteList
        implements Parcelable {

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

    private static final String SEP = ",";
    /** Cached copy of the users preferred site order. */
    private static final EnumMap<ListType, SiteList> sLists = new EnumMap<>(ListType.class);
    /** Type of this list. */
    @NonNull
    private final ListType mListType;
    /** The list. */
    @NonNull
    private final ArrayList<Site> mList;

    /**
     * Constructor.
     *
     * @param listType Type of this list.
     */
    public SiteList(@NonNull final ListType listType) {
        mListType = listType;
        mList = new ArrayList<>();
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    protected SiteList(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        mListType = in.readParcelable(ListType.class.getClassLoader());
        mList = new ArrayList<>();
        in.readTypedList(mList, Site.CREATOR);
    }

    /**
     * Copy constructor,
     *
     * @param list to copy
     */
    private SiteList(@NonNull final SiteList list) {
        mListType = list.mListType;
        //noinspection unchecked
        mList = (ArrayList<Site>) list.mList.clone();
    }

    /**
     * Get the (cached) list with user preferences for the data sites, ordered by reliability.
     *
     * @param context Current context
     *
     * @return the list
     */
    @NonNull
    static List<Site> getDataSitesByReliability(@NonNull final Context context) {
        return getList(context, ListType.Data)
                .reorder(SearchSites.DATA_RELIABILITY_ORDER)
                .getSites();
    }

    /**
     * Get the global search site list in the preferred order.
     * Includes enabled <strong>AND</strong> disabled sites.
     *
     * @param appContext Current context
     * @param listType   type
     *
     * @return the list
     */
    @NonNull
    public static SiteList getList(@NonNull final Context appContext,
                                   @NonNull final ListType listType) {
        // already loaded ?
        SiteList list = sLists.get(listType);
        if (list != null) {
            return new SiteList(list);
        }

        // create the list according to user preferences.
        SiteList newList = SearchSites.createSiteList(appContext, listType, true);

        // cache the list for reuse
        sLists.put(listType, newList);

        return new SiteList(newList);
    }

    @NonNull
    public static List<Site> getSites(@NonNull final Context context,
                                      @NonNull final ListType listType) {
        return getList(context, listType).getSites();
    }

    /**
     * Reset a list back to the hardcoded defaults.
     *
     * @param appContext Current context
     * @param listType   type
     *
     * @return the new list
     */
    public static SiteList resetList(@NonNull final Context appContext,
                                     @NonNull final ListType listType) {

        // create the list with all defaults applied.
        SiteList newList = SearchSites.createSiteList(appContext, listType, false);
        // overwrite stored user preferences
        newList.update(appContext);

        // cache the list for reuse
        sLists.put(listType, newList);

        return new SiteList(newList);
    }

    /**
     * Update the list.
     *
     * @param appContext Current context
     */
    public void update(@NonNull final Context appContext) {
        sLists.put(mListType, this);
        savePrefs(appContext);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(mListType, flags);
        dest.writeTypedList(mList);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Bring up an Alert to the user if the searchSites include a site where registration
     * is beneficial/required.
     *
     * @param context    Current context
     * @param required   {@code true} if we <strong>must</strong> have access to LT.
     *                   {@code false} if it would be beneficial.
     * @param prefSuffix Tip preference marker
     *
     * @return {@code true} if an alert is currently shown
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean promptToRegister(@NonNull final Context context,
                                    final boolean required,
                                    @NonNull final String prefSuffix) {
        boolean showingAlert = false;
        for (Site site : mList) {
            if (site.isEnabled()) {
                showingAlert |= site.getSearchEngine()
                                    .promptToRegister(context, required, prefSuffix);
            }
        }

        return showingAlert;
    }

    /**
     * Reorder the given list based on user preferences.
     * The site objects are the same as in the original list.
     *
     * @param order CSV string with site ids
     *
     * @return ordered list
     */
    private SiteList reorder(@NonNull final String order) {
        SiteList orderedList = new SiteList(mListType);
        for (String idStr : order.split(SEP)) {
            int id = Integer.parseInt(idStr);
            for (Site site : mList) {
                if (site.id == id) {
                    orderedList.add(site);
                    break;
                }
            }
        }
        return orderedList;
    }

    public void add(@NonNull final Site site) {
        mList.add(site);
    }

    /**
     * Get a bitmask with the enabled sites.
     *
     * @return bitmask containing only the enables sites
     */
    public int getEnabledSites() {
        int enabled = 0;
        for (Site site : mList) {
            if (site.isEnabled()) {
                // add the site
                enabled = enabled | site.id;
            }
        }
        return enabled;
    }

    /**
     * Save the site settings and the order of the list.
     *
     * @param appContext Current context
     */
    private void savePrefs(@NonNull final Context appContext) {
        // Save the order of the given list (ids) and the individual site settings to preferences.
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(appContext)
                                                       .edit();
        String order = Csv.join(SEP, mList, site -> {
            // store individual site settings
            site.saveToPrefs(ed);
            // and collect the id for the order string
            return String.valueOf(site.id);
        });
        ed.putString(mListType.getListOrderPreferenceKey(), order);
        ed.apply();
    }

    void loadPrefs(@NonNull final Context appContext) {
        for (Site site : mList) {
            site.loadFromPrefs(appContext);
        }
        String order = PreferenceManager.getDefaultSharedPreferences(appContext)
                                        .getString(mListType.getListOrderPreferenceKey(), null);
        if (order != null) {
            SiteList orderedList = reorder(order);
            if (orderedList.mList.size() < mList.size()) {
                // This is a fringe case: a new engine was added, and the user upgraded
                // this app. The stored order will lack the new engine.
                // Add any sites not added yet.
                for (Site site : mList) {
                    if (!orderedList.mList.contains(site)) {
                        orderedList.add(site);
                    }
                }
                savePrefs(appContext);
            }
        }
    }

    @NonNull
    public List<Site> getSites() {
        return mList;
    }

    public void clearAndAddAll(@NonNull final SiteList newList) {
        mList.clear();
        mList.addAll(newList.mList);
    }

    /**
     * The different types of configurable site lists we maintain.
     *
     * <strong>Note:</strong> the order of the enum values is used as the order
     * of the tabs in {@link SearchAdminActivity}.
     */
    public enum ListType
            implements Parcelable {

        Data, Covers, AltEditions;

        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<ListType> CREATOR =
                new Creator<ListType>() {
                    @Override
                    @NonNull
                    public ListType createFromParcel(@NonNull final Parcel in) {
                        return ListType.values()[in.readInt()];
                    }

                    @Override
                    @NonNull
                    public ListType[] newArray(int size) {
                        return new ListType[size];
                    }
                };
        private static final String PREFS_ORDER_PREFIX = "search.siteOrder.";
        private static final String TAG = "ListType";

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
            switch (this) {
                case Data:
                    return R.string.lbl_books;
                case Covers:
                    return R.string.lbl_covers;
                case AltEditions:
                    return R.string.tab_lbl_alternative_editions;

                default:
                    throw new UnexpectedValueException(this);
            }
        }

        public String getBundleKey() {
            switch (this) {
                case Data:
                    return TAG + ":data";
                case Covers:
                    return TAG + ":covers";
                case AltEditions:
                    return TAG + ":alt_ed";

                default:
                    throw new UnexpectedValueException(this);
            }
        }

        String getListOrderPreferenceKey() {
            switch (this) {
                case Data:
                    return PREFS_ORDER_PREFIX + "data";
                case Covers:
                    return PREFS_ORDER_PREFIX + "covers";
                case AltEditions:
                    return PREFS_ORDER_PREFIX + "alt_ed";

                default:
                    throw new UnexpectedValueException(this);
            }
        }
    }
}
