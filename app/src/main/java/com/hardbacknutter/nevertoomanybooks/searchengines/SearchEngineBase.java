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

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.EmptySuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpHead;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.util.logger.LoggerFactory;

public abstract class SearchEngineBase
        implements SearchEngine {

    @NonNull
    private final SearchEngineConfig config;
    @Nullable
    private SSLContext sslContext;

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

    /**
     * Create a new {@link FullDateParser}.
     * This method is meant to be overridden if SearchEngines need to apply
     * special rules.
     *
     * @param context Current context
     * @param locale  the site locale
     *
     * @return new instance
     */
    @EmptySuper
    @NonNull
    protected DateParser<LocalDateTime> getDateParser(@NonNull final Context context,
                                                      @NonNull final Locale locale) {
        final List<Locale> locales = LocaleListUtils.asList(context, locale);
        final Locale systemLocale = ServiceLocator
                .getInstance().getSystemLocaleList().get(0);
        return new FullDateParser(new ISODateParser(systemLocale), locales);
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

    protected void setSslContext(@Nullable final SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    @Nullable
    SSLContext getSslContext() {
        return sslContext;
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
        final FutureHttpHead<T> httpHead = new FutureHttpHead<>(
                config.getEngineId().getLabelResId());
        httpHead.setConnectTimeout(config.getConnectTimeoutInMs(context))
                .setReadTimeout(config.getReadTimeoutInMs(context))
                .setThrottler(config.getThrottler())
                .setSSLContext(sslContext)
                .enableLogging(config.isLogHttpGetRequests(context));
        return httpHead;
    }

    /**
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@link FutureHttpGet}.
     * <p>
     * The headers are set to the defaults as used by Firefox to request a "document"
     *
     * @param context Current context
     * @param <T>     return type
     *
     * @return new {@link FutureHttpGet} instance
     */
    @NonNull
    public <T> FutureHttpGet<T> createGetDocumentRequest(@NonNull final Context context) {
        final FutureHttpGet<T> httpGet = createRawGetRequest(context);

        // Improve compatibility by sending standard headers.
        // Some headers are overridden in #createGetImageRequest as needed.

        // Host & User-Agent are set in {@link FutureHttpBase#execute}
        // but can be overridden as needed.

        // Example of a Firefox request to https://developer.android.com

        //Host: developer.android.com
        //User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0
        //Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
        //Accept-Language: en-GB,en;q=0.8,nl-BE;q=0.5,de-DE;q=0.3
        //Accept-Encoding: gzip, deflate, br, zstd
        //DNT: 1
        //Sec-GPC: 1
        //Upgrade-Insecure-Requests: 1
        //Sec-Fetch-Dest: document
        //Sec-Fetch-Mode: navigate
        //Sec-Fetch-Site: none
        //Sec-Fetch-User: ?1
        //Connection: keep-alive

        // The "Sec-GPC" header above is documented as EXPERIMENTAL at
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-GPC
        // It seems only firefox is sending it and it's not used by any other browser.
        // We're not sending it for now.

        httpGet.setRequestProperty(HttpConstants.ACCEPT,
                                   HttpConstants.ACCEPT_KITCHEN_SINK);
        httpGet.setRequestProperty(HttpConstants.ACCEPT_LANGUAGE,
                                   createAcceptLanguageHeader(context));
        httpGet.setRequestProperty(HttpConstants.ACCEPT_ENCODING,
                                   HttpConstants.ACCEPT_ENCODING_GZIP);

        // Deprecated but Firefox/Chrome are still sending it by default.
        httpGet.setRequestProperty(HttpConstants.DNT, "1");

        httpGet.setRequestProperty(HttpConstants.CONNECTION,
                                   HttpConstants.CONNECTION_KEEP_ALIVE);
        httpGet.setRequestProperty(HttpConstants.UPGRADE_INSECURE_REQUESTS,
                                   HttpConstants.UPGRADE_INSECURE_REQUESTS_TRUE);

        // We want a generic document, e.g. html, xml, json, ...
        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_DEST,
                                   HttpConstants.SEC_FETCH_DEST_DOCUMENT);
        // The request is initiated by navigation between HTML documents.
        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_MODE,
                                   HttpConstants.SEC_FETCH_MODE_NAVIGATE);

        // The request was send by a "user" (our app) and not some auto/link/etc...
        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_SITE,
                                   HttpConstants.SEC_FETCH_SITE_NONE);
        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_USER, "?1");

        // TODO: could add Platform in combo with the Randomizer
        // "Android", "Chrome OS", "Chromium OS", "iOS", "Linux", "macOS", "Windows", or "Unknown".
        // httpGet.setRequestProperty("Sec-CH-UA-Platform", "Windows");

        return httpGet;
    }

    @NonNull
    public <T> FutureHttpGet<T> createGetImageRequest(@NonNull final Context context) {
        final FutureHttpGet<T> httpGet = createGetDocumentRequest(context);
        httpGet.setRequestProperty(HttpConstants.ACCEPT, HttpConstants.ACCEPT_IMAGE);
        // We want a generic image
        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_DEST,
                                   HttpConstants.SEC_FETCH_DEST_IMAGE);

        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_MODE,
                                   HttpConstants.SEC_FETCH_MODE_NO_CORS);
        httpGet.setRequestProperty(HttpConstants.SEC_FETCH_SITE,
                                   HttpConstants.SEC_FETCH_SITE_SAME_ORIGIN);
        return httpGet;
    }

    @NonNull
    private <T> FutureHttpGet<T> createRawGetRequest(@NonNull final Context context) {
        final FutureHttpGet<T> httpGet = new FutureHttpGet<>(
                config.getEngineId().getLabelResId());
        httpGet.setConnectTimeout(config.getConnectTimeoutInMs(context))
               .setReadTimeout(config.getReadTimeoutInMs(context))
               .setThrottler(config.getThrottler())
               .setSSLContext(sslContext)
               .enableLogging(config.isLogHttpGetRequests(context));
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
     * @throws StorageException The covers directory is not available
     */
    @WorkerThread
    @NonNull
    public Optional<String> saveImage(@NonNull final Context context,
                                      @NonNull final String url,
                                      @Nullable final Map<String, String> requestProperties,
                                      @Nullable final String bookId,
                                      @IntRange(from = 0, to = 1) final int cIdx,
                                      @Nullable final Size size)
            throws StorageException {

        synchronized (this) {
            if (imageDownloader == null) {
                final FutureHttpGet<File> httpGet = createGetImageRequest(context);
                if (requestProperties != null) {
                    requestProperties.forEach(httpGet::setRequestProperty);
                }
                imageDownloader = new ImageDownloader(httpGet);
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
    public void addAuthor(@NonNull final Author currentAuthor,
                          @Author.Type final int currentAuthorType,
                          @NonNull final Book book) {
        boolean add = true;
        // check if already present
        for (final Author author : book.getAuthors()) {
            if (author.equals(currentAuthor)) {
                // merge types.
                author.addType(currentAuthorType);
                // merge identifiers
                // ENHANCE: we could now have multiple identifiers
                //  for a single Author. As we don't support that...
                //  first id "wins"
                author.addIdentifiers(currentAuthor.getIdentifiers());

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
     * Process the given name and add as {@link Publisher} if appropriate.
     *
     * @param name of a publisher
     * @param book Bundle to update
     */
    public void addPublisher(@Nullable final String name,
                             @NonNull final Book book) {
        if (name == null || name.isBlank()) {
            return;
        }

        final Publisher currentPublisher = Publisher.from(name);
        // add if not already present
        if (book.getPublishers().stream().noneMatch(pub -> pub.equals(currentPublisher))) {
            book.add(currentPublisher);
        }
    }

    /**
     * Process the publication-date field according to the given site locale.
     * <p>
     * If the given date-string consists of 4 characters, it is assumed it's
     * a year-value and the simplified form will be set on the book.
     * Otherwise full parsing is done.
     * <p>
     * Note that the input <strong>MUST</strong> be either a 4-digit year,
     * or a full-date string in one of the supported formats.
     * Partial date-strings will <strong>FAIL</strong>
     *
     * @param context Current context
     * @param locale  for parsing
     * @param dateStr the date field as retrieved
     * @param book    Bundle to update
     */
    public void addPublicationDate(@NonNull final Context context,
                                   @NonNull final Locale locale,
                                   @Nullable final String dateStr,
                                   @NonNull final Book book) {

        if (dateStr == null || dateStr.isBlank()) {
            return;
        }

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
        getDateParser(context, locale)
                .parse(dateStr)
                .ifPresent(book::setPublicationDate);
    }

    /**
     * Process the first-publication-date field according to the given site locale.
     * <p>
     * If the given date-string consists of 4 characters, it is assumed it's
     * a year-value and the simplified form will be set on the book.
     * Otherwise full parsing is done.
     * <p>
     * Note that the input <strong>MUST</strong> be either a 4-digit year,
     * or a full-date string in one of the supported formats.
     * Partial date-strings will <strong>FAIL</strong>
     *
     * @param context Current context
     * @param locale  for parsing
     * @param dateStr the date field as retrieved
     * @param book    Bundle to update
     */
    protected void addFirstPublicationDate(@NonNull final Context context,
                                           @NonNull final Locale locale,
                                           @Nullable final String dateStr,
                                           @NonNull final Book book) {

        if (dateStr == null || dateStr.isBlank()) {
            return;
        }

        if (dateStr.length() == 4) {
            // we have a 4-digit year, use the simplified notation.
            try {
                book.setFirstPublicationDate(Integer.parseInt(dateStr));
                return;
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore and continue with full parsing
            }
        }

        // error or not 4 digits? Do a full parse.
        getDateParser(context, locale)
                .parse(dateStr)
                .ifPresent(book::setFirstPublicationDate);
    }

    /**
     * Process the price-listed field according to the given site locale.
     *
     * @param context     Current context
     * @param locale      for parsing
     * @param priceStr    the field as retrieved with or without currency embedded
     * @param currencyStr optional default currency string to use
     *                    when the priceStr does not have one
     * @param book        Bundle to update
     */
    public void addPriceListed(@NonNull final Context context,
                               @NonNull final Locale locale,
                               @NonNull final String priceStr,
                               @Nullable final String currencyStr,
                               @NonNull final Book book) {

        final List<Locale> locales = LocaleListUtils.asList(context, locale);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);
        final MoneyParser parser = new MoneyParser(locale, realNumberParser);

        // TODO: maybe move this logic to the MoneyParser class ?
        // First ignore the given currency string (if any) and try parsing
        final Optional<Money> oMoney = parser.parse(priceStr);
        if (oMoney.isPresent()) {
            Money money = oMoney.get();
            if (money.getCurrency() != null) {
                // We have parsed both the value and the currency from the input string.
                book.putMoney(DBKey.PRICE_LISTED, money);
                return;

            } else if (currencyStr != null && !currencyStr.isBlank()) {
                try {
                    // use the given currency string, and the value from the previous parse result
                    final Currency currency = Currency.getInstance(currencyStr);
                    money = new Money(money.getValue(), currency);
                    book.putMoney(DBKey.PRICE_LISTED, money);
                    return;
                } catch (@NonNull final IllegalArgumentException ignore) {
                    // ignore
                }
            }
        }

        // Parsing failed, store the input string as-is.
        book.putString(DBKey.PRICE_LISTED, priceStr);
        // Add the default currency if any
        if (currencyStr != null && !currencyStr.isBlank()) {
            book.putString(DBKey.PRICE_LISTED_CURRENCY, currencyStr);
        }

        // log this as we need to understand WHY it failed
        LoggerFactory.getLogger().w(TAG, "processPriceListed Failed to parse",
                                    "currencyStr=" + currencyStr,
                                    "priceStr=" + priceStr);
    }
}
