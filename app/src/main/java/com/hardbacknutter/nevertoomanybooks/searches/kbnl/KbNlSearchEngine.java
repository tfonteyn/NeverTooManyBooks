/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.kbnl;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;

/**
 * <a href="https://www.kb.nl/">Koninklijke Bibliotheek (KB), Nederland.</a>
 * <a href="https://www.kb.nl/">Royal Library, The Netherlands.</a>
 * <p>
 * 2020-01-04: "http://opc4.kb.nl" is not available on https.
 * see "src/main/res/xml/network_security_config.xml"
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

    /* Response with English labels. */
    //private static final String BOOK_URL =
    //      "/DB=1/SET=1/TTL=1/LNG=EN/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=%1$s";
    /* param 1: site specific author id. */
//    private static final String AUTHOR_URL = getBaseURL(context)
//    + "/DB=1/SET=1/TTL=1/REL?PPN=%1$s";
    /**
     * Response with Dutch labels.
     * <p>
     * param 1: isb
     */
    private static final String BOOK_URL =
            "/DB=1/SET=1/TTL=1/LNG=NE/CMD?ACT=SRCHA&IKT=1007&SRT=YOP&TRM=%1$s";

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext Application context
     */
    @SuppressWarnings("WeakerAccess")
    public KbNlSearchEngine(@NonNull final Context appContext,
                            final int engineId) {
        super(appContext, engineId);
    }

    public static SearchEngineRegistry.Config createConfig() {
        return new SearchEngineRegistry.Config.Builder(KbNlSearchEngine.class,
                                                       SearchSites.KB_NL,
                                                       R.string.site_kb_nl,
                                                       PREF_KEY,
                                                       "http://opc4.kb.nl")
                .setCountry("NL", "nl")
                .setFilenameSuffix("KB")
                .setSupportsMultipleCoverSizes(true)
                .build();
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BOOK_URL, validIsbn);

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final KbNlBookHandler handler = new KbNlBookHandler(bookData);

        // Don't follow redirects, so we get the XML instead of the rendered page
        try (TerminatorConnection con = createConnection(url, false)) {
            final SAXParser parser = factory.newSAXParser();
            parser.parse(con.getInputStream(), handler);
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "searchByIsbn", e);
            }
            throw new IOException(e);
        }

        checkForSeriesNameInTitle(bookData);

        if (isCancelled()) {
            return bookData;
        }

        if (fetchThumbnail[0]) {
            final ArrayList<String> imageList = searchBestCoverImageByIsbn(validIsbn, 0);
            if (!imageList.isEmpty()) {
                bookData.putStringArrayList(SearchCoordinator.BKEY_TMP_FILE_SPEC_ARRAY[0],
                                            imageList);
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
    public String searchCoverImageByIsbn(@NonNull final String validIsbn,
                                         @IntRange(from = 0, to = 1) final int cIdx,
                                         @Nullable final ImageFileInfo.Size size) {
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
