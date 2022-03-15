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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test parsing the Jsoup Document for ISFDB multi-edition data.
 */
class IsfdbEditionsHandlerTest
        extends Base {

    private static final String sBaseUrl = "http://www.isfdb.org";

    private IsfdbSearchEngine mSearchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        mSearchEngine = (IsfdbSearchEngine) Site.Type.Data
                .getSite(SearchSites.ISFDB).getSearchEngine();
        mSearchEngine.setCaller(new MockCancellable());
    }

    @Test
    void parseMultiEdition() {
        setLocale(Locale.UK);
        final String locationHeader = "http://www.isfdb.org/cgi-bin/title.cgi?11169";
        final String filename = "/isfdb/11169-multi-edition.html";

        Document document = null;
        try (InputStream in = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(in);
            document = Jsoup.parse(in, IsfdbSearchEngine.CHARSET_DECODE_PAGE, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(document);
        assertTrue(document.hasText());

        // we've set the doc, so no internet download will be done.
        final List<Edition> editions = mSearchEngine.parseEditions(document);

        assertEquals(24, editions.size());

        System.out.println(editions);
    }

    /**
     * Fairly simple test that does an active download, and checks if the resulting page
     * has the right "location" URL afterwards.
     * There have been some hick-ups from ISFDB with redirecting (Apache web server config issues);
     * and the JSoup parser is not fully redirect proof either.
     * <p>
     * Search for 0-88733-160-2; which has a single edition, so should redirect to the book.
     * Resulting url should have "pl.cgi".
     */
    @Test
    void searchSingleEditionIsbn() {

        final String path = sBaseUrl + "/cgi-bin/se.cgi?arg=0887331602&type=ISBN";
        Document document = null;
        try {
            document = mSearchEngine.loadDocument(mContext, path);
        } catch (@NonNull final SearchException | CredentialsException e) {
            fail(e);
        }
        assertNotNull(document);
        assertTrue(document.hasText());

        assertEquals(sBaseUrl + "/cgi-bin/pl.cgi?326539", document.location());
    }


    /**
     * Search for 978-1-4732-0892-6; which has two editions.
     * Resulting url should have "se.cgi".
     *
     * @see #searchSingleEditionIsbn()
     */
    @Test
    void searchMultiEditionIsbn() {

        final String path = sBaseUrl + "/cgi-bin/se.cgi?arg=9781473208926&type=ISBN";
        Document document = null;
        try {
            document = mSearchEngine.loadDocument(mContext, path);
        } catch (@NonNull final SearchException | CredentialsException e) {
            fail(e);
        }
        assertNotNull(document);
        assertTrue(document.hasText());

        assertEquals(sBaseUrl + "/cgi-bin/se.cgi?arg=9781473208926&type=ISBN",
                     document.location());
    }
}
