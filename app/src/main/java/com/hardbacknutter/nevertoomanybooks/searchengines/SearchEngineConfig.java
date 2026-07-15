/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * This class provides the <strong>mutable</strong> configuration
 * data for a {@link SearchEngine}.
 * <p>
 * There is a 1:1 relation with the {@link EngineId}.
 *
 * @see EngineId
 * @see SearchEngine
 * @see Site
 */
public class SearchEngineConfig {

    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * Whether a website-specific-search (using a url) menu should be shown.
     * <p>
     * {@code boolean}
     * <p>
     * The "shopping" part is legacy/misnamed.
     *
     * @see SearchEngineConfig
     */
    public static final String PK_SEARCH_WEBSITE_MENU = "search.shopping.menu";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP socket connect timeout.
     * <p>
     * {@code int} in seconds
     */
    public static final String PK_TIMEOUT_CONNECT_IN_SECONDS = "timeout.connect";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP socket read timeout
     * <p>
     * {@code int} in seconds
     */
    public static final String PK_TIMEOUT_READ_IN_SECONDS = "timeout.read";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * A full url, including the http(s) part.
     * <p>
     * {@code String}
     */
    public static final String PK_HOST_URL = "host.url";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * A full url, including the http(s) part.
     * <p>
     * {@code String}
     */
    public static final String PK_HOST_USER = "host.user";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * Clear text, but removed from debug reports.
     * <p>
     * {@code String}
     */
    public static final String PK_HOST_PASSWORD = "host.password";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * The set of Tags an engine will ignore when parsing a book.
     *
     * @see #getTagsToIgnore()
     */
    private static final String PK_TAGS_IGNORE = "tags.ignore";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * Whether to search by using the ISBN10 value or the original {@link DBKey#ISBN}.
     * <p>
     * {@code boolean}
     */
    public static final String PK_SEARCH_ISBN_PREFER_10 = "search.byIsbn.prefer.10";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP GET/HEAD requests will log urls, response-codes and manual redirects.
     * <p>
     * {@code boolean}
     */
    static final String PK_ENABLE_HTTP_LOGGING = "logging.http.get";
    private static final int TO_MILLIS = 1000;

    @NonNull
    private final EngineId engineId;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    @NonNull
    private final Throttler throttler;

    private final boolean prefersIsbn10;
    private final Set<String> tagsToIgnore;

    /**
     * Constructor.
     *
     * @param builder with configuration data
     */
    public SearchEngineConfig(@NonNull final Builder builder) {
        engineId = builder.engineId;

        prefersIsbn10 = builder.prefersIsbn10;
        tagsToIgnore = builder.tagsToIgnore;

        connectTimeoutMs = builder.connectTimeoutMs;
        readTimeoutMs = builder.readTimeoutMs;
        if (builder.throttlerTimeoutMs > 0) {
            throttler = new Throttler(builder.throttlerTimeoutMs);
        } else {
            throttler = new Throttler(Throttler.THROTTLER_DEFAULT_MS);
        }
    }

    /**
     * Called by {@link App#onCreate()}.
     *
     * @param context   <strong>Application</strong> or <strong>test</strong> context.
     * @param languages the language cache container
     */
    public static void createRegistry(@NonNull final Context context,
                                      @NonNull final Languages languages) {
        synchronized (SearchEngineConfig.class) {
            Arrays.stream(EngineId.values())
                  .filter(EngineId::isEnabled)
                  .forEach(EngineId::config);

            Arrays.stream(Site.Type.values())
                  .forEach(type -> type.createList(context, languages));
        }
    }

    /**
     * Get the user-configured timeout value for the given key.
     *
     * @param key          to fetch
     * @param defValueInMs default to use if not found
     *
     * @return timeout value in milliseconds
     */
    public static int getTimeoutValueInMs(@NonNull final String key,
                                          final int defValueInMs) {
        final int seconds = ServiceLocator.getInstance().getSharedPreferences()
                                          .getInt(key, 0);
        // The value from prefs is in SECONDS
        if (seconds > 0) {
            // convert to milliseconds
            return seconds * TO_MILLIS;
        } else {
            return defValueInMs;
        }
    }

    /**
     * Whether all {@code HTTP GET} calls should be logged.
     * This is a configuration setting the user can change.
     *
     * @return flag
     *
     * @see #setLogHttpGetRequests(boolean)
     */
    public boolean isLogHttpGetRequests() {
        return ServiceLocator.getInstance().getSharedPreferences().getBoolean(
                engineId.getPreferenceKey() + '.' + PK_ENABLE_HTTP_LOGGING,
                false);
    }

    /**
     * For tests only. The configuration is set by the user from a preference screen.
     *
     * @param flag value
     */
    @VisibleForTesting
    public void setLogHttpGetRequests(final boolean flag) {
        ServiceLocator.getInstance().getSharedPreferences().edit().putBoolean(
                              engineId.getPreferenceKey() + '.' + PK_ENABLE_HTTP_LOGGING, flag)
                      .apply();
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

    /**
     * Get the user-configured host url for this engine.
     *
     * @return host url
     */
    @NonNull
    public String getHostUrl() {
        return ServiceLocator.getInstance().getSharedPreferences().getString(
                engineId.getPreferenceKey() + '.' + PK_HOST_URL,
                engineId.getDefaultUrl());
    }

    /**
     * Indicates if ISBN code should be forced down to ISBN10 (if possible) before a search.
     * <p>
     * By default, we search on the ISBN entered by the user.
     * A preference setting per site can override this.
     * If set, and an ISBN13 is passed in, it will be translated to an ISBN10 before starting
     * the search.
     *
     * @return {@code true} if ISBN10 should be preferred.
     */
    public boolean prefersIsbn10() {
        return ServiceLocator.getInstance().getSharedPreferences().getBoolean(
                engineId.getPreferenceKey() + '.' + PK_SEARCH_ISBN_PREFER_10,
                prefersIsbn10);

    }

    /**
     * Get the set of tags we need to ignore.
     *
     * @return set
     */
    @NonNull
    public Set<String> getTagsToIgnore() {
        return ServiceLocator.getInstance().getSharedPreferences().getStringSet(
                engineId.getPreferenceKey() + '.' + PK_TAGS_IGNORE,
                tagsToIgnore);
    }

    /**
     * Timeout we allow for a connection to be established.
     *
     * @return milliseconds
     */
    public int getConnectTimeoutInMs() {
        return getTimeoutValueInMs(
                engineId.getPreferenceKey() + '.' + PK_TIMEOUT_CONNECT_IN_SECONDS,
                connectTimeoutMs);
    }

    /**
     * Timeout we allow for getting a response from the remote server.
     *
     * @return milliseconds
     */
    public int getReadTimeoutInMs() {
        return getTimeoutValueInMs(
                engineId.getPreferenceKey() + '.' + PK_TIMEOUT_READ_IN_SECONDS,
                readTimeoutMs);
    }

    /**
     * Get the throttler for regulating network access.
     *
     * @return throttler to use
     */
    @NonNull
    public Throttler getThrottler() {
        return throttler;
    }

    @NonNull
    @Override
    public String toString() {
        return "SearchEngineConfig{"
               + "engineId=" + engineId
               + ", connectTimeoutMs=" + connectTimeoutMs
               + ", readTimeoutMs=" + readTimeoutMs
               + ", throttler=" + throttler
               + ", searchPrefersIsbn10=" + prefersIsbn10
               + ", tagsToIgnore=" + tagsToIgnore
               + '}';
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    public static class Builder {

        static final int FIVE_SECONDS = 5_000;
        static final int TEN_SECONDS = 10_000;

        @NonNull
        private final EngineId engineId;

        /** The DEFAULT for the engine. */
        private int connectTimeoutMs = FIVE_SECONDS;
        /** The DEFAULT for the engine. */
        private int readTimeoutMs = TEN_SECONDS;
        /** The DEFAULT for the engine. */
        private int throttlerTimeoutMs = Throttler.THROTTLER_DEFAULT_MS;

        /** {@link SearchEngine.CoverByEdition} only. */
        private boolean supportsMultipleCoverSizes;

        /** The DEFAULT for the engine: {@code false}. */
        private boolean prefersIsbn10;

        /** The DEFAULT for the engine. */
        private Set<String> tagsToIgnore = Set.of();

        /**
         * Constructor.
         *
         * @param engineId to create
         */
        public Builder(@NonNull final EngineId engineId) {
            this.engineId = engineId;
        }

        /**
         * Finish the build. Initialise the engine with the configuration.
         *
         * @param configSupplier the base or superclass for the configuration.
         *
         * @return the config
         */
        @NonNull
        public SearchEngineConfig build(@NonNull final Function<Builder, SearchEngineConfig>
                                                configSupplier) {
            return configSupplier.apply(this);
        }

        @NonNull
        public Builder setThrottlerDelayInMs(final int timeoutInMillis) {
            throttlerTimeoutMs = timeoutInMillis;
            return this;
        }

        /**
         * Default for this timeout.
         *
         * @param timeoutInMillis millis
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setConnectTimeoutMs(final int timeoutInMillis) {
            connectTimeoutMs = timeoutInMillis;
            return this;
        }

        /**
         * Default for this timeout.
         *
         * @param timeoutInMillis millis
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setReadTimeoutMs(final int timeoutInMillis) {
            readTimeoutMs = timeoutInMillis;
            return this;
        }

        /**
         * Default for the flag.
         *
         * @param prefersIsbn10 flag
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setPrefersIsbn10(final boolean prefersIsbn10) {
            this.prefersIsbn10 = prefersIsbn10;
            return this;
        }

        /**
         * Default set of tags to ignore. Some sites provide fairly useless tags, e.g. "books".
         *
         * @param tagsToIgnore set
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setTagsToIgnore(@NonNull final Set<String> tagsToIgnore) {
            this.tagsToIgnore = tagsToIgnore;
            return this;
        }
    }
}
