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

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.debug.XmlDumpParser;
import com.hardbacknutter.nevertoomanybooks.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedSAXException;

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

    /** Preferences prefix. */
    private static final String PREF_KEY = "kbnl";

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
     * /LNG=NE
     * /LNG=EN
     * <p>
     * param 1: isb
     */
//    private static final String BOOK_URL =
//            "/DB=1/SET=1/TTL=1/LNG=NE/CMD?"
//            + "ACT=SRCHA&"
//            + "IKT=1007&"
//            + "SRT=YOP&"
//            + "TRM=%1$s";

    // 2021-04 new url:
    // https://opc-kb.oclc.org/DB=1/SET=4/TTL=1/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=9020612476
    private static final String BOOK_URL =
            "/DB=1/SET=4/TTL=1/CMD?"
            + "ACT=SRCHA&"
            + "IKT=1007&"
            + "SRT=YOP&"
            + "TRM=%1$s";

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public KbNlSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);
    }

    public static SearchEngineConfig createConfig() {
        return new SearchEngineConfig.Builder(KbNlSearchEngine.class,
                                              SearchSites.KB_NL,
                                              R.string.site_kb_nl,
                                              PREF_KEY,
                                              "https://opc-kb.oclc.org/")
                .setCountry("NL", "nl")
                .setFilenameSuffix("KB")
                .setSupportsMultipleCoverSizes(true)
                .build();
    }

    @Nullable
    private FutureHttpGet<Boolean> mFutureHttpGet;

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (mFutureHttpGet != null) {
                mFutureHttpGet.cancel();
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

        mFutureHttpGet = createFutureGetRequest();

        final String url = getSiteUrl() + String.format(BOOK_URL, validIsbn);

        final SAXParserFactory factory = SAXParserFactory.newInstance();
//        final DefaultHandler handler = new KbNlBookHandler(bookData);
        final DefaultHandler handler = new XmlDumpParser();

        try {
            final SAXParser parser = factory.newSAXParser();

            // Don't follow redirects, so we get the XML instead of the rendered page
            mFutureHttpGet.setInstanceFollowRedirects(false); //9020612476

            mFutureHttpGet.get(url, request -> {
                try (BufferedInputStream bis = new BufferedInputStream(request.getInputStream())) {
                    parser.parse(bis, handler);
                    checkForSeriesNameInTitle(bookData);
                    return true;

                } catch (@NonNull final IOException e) {
                    throw new UncheckedIOException(e);
                } catch (@NonNull final SAXException e) {
                    throw new UncheckedSAXException(e);
                }
            });

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
                                    @Nullable final ImageFileInfo.Size size)
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
