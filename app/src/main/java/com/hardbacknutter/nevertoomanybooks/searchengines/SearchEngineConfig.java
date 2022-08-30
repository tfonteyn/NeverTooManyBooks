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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

/**
 * Configuration data for a {@link SearchEngine}.
 *
 * @see EngineId
 * @see SearchEngine
 * @see SearchEngineRegistry
 * @see Site
 */
public final class SearchEngineConfig {

    @NonNull
    private final EngineId engineId;

    @StringRes
    private final int labelResId;

    @NonNull
    private final String hostUrl;

    /** Constructed from language+country. */
    @NonNull
    private final Locale locale;

    /** {@link SearchEngine.ByExternalId} only. */
    @Nullable
    private final Domain externalIdDomain;

    @IdRes
    private final int domainViewId;

    @IdRes
    private final int domainMenuId;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    /**
     * This is a reference to the <strong>static</strong> object created in the SearchEngine
     * implementation class.
     */
    @Nullable
    private final Throttler throttler;

    private final boolean searchPrefersIsbn10;

    /** {@link SearchEngine.CoverByIsbn} only. */
    private final boolean supportsMultipleCoverSizes;

    /**
     * Constructor.
     *
     * @param builder with configuration data
     */
    private SearchEngineConfig(@NonNull final Builder builder) {
        engineId = builder.engineId;
        labelResId = builder.labelResId;
        hostUrl = builder.hostUrl;

        if (builder.lang != null && !builder.lang.isEmpty()
            && builder.country != null && !builder.country.isEmpty()) {
            locale = new Locale(builder.lang, builder.country.toUpperCase(Locale.ENGLISH));

        } else {
            // be lenient...
            locale = Locale.US;
        }

        if (builder.domainKey == null || builder.domainKey.isEmpty()) {
            externalIdDomain = null;
        } else {
            externalIdDomain = DBDefinitions.TBL_BOOKS.getDomain(builder.domainKey);
        }

        domainViewId = builder.domainViewId;
        domainMenuId = builder.domainMenuId;

        connectTimeoutMs = builder.connectTimeoutMs;
        readTimeoutMs = builder.readTimeoutMs;

        throttler = builder.throttler;

        searchPrefersIsbn10 = builder.searchPrefersIsbn10;

        supportsMultipleCoverSizes = builder.supportsMultipleCoverSizes;
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
     * Get the human-readable name of the site.
     *
     * @return the displayable name resource id
     */
    @StringRes
    public int getLabelResId() {
        return labelResId;
    }

    /**
     * Get the name for this engine.
     *
     * @param context Current context
     *
     * @return name
     */
    public String getName(@NonNull final Context context) {
        return context.getString(labelResId);
    }

    @NonNull
    public String getHostUrl() {
        return ServiceLocator.getPreferences().getString(
                engineId.getPreferenceKey() + Prefs.pk_suffix_host_url, hostUrl);
    }

    /**
     * Get the <strong>standard</strong> Locale for this engine.
     *
     * @return site locale
     */
    @NonNull
    public Locale getLocale() {
        return locale;
    }

    @Nullable
    public Domain getExternalIdDomain() {
        return externalIdDomain;
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
    public boolean isSearchPrefersIsbn10() {
        final SharedPreferences preferences = ServiceLocator.getPreferences();

        final String engineKey = engineId.getPreferenceKey() + "."
                                 + Prefs.pk_search_isbn_prefer_10;
        if (preferences.contains(engineKey)) {
            return preferences.getBoolean(engineKey, searchPrefersIsbn10);
        } else {
            return preferences.getBoolean(Prefs.pk_search_isbn_prefer_10, false);
        }
    }

    @IdRes
    public int getDomainViewId() {
        return domainViewId;
    }

    @IdRes
    public int getDomainMenuId() {
        return domainMenuId;
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

    /**
     * {@link SearchEngine.CoverByIsbn} only.
     * <p>
     * A site can support a single (default) or multiple sizes.
     *
     * @return {@code true} if multiple sizes are supported.
     */
    public boolean supportsMultipleCoverSizes() {
        return supportsMultipleCoverSizes;
    }

    @NonNull
    @Override
    public String toString() {
        return "SearchEngineConfig{"
               + "engineId=" + engineId
               + ", labelResId=`" + labelResId + '`'
               + ", hostUrl=`" + hostUrl + '`'
               + ", locale=" + locale
               + ", externalIdDomain=" + externalIdDomain
               + ", domainViewId=" + domainViewId
               + ", domainMenuId=" + domainMenuId
               + ", connectTimeoutMs=" + connectTimeoutMs
               + ", readTimeoutMs=" + readTimeoutMs
               + ", throttler=" + throttler
               + ", searchPrefersIsbn10=" + searchPrefersIsbn10
               + ", supportsMultipleCoverSizes=" + supportsMultipleCoverSizes
               + '}';
    }

    public static class Builder {

        static final int FIVE_SECONDS = 5_000;
        static final int TEN_SECONDS = 10_000;

        @NonNull
        private final EngineId engineId;

        @StringRes
        private final int labelResId;

        @NonNull
        private final String hostUrl;

        @Nullable
        private String lang;

        @Nullable
        private String country;

        @Nullable
        private String domainKey;

        @IdRes
        private int domainViewId;

        @IdRes
        private int domainMenuId;

        /** The DEFAULT for the engine. */
        private int connectTimeoutMs = FIVE_SECONDS;
        /** The DEFAULT for the engine. */
        private int readTimeoutMs = TEN_SECONDS;

        @Nullable
        private Throttler throttler;

        /** {@link SearchEngine.CoverByIsbn} only. */
        private boolean supportsMultipleCoverSizes;

        /** The DEFAULT for the engine: {@code false}. */
        private boolean searchPrefersIsbn10;


        public Builder(@NonNull final EngineId engineId,
                       @StringRes final int labelResId,
                       @NonNull final String hostUrl) {
            this.engineId = engineId;
            this.labelResId = labelResId;
            this.hostUrl = hostUrl;
        }

        @NonNull
        public SearchEngineConfig build() {
            return new SearchEngineConfig(this);
        }

        @NonNull
        public Builder setCountry(@NonNull final String country,
                                  @NonNull final String lang) {
            this.country = country;
            this.lang = lang;
            return this;
        }

        @NonNull
        public Builder setStaticThrottler(@Nullable final Throttler staticThrottler) {
            throttler = staticThrottler;
            return this;
        }

        @NonNull
        public Builder setConnectTimeoutMs(final int timeoutInMillis) {
            connectTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        public Builder setReadTimeoutMs(final int timeoutInMillis) {
            readTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        public Builder setDomainKey(@NonNull final String domainKey) {
            this.domainKey = domainKey;
            return this;
        }

        @NonNull
        public Builder setDomainMenuId(@IdRes final int domainMenuId) {
            this.domainMenuId = domainMenuId;
            return this;
        }

        @NonNull
        public Builder setDomainViewId(@IdRes final int domainViewId) {
            this.domainViewId = domainViewId;
            return this;
        }

        @NonNull
        public Builder setSupportsMultipleCoverSizes(final boolean supportsMultipleCoverSizes) {
            this.supportsMultipleCoverSizes = supportsMultipleCoverSizes;
            return this;
        }

        public Builder setSearchPrefersIsbn10(final boolean searchPrefersIsbn10) {
            this.searchPrefersIsbn10 = searchPrefersIsbn10;
            return this;
        }
    }
}
