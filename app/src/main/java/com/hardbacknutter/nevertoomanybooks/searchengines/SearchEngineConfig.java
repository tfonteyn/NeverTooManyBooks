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
import android.content.SharedPreferences;

import androidx.annotation.IdRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Configuration data for a {@link SearchEngine}.
 * <p>
 * TODO: this class should/could be merged with {@link EngineId}.
 * However... do we remove the config entirely, or do we move all fixed settings from EngineId
 * to the config?
 *
 * @see EngineId
 * @see SearchEngine
 * @see Site
 */
public final class SearchEngineConfig {

    @NonNull
    private final EngineId engineId;

    /** {@link SearchEngine.ByExternalId} only. */
    @Nullable
    private final Domain externalIdDomain;
    @IdRes
    private final int domainViewId;
    @IdRes
    private final int domainMenuId;
    @IntegerRes
    private final int domainMenuOrder;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    /**
     * This is a reference to the <strong>static</strong> object created in the SearchEngine
     * implementation class.
     */
    @Nullable
    private final Throttler throttler;

    private final boolean prefersIsbn10;
    /** {@link SearchEngine.CoverByIsbn} only. */
    private final boolean supportsMultipleCoverSizes;

    /**
     * Constructor.
     *
     * @param builder with configuration data
     */
    private SearchEngineConfig(@NonNull final Builder builder) {
        engineId = builder.engineId;

        prefersIsbn10 = builder.prefersIsbn10;
        supportsMultipleCoverSizes = builder.supportsMultipleCoverSizes;

        externalIdDomain = builder.externalIdDomain;
        domainViewId = builder.domainViewId;
        domainMenuId = builder.domainMenuId;
        domainMenuOrder = builder.domainMenuOrder;

        connectTimeoutMs = builder.connectTimeoutMs;
        readTimeoutMs = builder.readTimeoutMs;
        if (builder.throttlerTimeoutMs > 0) {
            throttler = new Throttler(builder.throttlerTimeoutMs);
        } else {
            throttler = null;
        }
    }

    /**
     * Called by {@link ServiceLocator#create(Context)}.
     *
     * @param context <strong>Application</strong> or <strong>test</strong> context.
     */
    public static void createRegistry(@NonNull final Context context) {
        synchronized (SearchEngineConfig.class) {
            EngineId.registerSearchEngines();
            Arrays.stream(Site.Type.values())
                  .forEach(type -> type.createList(context));
        }
    }

    /**
     * Search for the {@link SearchEngineConfig} defined by the given viewId.
     *
     * @param viewId for the engine
     *
     * @return Optional {@link SearchEngineConfig}
     */
    @NonNull
    public static Optional<SearchEngineConfig> getByViewId(@IdRes final int viewId) {
        return getAll()
                .stream()
                .filter(config -> config.getDomainViewId() == viewId)
                .findFirst();
    }

    /**
     * Search for the {@link SearchEngineConfig} defined by the given menuId.
     *
     * @param menuId to get
     *
     * @return Optional {@link SearchEngineConfig}
     */
    @NonNull
    public static Optional<SearchEngineConfig> getByMenuId(@IdRes final int menuId) {
        return getAll()
                .stream()
                .filter(config -> config.getDomainMenuResId() == menuId)
                .findFirst();
    }

