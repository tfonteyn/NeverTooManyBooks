/*
 * @Copyright 2019 HardBackNutter
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
import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.searches.CommonSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test parsing the Jsoup Document for ISFDB multi-edition data.
 */
class IsfdbEditionsHandlerTest
        extends CommonSetup {

    @Test
    void parseMultiEdition() {

        String locationHeader = "http://www.isfdb.org/cgi-bin/title.cgi?11169";
        String filename = "/isfdb/11169-multi-edition.html";

        Document doc = null;
        try (InputStream in = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(in);
            doc = Jsoup.parse(in, IsfdbManager.CHARSET_DECODE_PAGE, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        IsfdbEditionsHandler isfdbEditionsHandler = new IsfdbEditionsHandler(mContext, doc);
        // we've set the doc, so no internet download will be done.
        ArrayList<Edition> editions = isfdbEditionsHandler.parseDoc();

        assertEquals(24, editions.size());

        System.out.println(editions);
    }
}
