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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * Encapsulates a {@link SearchEngine} instance + the current enabled/disabled state.
 */
public final class Site
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<Site> CREATOR = new Creator<Site>() {
        @Override
        public Site createFromParcel(@NonNull final Parcel source) {
            return new Site(source);
        }

        @Override
        public Site[] newArray(final int size) {
            return new Site[size];
        }
    };

    /** Preferences prefix for individual site settings. */
    private static final String PREF_PREFIX = "search.site.";

    /**
     * The (for now) only actual preference:
     * whether this site is enabled <strong>for the list it belongs to</strong>.
     */
    private static final String PREF_SUFFIX_ENABLED = "enabled";

    /** SearchEngine ID. Used to (re)create {@link #mSearchEngine}. */
    @SearchSites.EngineId
    public final int engineId;

    /** Type of this site. */
    @NonNull
    private final Type mType;

    /** user preference: enable/disable this site. */
    private boolean mEnabled;

    /** the class which implements the search engine for a specific site. */
    @Nullable
    private SearchEngine mSearchEngine;

    /**
     * Constructor.
     *
     * @param type     the type of Site list this Site will belong to
     * @param engineId the search engine id
     * @param enabled  flag
     */
    private Site(@NonNull final Type type,
                 @SearchSites.EngineId final int engineId,
                 final boolean enabled) {

        this.engineId = engineId;
        mType = type;
        mEnabled = enabled;
    }

    /**
     * Copy constructor.
     *
     * @param from object to copy
     */
    private Site(@NonNull final Site from) {
        engineId = from.engineId;
        mType = from.mType;

        // Copy the current state
        mEnabled = from.mEnabled;
        // don't copy the mSearchEngine, let it be recreated.
        mSearchEngine = null;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Site(@NonNull final Parcel in) {
        engineId = in.readInt();
        //noinspection ConstantConditions
        mType = in.readParcelable(Type.class.getClassLoader());
        mEnabled = in.readByte() != 0;
    }

    /**
     * Get the enabled sites in the <strong>given</strong> list.
     *
     * @param sites to filter
     *
     * @return new list instance containing the <strong>original</strong> site objects;
     * filtered for being enabled. The order is the same.
     */
    @NonNull
    public static ArrayList<Site> filterForEnabled(@NonNull final Collection<Site> sites) {
        return sites.stream()
                    .filter(Site::isEnabled)
                    .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Bring up an Alert to the user if the given list includes a site where registration
     * is beneficial (but not required... it's just one of many engines here).
     *
     * @param context        Current context
     * @param sites          the list to check
     * @param callerIdString String used to flag in preferences if we showed the alert from
     *                       that caller already or not.
     * @param onFinished     (optional) Runnable to call when all sites have been processed.
     */
    public static void promptToRegister(@NonNull final Context context,
                                        @NonNull final Collection<Site> sites,
                                        @NonNull final String callerIdString,
                                        @Nullable final Runnable onFinished) {

        final Deque<Site> stack = new ArrayDeque<>(sites);
        promptToRegister(context, stack, callerIdString, onFinished);
    }

    /**
     * Recursive stack-based version of
     * {@link #promptToRegister(Context, Collection, String, Runnable)}.
     *
     * @param context        Current context
     * @param sites          the stack of sites to check
     * @param callerIdString String used to flag in preferences if we showed the alert from
     *                       that caller already or not.
     * @param onFinished     (optional) Runnable to call when all sites have been processed.
     */
    private static void promptToRegister(@NonNull final Context context,
                                         @NonNull final Deque<Site> sites,
                                         @NonNull final String callerIdString,
                                         @Nullable final Runnable onFinished) {
        while (!sites.isEmpty()) {
            final Site site = sites.poll();
            //noinspection ConstantConditions
            if (site.isEnabled()) {
                final SearchEngine searchEngine = site.getSearchEngine();
                if (searchEngine.promptToRegister(context, false, callerIdString, action -> {
                    switch (action) {
                        case Register:
                            throw new IllegalStateException("Engine must handle Register");

                        case NotNow:
                        case NotEver:
                            // restart the loop with the remaining sites to check.
                            promptToRegister(context, sites, callerIdString, onFinished);
                            return;

                        case Cancelled:
                            // user explicitly cancelled, we're done here
                            if (onFinished != null) {
                                onFinished.run();
                            }
                            break;
                    }
                })) {
                    // we are showing a registration dialog, quit the loop
                    return;
                }
            }
        }

        // all engines have registration, or were dismissed.
        if (onFinished != null) {
            onFinished.run();
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(engineId);
        dest.writeParcelable(mType, flags);
        dest.writeByte((byte) (mEnabled ? 1 : 0));
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Get the {@link SearchEngine} instance for this site.
     * If the engine was cached, it will be reset before being returned.
     *
     * @return (cached) instance
     */
    @NonNull
    public SearchEngine getSearchEngine() {
        if (mSearchEngine == null) {
            mSearchEngine = SearchEngineRegistry
                    .getInstance().createSearchEngine(engineId);
        }

        mSearchEngine.reset();
        return mSearchEngine;
    }

    /**
     * Convenience method to get the {@link SearchEngine} instance for this site,
     * and set the caller.
     *
     * @param caller to set
     *
     * @return (cached) instance
     */
    @NonNull
    public SearchEngine getSearchEngine(@NonNull final Canceller caller) {
        final SearchEngine searchEngine = getSearchEngine();
        searchEngine.setCaller(caller);
        return searchEngine;
    }

    @NonNull
    public Type getType() {
        return mType;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(final boolean enabled) {
        mEnabled = enabled;
    }

    /**
     * Helper to combine all parts of the preference key used.
     *
     * @return full prefix for the preference key
     */
    @NonNull
    private String getPrefPrefix() {
        return PREF_PREFIX
               + SearchEngineRegistry.getInstance().getByEngineId(engineId).getPreferenceKey()
               + '.'
               + mType.mTypeName
               + '.';
    }

    private void loadFromPrefs(@NonNull final SharedPreferences global) {
        mEnabled = global.getBoolean(getPrefPrefix() + PREF_SUFFIX_ENABLED, mEnabled);
    }

    private void saveToPrefs(@NonNull final SharedPreferences.Editor editor) {
        editor.putBoolean(getPrefPrefix() + PREF_SUFFIX_ENABLED, mEnabled);
    }

    @Override
    @NonNull
    public String toString() {
        return "Site{"
               + "engineId=" + engineId
               + ", mType=" + mType
               + ", mEnabled=" + mEnabled
               + ", mSearchEngine=" + mSearchEngine
               + '}';
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
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public Type[] newArray(final int size) {
                return new Type[size];
            }
        };

        /** Preferences prefix for site order per type. */
        private static final String PREFS_ORDER_PREFIX = "search.siteOrder.";

        /** Log tag. */
        private static final String TAG = "Site.Type";

        /** User displayable name. */
        @StringRes
        private final int mNameResId;
        /** Internal name (for prefs). */
        private final String mTypeName;

        private final Collection<Site> mList = new ArrayList<>();

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

        /**
         * Get the data sites list, ordered by reliability.
         * Includes enabled <strong>AND</strong> disabled sites.
         *
         * @return deep-copy unmodifiable List sorted by reliability of data
         */
        @NonNull
        static List<Site> getDataSitesByReliability() {
            return Collections.unmodifiableList(
                    reorder(Data.getSites(), SearchSites.DATA_RELIABILITY_ORDER));
        }

        /**
         * Create a new list, but reordered according to the given order string.
         * The site objects are the <strong>same</strong> as in the original list.
         * The original list order is NOT modified.
         * <p>
         * The reordered list <strong>MAY</strong> be shorter than the original,
         * as sites from the original which are not present in the order string
         * are <strong>NOT</strong> added.
         *
         * @param sites list to reorder
         * @param order CSV string with site ID's
         *
         * @return new list instance containing the <strong>original</strong> site objects
         * in the desired order.
         */
        @VisibleForTesting
        public static List<Site> reorder(@NonNull final Collection<Site> sites,
                                         @NonNull final String order) {

            final List<Site> reorderedList = new ArrayList<>();
            for (final String idStr : order.split(",")) {
                final int id = Integer.parseInt(idStr);
                for (final Site site : sites) {
                    if (site.engineId == id) {
                        reorderedList.add(site);
                        break;
                    }
                }
            }
            return reorderedList;
        }

        /**
         * Create the list for <strong>all</strong> types.
         *
         * @param context Current context
         */
        public static void registerAllTypes(@NonNull final Context context) {
            final Locale systemLocale = AppLocale.getInstance().getSystemLocale();
            final Locale userLocale = AppLocale.getInstance().getUserLocale(context);

            // configure the site type enums.
            for (final Type type : values()) {
                type.createList(systemLocale, userLocale);
            }
        }

        /**
         * Create the list for <strong>this</strong> type.
         *
         * @param systemLocale device Locale <em>(passed in to allow mocking)</em>
         * @param userLocale   user locale <em>(passed in to allow mocking)</em>
         */
        private void createList(@NonNull final Locale systemLocale,
                                @NonNull final Locale userLocale) {

            // re-create the global list for the type
            mList.clear();
            SearchSites.createSiteList(systemLocale, userLocale, this);

            // apply stored user preferences to the list
            loadPrefs();
        }

        /**
         * Reset the list back to the hardcoded defaults.
         *
         * @param systemLocale device Locale <em>(passed in to allow mocking)</em>
         * @param userLocale   user locale <em>(passed in to allow mocking)</em>
         */
        public void resetList(@NonNull final Locale systemLocale,
                              @NonNull final Locale userLocale) {

            // re-create the global list for the type
            mList.clear();
            SearchSites.createSiteList(systemLocale, userLocale, this);

            // overwrite stored user preferences with the defaults from the list
            savePrefs();
        }

        /**
         * Replace the current list with the given list. A deep-copy will be taken.
         *
         * @param sites   list to use
         */
        public void setList(@NonNull final Collection<Site> sites) {
            mList.clear();
            for (final Site site : sites) {
                mList.add(new Site(site));
            }
            savePrefs();
        }

        /**
         * Get a <strong>deep-copy</strong> of the desired Site.
         *
         * @param engineId the search engine id
         *
         * @return deep-copy instance of the Site
         */
        @NonNull
        public Site getSite(@SearchSites.EngineId final int engineId) {
            for (final Site site : mList) {
                if (site.engineId == engineId) {
                    return new Site(site);
                }
            }
            throw new IllegalArgumentException(String.valueOf(engineId));
        }


        /**
         * Get a <strong>deep-copy</strong> of the list.
         * Includes enabled <strong>AND</strong> disabled sites.
         *
         * @return deep-copy instance of the Site list
         */
        @NonNull
        public ArrayList<Site> getSites() {
            final ArrayList<Site> list = new ArrayList<>();
            for (final Site site : mList) {
                list.add(new Site(site));
            }
            return list;
        }

        /**
         * Helper for {@link SearchSites#createSiteList}.
         *
         * @param engineId the search engine id
         */
        public void addSite(@SearchSites.EngineId final int engineId) {
            mList.add(new Site(this, engineId, true));
        }

        /**
         * Helper for {@link SearchSites#createSiteList}.
         *
         * @param engineId the search engine id
         * @param enabled  flag
         */
        public void addSite(@SearchSites.EngineId final int engineId,
                            final boolean enabled) {
            mList.add(new Site(this, engineId, enabled));
        }

        /**
         * Load the site settings and the order of the list.
         */
        @VisibleForTesting
        public void loadPrefs() {

            final SharedPreferences global = ServiceLocator.getGlobalPreferences();
            for (final Site site : mList) {
                site.loadFromPrefs(global);
            }

            final String order = global.getString(PREFS_ORDER_PREFIX + mTypeName, null);
            if (order != null) {
                // Reorder keeps the original list members.
                final List<Site> reorderedList = reorder(mList, order);

                if (reorderedList.size() < mList.size()) {
                    // This is a fringe case: a new engine was added, and the user upgraded
                    // this app. The stored order will lack the new engine.
                    // Add any sites not added yet to the end of the list
                    for (final Site site : mList) {
                        if (!reorderedList.contains(site)) {
                            reorderedList.add(site);
                        }
                    }
                    savePrefs();
                }

                // simply replace in the new order.
                mList.clear();
                mList.addAll(reorderedList);
            }
        }

        /**
         * Save the site settings and the order of the list.
         */
        public void savePrefs() {

            // Save the order of the given list (ID's) and
            // the individual site settings to preferences.
            final SharedPreferences.Editor ed = ServiceLocator.getGlobalPreferences().edit();

            final String order = mList.stream()
                                      .map(site -> {
                                          // store individual site settings
                                          site.saveToPrefs(ed);
                                          // and collect the id for the order string
                                          return String.valueOf(site.engineId);
                                      })
                                      .collect(Collectors.joining(","));

            ed.putString(PREFS_ORDER_PREFIX + mTypeName, order);
            ed.apply();
        }


        @StringRes
        public int getLabelId() {
            return mNameResId;
        }

        @NonNull
        public String getBundleKey() {
            return TAG + ":" + mTypeName;
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
    }
}