    /**
     * Convenience method to get the list of <strong>configured</strong> (i.e. non-null)
     * external-id domains.
     *
     * @return list
     */
    @NonNull
    public static List<Domain> getExternalIdDomains() {
        return getAll()
                .stream()
                .map(SearchEngineConfig::getExternalIdDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Convenience method. Get all configurations (i.e. non-null).
     *
     * @return list
     */
    @NonNull
    public static List<SearchEngineConfig> getAll() {
        return Arrays.stream(EngineId.values())
                     .map(EngineId::getConfig)
                     .filter(Objects::nonNull)
                     .collect(Collectors.toList());
    }

    /**
     * Get the engine id.
     *
     * @return engine id
     */
    @NonNull
    public EngineId getEngineId() {
        return engineId;
    }

    @NonNull
    public String getHostUrl() {
        return ServiceLocator.getPreferences().getString(
                engineId.getPreferenceKey() + Prefs.pk_suffix_host_url,
                engineId.getDefaultUrl());
    }

    @Nullable
    public Domain getExternalIdDomain() {
        return externalIdDomain;
    }

    /**
     * {@link SearchEngine.CoverByIsbn} only.
     * <p>
     * A site can support a single (default) or multiple sizes.
     *
     * @return {@code true} if multiple sizes are supported.
     */
    boolean supportsMultipleCoverSizes() {
        return supportsMultipleCoverSizes;
    }

    /**
     * Indicates if ISBN code should be forced down to ISBN10 (if possible) before a search.
     * <p>
     * By default, we search on the ISBN entered by the user.
     * A preference setting per site can override this.
     * If set, and an ISBN13 is passed in, it will be translated to an ISBN10 before starting
     * the search.
     * <p>
     * We first try to get the engine specific setting, and if that does not exist,
     * the global setting. The global default is {@code false}.
     *
     * @return {@code true} if ISBN10 should be preferred.
     */
    boolean prefersIsbn10() {
        final SharedPreferences preferences = ServiceLocator.getPreferences();

        final String key = engineId.getPreferenceKey() + "." + Prefs.pk_search_isbn_prefer_10;
        if (preferences.contains(key)) {
            return preferences.getBoolean(key, prefersIsbn10);
        } else {
            return preferences.getBoolean(Prefs.pk_search_isbn_prefer_10, false);
        }
    }

    @SuppressWarnings("WeakerAccess")
    @IdRes
    public int getDomainViewId() {
        return domainViewId;
    }

    /**
     * Get the resource id to use for the "View on" menu item.
     *
     * @return res id
     */
    @IdRes
    public int getDomainMenuResId() {
        return domainMenuId;
    }

    /**
     * Get the <strong>resource id</strong> to use for the "View on" menu item order.
     * Typical use will require code like this:
     * <pre>
     * {@code
     *  int resId = x.getDomainMenuOrderResId();
     *  int order = context.getResources().getInteger(resId)
     *  }
     *  </pre>
     *
     * @return res id
     */
    @IntegerRes
    public int getDomainMenuOrderResId() {
        return domainMenuOrder;
    }

    /**
     * Timeout we allow for a connection to be established.
     *
     * @return milli seconds
     */
    public int getConnectTimeoutInMs() {
        return Prefs.getTimeoutValueInMs(engineId.getPreferenceKey() + "."
                                         + Prefs.pk_timeout_connect_in_seconds,
                                         connectTimeoutMs);
    }

    /**
     * Timeout we allow for getting a response from the remote server.
     *
     * @return milli seconds
     */
    public int getReadTimeoutInMs() {
        return Prefs.getTimeoutValueInMs(engineId.getPreferenceKey() + "."
                                         + Prefs.pk_timeout_read_in_seconds,
                                         readTimeoutMs);
    }

    /**
     * Get the throttler for regulating network access.
     * <p>
     * The <strong>static</strong> Throttler is created in the SearchEngine implementation class.
     *
     * @return throttler to use, or {@code null} for none.
     */
    @Nullable
    public Throttler getThrottler() {
        return throttler;
    }

    @NonNull
    @Override
    public String toString() {
        return "SearchEngineConfig{"
               + "engineId=" + engineId
               + ", externalIdDomain=" + externalIdDomain
               + ", domainViewId=" + domainViewId
               + ", domainMenuId=" + domainMenuId
               + ", domainMenuOrder=" + domainMenuOrder
               + ", connectTimeoutMs=" + connectTimeoutMs
               + ", readTimeoutMs=" + readTimeoutMs
               + ", throttler=" + throttler
               + ", searchPrefersIsbn10=" + prefersIsbn10
               + ", supportsMultipleCoverSizes=" + supportsMultipleCoverSizes
               + '}';
    }

    public static class Builder {

        static final int FIVE_SECONDS = 5_000;
        static final int TEN_SECONDS = 10_000;

        @NonNull
        private final EngineId engineId;

        @Nullable
        private Domain externalIdDomain;

        @IdRes
        private int domainViewId;

        @IdRes
        private int domainMenuId;
        @IntegerRes
        private int domainMenuOrder;

        /** The DEFAULT for the engine. */
        private int connectTimeoutMs = FIVE_SECONDS;
        /** The DEFAULT for the engine. */
        private int readTimeoutMs = TEN_SECONDS;

        private int throttlerTimeoutMs;

        /** {@link SearchEngine.CoverByIsbn} only. */
        private boolean supportsMultipleCoverSizes;

        /** The DEFAULT for the engine: {@code false}. */
        private boolean prefersIsbn10;

        public Builder(@NonNull final EngineId engineId) {
            this.engineId = engineId;
        }

        public void build() {
            engineId.setConfig(new SearchEngineConfig(this));
        }

        @NonNull
        Builder setThrottlerTimeoutMs(@SuppressWarnings("SameParameterValue") final int timeoutInMillis) {
            throttlerTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        Builder setConnectTimeoutMs(final int timeoutInMillis) {
            connectTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        Builder setReadTimeoutMs(
                @SuppressWarnings("SameParameterValue") final int timeoutInMillis) {
            readTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        Builder setDomainKey(@NonNull final String domainKey) {
            if (domainKey.isEmpty()) {
                externalIdDomain = null;
            } else {
                externalIdDomain = DBDefinitions.TBL_BOOKS.getDomain(domainKey);
            }
            return this;
        }

        @NonNull
        Builder setDomainMenuId(@IdRes final int domainMenuId,
                                @IntegerRes final int domainMenuOrder) {
            this.domainMenuId = domainMenuId;
            this.domainMenuOrder = domainMenuOrder;
            return this;
        }

        @NonNull
        Builder setDomainViewId(@IdRes final int domainViewId) {
            this.domainViewId = domainViewId;
            return this;
        }

        @NonNull
        Builder setSupportsMultipleCoverSizes(
                @SuppressWarnings("SameParameterValue") final boolean supportsMultipleCoverSizes) {
            this.supportsMultipleCoverSizes = supportsMultipleCoverSizes;
            return this;
        }

        @NonNull
        Builder setPrefersIsbn10(
                @SuppressWarnings("SameParameterValue") final boolean prefersIsbn10) {
            this.prefersIsbn10 = prefersIsbn10;
            return this;
        }
    }
}
