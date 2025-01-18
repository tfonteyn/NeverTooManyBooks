/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * This class provides the <strong>mutable</strong> configuration
 * data for a {@link SearchEngine}.
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
     * @see #getTagsToIgnore(Context)
     */
    public static final String PK_TAGS_IGNORE = "tags.ignore";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * Whether to search by using the ISBN10 value or the original {@link DBKey#BOOK_ISBN}.
     * <p>
     * {@code boolean}
     */
    private static final String PK_SEARCH_ISBN_PREFER_10 = "search.byIsbn.prefer.10";
    /**
     * Prefixed with {@link EngineId#getPreferenceKey()}.
     * HTTP GET/HEAD requests will log urls, response-codes and manual redirects.
     * <p>
     * {@code boolean}
     */
    private static final String PK_ENABLE_HTTP_LOGGING = "logging.http.get";

    @NonNull
    private final EngineId engineId;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    /**
     * This is a reference to the <strong>static</strong> object created in the SearchEngine
     * implementation class.
     */
    @Nullable
    private final Throttler throttler;

    private final boolean prefersIsbn10;

    /**
     * Constructor.
     *
     * @param builder with configuration data
     */
    public SearchEngineConfig(@NonNull final Builder builder) {
        engineId = builder.engineId;

        prefersIsbn10 = builder.prefersIsbn10;

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
     * @param context   <strong>Application</strong> or <strong>test</strong> context.
     * @param languages the language cache container
     */
    public static void createRegistry(@NonNull final Context context,
                                      @NonNull final Languages languages) {
        synchronized (SearchEngineConfig.class) {
            EngineId.createEngineConfigurations(context);
            Arrays.stream(Site.Type.values())
                  .forEach(type -> type.createList(context, languages));
        }
    }

    /**
     * Get the user-configured timeout value for the given key.
     *
     * @param context      Current context
     * @param key          to fetch
     * @param defValueInMs default to use if not found
     *
     * @return timeout value in milliseconds
     */
    public static int getTimeoutValueInMs(@NonNull final Context context,
                                          @NonNull final String key,
                                          final int defValueInMs) {
        final int seconds = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getInt(key, 0);
        // <1000 as sanity check for roque preference file imports
        if (seconds > 0 && seconds < 1000) {
            return seconds * 1000;
        } else {
            return defValueInMs;
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public boolean isLogHttpGetRequests(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                engineId.getPreferenceKey() + '.' + PK_ENABLE_HTTP_LOGGING, false);
    }

    @VisibleForTesting
    public void setLogHttpGetRequests(@NonNull final Context context,
                                      final boolean flag) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putBoolean(engineId.getPreferenceKey() + '.' + PK_ENABLE_HTTP_LOGGING,
                                     flag)
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
     * @param context Current context
     *
     * @return host url
     */
    @NonNull
    public String getHostUrl(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(
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
     * <p>
     * We first try to get the engine specific setting, and if that does not exist,
     * the global setting. The global default is {@code false}.
     *
     * @param context Current context
     *
     * @return {@code true} if ISBN10 should be preferred.
     */
    boolean prefersIsbn10(@NonNull final Context context) {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        final String key = engineId.getPreferenceKey() + "." + PK_SEARCH_ISBN_PREFER_10;
        if (preferences.contains(key)) {
            return preferences.getBoolean(key, prefersIsbn10);
        } else {
            return preferences.getBoolean(PK_SEARCH_ISBN_PREFER_10, false);
        }
    }

    public void setTagsToIgnore(@NonNull final Context context,
                                @NonNull final Set<String> tags) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putStringSet(engineId.getPreferenceKey() + PK_TAGS_IGNORE, tags)
                         .apply();
    }

    @NonNull
    public Set<String> getTagsToIgnore(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getStringSet(engineId.getPreferenceKey() + PK_TAGS_IGNORE,
                                              Set.of());
    }

    /**
     * Timeout we allow for a connection to be established.
     *
     * @param context Current context
     *
     * @return milli seconds
     */
    public int getConnectTimeoutInMs(@NonNull final Context context) {
        return getTimeoutValueInMs(context, engineId.getPreferenceKey() + "."
                                            + PK_TIMEOUT_CONNECT_IN_SECONDS,
                                   connectTimeoutMs);
    }

    /**
     * Timeout we allow for getting a response from the remote server.
     *
     * @param context Current context
     *
     * @return milli seconds
     */
    public int getReadTimeoutInMs(@NonNull final Context context) {
        return getTimeoutValueInMs(context, engineId.getPreferenceKey() + "."
                                            + PK_TIMEOUT_READ_IN_SECONDS,
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
               + ", connectTimeoutMs=" + connectTimeoutMs
               + ", readTimeoutMs=" + readTimeoutMs
               + ", throttler=" + throttler
               + ", searchPrefersIsbn10=" + prefersIsbn10
               + '}';
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    public static class Builder {

        /**
         * Even if there are no specific terms of usage,
         * we're only going to send one request a second by default
         * as a courtesy/precaution.
         */
        static final int THROTTLER_DEFAULT_MS = 1_000;
        static final int FIVE_SECONDS = 5_000;
        static final int TEN_SECONDS = 10_000;

        @NonNull
        private final EngineId engineId;

        /** The DEFAULT for the engine. */
        private int connectTimeoutMs = FIVE_SECONDS;
        /** The DEFAULT for the engine. */
        private int readTimeoutMs = TEN_SECONDS;
        /** The DEFAULT for the engine. */
        private int throttlerTimeoutMs = THROTTLER_DEFAULT_MS;

        /** {@link SearchEngine.CoverByEdition} only. */
        private boolean supportsMultipleCoverSizes;

        /** The DEFAULT for the engine: {@code false}. */
        private boolean prefersIsbn10;

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
        public SearchEngineConfig build(@NonNull final Function<Builder, SearchEngineConfig> configSupplier) {
            final SearchEngineConfig config = configSupplier.apply(this);
            engineId.setConfig(config);
            return config;
        }

        @NonNull
        Builder setThrottlerTimeoutMs(final int timeoutInMillis) {
            throttlerTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        Builder setConnectTimeoutMs(final int timeoutInMillis) {
            connectTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        Builder setReadTimeoutMs(final int timeoutInMillis) {
            readTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        Builder setPrefersIsbn10(final boolean prefersIsbn10) {
            this.prefersIsbn10 = prefersIsbn10;
            return this;
        }
    }
}
