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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.network.Throttler;

/**
 * Immutable configuration data for a {@link SearchEngine}.
 * See {@link SearchSites} for more details.
 */
public final class SearchEngineConfig {

    @NonNull
    private final Class<? extends SearchEngine> mClass;

    @SearchSites.EngineId
    private final int mId;

    @StringRes
    private final int mLabelId;

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

    /**
     * This is a reference to the <strong>static</strong> object created in the SearchEngine
     * implementation class.
     */
    @Nullable
    private final Throttler mStaticThrottler;

    /** {@link SearchEngine.CoverByIsbn} only. */
    private final boolean mSupportsMultipleCoverSizes;

    /** file suffix for cover files. */
    @NonNull
    private final String mFilenameSuffix;


    /**
     * Constructor.
     */
    private SearchEngineConfig(@NonNull final Builder builder) {
        mClass = builder.mClass;
        mId = builder.mId;
        mLabelId = builder.mLabelId;
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
        mStaticThrottler = builder.mStaticThrottler;

        mSupportsMultipleCoverSizes = builder.mSupportsMultipleCoverSizes;
        mFilenameSuffix = builder.mFilenameSuffix != null ? builder.mFilenameSuffix : "";
    }

    @NonNull
    SearchEngine createSearchEngine() {
        try {
            final Constructor<? extends SearchEngine> c = mClass.getConstructor(
                    SearchEngineConfig.class);
            return c.newInstance(this);

        } catch (@NonNull final NoSuchMethodException | IllegalAccessException
                | InstantiationException | InvocationTargetException e) {
            throw new IllegalStateException(mClass + " must implement SearchEngine(int)", e);
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
    public int getLabelId() {
        return mLabelId;
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
    public int getConnectTimeoutInMs() {
        return mConnectTimeoutMs;
    }

    /**
     * Timeout we allow for a response to a request.
     *
     * @return defaults to 10 second. Override as needed.
     */
    public int getReadTimeoutInMs() {
        return mReadTimeoutMs;
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
        return mStaticThrottler;
    }

    /**
     * {@link SearchEngine.CoverByIsbn} only.
     * <p>
     * A site can support a single (default) or multiple sizes.
     *
     * @return {@code true} if multiple sizes are supported.
     */
    public boolean supportsMultipleCoverSizes() {
        return mSupportsMultipleCoverSizes;
    }

    @Override
    public String toString() {
        return "SearchEngineConfig{"
               + "mClass=" + mClass
               + ", mId=" + mId
               + ", mName=`" + mLabelId + '`'
               + ", mPrefKey=`" + mPrefKey + '`'
               + ", mUrl=`" + mUrl + '`'
               + ", mLocale=" + mLocale
               + ", mExternalIdDomain=" + mExternalIdDomain
               + ", mDomainViewId=" + mDomainViewId
               + ", mDomainMenuId=" + mDomainMenuId
               + ", mConnectTimeoutMs=" + mConnectTimeoutMs
               + ", mReadTimeoutMs=" + mReadTimeoutMs
               + ", mStaticThrottler=" + mStaticThrottler
               + ", mSupportsMultipleCoverSizes=" + mSupportsMultipleCoverSizes
               + ", mFilenameSuffix=`" + mFilenameSuffix + '`'
               + '}';
    }

    public static class Builder {

        static final int FIVE_SECONDS = 5_000;
        static final int TEN_SECONDS = 10_000;

        @NonNull
        private final Class<? extends SearchEngine> mClass;

        @SearchSites.EngineId
        private final int mId;

        @StringRes
        private final int mLabelId;

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

        private int mConnectTimeoutMs = FIVE_SECONDS;

        private int mReadTimeoutMs = TEN_SECONDS;

        @Nullable
        private Throttler mStaticThrottler;

        /** {@link SearchEngine.CoverByIsbn} only. */
        private boolean mSupportsMultipleCoverSizes;

        /** file suffix for cover files. */
        @Nullable
        private String mFilenameSuffix;


        public Builder(@NonNull final Class<? extends SearchEngine> clazz,
                       @SearchSites.EngineId final int id,
                       @StringRes final int labelId,
                       @NonNull final String prefKey,
                       @NonNull final String url) {
            mClass = clazz;
            mId = id;
            mLabelId = labelId;
            mPrefKey = prefKey;
            mUrl = url;
        }

        @NonNull
        public SearchEngineConfig build() {
            return new SearchEngineConfig(this);
        }

        @NonNull
        public Builder setCountry(@NonNull final String country,
                                  @NonNull final String lang) {
            mCountry = country;
            mLang = lang;
            return this;
        }

        @NonNull
        public Builder setStaticThrottler(@Nullable final Throttler staticThrottler) {
            mStaticThrottler = staticThrottler;
            return this;
        }

        @NonNull
        public Builder setConnectTimeoutMs(final int timeoutInMillis) {
            mConnectTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        public Builder setReadTimeoutMs(final int timeoutInMillis) {
            mReadTimeoutMs = timeoutInMillis;
            return this;
        }

        @NonNull
        public Builder setDomainKey(@NonNull final String domainKey) {
            mDomainKey = domainKey;
            return this;
        }

        @NonNull
        public Builder setDomainMenuId(@IdRes final int domainMenuId) {
            mDomainMenuId = domainMenuId;
            return this;
        }

        @NonNull
        public Builder setDomainViewId(@IdRes final int domainViewId) {
            mDomainViewId = domainViewId;
            return this;
        }

        @NonNull
        public Builder setSupportsMultipleCoverSizes(final boolean supportsMultipleCoverSizes) {
            mSupportsMultipleCoverSizes = supportsMultipleCoverSizes;
            return this;
        }

        @NonNull
        public Builder setFilenameSuffix(@NonNull final String filenameSuffix) {
            mFilenameSuffix = filenameSuffix;
            return this;
        }
    }
}
