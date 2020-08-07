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
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * A registry of all {@link SearchEngine} classes and their {@link SearchEngine.Configuration}.
 */
public final class SearchEngineRegistry {

    /** All site configurations. */
    private static final Map<Integer, Config> SITE_CONFIGS_MAP = new HashMap<>();

    private SearchEngineRegistry() {
    }

    public static void create(@NonNull final Context context) {

        SearchSites.registerSearchEngineClasses();

        final Locale systemLocale = LocaleUtils.getSystemLocale();
        final Locale userLocale = LocaleUtils.getUserLocale(context);

        for (Site.Type type : Site.Type.values()) {
            type.createList(context, systemLocale, userLocale);
        }
    }

    /**
     * Register a {@link SearchEngine}.
     *
     * @param searchEngineClass to register
     */
    static void add(@NonNull final Class<? extends SearchEngine> searchEngineClass) {
        final Config config = new Config(searchEngineClass);
        SITE_CONFIGS_MAP.put(config.getEngineId(), config);
    }

    /**
     * Get the list of all configured search engines.
     *
     * @return list
     */
    @NonNull
    public static Collection<Config> getAll() {
        return SITE_CONFIGS_MAP.values();
    }

    /**
     * Get the configuration for the given EngineId.
     *
     * @param engineId the search engine id
     *
     * @return Config, or {@link null} if not found
     */
    @Nullable
    public static Config getByEngineId(@SearchSites.EngineId final int engineId) {
        return SITE_CONFIGS_MAP.get(engineId);
    }

    @Nullable
    public static Config getByViewId(@IdRes final int viewId) {
        for (Config config : SITE_CONFIGS_MAP.values()) {
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
    public static Config getByMenuId(@IdRes final int menuId) {
        for (Config config : SITE_CONFIGS_MAP.values()) {
            if (config.getDomainMenuId() == menuId) {
                return config;
            }
        }
        return null;
    }

    @NonNull
    static SearchEngine createSearchEngine(@NonNull final Context context,
                                           final int engineId) {
        //noinspection ConstantConditions
        return SITE_CONFIGS_MAP.get(engineId).createSearchEngine(context);
    }

    /**
     * Get the list of <strong>configured</strong> external-id domains.
     *
     * @return list
     */
    @NonNull
    public static List<Domain> getExternalIdDomains() {
        final List<Domain> externalIds = new ArrayList<>();
        for (Config config : SITE_CONFIGS_MAP.values()) {
            final Domain domain = config.getExternalIdDomain();
            if (domain != null) {
                externalIds.add(domain);
            }
        }
        return externalIds;
    }

    /**
     * Immutable configuration data for a {@link SearchEngine}.
     * Encapsulates the annotation class together with the search engine class,
     * and provides the 'complex' configuration objects along side the 'simple' attributes.
     * <p>
     * See {@link SearchSites} for more details.
     */
    public static final class Config {

        @NonNull
        private final Class<? extends SearchEngine> mClass;
        @NonNull
        private final SearchEngine.Configuration mSEConfig;

        /** Constructed from language+country. */
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
        public Config(@NonNull final Class<? extends SearchEngine> searchEngineClass) {
            mClass = searchEngineClass;
            mSEConfig = Objects.requireNonNull(searchEngineClass.getAnnotation(
                    SearchEngine.Configuration.class));

            if ("en".equals(mSEConfig.lang()) && mSEConfig.country().isEmpty()) {
                // be lenient...
                mLocale = Locale.US;
            } else {
                mLocale = new Locale(mSEConfig.lang(),
                                     mSEConfig.country().toUpperCase(Locale.ENGLISH));
            }

            final String domainKey = mSEConfig.domainKey();
            if (domainKey.isEmpty()) {
                mExternalIdDomain = null;
            } else {
                mExternalIdDomain = DBDefinitions.TBL_BOOKS.getDomain(domainKey);
            }
        }


        @VisibleForTesting
        @NonNull
        SearchEngine createSearchEngine(@NonNull final Context context) {
            try {
                final Constructor<? extends SearchEngine> c =
                        mClass.getConstructor(Context.class);
                // ALWAYS use the localized Application context here
                // It's going to get used in background tasks!
                //URGENT: do we need the call to applyLocale?
//                final Context appContext = LocaleUtils.applyLocale(context.getApplicationContext());
                final Context appContext = context.getApplicationContext();
                return c.newInstance(appContext);

            } catch (@NonNull final NoSuchMethodException | IllegalAccessException
                    | InstantiationException | InvocationTargetException e) {
                throw new IllegalStateException(mClass
                                                + " must implement SearchEngine(Context)", e);
            }
        }

        @SearchSites.EngineId
        public int getEngineId() {
            return mSEConfig.id();
        }

        /**
         * Get the resource id for the human-readable name of the site.
         *
         * @return the resource id of the name
         */
        @StringRes
        public int getNameResId() {
            return mSEConfig.nameResId();
        }

        @NonNull
        public String getPreferenceKey() {
            return mSEConfig.prefKey();
        }

        @NonNull
        public String getFilenameSuffix() {
            return mSEConfig.filenameSuffix();
        }

        @NonNull
        public String getSiteUrl() {
            return mSEConfig.url();
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
        int getDomainViewId() {
            return mSEConfig.domainViewId();
        }

        @IdRes
        int getDomainMenuId() {
            return mSEConfig.domainMenuId();
        }

        /**
         * Timeout we allow for a connection to work.
         *
         * @return defaults to 5 second. Override as needed.
         */
        public int getConnectTimeoutMs() {
            return mSEConfig.connectTimeoutMs();
        }

        /**
         * Timeout we allow for a response to a request.
         *
         * @return defaults to 10 second. Override as needed.
         */
        public int getReadTimeoutMs() {
            return mSEConfig.readTimeoutMs();
        }

        /**
         * {@link SearchEngine.CoverByIsbn} only.
         * <p>
         * A site can support a single (default) or multiple sizes.
         *
         * @return {@code true} if multiple sizes are supported.
         */
        boolean supportsMultipleCoverSizes() {
            return mSEConfig.supportsMultipleCoverSizes();
        }

        @Override
        public String toString() {
            return "Config{"
                   + "mClass=" + mClass
                   + ", mSEConfig=" + mSEConfig
                   + ", mLocale=" + mLocale
                   + ", mExternalIdDomain=" + mExternalIdDomain
                   + '}';
        }
    }
}
