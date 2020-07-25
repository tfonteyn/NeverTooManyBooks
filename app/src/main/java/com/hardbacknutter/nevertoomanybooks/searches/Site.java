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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Represents a site we can search.
 */
public class Site
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<Site> CREATOR =
            new Creator<Site>() {
                @Override
                public Site createFromParcel(@NonNull final Parcel source) {
                    return new Site(source);
                }

                @Override
                public Site[] newArray(final int size) {
                    return new Site[size];
                }
            };

    /**
     * The (for now) only actual preference:
     * whether this site is enabled <strong>for the list it belongs to</strong>.
     */
    private static final String PREF_SUFFIX_ENABLED = ".enabled";


    /** All site configurations. */
    private static final HashMap<Integer, Config> ALL_SITE_CONFIGS = new HashMap<>();

    /** SearchEngine ID. Used to (re)create {@link #mSearchEngine}. */
    @SearchSites.EngineId
    public final int engineId;

    /** key into prefs. */
    @NonNull
    private final String mPreferenceKey;

    /** user preference: enable/disable this site. */
    private boolean mEnabled;

    /** the class which implements the search engine for a specific site. */
    private SearchEngine mSearchEngine;

    /**
     * Constructor.
     *
     * @param engineId for the engine
     * @param type     the list type this site will belong to
     * @param enabled  flag
     */
    Site(@SearchSites.EngineId final int engineId,
         @NonNull final SiteList.Type type,
         final boolean enabled) {

        this.engineId = engineId;
        mEnabled = enabled;
        mPreferenceKey = type.getSitePreferenceKey(engineId);
    }

    /**
     * Copy constructor.
     *
     * @param from object to copy
     */
    Site(@NonNull final Site from) {
        engineId = from.engineId;
        mEnabled = from.mEnabled;
        mPreferenceKey = from.mPreferenceKey;
        // don't copy, it will be recreated
        mSearchEngine = null;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Site(@NonNull final Parcel in) {
        engineId = in.readInt();
        mEnabled = in.readInt() != 0;
        //noinspection ConstantConditions
        mPreferenceKey = in.readString();
        // Reminder: this is IPC.. so don't load prefs!
    }

    static void add(@NonNull final Class<? extends SearchEngine> searchEngineClass) {
        final Config config = new Config(searchEngineClass);
        ALL_SITE_CONFIGS.put(config.getEngineId(), config);
    }

    /**
     * Get the list of all configured search engines.
     *
     * @return list
     */
    @NonNull
    public static Collection<Config> getConfigs() {
        return ALL_SITE_CONFIGS.values();
    }

    /**
     * Get the configuration for the given EngineId.
     *
     * @param engineId to get the config for
     *
     * @return Config, or {@link null} if not found
     */
    @Nullable
    public static Config getConfig(@SearchSites.EngineId final int engineId) {
        return ALL_SITE_CONFIGS.get(engineId);
    }

    @Nullable
    public static Config getConfigByViewId(@IdRes final int viewId) {
        for (Config config : getConfigs()) {
            if (config.getDomainViewId() == viewId) {
                return config;
            }
        }
        return null;
    }

    /**
     * Search for the configuration containing the given menuId.
     *
     * @param menuId to get
     *
     * @return Config, or {@link null} if not found
     */
    @Nullable
    public static Config getConfigByMenuId(@IdRes final int menuId) {
        for (Config config : getConfigs()) {
            if (config.getDomainMenuId() == menuId) {
                return config;
            }
        }
        return null;
    }

    /**
     * Get the list of configured external-id domains.
     *
     * @return list
     */
    @NonNull
    public static List<Domain> getExternalIdDomains() {
        final List<Domain> externalIds = new ArrayList<>();
        for (Config config : getConfigs()) {
            final Domain domain = config.getExternalIdDomain();
            if (domain != null) {
                externalIds.add(domain);
            }
        }
        return externalIds;
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

            final Config config = ALL_SITE_CONFIGS.get(engineId);
            Objects.requireNonNull(config, ErrorMsg.NULL_SEARCH_ENGINE_CONFIG);
            mSearchEngine = config.createSearchEngine();
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

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(final boolean enabled) {
        mEnabled = enabled;
    }

    void loadFromPrefs(@NonNull final SharedPreferences preferences) {
        mEnabled = preferences.getBoolean(mPreferenceKey + PREF_SUFFIX_ENABLED, mEnabled);
    }

    void saveToPrefs(@NonNull final SharedPreferences.Editor editor) {
        editor.putBoolean(mPreferenceKey + PREF_SUFFIX_ENABLED, mEnabled);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Reminder: this is IPC.. so don't save prefs.
     */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(engineId);
        dest.writeInt(mEnabled ? 1 : 0);
        dest.writeString(mPreferenceKey);
    }

    @Override
    @NonNull
    public String toString() {
        return "Site{"
               + "engineId=" + engineId
               + ", mPreferenceKey=`" + mPreferenceKey + '`'
               + ", mEnabled=" + mEnabled
               + ", mSearchEngine=" + mSearchEngine
               + '}';
    }

    /**
     * Immutable configuration data for a {@link SearchEngine}.
     * <p>
     * See {@link SearchSites} for more details.
     */
    public static class Config {

        @NonNull
        private final Class<? extends SearchEngine> mClass;
        @NonNull
        private final SearchEngine.Configuration mConfiguration;

        @NonNull
        private final Locale mLocale;

        /** {@link SearchEngine.ByExternalId} only. */
        @Nullable
        private final Domain mExternalIdDomain;

        /**
         * Constructor.
         *
         * @param searchEngineClass to configure
         */
        private Config(@NonNull final Class<? extends SearchEngine> searchEngineClass) {
            mClass = searchEngineClass;
            mConfiguration = Objects.requireNonNull(searchEngineClass.getAnnotation(
                    SearchEngine.Configuration.class));

            if ("en".equals(mConfiguration.lang()) && mConfiguration.country().isEmpty()) {
                // be lenient...
                mLocale = Locale.US;
            } else {
                mLocale = new Locale(mConfiguration.lang(),
                                     mConfiguration.country().toUpperCase(Locale.ENGLISH));
            }

            final String domainKey = mConfiguration.domainKey();
            if (domainKey.isEmpty()) {
                mExternalIdDomain = null;
            } else {
                mExternalIdDomain = DBDefinitions.TBL_BOOKS.getDomain(domainKey);
            }
        }


        /**
         * Create an instance of the {@link SearchEngine} for this configuration.
         *
         * @return new instance
         */
        @NonNull
        public SearchEngine createSearchEngine() {
            // ALWAYS use the localized Application context here
            // It's going to get used in background tasks!
            final Context appContext = LocaleUtils.applyLocale(App.getTaskContext());
            return createSearchEngine(appContext);
        }

        @VisibleForTesting
        @NonNull
        public SearchEngine createSearchEngine(@NonNull final Context appContext) {
            try {
                final Constructor<? extends SearchEngine> c =
                        mClass.getConstructor(Context.class);
                return c.newInstance(appContext);

            } catch (@NonNull final NoSuchMethodException | IllegalAccessException
                    | InstantiationException | InvocationTargetException e) {
                throw new IllegalStateException(mClass
                                                + " must implement SearchEngine(Context)", e);
            }
        }

        @SearchSites.EngineId
        public int getEngineId() {
            return mConfiguration.id();
        }

        /**
         * Get the resource id for the human-readable name of the site.
         *
         * @return the resource id of the name
         */
        @StringRes
        public int getNameResId() {
            return mConfiguration.nameResId();
        }

        @NonNull
        public String getPreferenceKey() {
            return mConfiguration.prefKey();
        }

        @NonNull
        public String getSiteUrl() {
            return mConfiguration.url();
        }

        /**
         * Get the <strong>standard</strong> Locale for this engine.
         *
         * @return site locale
         */
        @NonNull
        public Locale getLocale() {
            return mLocale;
        }

        @Nullable
        public Domain getExternalIdDomain() {
            return mExternalIdDomain;
        }

        @IdRes
        public int getDomainViewId() {
            return mConfiguration.domainViewId();
        }

        @IdRes
        public int getDomainMenuId() {
            return mConfiguration.domainMenuId();
        }

        /**
         * Timeout we allow for a connection to work.
         *
         * @return defaults to 5 second. Override as needed.
         */
        public int getConnectTimeoutMs() {
            return mConfiguration.connectTimeoutMs();
        }

        /**
         * Timeout we allow for a response to a request.
         *
         * @return defaults to 10 second. Override as needed.
         */
        public int getReadTimeoutMs() {
            return mConfiguration.readTimeoutMs();
        }

        /** {@link SearchEngine.CoverByIsbn} only. */
        public boolean supportsMultipleCoverSizes() {
            return mConfiguration.supportsMultipleCoverSizes();
        }
    }
}
