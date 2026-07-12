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
import android.os.LocaleList;

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
import java.net.URL;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpLanguageHeader;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.database.dao.IdentifierDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.network.HttpCallFactory;
import com.hardbacknutter.util.logger.LoggerFactory;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public abstract class SearchEngineBase
        implements SearchEngine {

    @NonNull
    private final SearchEngineConfig config;
    /**
     * Set by a client or from within the task.
     * It's a <strong>request</strong> to cancel while running.
     */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();

    private final IdentifierDao identifierDao;
    protected final ISODateParser isoDateParser;

    @Nullable
    private SSLContext sslContext;
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

        identifierDao = ServiceLocator.getInstance().getIdentifierDao();

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        isoDateParser = new ISODateParser(systemLocale);
    }

    @NonNull
    private static Map<String, String> createHeadersForGET(@NonNull final Locale siteLocale,
                                                           @NonNull final Locale userLocale) {
        final String acceptLanguageHeader = HttpLanguageHeader.create(siteLocale, userLocale);
        // Improve compatibility by sending standard headers.

        // Example of a Firefox request to https://developer.android.com

        // GET / HTTP/1.1
        // Host: developer.android.com
        // User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0
        // Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
        // Accept-Language: en-GB,en;q=0.9,nl-BE;q=0.8,de-DE;q=0.7
        // Accept-Encoding: gzip, deflate, br, zstd
        // DNT: 1
        // Sec-GPC: 1
        // Upgrade-Insecure-Requests: 1
        // Sec-Fetch-Dest: document
        // Sec-Fetch-Mode: navigate
        // Sec-Fetch-Site: none
        // Sec-Fetch-User: ?1
        // Connection: keep-alive

        // ordered list.
        final Map<String, String> headers = new LinkedHashMap<>();
        // Host & User-Agent are prefixed to the below in {@link FutureHttp#execute}

        headers.put(HttpConstants.ACCEPT,
                    HttpConstants.ACCEPT_KITCHEN_SINK);
        headers.put(HttpConstants.ACCEPT_LANGUAGE,
                    acceptLanguageHeader);
        headers.put(HttpConstants.ACCEPT_ENCODING,
                    HttpConstants.ACCEPT_ENCODING_GZIP);

        headers.put(HttpConstants.DNT, "1");
        headers.put(HttpConstants.SEC_GPC, "1");

        headers.put(HttpConstants.UPGRADE_INSECURE_REQUESTS,
                    HttpConstants.UPGRADE_INSECURE_REQUESTS_TRUE);

        // We want a generic document, e.g. html, xml, json, ...
        headers.put(HttpConstants.SEC_FETCH_DEST,
                    HttpConstants.SEC_FETCH_DEST_DOCUMENT);
        // The request is initiated by navigation between HTML documents.
        headers.put(HttpConstants.SEC_FETCH_MODE,
                    HttpConstants.SEC_FETCH_MODE_NAVIGATE);

        // The request was sent by a "user" (our app) and not some auto/link/etc...
        headers.put(HttpConstants.SEC_FETCH_SITE,
                    HttpConstants.SEC_FETCH_SITE_NONE);
        headers.put(HttpConstants.SEC_FETCH_USER, "?1");

        headers.put(HttpConstants.CONNECTION,
                    HttpConstants.CONNECTION_KEEP_ALIVE);
        return headers;
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

    @Override
    @NonNull
    public String getHostUrl() {
        return config.getHostUrl();
    }

    @Override
    public void ping()
            throws UnknownHostException,
                   IOException,
                   SocketTimeoutException,
                   MalformedURLException {
        ServiceLocator.getInstance().getNetworkChecker().ping(
                getHostUrl(), config.getConnectTimeoutInMs());
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
                    LoggerFactory.getLogger()
                                 .d(getName(context), "baseUrl=" + baseUrl,
                                    "getLocale", "locale=" + locale);
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
    @NonNull
    protected DateParser<LocalDateTime> getFullDateParser(@NonNull final Context context,
                                                          @NonNull final Locale locale) {
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(locale, userLocales);
        return new FullDateParser(isoDateParser, allLocales);
    }

    @Override
    @AnyThread
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

    @Nullable
    protected SSLContext getSslContext() {
        return sslContext;
    }

    protected void setSslContext(@Nullable final SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@code HEAD} request.
     *
     * @param <T> return type
     *
     * @return new {@code HEAD} request instance
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public <T> FutureHttp<T> createHeadRequest() {
        final FutureHttp<T> httpHead = HttpCallFactory.create(config.getEngineId());
        httpHead.setSSLContext(sslContext);
        return httpHead;
    }

    /**
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@code GET} request.
     * <p>
     * The headers are set to the defaults as used by Firefox to request a "document"
     *
     * @param context Current context
     * @param <T>     return type
     *
     * @return new {@code GET} request instance
     */
    @NonNull
    public <T> FutureHttp<T> createGetDocumentRequest(@NonNull final Context context) {

        final Locale siteLocale = getLocale(context);
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        final Map<String, String> headers = createHeadersForGET(siteLocale, userLocale);

        final FutureHttp<T> httpGet = HttpCallFactory.create(config.getEngineId());
        httpGet.setSSLContext(sslContext);
        httpGet.setHeaders(headers);

        return httpGet;
    }

    /**
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@link OkHttpClient}.
     * <p>
     * Overridable for sites requiring quirks...
     *
     * @return new {@link OkHttpClient} instance
     *
     * @see #createGetDocumentRequest(Context)
     */
    @NonNull
    @EmptySuper
    public OkHttpClient createHttpClient() {
        return HttpCallFactory.createHttpClient(getEngineId(), sslContext);
    }

    /**
     * Convenience method to create a suitable {@code GET} {@link Request}.
     * <p>
     * Overridable for sites requiring quirks...
     *
     * @param context           Current context
     * @param urlStr            to use
     * @param requestProperties (optional) extra headers to add/override
     *
     * @return new {@code GET} request instance
     *
     * @throws MalformedURLException on url errors
     * @see #createGetDocumentRequest(Context)
     */
    @NonNull
    @EmptySuper
    protected Request createImageRequest(@NonNull final Context context,
                                         @NonNull final String urlStr,
                                         @Nullable final Map<String, String> requestProperties)
            throws MalformedURLException {

        // GET /devrel-devsite/prod/v63ff991b83776932202eabe7967909a8dae574de15846bab934768a76bf6c589/android/images/lockup.png HTTP/1.1
        // Host: www.gstatic.com
        // User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0
        // Accept: image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5
        // Accept-Language: en-GB,en;q=0.9,nl-BE;q=0.8,de-DE;q=0.7
        // Accept-Encoding: gzip, deflate, br, zstd
        // Referer: https://developer.android.com/
        // Sec-Fetch-Storage-Access: none
        // DNT: 1
        // Sec-GPC: 1
        // Sec-Fetch-Dest: image
        // Sec-Fetch-Mode: no-cors
        // Sec-Fetch-Site: cross-site
        //C onnection: keep-alive

        final Request.Builder builder = new Request.Builder()
                .url(urlStr)
                .header(HttpConstants.HOST, new URL(urlStr).getHost())
                .header(HttpConstants.USER_AGENT,
                        HttpConstants.USER_AGENT_FIREFOX)

                .header(HttpConstants.ACCEPT,
                        HttpConstants.ACCEPT_IMAGE)
                .header(HttpConstants.ACCEPT_LANGUAGE,
                        createAcceptLanguageHeader(context))
                .header(HttpConstants.ACCEPT_ENCODING,
                        HttpConstants.ACCEPT_ENCODING_GZIP)

                .header(HttpConstants.SEC_FETCH_STORAGE_ACCESS,
                        HttpConstants.SEC_FETCH_STORAGE_ACCESS_NONE)

                .header(HttpConstants.DNT, "1")
                .header(HttpConstants.SEC_GPC, "1")

                //We want a generic image
                .header(HttpConstants.SEC_FETCH_DEST,
                        HttpConstants.SEC_FETCH_DEST_IMAGE)
                .header(HttpConstants.SEC_FETCH_MODE,
                        HttpConstants.SEC_FETCH_MODE_NO_CORS)
                // same site... might need to use SEC_FETCH_SITE_CROSS_SITE ?
                .header(HttpConstants.SEC_FETCH_SITE,
                        HttpConstants.SEC_FETCH_SITE_NONE)

                .header(HttpConstants.CONNECTION,
                        HttpConstants.CONNECTION_KEEP_ALIVE);

        // add or override
        if (requestProperties != null) {
            requestProperties.forEach(builder::header);
        }

        return builder.build();
    }

    /**
     * Create a suitable "Accept-Language" with user and site language.
     * The priorities will be a little randomised to help prevent fingerprinting
     *
     * @param context Current context
     *
     * @return header string
     */
    @NonNull
    private String createAcceptLanguageHeader(@NonNull final Context context) {
        final Locale siteLocale = getLocale(context);
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        return HttpLanguageHeader.create(siteLocale, userLocale);
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
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    public Optional<String> saveImage(@NonNull final Context context,
                                      @NonNull final String url,
                                      @Nullable final Map<String, String> requestProperties,
                                      @Nullable final String bookId,
                                      @IntRange(from = 0, to = 3) final int cIdx,
                                      @Nullable final ImageWebSize size)
            throws StorageException {

        synchronized (this) {
            if (imageDownloader == null) {
                final OkHttpClient httpClient = createHttpClient();
                imageDownloader = new ImageDownloader(httpClient,
                                                      config.getThrottler(),
                                                      config.getEngineId().getLabelResId(),
                                                      config.isLogHttpGetRequests());
            }
        }
        final String tempFilename = ImageFileInfo.getTempFilename(
                getEngineId().getPreferenceKey(), bookId, cIdx, size);

        try {
            final Request imageRequest = createImageRequest(context, url, requestProperties);
            return imageDownloader.fetch(imageRequest, tempFilename)
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
     * @param currentAuthorRole role
     * @param book              Bundle to update
     * @param addAsFirst        set to {@code true} if new ones should
     *                          be added at the top of the list.
     *                          Otherwise, they are appended as normal.
     */
    public void addAuthor(@NonNull final Author currentAuthor,
                          @AuthorRole.Role final int currentAuthorRole,
                          @NonNull final Book book,
                          final boolean addAsFirst) {
        boolean add = true;
        // check if already present
        for (final Author author : book.getAuthors()) {
            if (author.equals(currentAuthor)) {
                // merge roles.
                author.addRole(currentAuthorRole);
                // merge identifiers
                // ENHANCE: we could now have multiple identifiers
                //  for a single Author. As we don't support that...
                //  first id "wins"
                // Explicitly prune here to make unit tests easier.
                final List<Identifier.Value> all = new ArrayList<>(author.getIdentifiers());
                all.addAll(currentAuthor.getIdentifiers());
                identifierDao.pruneList(all);
                author.setIdentifiers(all);

                add = false;
                // keep looping
            }
        }

        if (add) {
            currentAuthor.setRole(currentAuthorRole);
            if (addAsFirst) {
                book.getAuthors().add(0, currentAuthor);
            } else {
                book.add(currentAuthor);
            }
        }
    }

    /**
     * Process the publication-date field according to the given site locale.
     * <p>
     * If the given date-string consists of 4 characters, it is assumed it's
     * a year-value and the simplified form will be set on the book.
     * Otherwise, full parsing is done.
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
    protected void addPublicationDate(@NonNull final Context context,
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
        getFullDateParser(context, locale)
                .parse(dateStr)
                .ifPresent(book::setPublicationDate);
    }

    /**
     * Process the price-listed field according to the given site locale.
     *
     * @param context     Current context
     * @param moneyParser for parsing
     * @param priceStr    the field as retrieved with or without currency embedded
     * @param currencyStr optional default currency string to use
     *                    when the priceStr does not have one
     * @param book        Bundle to update
     */
    public void addPriceListed(@NonNull final Context context,
                               @NonNull final MoneyParser moneyParser,
                               @NonNull final String priceStr,
                               @Nullable final String currencyStr,
                               @NonNull final Book book) {

        // First ignore the given currency string (if any) and try parsing
        final Optional<Money> oMoney = moneyParser.parse(priceStr);
        if (oMoney.isPresent()) {
            Money money = oMoney.get();
            if (money.getCurrency() != null) {
                // We have parsed both the value and the currency from the input string.
                book.setPriceListed(money);
                return;

            } else if (currencyStr != null && !currencyStr.isBlank()) {
                try {
                    // use the given currency string, and the value from the previous parse result
                    final Currency currency = Currency.getInstance(currencyStr);
                    money = new Money(money.getValue(), currency);
                    book.setPriceListed(money);
                    return;
                } catch (@NonNull final IllegalArgumentException ignore) {
                    // ignore
                }
            }
        }

        // Parsing failed, store the input string as-is.
        book.setPriceListed(priceStr, currencyStr);

        // Log this as we need to understand WHY it failed.
        LoggerFactory.getLogger().w(getName(context), "processPriceListed Failed to parse",
                                    "currencyStr=" + currencyStr,
                                    "priceStr=" + priceStr);
    }

    /**
     * Process the list of tag names, remove blank, duplicates and unwanted.
     *
     * @param tagNames to use
     * @param book     Bundle to update
     */
    protected void setTags(@NonNull final Collection<String> tagNames,
                           @NonNull final Book book) {
        //noinspection DataFlowIssue
        final Set<String> tagsToIgnore = getEngineId().getConfig().getTagsToIgnore();
        final List<Tag> tags = tagNames.stream()
                                       .filter(t -> !t.isBlank())
                                       .filter(t -> !tagsToIgnore.contains(t))
                                       .distinct()
                                       .map(Tag::new)
                                       .collect(Collectors.toList());
        book.setTags(tags);
    }
}
