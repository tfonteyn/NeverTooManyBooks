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
package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpHead;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionIsbn;
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
 */
public class KbNlSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.CoverByEdition {

    public static final String SITE_URL = "https://www.kb.nl";
    public static final String BOOK_URL = "https://webggc.oclc.org/cbs/DB=2.37/XMLPRS=Y/PPN?PPN=%s";
    public static final String AUTHOR_URL = "https://webggc.oclc.org/cbs/DB=2.37/REL?PPN=%s";

    /**
     * <strong>Note:</strong> This is not the same site as the search site itself.
     * We have no indication that this site has an image we want, we just try it.
     * <p>
     * param 1: isbn, param 2: size.
     */
    private static final String BASE_URL_COVERS =
            "https://webservices.bibliotheek.be/index.php?func=cover&ISBN=%1$s&coversize=%2$s";

    /* param 1: site specific author id. */
    //    private static final String AUTHOR_URL = getBaseURL(context)
    //    + "/DB=1/SET=1/TTL=1/REL?PPN=%1$s";

    /**
     * param 1: db version (part of the site session vars).
     * param 2: the set number (part of the site session vars).
     * param 3: the ISBN.
     */
    private static final String SEARCH_URL = "/cbs/DB=%1$s/SET=%2$s/TTL=1/CMD?"
                                             // Action is a search
                                             + "ACT=SRCHA&"
                                             // by ISBN/ISSN
                                             + "IKT=1007&"
                                             // Results sorted by Relevance
                                             + "SRT=RLV&"
                                             // search term
                                             + "TRM=%3$s";

    /**
     * param 1: db version (part of the site session vars).
     * param 2: the set number (part of the site session params).
     * Param 3: the SHW part of the url as found in a multi-result.
     */
    private static final String MULTI_RESULT_BOOK_URL = "/cbs/DB=%1$s/SET=%2$s/TTL=1/%3$s";

    /** Fallback only, we should always extract it from the url. */
    private static final String DEFAULT_DB_VERSION = "2.37";
    /** Fallback only, we should always extract it from the url. */
    private static final String DEFAULT_SET_NUMBER = "1";
    /** See {@link KbNlBookHandler}#PERMALINK_PATTERN. */
    private static final String PERMALINK_URL = "/cbs/DB=%1$s/XMLPRS=Y/PPN?PPN=%2$s";

    @Nullable
    private FutureHttpGet<Boolean> futureHttpGet;

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
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public KbNlSearchEngine(@NonNull final Context appContext,
                            @NonNull final SearchEngineConfig config) {
        super(appContext, config);
        // We MUST bootstrap it here to ensure it's active before the first http request send
        // No further interaction with it is needed.
        ServiceLocator.getInstance().getCookieManager();
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
        return new EngineId.Builder("kbnl",
                                    R.string.site_kb_nl,
                                    List.of(R.string.site_description_dutch_and_more,
                                      R.string.site_description_catalog),
                                    "https://webggc.oclc.org",
                                    new Locale("nl", "NL"))
                .setIdentifierKey(Identifier.SID_KBNL)
                .setMultipleCoverSizes(true);
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (futureHttpGet != null) {
                futureHttpGet.cancel();
            }
        }
    }

    /**
     * Send a HEAD request to prepare a cookie for further calls.
     *
     * @param context Current context
     *
     * @throws SearchException on any error
     */
    private void ensureCookie(@NonNull final Context context)
            throws SearchException {
        final FutureHttpHead<Boolean> futureHttpHead = createFutureHeadRequest(context);
        try {
            futureHttpHead.head(getHostUrl(context) + "/cbs/", con -> true);
        } catch (@NonNull final StorageException | IOException e) {
            throw new SearchException(getEngineId(), e);
        }
    }

    @NonNull
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException,
                   SearchException,
                   CredentialsException {

        final String url = getHostUrl(context)
                           + String.format(SEARCH_URL, dbVersion, setNr, validIsbn);
        final Book book = getBook(context, url);

        if (isCancelled()) {
            return book;
        }

        if (fetchCovers[0]) {
            final AltEdition edition = new AltEditionIsbn(validIsbn);
            searchBestCoverByEdition(context, edition, 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final String url = getHostUrl(context) + String.format(PERMALINK_URL, dbVersion,
                                                               externalId);
        final Book book = getBook(context, url);
        if (isCancelled()) {
            return book;
        }

        if (fetchCovers[0]) {
            final ISBN isbn = new ISBN(book.getString(DBKey.ISBN), true);
            if (isbn.isValid(true)) {
                final AltEdition edition = new AltEditionIsbn(isbn.asText());
                searchBestCoverByEdition(context, edition, 0).ifPresent(
                        fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
            }
        }
        return book;
    }

    @NonNull
    private Book getBook(@NonNull final Context context,
                         @NonNull final String url)
            throws SearchException, StorageException {

        ensureCookie(context);

        final Book book = new Book();

        futureHttpGet = createGetDocumentRequest(context);

        final DefaultHandler handler = new KbNlBookHandler(this, book);

        final SAXParser parser;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser();
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(e);
        }

        try {
            // Do the search... we'll either get a parsed list-page back, or the parsed book page.
            futureHttpGet.get(url, (con, is) -> handleResponse(is, parser, handler, book));

            // If it was a list page, fetch and parse the 1st book found;
            // If it was a book page, we're already done and can skip this step.
            final String show = book.getString(KbNlHandlerBase.BKEY_SHOW_URL, null);
            if (show != null && !show.isEmpty()) {
                book.clearData();
                final String url2 = getHostUrl(context)
                                    + String.format(MULTI_RESULT_BOOK_URL, dbVersion, setNr, show);
                futureHttpGet.get(url2, (con, is) -> handleResponse(is, parser, handler, book));
            }
        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        }

        return book;
    }

    private boolean handleResponse(@NonNull final InputStream is,
                                   @NonNull final SAXParser parser,
                                   @NonNull final DefaultHandler handler,
                                   @NonNull final Book book)
            throws IOException, SAXException {
        parser.parse(is, handler);
        //noinspection DataFlowIssue
        dbVersion = book.getString(KbNlHandlerBase.BKEY_DB_VERSION, DEFAULT_DB_VERSION);
        //noinspection DataFlowIssue
        setNr = book.getString(KbNlHandlerBase.BKEY_SET_NUMBER, DEFAULT_SET_NUMBER);
        return true;
    }

    /**
     * Wrapper for {@link #searchCoverByEdition(Context, AltEdition, int, Size)}.
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
                                                      @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        Optional<String> oFileSpec = searchCoverByEdition(context, edition, cIdx, Size.Large);
        if (oFileSpec.isEmpty()) {
            oFileSpec = searchCoverByEdition(context, edition, cIdx, Size.Medium);
            if (oFileSpec.isEmpty()) {
                oFileSpec = searchCoverByEdition(context, edition, cIdx, Size.Small);
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
                                                 @IntRange(from = 0, to = 1) final int cIdx,
                                                 @Nullable final Size size)
            throws StorageException {

        if (altEdition instanceof AltEditionIsbn) {
            final AltEditionIsbn edition = (AltEditionIsbn) altEdition;
            final String isbn = edition.getIsbn();

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

            final String url = String.format(BASE_URL_COVERS, isbn, sizeParam);
            return saveImage(context, url, null, isbn, cIdx, size);
        }
        return Optional.empty();
    }
}
