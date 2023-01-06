/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedSAXException;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>
 * <p>
 * ENHANCE: implement the new KB url/page
 * make sure to add KbNlBookHandler#cancel()
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
     * Response with Dutch or English labels.
     * /LNG=NE (default)
     * /LNG=EN
     * <p>
     * 2023-01-05
     */
    private static final String SEARCH_URL =
            "/cbs" +
            "/DB=2.37" +
            // the set number normally goes up for each search; but we only go
            // through a single search/session, so we can just use '1'
            "/SET=1" +
            "/TTL=1" +
            "/CMD?"
            + "ACT=SRCHA&"
            + "IKT=1007&"
            // Year of publication
            // "SRT=LST_Ya&"
            // Relevance
            + "SRT=RLV&"
            // param 1: isbn
            + "TRM=%1$s";

    private static final String BOOK_URL = "/cbs/DB=2.37/SET=1/TTL=1/";

    @Nullable
    private FutureHttpGet<Boolean> futureHttpGet;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public KbNlSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);
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
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchCovers)
            throws StorageException,
                   SearchException,
                   CredentialsException {

        ServiceLocator.getInstance().getCookieManager();

        final Bundle bookData = ServiceLocator.newBundle();

        futureHttpGet = createFutureGetRequest();

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final DefaultHandler handler = new KbNlBookHandler(bookData);

        try {
            final SAXParser parser = factory.newSAXParser();

            // Don't follow redirects, so we get the XML instead of the rendered page
            futureHttpGet.setInstanceFollowRedirects(false);

            // do the actual search.
            String url = getHostUrl() + String.format(SEARCH_URL, validIsbn);
            futureHttpGet.get(url, request -> {
                // We'll either get a parsed list-page back,
                // or the parsed book page.
                try (BufferedInputStream bis = new BufferedInputStream(
                        request.getInputStream())) {
                    parser.parse(bis, handler);
                    return true;

                } catch (@NonNull final IOException e) {
                    throw new UncheckedIOException(e);
                } catch (@NonNull final SAXException e) {
                    throw new UncheckedSAXException(e);
                }
            });

            // If it was a list page, fetch and parse the 1st book found;
            // If it was a book page, we're already done and can skip this step.
            final String show = bookData.getString(KbNlHandlerBase.SHOW_URL);
            if (show != null && !show.isEmpty()) {
                url = getHostUrl() + BOOK_URL + show;
                bookData.clear();

                futureHttpGet.get(url, request -> {
                    try (BufferedInputStream bis = new BufferedInputStream(
                            request.getInputStream())) {
                        parser.parse(bis, handler);
                        return true;

                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (@NonNull final SAXException e) {
                        throw new UncheckedSAXException(e);
                    }
                });
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
            return bookData;
        }

        if (fetchCovers[0]) {
            final ArrayList<String> list = searchBestCoverByIsbn(context, validIsbn, 0);
            if (!list.isEmpty()) {
                bookData.putStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0], list);
            }
        }
        return bookData;
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
