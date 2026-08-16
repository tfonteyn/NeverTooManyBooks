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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.util.Log;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test parsing the Jsoup Document for ISFDB multi-edition data.
 * <p>
 * Unless noted, these tests will make a live call to the ISFDB website.
 */
class IsfdbEditionsHandlerTest
        extends BaseDBTest {

    private static final String TAG = "IsfdbEditionsHandlerTes";

    private IsfdbSearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Isfdb.getConfig().setLogHttpGetRequests(true);
        searchEngine = EngineId.Isfdb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void parseMultiEdition()
            throws IOException {

        final String locationHeader = "https://www.isfdb.org/cgi-bin/title.cgi?11169";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_11169_multi_edition;

        final Document document = loadDocument(resId, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                                               locationHeader);
        assertNotNull(document);
        assertTrue(document.hasText());

        // we've set the doc, so no internet download will be done.
        final List<AltEditionIsfdb> editions =
                searchEngine.parseEditions(context, document, null);

        assertEquals(27, editions.size());
        assertEquals("eng", editions.get(0).getLangIso3());

        Log.d(TAG, editions.toString());
    }

    @Test
    void parseMultiEdition2()
            throws IOException {

        final String locationHeader = "https://www.isfdb.org/cgi-bin/title.cgi?1360173";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_1360173_multi_edition;

        final Document document = loadDocument(resId, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                                               locationHeader);
        assertNotNull(document);
        assertTrue(document.hasText());

        // we've set the doc, so no internet download will be done.
        final List<AltEditionIsfdb> editions =
                searchEngine.parseEditions(context, document, null);

        assertEquals(4, editions.size());
        assertEquals("nld", editions.get(0).getLangIso3());

        Log.d(TAG, editions.toString());
    }

    /**
     * Fairly simple test that does an active download, and checks if the resulting page
     * has the right "location" URL afterwards.
     * There have been some hick-ups from ISFDB with redirecting (Apache web server config issues);
     * and the JSoup parser is not fully redirect proof either.
     * <p>
     * Search for 9020612476; which has a single edition, so should redirect to the book.
     * Resulting url should have "pl.cgi".
     */
    @Test
    void searchSingleEditionIsbn()
            throws SearchException, CredentialsException {

        final String path = "https://www.isfdb.org/cgi-bin/se.cgi?arg=9020612476&type=ISBN";
        final Document document = searchEngine.loadHtml(context, path, null);
        assertNotNull(document);
        assertTrue(document.hasText());

        assertEquals("https://www.isfdb.org/cgi-bin/pl.cgi?406329", document.location());
    }


    /**
     * Search for 978-1-4732-0892-6; which has two editions.
     * Resulting url should have "se.cgi".
     *
     * @see #searchSingleEditionIsbn()
     */
    @Test
    void searchMultiEditionIsbn()
            throws SearchException, CredentialsException {

        final String path = "https://www.isfdb.org/cgi-bin/se.cgi?arg=9781473208926&type=ISBN";
        final Document document = searchEngine.loadHtml(context, path, null);

        assertNotNull(document);
        assertTrue(document.hasText());

        assertEquals("https://www.isfdb.org/cgi-bin/se.cgi?arg=9781473208926&type=ISBN",
                     document.location());
    }
}
