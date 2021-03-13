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
package com.hardbacknutter.nevertoomanybooks.searches.stripinfo;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.ImportCollection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ImportCollectionTest
        extends JSoupBase {

    @Test
    void parseCollectionPage() {
        setLocale(Locale.FRANCE);

        // This user does not exist on the site, it was manually replaced in the sample file
        // username: NTMBUser
        final String userId = "666";

        final String locationHeader = "https://www.stripinfo.be/userCollection/index/666/0/0/1000";
        final String filename = "/stripinfo/collection.html";

        final ImportCollection ic = new ImportCollection(userId);

        final Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, "UTF-8", locationHeader);

            assertNotNull(document);
            assertTrue(document.hasText());

            final List<ImportCollection.ColData> collection = ic.parse(document);

            assertEquals(25, collection.size());

            System.out.println(collection);

        } catch (@NonNull final IOException e) {
            fail(e);
        }
    }

    @Test
    void parseCollectionLastPage() {
        setLocale(Locale.FRANCE);

        // This user does not exist on the site, it was manually replaced in the sample file
        // username: NTMBUser
        final String userId = "666";

        final String locationHeader = "https://www.stripinfo.be/userCollection/index/666/0/3/1000";
        final String filename = "/stripinfo/collection-last-page.html";

        final ImportCollection ic = new ImportCollection(userId);

        final Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, "UTF-8", locationHeader);

            assertNotNull(document);
            assertTrue(document.hasText());

            final List<ImportCollection.ColData> collection = ic.parse(document);

            assertEquals(1, collection.size());

            System.out.println(collection);

        } catch (@NonNull final IOException e) {
            fail(e);
        }
    }
}
