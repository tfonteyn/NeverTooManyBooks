/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;

public abstract class SearchEngineBase
        implements SearchEngine {

    @NonNull
    protected final Context appContext;
    @NonNull
    private final SearchEngineConfig config;

    /**
     * Set by a client or from within the task.
     * It's a <strong>request</strong> to cancel while running.
     */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    @NonNull
    private final ImageDownloader imageDownloader;
    @Nullable
    private Cancellable caller;

    /**
     * Constructor.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    public SearchEngineBase(@NonNull final Context appContext,
                            @NonNull final SearchEngineConfig config) {
        // only stored to use for preference etc lookups.
        this.appContext = appContext;
        this.config = config;
        imageDownloader = new ImageDownloader(createFutureGetRequest(),
                                              ServiceLocator.getInstance()::getCoverStorage);
    }

    /**
     * Helper method.
     * <p>
     * Look for a book title; if present try to get a Series from it and clean the book title.
     * <p>
     * This default implementation is fine for most engines but not always needed.
     * TODO: we probably call checkForSeriesNameInTitle for sites that don't need it.
     *
     * @param book Bundle to update
     */
    protected static void checkForSeriesNameInTitle(@NonNull final Book book) {
        final String fullTitle = book.getString(DBKey.TITLE, null);
        if (fullTitle != null && !fullTitle.isEmpty()) {
            final Matcher matcher = Series.TEXT1_BR_TEXT2_BR_PATTERN.matcher(fullTitle);
            if (matcher.find()) {
                // the cleansed title
                final String bookTitle = matcher.group(1);
                if (bookTitle != null) {
                    // the series title/number
                    final String seriesTitleWithNumber = matcher.group(2);

                    if (seriesTitleWithNumber != null && !seriesTitleWithNumber.isEmpty()) {
                        // add to the TOP of the list.
                        book.add(0, Series.from(seriesTitleWithNumber));

                        // and store cleansed book title back
                        book.putString(DBKey.TITLE, bookTitle);
                    }
                }
            }
        }
    }

    @NonNull
    @Override
    public EngineId getEngineId() {
        return config.getEngineId();
    }

    @NonNull
    @Override
    public String getName(@NonNull final Context context) {
        return config.getEngineId().getName(context);
    }

    @NonNull
    @Override
    public String getHostUrl() {
        return config.getHostUrl(appContext);
    }

    @Override
    public void ping()
            throws UnknownHostException,
                   IOException,
                   SocketTimeoutException,
                   MalformedURLException {
        ServiceLocator.getInstance().getNetworkChecker().ping(
                getHostUrl(), config.getConnectTimeoutInMs(appContext));
    }

    @Override
    public boolean supportsMultipleCoverSizes() {
        return config.supportsMultipleCoverSizes();
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
                // country code is GB (july 2020: for now...)
                return Locale.UK;

            default:
                // other sites are (should be ?) just the country code.
                final Optional<Locale> locale = ServiceLocator.getInstance().getAppLocale()
                                                              .getLocale(context, root);
                if (BuildConfig.DEBUG /* always */) {
                    LoggerFactory.getLogger()
                                 .d(TAG, "getLocale", "locale=" + locale);
                }
                return locale.orElse(Locale.US);
        }
    }


    //FIXME: Potentially unsafe 'if != null then cancel'
    @AnyThread
    @Override
    @CallSuper
    public void cancel() {
        cancelRequested.set(true);
        synchronized (imageDownloader) {
            imageDownloader.cancel();
        }
    }

    @Override
    public void reset() {
        setCaller(null);
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
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@link FutureHttpGet#createGet(int)}.
     *
     * @param <T> return type
     *
     * @return new {@link FutureHttpGet} instance
     */
    @NonNull
    public <T> FutureHttpGet<T> createFutureGetRequest() {
        final FutureHttpGet<T> httpGet = FutureHttpGet
                .createGet(config.getEngineId().getLabelResId());

        httpGet.setConnectTimeout(config.getConnectTimeoutInMs(appContext))
               .setReadTimeout(config.getReadTimeoutInMs(appContext))
               .setThrottler(config.getThrottler());
        return httpGet;
    }

    /**
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@link FutureHttpGet#createHead(int)}.
     *
     * @param <T> return type
     *
     * @return new {@link FutureHttpGet} instance
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public <T> FutureHttpGet<T> createFutureHeadRequest() {
        final FutureHttpGet<T> httpGet = FutureHttpGet
                .createHead(config.getEngineId().getLabelResId());

        httpGet.setConnectTimeout(config.getConnectTimeoutInMs(appContext))
               .setReadTimeout(config.getReadTimeoutInMs(appContext))
               .setThrottler(config.getThrottler());
        return httpGet;
    }

    /**
     * Convenience method to save an image using the engines specific network configuration.
     *
     * @param url    Image file URL
     * @param bookId more or less unique id; e.g. isbn or website native id, etc...
     * @param cIdx   0..n image index
     * @param size   (optional) size parameter for engines/sites which support one
     *
     * @return File fileSpec, or {@code null} on failure
     *
     * @throws StorageException The covers directory is not available
     */
    @WorkerThread
    @Nullable
    public String saveImage(@NonNull final String url,
                            @Nullable final String bookId,
                            @IntRange(from = 0, to = 1) final int cIdx,
                            @Nullable final Size size)
            throws StorageException {

        final String tempFilename = ImageDownloader.getTempFilename(
                getEngineId().getPreferenceKey(), bookId, cIdx, size);

        try {
            return imageDownloader.fetch(url, tempFilename)
                                  .map(File::getAbsolutePath)
                                  .orElse(null);
        } catch (@NonNull final IOException e) {
            // we swallow IOExceptions, even when the disk is full.
            // We're counting on that condition to be caught elsewhere...
            // as handling it in each call here would become [bleep] fast.
            return null;
        }
    }
}
