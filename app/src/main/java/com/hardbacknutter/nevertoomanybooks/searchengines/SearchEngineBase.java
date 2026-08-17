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

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.net.CookieStore;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpLanguageHeader;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.network.HttpFutureFactory;
import com.hardbacknutter.nevertoomanybooks.network.HttpCallFactory;
import com.hardbacknutter.util.logger.LoggerFactory;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class SearchEngineBase
        implements SearchEngine {

    @NonNull
    private final SearchEngineConfig config;
    /**
     * Set by a client or from within the task.
     * It's a <strong>request</strong> to cancel while running.
     */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();

    @NonNull
    protected final BookParserHelper bookParserHelper;
    @NonNull
    protected final HttpFutureFactory httpFutureFactory;
    @NonNull
    protected final HttpCallFactory httpCallFactory;
    private final String languageHeader;

    /** Lazy created in {@link #getImageDownloader()}. */
    @Nullable
    private volatile ImageDownloader imageDownloader;
    /**
     * Either set from a child constructor with
     * {@link #setImageRequestFactory(RequestFactory)}
     * or lazy created in {@link #getImageRequestFactory()}.
     */
    @Nullable
    private volatile RequestFactory imageRequestFactory;

    @Nullable
    private Cancellable caller;

    /**
     * Constructor.
     *
     * @param context Current context. NOT stored.
     * @param config  the search engine configuration
     *
     * @see EngineId#createSearchEngine(Context)
     */
    protected SearchEngineBase(@NonNull final Context context,
                               @NonNull final SearchEngineConfig config) {
        this.config = config;
        this.bookParserHelper = new BookParserHelper(config);

        final CookieStore cookieStore = ServiceLocator.getInstance()
                                                      .getCookieManager()
                                                      .getCookieStore();
        languageHeader = createLanguageHeader(context);
        httpFutureFactory = new HttpFutureFactory(config, null, cookieStore, languageHeader);
        httpCallFactory = new HttpCallFactory(config, null, cookieStore, languageHeader);
    }

    private String createLanguageHeader(@NonNull final Context context) {
        final Locale siteLocale = getLocale(context);
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        return HttpLanguageHeader.create(siteLocale, userLocale);
    }

    /**
     * Override the default.
     * This method <strong>must</strong> be called from the child constructor.
     *
     * @param imageRequestFactory to use
     */
    protected void setImageRequestFactory(@NonNull final RequestFactory imageRequestFactory) {
        this.imageRequestFactory = imageRequestFactory;
    }

    @NonNull
    @Override
    public EngineId getEngineId() {
        return config.getEngineId();
    }

    @Override
    public void ping()
            throws UnknownHostException,
                   IOException,
                   SocketTimeoutException,
                   MalformedURLException {
        ServiceLocator.getInstance().getNetworkChecker().ping(
                config.getHostUrl(), config.getConnectTimeoutInMs());
    }

    /**
     * Get the <strong>standard</strong> Locale for this engine.
     * <p>
     * Override to derive the locale from the host-url instead
     *
     * @return site locale
     *
     * @see #getLocale(Context, String)
     */
    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        return config.getEngineId().getDefaultLocale();
    }

    /**
     * Derive the Locale from the actual url.
     * <p>
     * Sites which support multiple countries, should overwrite {@link #getLocale(Context)} with
     * {@code getLocale(context, getHostUrl()); }
     *
     * @param context Current context
     * @param baseUrl to digest
     *
     * @return Locale matching the url root domain
     */
    @NonNull
    protected Locale getLocale(@NonNull final Context context,
                               @NonNull final String baseUrl) {

        final String root = baseUrl.substring(baseUrl.lastIndexOf('.') + 1);
        switch (root) {
            case "com":
                return Locale.US;

            case "uk":
                // country code is GB
                return Locale.UK;

            default:
                // other sites are (should be ?) just the country code.
                final Locale userLocale = context.getResources().getConfiguration().getLocales()
                                                 .get(0);
                final Optional<Locale> locale = ServiceLocator.getInstance().getAppLocale()
                                                              .getLocale(root, userLocale);
                if (BuildConfig.DEBUG /* always */) {
                    LoggerFactory.getLogger().d(config.getEngineId().name(),
                                                "baseUrl=" + baseUrl, "getLocale=" + locale);
                }
                return locale.orElse(Locale.US);
        }
    }

    @NonNull
    public SearchEngineConfig getConfig() {
        return config;
    }

    @VisibleForTesting
    @NonNull
    public BookParserHelper getParserHelper() {
        return bookParserHelper;
    }

    @NonNull
    public HttpFutureFactory getHttpFutureFactory() {
        return httpFutureFactory;
    }

    @NonNull
    public HttpCallFactory getHttpCallFactory() {
        return httpCallFactory;
    }

    @Override
    @AnyThread
    @CallSuper
    public void cancel() {
        cancelRequested.set(true);
        synchronized (this) {
            final ImageDownloader downloader = imageDownloader;
            if (downloader != null) {
                downloader.cancel();
            }
        }
    }

    @Override
    public void setCaller(@Nullable final Cancellable caller) {
        this.caller = caller;
        cancelRequested.set(false);
    }

    @Override
    public boolean isCancelled() {
        // caller being null should only happen when we check if we're cancelled
        // before a search was started.
        return cancelRequested.get() || caller == null || caller.isCancelled();
    }

    /**
     * Convenience method to save an image using the engines specific network configuration.
     *
     * @param context           Current context
     * @param url               Image file URL
     * @param requestProperties optional map
     * @param bookId            more or less unique id; e.g. isbn or website native id, etc...
     * @param cIdx              0..n image index
     * @param size              (optional) size parameter for engines/sites which support one
     *
     * @return File fileSpec, or {@code Optional.empty()} on failure
     *
     * @throws CoverStorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    public Optional<String> saveImage(@NonNull final Context context,
                                      @NonNull final String url,
                                      @Nullable final Map<String, String> requestProperties,
                                      @Nullable final String bookId,
                                      @IntRange(from = 0, to = 3) final int cIdx,
                                      @Nullable final ImageWebSize size)
            throws CoverStorageException {

        final String tempFilename = ImageFileInfo.getTempFilename(
                config.getEngineId().getPreferenceKey(), bookId, cIdx, size);

        try {
            final Request imageRequest = getImageRequestFactory()
                    .createRequest(url, requestProperties);

            return getImageDownloader().fetch(imageRequest, tempFilename)
                                       .map(File::getAbsolutePath);

        } catch (@NonNull final IOException e) {
            // we swallow IOExceptions, even when the disk is full.
            // We're counting on that condition to be caught elsewhere...
            // as handling it in each call here would become [bleep] fast.
            return Optional.empty();
        }
    }

    @NonNull
    private RequestFactory getImageRequestFactory() {
        RequestFactory instance = imageRequestFactory;
        if (instance == null) {
            synchronized (this) {
                instance = imageRequestFactory;
                if (instance == null) {
                    instance = new ImageRequestFactoryDefault(languageHeader);
                    imageRequestFactory = instance;
                }
            }
        }
        return instance;
    }

    @NonNull
    private ImageDownloader getImageDownloader() {
        ImageDownloader instance = imageDownloader;
        if (instance == null) {
            synchronized (this) {
                instance = imageDownloader;
                if (instance == null) {
                    final OkHttpClient httpClient = httpCallFactory.getHttpClient();
                    instance = new ImageDownloader(httpClient,
                                                   config.getThrottler(),
                                                   config.getEngineId().getLabelResId(),
                                                   config.isLogHttpGetRequests());
                    imageDownloader = instance;
                }
            }
        }
        return instance;
    }
}
