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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

/**
 * A registry of all {@link SearchEngine} classes and their {@link SearchEngineConfig}.
 */
public final class SearchEngineRegistry {

    private static final String ERROR_EMPTY_CONFIG_MAP = "empty config map";

    /** Singleton. */
    @Nullable
    private static SearchEngineRegistry sInstance;

    /** All site configurations. */
    private final Map<Integer, SearchEngineConfig> mSiteConfigs = new HashMap<>();

    /**
     * Constructor. Use {@link #getInstance()}.
     */
    private SearchEngineRegistry() {
    }

    public static void create(@NonNull final Context context) {
        synchronized (SearchEngineRegistry.class) {
            sInstance = new SearchEngineRegistry();
            SearchSites.registerSearchEngineClasses(sInstance);
            Site.Type.registerAllTypes(context);
        }
    }

    /**
     * Get the singleton instance.
     *
     * @return instance
     */
    @NonNull
    public static SearchEngineRegistry getInstance() {
        // do not lazy initialize here. We want the SearchEngineRegistry running at startup.
        return Objects.requireNonNull(sInstance, "SearchEngineRegistry not created");
    }

    /**
     * Register a {@link SearchEngine}.
     *
     * @param config to register
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    SearchEngineRegistry add(@NonNull final SearchEngineConfig config) {
        mSiteConfigs.put(config.getEngineId(), config);
        return this;
    }

    /**
     * Get the list of all configured search engines.
     *
     * @return list
     */
    @NonNull
    public Collection<SearchEngineConfig> getAll() {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requirePositiveValue(mSiteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }
        return mSiteConfigs.values();
    }

    /**
     * Get the configuration for the given EngineId.
     *
     * @param engineId the search engine id
     *
     * @return SearchEngineConfig
     */
    @NonNull
    public SearchEngineConfig getByEngineId(@SearchSites.EngineId final int engineId) {
        return Objects.requireNonNull(mSiteConfigs.get(engineId), "engine not found");
    }

    /**
     * Search for the configuration defined by the given viewId.
     *
     * @param viewId for the engine
     *
     * @return Optional SearchEngineConfig
     */
    @NonNull
    public Optional<SearchEngineConfig> getByViewId(@IdRes final int viewId) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requirePositiveValue(mSiteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }
        return mSiteConfigs.values().stream()
                           .filter(config -> config.getDomainViewId() == viewId)
                           .findFirst();
    }

    /**
     * Search for the configuration defined by the given menuId.
     *
     * @param menuId to get
     *
     * @return Optional SearchEngineConfig
     */
    @NonNull
    public Optional<SearchEngineConfig> getByMenuId(@IdRes final int menuId) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requirePositiveValue(mSiteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }
        return mSiteConfigs.values().stream()
                           .filter(config -> config.getDomainMenuId() == menuId)
                           .findFirst();
    }

    /**
     * Create a SearchEngine instance.
     *
     * @param engineId the search engine id
     *
     * @return the instance
     */
    @NonNull
    public SearchEngine createSearchEngine(@SearchSites.EngineId final int engineId) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requirePositiveValue(mSiteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }
        //noinspection ConstantConditions
        return mSiteConfigs.get(engineId).createSearchEngine();
    }

    /**
     * Get the list of <strong>configured</strong> external-id domains.
     *
     * @return list
     */
    @NonNull
    public List<Domain> getExternalIdDomains() {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requirePositiveValue(mSiteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }
        return mSiteConfigs.values()
                           .stream()
                           .map(SearchEngineConfig::getExternalIdDomain)
                           .filter(Objects::nonNull)
                           .collect(Collectors.toList());
    }

}
