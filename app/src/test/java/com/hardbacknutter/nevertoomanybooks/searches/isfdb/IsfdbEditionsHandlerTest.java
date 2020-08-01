/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.CommonMocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test parsing the Jsoup Document for ISFDB multi-edition data.
 */
class IsfdbEditionsHandlerTest
        extends CommonMocks {

    private static final String sBaseUrl = "http://www.isfdb.org";

    private IsfdbSearchEngine mSearchEngine;

    @BeforeEach
    public void setUp() {
        super.setUp();
        mSearchEngine = new IsfdbSearchEngine(mContext);
        mSearchEngine.setCaller(new TextCaller());
    }

    @Test
    void parseMultiEdition() {
        setLocale(Locale.UK);
        String locationHeader = "http://www.isfdb.org/cgi-bin/title.cgi?11169";
        String filename = "/isfdb/11169-multi-edition.html";

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
            document = mSearchEngine.loadDocument(path);
        } catch (@NonNull final IOException e) {
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
            document = mSearchEngine.loadDocument(path);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(document);
        assertTrue(document.hasText());

        assertEquals(sBaseUrl + "/cgi-bin/se.cgi?arg=9781473208926&type=ISBN",
                     document.location());
    }
}
