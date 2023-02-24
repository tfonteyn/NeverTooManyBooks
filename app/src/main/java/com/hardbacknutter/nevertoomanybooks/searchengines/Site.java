/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.CoverBrowserDialogFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminFragment;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * Encapsulates an {@link EngineId} + the {@link Type} of the site and its active state.
 */
public final class Site
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<Site> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public Site createFromParcel(@NonNull final Parcel source) {
            return new Site(source);
        }

        @Override
        @NonNull
        public Site[] newArray(final int size) {
            return new Site[size];
        }
    };

    /** Preferences prefix for individual site settings. */
    private static final String PREF_PREFIX = "search.site.";

    /**
     * The (for now) only actual preference:
     * whether this site is active <strong>for the list it belongs to</strong>.
     * <p>
     * <strong>Dev. note:</strong> code uses 'active' as in the user can activate/deactivate
     * a site, while enabled/disabled is used at compile time from the gradle script.
     * <p>
     * This key uses 'enabled' for backwards compatibility.
     */
    private static final String PREF_SUFFIX_ACTIVE = "enabled";

    @NonNull
    private final EngineId engineId;

    /** Type of this site. */
    @NonNull
    private final Type type;

    /** user preference: active/deactivated this site. */
    private boolean active;

    /**
     * Constructor.
     *
     * @param type     the type of Site list this Site will belong to
     * @param engineId the search engine id
     * @param active   flag
     */
    private Site(@NonNull final Type type,
                 @NonNull final EngineId engineId,
                 final boolean active) {

        this.engineId = engineId;
        this.type = type;
        this.active = active;
    }

    /**
     * Copy constructor.
     *
     * @param from object to copy
     */
    private Site(@NonNull final Site from) {
        engineId = from.engineId;
        type = from.type;
        active = from.active;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Site(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        engineId = in.readParcelable(Type.class.getClassLoader());
        //noinspection ConstantConditions
        type = in.readParcelable(Type.class.getClassLoader());
        active = in.readByte() != 0;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    @NonNull
    public EngineId getEngineId() {
        return engineId;
    }

    /**
     * Helper to combine all parts of the preference key used.
     *
     * @return full prefix for the preference key
     */
    @NonNull
    private String getPrefPrefix() {
        return PREF_PREFIX + engineId.getPreferenceKey() + '.' + type.key + '.';
    }

    private void loadFromPrefs(@NonNull final SharedPreferences prefs) {
        active = prefs.getBoolean(getPrefPrefix() + PREF_SUFFIX_ACTIVE, active);
    }

    private void saveToPrefs(@NonNull final SharedPreferences.Editor editor) {
        editor.putBoolean(getPrefPrefix() + PREF_SUFFIX_ACTIVE, active);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(engineId, flags);
        dest.writeParcelable(type, flags);
        dest.writeByte((byte) (active ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "Site{" + "engineId=" + engineId + ", type=" + type + ", active=" + active + '}';
    }

    /**
     * The different types of configurable site lists we maintain.
     * <p>
     * <strong>Note:</strong> the order of the enum values is used as the order
     * of the tabs in {@link SearchAdminFragment}.
     * <p>
     * <strong>Note: NEVER change the "key" of the types</strong>.
     */
    public enum Type
            implements Parcelable {

        /** {@link SearchEngine} - Generic searches (includes books AND covers). */
        Data("data", R.string.lbl_books),

        /** {@link SearchEngine} - Alternative editions for a given isbn. */
        AltEditions("alted", R.string.lbl_tab_alternative_editions),

        /** {@link CoverBrowserDialogFragment} - Dedicated covers searches. */
        Covers("covers", R.string.lbl_covers),

        /** List of sites for which we store an id. */
        ViewOnSite("view", R.string.option_view_book_at);

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<Type> CREATOR = new Creator<>() {
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

        /** Internal name (for prefs). */
        @NonNull
        private final String key;

        /** User displayable name. */
        @StringRes
        private final int labelResId;

        /** The list of sites in this type. */
        private final Collection<Site> siteList = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param key        unique key string for internal usage
         * @param labelResId for displaying the type name to the user
         */
        Type(@NonNull final String key,
             @StringRes final int labelResId) {
            this.labelResId = labelResId;
            this.key = key;
        }

        /**
         * Create the list for <strong>this</strong> type.
         *
         * @param context Current context
         */
        void createList(@NonNull final Context context,
                        @NonNull final Languages languages) {
            siteList.clear();
            EngineId.registerSites(context, this, languages);

            // apply stored user preferences to the list
            loadPrefs(context);
        }

        /**
         * Reset the list back to the hardcoded defaults.
         *
         * @param context Current context
         */
        public void resetList(@NonNull final Context context,
                              @NonNull final Languages languages) {
            siteList.clear();
            EngineId.registerSites(context, this, languages);

            // overwrite stored user preferences with the defaults from the list
            savePrefs(context);
        }

        /**
         * Replace the current list with the given list. A deep-copy will be taken.
         *
         * @param context Current context
         * @param sites   list to use
         */
        public void setSiteList(@NonNull final Context context,
                                @NonNull final Collection<Site> sites) {
            siteList.clear();
            for (final Site site : sites) {
                siteList.add(new Site(site));
            }
            savePrefs(context);
        }

        /**
         * Get a <strong>deep-copy</strong> of the desired Site.
         *
         * @param engineId the search engine id
         *
         * @return deep-copy instance of the Site
         */
        @NonNull
        public Site getSite(@NonNull final EngineId engineId) {
            final Site s = siteList.stream().filter(site -> site.engineId == engineId).findFirst()
                                   .orElseThrow(() -> new IllegalArgumentException(
                                           String.valueOf(engineId)));
            return new Site(s);
        }

        public boolean contains(@NonNull final EngineId engineId) {
            return siteList.stream().anyMatch(site -> site.engineId == engineId);
        }

        /**
         * Get a <strong>deep-copy</strong> of the list.
         * Includes active <strong>AND</strong> deactivated sites.
         *
         * @return deep-copy instance of the Site list
         */
        @NonNull
        public ArrayList<Site> getSites() {
            return siteList.stream().map(Site::new)
                           .collect(Collectors.toCollection(ArrayList::new));
        }

        /**
         * Helper for {@link EngineId#registerSites}.
         *
         * @param engineId the search engine id
         * @param active   flag
         */
        void addSite(@NonNull final EngineId engineId,
                     final boolean active) {
            if (!engineId.isEnabled()) {
                return;
            }
            siteList.add(new Site(this, engineId, active));
        }

        /**
         * Load the site settings and the order of the list.
         */
        @VisibleForTesting
        void loadPrefs(@NonNull final Context context) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            siteList.forEach(site -> site.loadFromPrefs(prefs));

            final String order = prefs.getString(PREFS_ORDER_PREFIX + key, null);
            if (order != null) {
                final List<EngineId> list = new ArrayList<>();
                Arrays.stream(order.split(","))
                      .forEach(prefKey -> Arrays.stream(EngineId.values())
                                                .filter(engineId -> engineId.getPreferenceKey()
                                                                            .equals(prefKey))
                                                .findFirst()
                                                .ifPresent(list::add));

                // Reorder keeping the original list members.
                final List<Site> reorderedList = new ArrayList<>();
                list.forEach(id -> siteList.stream()
                                           .filter(site -> site.engineId == id)
                                           .findFirst()
                                           .ifPresent(reorderedList::add));

                if (reorderedList.size() < siteList.size()) {
                    // This is a fringe case: a new engine was added, and the user upgraded
                    // this app. The stored order will lack the new engine.
                    // Add any sites not added yet to the end of the list
                    siteList.stream()
                            .filter(site -> !reorderedList.contains(site))
                            .forEach(reorderedList::add);
                    savePrefs(context);
                }

                // simply replace in the new order.
                siteList.clear();
                siteList.addAll(reorderedList);
            }
        }

        /**
         * Save the settings for each site in this list + the order of the sites in the list.
         */
        void savePrefs(@NonNull final Context context) {
            final SharedPreferences.Editor ed = PreferenceManager
                    .getDefaultSharedPreferences(context).edit();

            final String order = siteList.stream().map(site -> {
                // store individual site settings
                site.saveToPrefs(ed);
                // and collect the id for the order string
                return site.getEngineId().getPreferenceKey();
            }).collect(Collectors.joining(","));

            ed.putString(PREFS_ORDER_PREFIX + key, order);
            ed.apply();

            // for reference, the prefs will look somewhat like this:
            //
            //    <boolean name="search.site.amazon.covers.enabled" value="true" />
            //    <boolean name="search.site.amazon.data.enabled" value="false" />
            //    <boolean name="search.site.googlebooks.data.enabled" value="false" />
            //    <boolean name="search.site.isfdb.alted.enabled" value="true" />
            //    <boolean name="search.site.isfdb.covers.enabled" value="true" />
            //    <boolean name="search.site.isfdb.data.enabled" value="true" />
            //    <boolean name="search.site.kbnl.covers.enabled" value="false" />
            //    <boolean name="search.site.kbnl.data.enabled" value="false" />
            //    <boolean name="search.site.lastdodo.data.enabled" value="false" />
            //    <boolean name="search.site.librarything.alted.enabled" value="true" />
            //    <boolean name="search.site.openlibrary.covers.enabled" value="false" />
            //    <boolean name="search.site.openlibrary.data.enabled" value="false" />
            //    <boolean name="search.site.stripinfo.data.enabled" value="false" />
            //
            //  The order includes both activated and deactivated sites!
            //    <string name="search.siteOrder.alted">librarything,isfdb</string>
            //    <string name="search.siteOrder.covers">amazon,isfdb,openlibrary</string>
            //    <string name="search.siteOrder.data">isfdb,amazon,stripinfo,googlebooks,
            //                                         lastdodo,openlibrary</string>
        }

        @StringRes
        public int getLabelResId() {
            return labelResId;
        }

        @NonNull
        public String getBundleKey() {
            return TAG + ":" + key;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }
    }
}
