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
package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.parsers.UncheckedSAXException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>
 */
public class KbNlSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.CoverByIsbn {

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
     * param 1: db version (part of the site session vars)
     * param 2: the set number (part of the site session vars)
     * param 3: the ISBN
     */
    private static final String SEARCH_URL =
            "/cbs" +
            "/DB=%1$s" +
            "/SET=%2$s" +
            "/TTL=1" +
            "/CMD?"
            // Action is a search
            + "ACT=SRCHA&"
            // by ISBN/ISSN
            + "IKT=1007&"
            // Results sorted by Relevance
            + "SRT=RLV&"
            // search term
            + "TRM=%3$s";

    /**
     * param 1: db version (part of the site session vars)
     * param 2: the set number (part of the site session params)
     * Param 3: the SHW part of the url as found in a multi-result
     */
    private static final String BOOK_URL = "/cbs/DB=%1$s/SET=%2$s/TTL=1/%3$s";

    /** Fallback only, we should always extract it from the url. */
    private static final String DEFAULT_DB_VERSION = "2.37";
    /** Fallback only, we should always extract it from the url. */
    private static final String DEFAULT_SET_NUMBER = "1";

    @Nullable
    private FutureHttpGet<Boolean> futureHttpGet;

    @NonNull
    private String dbVersion = DEFAULT_DB_VERSION;
    @NonNull
    private String setNr = DEFAULT_SET_NUMBER;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public KbNlSearchEngine(@NonNull final Context appContext,
                            @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        ServiceLocator.getInstance().getCookieManager();
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

    @NonNull
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException,
                   SearchException,
                   CredentialsException {

        futureHttpGet = createFutureHeadRequest();
        try {
            futureHttpGet.get(getHostUrl() + "/cbs/", request -> true);
        } catch (@NonNull final IOException e) {
            throw new SearchException(getName(context), e);
        }

        final Book book = new Book();

        futureHttpGet = createFutureGetRequest();

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final DefaultHandler handler = new KbNlBookHandler(context, book);

        try {
            final SAXParser parser = factory.newSAXParser();

            // Do the search... we'll either get a parsed list-page back, or the parsed book page.
            String url = getHostUrl() + String.format(SEARCH_URL, dbVersion, setNr,
                                                      validIsbn);
            futureHttpGet.get(url, request -> processRequest(request, handler, parser, book));

            // If it was a list page, fetch and parse the 1st book found;
            // If it was a book page, we're already done and can skip this step.
            final String show = book.getString(KbNlHandlerBase.BKEY_SHOW_URL, null);
            if (show != null && !show.isEmpty()) {
                book.clearData();
                url = getHostUrl() + String.format(BOOK_URL, dbVersion, setNr, show);
                futureHttpGet.get(url, request -> processRequest(request, handler, parser, book));
            }
        } catch (@NonNull final SAXException e) {
            // unwrap SAXException using getException() !
            final Exception cause = e.getException();
            if (cause instanceof StorageException) {
                throw (StorageException) cause;
            }
            // wrap other parser exceptions
            throw new SearchException(getName(context), e);

        } catch (@NonNull final ParserConfigurationException | IOException e) {
            throw new SearchException(getName(context), e);
        }

        if (isCancelled()) {
            return book;
        }

        if (fetchCovers[0]) {
            final ArrayList<String> list = searchBestCoverByIsbn(context, validIsbn, 0);
            if (!list.isEmpty()) {
                book.putStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0], list);
            }
        }
        return book;
    }

    private boolean processRequest(@NonNull final HttpURLConnection request,
                                   @NonNull final DefaultHandler handler,
                                   @NonNull final SAXParser parser,
                                   @NonNull final Book book) {

        try (BufferedInputStream bis = new BufferedInputStream(request.getInputStream())) {
            parser.parse(bis, handler);
            //noinspection ConstantConditions
            dbVersion = book.getString(KbNlHandlerBase.BKEY_DB_VERSION, DEFAULT_DB_VERSION);
            //noinspection ConstantConditions
            setNr = book.getString(KbNlHandlerBase.BKEY_SET_NUMBER, DEFAULT_SET_NUMBER);
            return true;

        } catch (@NonNull final IOException e) {
            throw new UncheckedIOException(e);
        } catch (@NonNull final SAXException e) {
            throw new UncheckedSAXException(e);
        }
    }

    /**
     * Ths kb.nl site does not have images, but we try bibliotheek.be.
     * <p>
     * https://webservices.bibliotheek.be/index.php?func=cover&ISBN=9789463731454&coversize=large
     *
     * <br><br>{@inheritDoc}
     */
    @Nullable
    @Override
    public String searchCoverByIsbn(@NonNull final Context context,
                                    @NonNull final String validIsbn,
                                    @IntRange(from = 0, to = 1) final int cIdx,
                                    @Nullable final Size size)
            throws StorageException {
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

        final String url = String.format(BASE_URL_COVERS, validIsbn, sizeParam);
        return saveImage(url, validIsbn, cIdx, size);
    }
}
