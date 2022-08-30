/*
 * @Copyright 2018-2022 HardBackNutter
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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.EnumMap;
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
 *
 * @see EngineId
 * @see SearchEngine
 * @see SearchEngineConfig
 * @see Site
 */
public final class SearchEngineRegistry {

    private static final String ERROR_ENGINE_NOT_FOUND = "engine not found";
    private static final String ERROR_EMPTY_CONFIG_MAP = "empty config map";
    private static final String ERROR_REGISTRY_NOT_CREATED = "SearchEngineRegistry not created";

    /** Singleton. */
    @Nullable
    private static SearchEngineRegistry sInstance;

    /** All site configurations. */
    private final Map<EngineId, SearchEngineConfig> siteConfigs = new EnumMap<>(EngineId.class);

    /**
     * Constructor. Use {@link #getInstance()}.
     */
    private SearchEngineRegistry() {
    }

    public static void create(@NonNull final Context context) {
        synchronized (SearchEngineRegistry.class) {
            sInstance = new SearchEngineRegistry();
            EngineId.registerSearchEngineClasses(sInstance);
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
        return Objects.requireNonNull(sInstance, ERROR_REGISTRY_NOT_CREATED);
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
        siteConfigs.put(config.getEngineId(), config);
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
            SanityCheck.requirePositiveValue(siteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }
        return siteConfigs.values();
    }

    /**
     * Get the configuration for the given EngineId.
     *
     * @param engineId the search engine id
     *
     * @return SearchEngineConfig
     */
    @NonNull
    public SearchEngineConfig getByEngineId(@NonNull final EngineId engineId) {
        return Objects.requireNonNull(siteConfigs.get(engineId), ERROR_ENGINE_NOT_FOUND);
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
            SanityCheck.requirePositiveValue(siteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }
        return siteConfigs.values().stream()
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
            SanityCheck.requirePositiveValue(siteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }
        return siteConfigs.values().stream()
                          .filter(config -> config.getDomainMenuId() == menuId)
                          .findFirst();
    }

    /**
     * Get the list of <strong>configured</strong> external-id domains.
     *
     * @return list
     */
    @NonNull
    public List<Domain> getExternalIdDomains() {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requirePositiveValue(siteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }
        return siteConfigs.values()
                          .stream()
                          .map(SearchEngineConfig::getExternalIdDomain)
                          .filter(Objects::nonNull)
                          .collect(Collectors.toList());
    }

    /**
     * Create a SearchEngine instance based on the registered configuration for the given id.
     *
     * @param engineId the search engine id
     *
     * @return a new instance
     */
    @NonNull
    public SearchEngine createSearchEngine(@NonNull final EngineId engineId) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requirePositiveValue(siteConfigs.size(), ERROR_EMPTY_CONFIG_MAP);
        }

        final SearchEngineConfig searchEngineConfig = siteConfigs.get(engineId);
        try {
            final Constructor<? extends SearchEngine> c = engineId.getClazz().getConstructor(
                    SearchEngineConfig.class);
            return c.newInstance(searchEngineConfig);

        } catch (@NonNull final NoSuchMethodException | IllegalAccessException
                | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException(
                    engineId.getClazz() + " must implement SearchEngine(SearchEngineConfig)", e);
        }
    }
}
