/*
 * @Copyright 2018-2024 HardBackNutter
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpHead;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public abstract class SearchEngineBase
        implements SearchEngine {

    @NonNull
    private final SearchEngineConfig config;

    /**
     * Set by a client or from within the task.
     * It's a <strong>request</strong> to cancel while running.
     */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    /** Helper to randomize some urls to avoid fingerprinting by the servers. */
    @NonNull
    private final Random random;
    @Nullable
    private ImageDownloader imageDownloader;
    @Nullable
    private Cancellable caller;

    /**
     * Constructor.
     *
     * @param appContext The <strong>application</strong> context.
     *                   NOT stored.
     * @param config     the search engine configuration
     */
    protected SearchEngineBase(@NonNull final Context appContext,
                               @NonNull final SearchEngineConfig config) {
        this.config = config;

        random = new Random();
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
    public String getHostUrl(@NonNull final Context context) {
        return config.getHostUrl(context);
    }

    @Override
    public void ping(@NonNull final Context context)
            throws UnknownHostException,
                   IOException,
                   SocketTimeoutException,
                   MalformedURLException {
        ServiceLocator.getInstance().getNetworkChecker().ping(
                getHostUrl(context), config.getConnectTimeoutInMs(context));
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

    @NonNull
    protected RealNumberParser getRealNumberParser(@NonNull final Context context,
                                                   @NonNull final Locale siteLocale) {
        final List<Locale> locales = LocaleListUtils.asList(context, siteLocale);
        return new RealNumberParser(locales);
    }

    @NonNull
    protected MoneyParser getMoneyParser(@NonNull final Context context,
                                         @NonNull final Locale siteLocale) {
        return new MoneyParser(siteLocale, getRealNumberParser(context, siteLocale));
    }

    @NonNull
    protected DateParser getDateParser(@NonNull final Context context,
                                       @NonNull final Locale siteLocale) {
        final List<Locale> locales = LocaleListUtils.asList(context, siteLocale);
        final Locale systemLocale = ServiceLocator
                .getInstance().getSystemLocaleList().get(0);
        return new FullDateParser(systemLocale, locales);
    }

    @AnyThread
    @Override
    @CallSuper
    public void cancel() {
        cancelRequested.set(true);
        synchronized (this) {
            if (imageDownloader != null) {
                imageDownloader.cancel();
            }
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
     * to create a suitable {@link FutureHttpGet}.
     *
     * @param context Current context
     * @param <T>     return type
     *
     * @return new {@link FutureHttpGet} instance
     */
    @NonNull
    public <T> FutureHttpGet<T> createFutureGetRequest(@NonNull final Context context) {
        final FutureHttpGet<T> httpGet = new FutureHttpGet<>(config.getEngineId().getLabelResId());

        // Improve compatibility by sending standard headers.
        // Some headers are overridden in the ImageDownloader as needed.

        // Host & User-Agent are set in {@link FutureHttpBase#execute}
        // but can be overridden as needed.

        //Host: developer.android.com
        //User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0
        //Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8
        //Accept-Language: en-GB,en;q=0.8,nl-BE;q=0.5,de-DE;q=0.3
        //Accept-Encoding: gzip, deflate, br
        //DNT: 1
        //Connection: keep-alive
        //Upgrade-Insecure-Requests: 1
        //Sec-Fetch-Dest: document
        //Sec-Fetch-Mode: navigate
        //Sec-Fetch-Site: none
        //Sec-Fetch-User: ?1

        httpGet.setRequestProperty(HttpConstants.ACCEPT,
                                   HttpConstants.ACCEPT_KITCHEN_SINK);
        httpGet.setRequestProperty(HttpConstants.ACCEPT_LANGUAGE,
                                   createAcceptLanguageHeader(context));
        httpGet.setRequestProperty(HttpConstants.ACCEPT_ENCODING,
                                   HttpConstants.ACCEPT_ENCODING_GZIP);

        httpGet.setRequestProperty(HttpConstants.DNT, "1");

        httpGet.setRequestProperty(HttpConstants.CONNECTION,
                                   HttpConstants.CONNECTION_KEEP_ALIVE);
        httpGet.setRequestProperty(HttpConstants.UPGRADE_INSECURE_REQUESTS,
                                   HttpConstants.UPGRADE_INSECURE_REQUESTS_TRUE);

        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_DEST, "document");
        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_MODE, "navigate");
        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_SITE, "none");
        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_USER, "?1");

        // TODO: could add Platform in combo with the Randomizer
        // "Android", "Chrome OS", "Chromium OS", "iOS", "Linux", "macOS", "Windows", or "Unknown".
        // httpGet.setRequestProperty("Sec-CH-UA-Platform", "Windows");

        // httpGet.setRequestProperty(HttpConstants.CACHE_CONTROL, HttpConstants.CACHE_CONTROL_0);


        httpGet.setConnectTimeout(config.getConnectTimeoutInMs(context))
               .setReadTimeout(config.getReadTimeoutInMs(context))
               .setThrottler(config.getThrottler());
        return httpGet;
    }

    /**
     * Create a suitable "Accept-Language" with user and site language.
     * The priorities will be a little randomized to help prevent fingerprinting
     *
     * @param context Current context
     *
     * @return header string
     */
    @NonNull
    private String createAcceptLanguageHeader(@NonNull final Context context) {
        final Set<String> noDups = new HashSet<>();
        boolean addQ;

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final String userLanguage = userLocale.getLanguage();
        final String languageTag = userLocale.toLanguageTag();

        final Locale siteLocale = getLocale(context);
        final String siteLanguageTag = siteLocale.toLanguageTag();
        final String siteLanguage = siteLocale.getLanguage();

        final StringBuilder accept = new StringBuilder(languageTag);
        noDups.add(languageTag);

        if (!noDups.contains(userLanguage)) {
            accept.append(',').append(userLanguage);
            noDups.add(userLanguage);
        }

        final int offset = random.nextInt(2);

        // use 0.8 or 0.7
        //noinspection CheckStyle
        accept.append(";q=0.").append(8 + offset);

        addQ = false;
        if (!noDups.contains(siteLanguageTag)) {
            accept.append(',').append(siteLanguageTag);
            noDups.add(siteLanguageTag);
            addQ = true;
        }
        if (!noDups.contains(siteLanguage)) {
            accept.append(',').append(siteLanguage);
            noDups.add(siteLanguage);
            addQ = true;
        }
        // only add q if we actually added a value.
        if (addQ) {
            // use 0.5 or 0.4
            //noinspection CheckStyle
            accept.append(";q=0.").append(4 + offset);
        }

        // Always add english if not there already.
        //noinspection CheckStyle
        if (!noDups.contains("en")) {
            accept.append(',').append("en");
            // use 0.3 or 0.2
            accept.append(";q=0.").append(2 + offset);
        }

        return accept.toString();
    }

    /**
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@link FutureHttpHead}.
     *
     * @param context Current context
     * @param <T>     return type
     *
     * @return new {@link FutureHttpHead} instance
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public <T> FutureHttpHead<T> createFutureHeadRequest(@NonNull final Context context) {
        final FutureHttpHead<T> futureRequest = new FutureHttpHead<>(
                config.getEngineId().getLabelResId());

        futureRequest.setConnectTimeout(config.getConnectTimeoutInMs(context))
                     .setReadTimeout(config.getReadTimeoutInMs(context))
                     .setThrottler(config.getThrottler());
        return futureRequest;
    }

    /**
     * Convenience method to save an image using the engines specific network configuration.
     *
     * @param context Current context
     * @param url     Image file URL
     * @param bookId  more or less unique id; e.g. isbn or website native id, etc...
     * @param cIdx    0..n image index
     * @param size    (optional) size parameter for engines/sites which support one
     *
     * @return File fileSpec, or {@code null} on failure
     *
     * @throws StorageException The covers directory is not available
     */
    @WorkerThread
    @NonNull
    public Optional<String> saveImage(@NonNull final Context context,
                                      @NonNull final String url,
                                      @Nullable final String bookId,
                                      @IntRange(from = 0, to = 1) final int cIdx,
                                      @Nullable final Size size)
            throws StorageException {

        synchronized (this) {
            if (imageDownloader == null) {
                imageDownloader = new ImageDownloader(createFutureGetRequest(context));
            }
        }
        final String tempFilename = ImageDownloader.getTempFilename(
                getEngineId().getPreferenceKey(), bookId, cIdx, size);

        try {
            return imageDownloader.fetch(url, tempFilename)
                                  .map(File::getAbsolutePath);
        } catch (@NonNull final IOException e) {
            // we swallow IOExceptions, even when the disk is full.
            // We're counting on that condition to be caught elsewhere...
            // as handling it in each call here would become [bleep] fast.
            return Optional.empty();
        }
    }

    /**
     * Add or merge the given Author with/to the list of Authors already present
     * on the book.
     *
     * @param currentAuthor     to add
     * @param currentAuthorType type
     * @param book              Bundle to update
     */
    public void processAuthor(@NonNull final Author currentAuthor,
                              @Author.Type final int currentAuthorType,
                              @NonNull final Book book) {
        boolean add = true;
        // check if already present
        for (final Author author : book.getAuthors()) {
            if (author.equals(currentAuthor)) {
                // merge types.
                author.addType(currentAuthorType);
                add = false;
                // keep looping
            }
        }

        if (add) {
            currentAuthor.setType(currentAuthorType);
            book.add(currentAuthor);
        }
    }

    /**
     * Process the publication-date field according to the given site locale.
     * <p>
     * If the given date-string consists of 4 characters, it is assumed it's
     * a year-value and the simplified form will be set on the book.
     * Otherwise full parsing is done.
     *
     * @param context    Current context
     * @param siteLocale for parsing
     * @param dateStr    the date field as retrieved
     * @param book       Bundle to update
     */
    public void processPublicationDate(@NonNull final Context context,
                                       @NonNull final Locale siteLocale,
                                       @Nullable final String dateStr,
                                       @NonNull final Book book) {

        if (dateStr != null && !dateStr.isBlank()) {
            if (dateStr.length() == 4) {
                // we have a 4-digit year, use the simplified notation.
                try {
                    book.setPublicationDate(Integer.parseInt(dateStr));
                    return;
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore and continue with full parsing
                }
            }

            // error or not 4 digits? Do a full parse.
            getDateParser(context, siteLocale)
                    .parse(dateStr)
                    .ifPresent(book::setPublicationDate);

        }
    }
}
