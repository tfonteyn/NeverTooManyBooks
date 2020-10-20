/*
 * @Copyright 2020 HardBackNutter
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
import androidx.annotation.StringRes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * A registry of all {@link SearchEngine} classes and their {@link Config}.
 */
public final class SearchEngineRegistry {

    /** All site configurations. */
    private static final Map<Integer, Config> SITE_CONFIGS_MAP = new HashMap<>();

    private SearchEngineRegistry() {
    }

    public static void create(@NonNull final Context context) {

        SearchSites.registerSearchEngineClasses();

        final Locale systemLocale = AppLocale.getInstance().getSystemLocale();
        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);

        for (final Site.Type type : Site.Type.values()) {
            type.createList(context, systemLocale, userLocale);
        }
    }

    /**
     * Register a {@link SearchEngine}.
     *
     * @param config to register
     */
    static void add(@NonNull final Config config) {
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
     * @return Config
     */
    @NonNull
    public static Config getByEngineId(@SearchSites.EngineId final int engineId) {
        return Objects.requireNonNull(SITE_CONFIGS_MAP.get(engineId), "engine not found");
    }

    /**
     * Search for the configuration defined by the given viewId.
     *
     * @param viewId for the engine
     *
     * @return Optional Config
     */
    @NonNull
    public static Optional<Config> getByViewId(@IdRes final int viewId) {
        return SITE_CONFIGS_MAP.values().stream()
                               .filter(config -> config.getDomainViewId() == viewId)
                               .findFirst();
    }

    /**
     * Search for the configuration defined by the given menuId.
     *
     * @param menuId to get
     *
     * @return Optional Config
     */
    @NonNull
    public static Optional<Config> getByMenuId(@IdRes final int menuId) {
        return SITE_CONFIGS_MAP.values().stream()
                               .filter(config -> config.getDomainMenuId() == menuId)
                               .findFirst();
    }

    @NonNull
    public static SearchEngine createSearchEngine(@NonNull final Context context,
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
        return SITE_CONFIGS_MAP.values().stream()
                               .map(Config::getExternalIdDomain)
                               .filter(Objects::nonNull)
                               .collect(Collectors.toList());
    }

    /**
     * Immutable configuration data for a {@link SearchEngine}.
     * See {@link SearchSites} for more details.
     */
    public static final class Config {

        @NonNull
        private final Class<? extends SearchEngine> mClass;

        @SearchSites.EngineId
        private final int mId;

        @StringRes
        private final int mNameResId;

        @NonNull
        private final String mPrefKey;

        @NonNull
        private final String mUrl;

        /** Constructed from language+country. */
        @NonNull
        private final Locale mLocale;

        /** {@link SearchEngine.ByExternalId} only. */
        @Nullable
        private final Domain mExternalIdDomain;

        @IdRes
        private final int mDomainViewId;

        @IdRes
        private final int mDomainMenuId;

        private final int mConnectTimeoutMs;

        private final int mReadTimeoutMs;

        /** {@link SearchEngine.CoverByIsbn} only. */
        private final boolean mSupportsMultipleCoverSizes;

        /** file suffix for cover files. */
        @NonNull
        private final String mFilenameSuffix;


        /**
         * Constructor.
         */
        private Config(@NonNull final Builder builder) {
            mClass = builder.mClass;
            mId = builder.mId;
            mNameResId = builder.mNameResId;
            mPrefKey = builder.mPrefKey;
            mUrl = builder.mUrl;

            if (builder.mLang != null && !builder.mLang.isEmpty()
                && builder.mCountry != null && !builder.mCountry.isEmpty()) {
                mLocale = new Locale(builder.mLang, builder.mCountry.toUpperCase(Locale.ENGLISH));

            } else {
                // be lenient...
                mLocale = Locale.US;
            }

            if (builder.mDomainKey == null || builder.mDomainKey.isEmpty()) {
                mExternalIdDomain = null;
            } else {
                mExternalIdDomain = DBDefinitions.TBL_BOOKS.getDomain(builder.mDomainKey);
            }

            mDomainViewId = builder.mDomainViewId;
            mDomainMenuId = builder.mDomainMenuId;

            mConnectTimeoutMs = builder.mConnectTimeoutMs;
            mReadTimeoutMs = builder.mReadTimeoutMs;

            mSupportsMultipleCoverSizes = builder.mSupportsMultipleCoverSizes;
            mFilenameSuffix = builder.mFilenameSuffix != null ? builder.mFilenameSuffix : "";
        }

        @NonNull
        SearchEngine createSearchEngine(@NonNull final Context context) {
            try {
                final Constructor<? extends SearchEngine> c =
                        mClass.getConstructor(Context.class, int.class);
                // ALWAYS use the localized Application context here
                // It's going to get used in background tasks!
                return c.newInstance(context.getApplicationContext(), mId);

            } catch (@NonNull final NoSuchMethodException | IllegalAccessException
                    | InstantiationException | InvocationTargetException e) {
                throw new IllegalStateException(mClass
                                                + " must implement SearchEngine(Context,int)", e);
            }
        }

        @SearchSites.EngineId
        public int getEngineId() {
            return mId;
        }

        /**
         * Get the human-readable name of the site.
         *
         * @return the displayable name resource id
         */
        @StringRes
        public int getNameResId() {
            return mNameResId;
        }

        @NonNull
        String getPreferenceKey() {
            return mPrefKey;
        }

        @NonNull
        String getFilenameSuffix() {
            return mFilenameSuffix;
        }

        @NonNull
        public String getSiteUrl() {
            return mUrl;
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
            return mDomainViewId;
        }

        @IdRes
        int getDomainMenuId() {
            return mDomainMenuId;
        }

        /**
         * Timeout we allow for a connection to work.
         *
         * @return defaults to 5 second. Override as needed.
         */
        public int getConnectTimeoutMs() {
            return mConnectTimeoutMs;
        }

        /**
         * Timeout we allow for a response to a request.
         *
         * @return defaults to 10 second. Override as needed.
         */
        public int getReadTimeoutMs() {
            return mReadTimeoutMs;
        }

        /**
         * {@link SearchEngine.CoverByIsbn} only.
         * <p>
         * A site can support a single (default) or multiple sizes.
         *
         * @return {@code true} if multiple sizes are supported.
         */
        boolean supportsMultipleCoverSizes() {
            return mSupportsMultipleCoverSizes;
        }

        @Override
        public String toString() {
            return "Config{"
                   + "mClass=" + mClass
                   + ", mId=" + mId
                   + ", mName=`" + mNameResId + '`'
                   + ", mPrefKey=`" + mPrefKey + '`'
                   + ", mUrl=`" + mUrl + '`'
                   + ", mLocale=" + mLocale
                   + ", mExternalIdDomain=" + mExternalIdDomain
                   + ", mDomainViewId=" + mDomainViewId
                   + ", mDomainMenuId=" + mDomainMenuId
                   + ", mConnectTimeoutMs=" + mConnectTimeoutMs
                   + ", mReadTimeoutMs=" + mReadTimeoutMs
                   + ", mSupportsMultipleCoverSizes=" + mSupportsMultipleCoverSizes
                   + ", mFilenameSuffix=`" + mFilenameSuffix + '`'
                   + '}';
        }

        public static class Builder {

            @NonNull
            private final Class<? extends SearchEngine> mClass;

            @SearchSites.EngineId
            private final int mId;

            @StringRes
            private final int mNameResId;

            @NonNull
            private final String mPrefKey;

            @NonNull
            private final String mUrl;

            @Nullable
            private String mLang;

            @Nullable
            private String mCountry;

            @Nullable
            private String mDomainKey;

            @IdRes
            private int mDomainViewId;

            @IdRes
            private int mDomainMenuId;

            private int mConnectTimeoutMs = 5_000;

            private int mReadTimeoutMs = 10_000;

            /** {@link SearchEngine.CoverByIsbn} only. */
            private boolean mSupportsMultipleCoverSizes;

            /** file suffix for cover files. */
            @Nullable
            private String mFilenameSuffix;


            public Builder(@NonNull final Class<? extends SearchEngine> clazz,
                           @SearchSites.EngineId final int id,
                           @StringRes final int nameResId,
                           @NonNull final String prefKey,
                           @NonNull final String url) {
                mClass = clazz;
                mId = id;
                mNameResId = nameResId;
                mPrefKey = prefKey;
                mUrl = url;
            }

            public Config build() {
                return new Config(this);
            }

            public Builder setCountry(@NonNull final String country,
                                      @NonNull final String lang) {
                mCountry = country;
                mLang = lang;
                return this;
            }

            public Builder setTimeout(final int connectTimeoutMs,
                                      final int readTimeoutMs) {
                mConnectTimeoutMs = connectTimeoutMs;
                mReadTimeoutMs = readTimeoutMs;
                return this;
            }

            public Builder setDomainKey(@NonNull final String domainKey) {
                mDomainKey = domainKey;
                return this;
            }

            public Builder setDomainMenuId(@IdRes final int domainMenuId) {
                mDomainMenuId = domainMenuId;
                return this;
            }

            public Builder setDomainViewId(@IdRes final int domainViewId) {
                mDomainViewId = domainViewId;
                return this;
            }

            public Builder setSupportsMultipleCoverSizes(final boolean supportsMultipleCoverSizes) {
                mSupportsMultipleCoverSizes = supportsMultipleCoverSizes;
                return this;
            }

            public Builder setFilenameSuffix(@NonNull final String filenameSuffix) {
                mFilenameSuffix = filenameSuffix;
                return this;
            }
        }
    }
}
