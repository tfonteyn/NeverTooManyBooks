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
package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>
 * <p>
 * Dutch language books & comics.
 * <p>
 * Dev. note: When accessing the site with a browser, the server actually returns Pica XML,
 * which the browser then transforms to html using XSLT.
 * We simply run the search, and get/parse the XML.
 * Experiments done trying to convince the server to give us a more parser friendly
 * XML (or marc21 or json) have failed so far. It's likely hardcoded to only return the
 * Pica display xml.
 */
public class KbNlSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByIssn,
                   SearchEngine.ByExternalId,
                   SearchEngine.CoverByEdition {

    /** {@link SearchEngineConfig#getHostUrl()}. */
    static final String HOST_URL = "https://webggc.oclc.org";
    /** {@link EngineId#getDefaultLocale()}. */
    private static final Locale HOST_LOCALE = new Locale("nl", "NL");
    /** {@link EngineId#getPreferenceKey()}. */
    private static final String HOST_PREF_KEY = "kbnl";

    /**
     * <strong>Note:</strong> This is not the same site as the search site itself.
     * We have no indication that this site has an image we want, we just try it.
     * <p>
     * param 1: isbn, param 2: size.
     */
    private static final String BASE_URL_COVERS =
            "https://webservices.bibliotheek.be/index.php?func=cover&ISBN=%1$s&coversize=%2$s";

    /** Fallback only, we should always extract it from the url. */
    private static final String DEFAULT_DB_VERSION = "2.37";
    /** Fallback only, we should always extract it from the url. */
    private static final String DEFAULT_SET_NUMBER = "1";

    /**
     * Search by product-code.
     * <p>
     * param 1: db version (part of the site session vars)
     * param 2: the set number (part of the site session vars)
     * param 3: the ISBN or ISSN
     */
    private static final String SEARCH_URL =
            HOST_URL + "/cbs/DB=%1$s/SET=%2$s/TTL=1/CMD?"
            // Action is a search
            + "ACT=SRCHA&"
            // by ISBN/ISSN
            + "IKT=1007&"
            // Results sorted by Relevance
            + "SRT=RLV&"
            // search term
            + "TRM=%3$s";

    /**
     * Fetch a book.
     * <p>
     * param 1: db version (part of the site session vars)
     * param 2: the set number (part of the site session params)
     * Param 3: the SHW part of the url as found in a multi-result
     */
    private static final String MULTI_RESULT_BOOK_URL =
            HOST_URL + "/cbs/DB=%1$s/SET=%2$s/TTL=1/%3$s";

    /** See {@link KbNlBookHandler}#PERMALINK_PATTERN. */
    private static final String PERMALINK_URL =
            HOST_URL + "/cbs/DB=%1$s/XMLPRS=Y/PPN?PPN=%2$s";

    @Nullable
    private FutureHttp<Boolean> httpCall;

    @NonNull
    private String dbVersion = DEFAULT_DB_VERSION;
    @NonNull
    private String setNr = DEFAULT_SET_NUMBER;

    /**
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param context Current context. NOT stored.
     * @param config  the search engine configuration
     *
     * @see EngineId#createSearchEngine(Context)
     */
    @Keep
    public KbNlSearchEngine(@NonNull final Context context,
                            @NonNull final SearchEngineConfig config) {
        super(context, config);
    }

    /**
     * Called during startup to initialise the immutable/default engine configuration.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @return {@link EngineId.Builder}
     */
    @Keep
    @NonNull
    public static EngineId.Builder init() {
        return new EngineId.Builder(HOST_PREF_KEY,
                                    R.string.site_kb_nl,
                                    List.of(R.string.site_description_dutch_and_more,
                                            R.string.site_description_catalog),
                                    HOST_URL,
                                    HOST_LOCALE)
                .setPreferenceFragmentClazz(KbNlPreferencesFragment.class)
                .setIdentifierKey(Identifier.EntityType.Book, Identifier.SID_KBNL)
                .setMultipleCoverSizes(true);
    }

    /**
     * Called at <strong>installation/upgrade</strong> time to create the initial set
     * in the database.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param context Current context
     *
     * @return list
     */
    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_kb_nl);
        final String site = "https://www.kb.nl";
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Number,
                               Identifier.SID_KBNL,
                               name, site,
                               "https://webggc.oclc.org/cbs/DB=2.37/XMLPRS=Y/PPN?PPN=%s",
                               null),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Number,
                               Identifier.SID_KBNL,
                               name, site,
                               "https://webggc.oclc.org/cbs/DB=2.37/REL?PPN=%s",
                               "P1006")
        );
    }

    /**
     * Send a HEAD request to prepare a cookie for further calls.
     *
     * @throws SearchException on any error
     */
    private void ensureCookie()
            throws SearchException {
        try {
            httpFutureFactory.head(HOST_URL + "/cbs/");
        } catch (@NonNull final StorageException | IOException e) {
            throw new SearchException(getEngineId(), e);
        }
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final String externalId = criteria.requireSid(getEngineId());
        final String url = String.format(PERMALINK_URL, dbVersion, externalId);
        final Book book = getBook(url);
        if (isCancelled()) {
            return book;
        }

        fetchCovers(context, criteria, book);
        return book;
    }

    @Override
    @NonNull
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());
        final String url = String.format(SEARCH_URL, dbVersion, setNr, codeStr);
        final Book book = getBook(url);
        if (isCancelled()) {
            return book;
        }

        fetchCovers(context, criteria, book);
        return book;
    }

    @NonNull
    @Override
    public Book searchByIssn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {
        // Searching on an ISSN is identical to isbn
        return searchByIsbn(context, criteria);
    }

    @NonNull
    private Book getBook(@NonNull final String url)
            throws SearchException, StorageException {

        ensureCookie();

        final Book book = new Book();

        final DefaultHandler handler = new KbNlBookHandler(bookParserHelper, book);

        final SAXParser parser;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser();
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(e);
        }

        httpCall = httpFutureFactory.createGetDocumentRequest();
        try {
            // Do the search... we'll either get a parsed list-page back, or the parsed book page.
            httpCall.get(url, (con, is) -> handleResponse(is, parser, handler, book));

            // If it was a list page, fetch and parse the 1st book found;
            // If it was a book page, we're already done and can skip this step.
            final String show = book.getString(KbNlHandlerBase.BKEY_SHOW_URL, null);
            if (show != null && !show.isEmpty()) {
                book.clearData();
                final String url2 = String.format(MULTI_RESULT_BOOK_URL, dbVersion, setNr, show);
                httpCall.get(url2, (con, is) -> handleResponse(is, parser, handler, book));
            }
        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            httpCall = null;
        }

        return book;
    }

    private boolean handleResponse(@NonNull final InputStream is,
                                   @NonNull final SAXParser parser,
                                   @NonNull final DefaultHandler handler,
                                   @NonNull final Book book)
            throws IOException, SAXException {

        // Do the actual parsing which will populate the book.
        parser.parse(is, handler);

        // Extract the site tracking values.
        //noinspection DataFlowIssue
        dbVersion = book.getString(KbNlHandlerBase.BKEY_DB_VERSION, DEFAULT_DB_VERSION);
        //noinspection DataFlowIssue
        setNr = book.getString(KbNlHandlerBase.BKEY_SET_NUMBER, DEFAULT_SET_NUMBER);
        return true;
    }

    private void fetchCovers(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria,
                             @NonNull final Book book)
            throws StorageException {
        if (criteria.getFetchCovers()[0]) {
            final ProductCode productCode = book.getProductCode();
            // The KBR coversite we use only supports ISBN.
            if (productCode != null && productCode.isIsbn()) {
                final AltEdition edition = new AltEditionProductCode(productCode);
                searchBestCoverByEdition(context, edition, 0).ifPresent(
                        fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
            }
        }
    }

    /**
     * Wrapper for {@link #searchCoverByEdition(Context, AltEdition, int, ImageWebSize)}.
     * <p>
     * Try to get an image in order of large, medium, small.
     * i.e. the 'best' image being the largest we can find.
     *
     * @param context Current context
     * @param edition to search for
     * @param cIdx    0..n image index
     *
     * @return fileSpec
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    private Optional<String> searchBestCoverByEdition(@NonNull final Context context,
                                                      @NonNull final AltEdition edition,
                                                      @IntRange(from = 0, to = 0) final int cIdx)
            throws StorageException {

        Optional<String> oFileSpec = searchCoverByEdition(context, edition, cIdx,
                                                          ImageWebSize.Large);
        if (oFileSpec.isEmpty()) {
            oFileSpec = searchCoverByEdition(context, edition, cIdx, ImageWebSize.Medium);
            if (oFileSpec.isEmpty()) {
                oFileSpec = searchCoverByEdition(context, edition, cIdx, ImageWebSize.Small);
            }
        }
        return oFileSpec;
    }

    /**
     * The kb.nl site does not have images, but we try bibliotheek.be.
     * <p>
     * https://webservices.bibliotheek.be/index.php?func=cover&ISBN=9789463731454&coversize=large
     *
     * <br><br>{@inheritDoc}
     *
     * @see #searchBestCoverByEdition(Context, AltEdition, int)
     */
    @NonNull
    @Override
    public Optional<String> searchCoverByEdition(@NonNull final Context context,
                                                 @NonNull final AltEdition altEdition,
                                                 @IntRange(from = 0, to = 0) final int cIdx,
                                                 @Nullable final ImageWebSize size)
            throws StorageException {

        if (altEdition instanceof AltEditionProductCode) {
            final String sizeParam;
            if (size == null) {
                sizeParam = "large";
            } else {
                switch (size) {
                    case Small:
                        sizeParam = "small";
                        break;
                    case Medium:
                        sizeParam = "medium";
                        break;
                    case Large:
                    default:
                        sizeParam = "large";
                        break;
                }
            }

            final AltEditionProductCode edition = (AltEditionProductCode) altEdition;
            final ProductCode productCode = edition.getCode();
            final String codeStr = productCode.getFormatted(getEngineId());
            final String url = String.format(BASE_URL_COVERS, codeStr, sizeParam);
            return getHttpCallFactory().saveImage(url, null, codeStr, cIdx, size);
        }
        return Optional.empty();
    }

    @Override
    @AnyThread
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (httpCall != null) {
                httpCall.cancel();
            }
        }
    }
}
