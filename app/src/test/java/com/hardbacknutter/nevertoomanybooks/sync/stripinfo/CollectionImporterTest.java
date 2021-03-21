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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CollectionImporterTest
        extends JSoupBase {

    private final ProgressListener mLogger =
            new TestProgressListener("CollectionImporterTest");

    @Test
    void parseCollectionPage() {
        setLocale(Locale.FRANCE);

        // This user does not exist on the site, it was manually replaced in the sample file
        // username: NTMBUser
        final String userId = "666";

        final String locationHeader = "https://www.stripinfo.be/userCollection/index/666/0/0/1000";
        final String filename = "/stripinfo/collection.html";

        final Bookshelf wishList = new Bookshelf("wishListBS", UUID.randomUUID().toString());
        final CollectionImporter ic = new CollectionImporter(userId, wishList);

        final Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, "UTF-8", locationHeader);

            assertNotNull(document);
            Assertions.assertTrue(document.hasText());

            // should call this... but we can't as it uses "new Bundle()"
            //final List<Bundle> collection = ic.parse(mContext, document, mLogger);
            final List<Bundle> collection = parseHere(ic, document);

            assertEquals(25, collection.size());

            assertEquals(5, collection
                    .stream()
                    .map(b -> b.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST))
                    .filter(Objects::nonNull)
                    .count());

            final Bundle b0 = collection.get(0);
            assertEquals(5435, b0.getLong(DBKeys.KEY_ESID_STRIP_INFO_BE));
            assertEquals(45f, b0.getDouble(DBKeys.KEY_PRICE_PAID));
            assertEquals("EUR", b0.getString(DBKeys.KEY_PRICE_PAID_CURRENCY));
            assertEquals("2021-03-10", b0.getString(DBKeys.KEY_DATE_ACQUIRED));

            assertEquals(1, b0.getInt(StripInfoSearchEngine.SiteField.AMOUNT));
            assertTrue(b0.getBoolean(StripInfoSearchEngine.SiteField.OWNED));
            assertTrue(b0.getBoolean(StripInfoSearchEngine.SiteField.IN_COLLECTION));

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

        final CollectionImporter ic = new CollectionImporter(userId, null);

        final Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, "UTF-8", locationHeader);

            assertNotNull(document);
            assertTrue(document.hasText());

            // should call this... but we can't as it uses "new Bundle()"
            //final List<Bundle> collection = ic.parse(mContext, document, mLogger);
            final List<Bundle> collection = parseHere(ic, document);

            assertEquals(1, collection.size());

        } catch (@NonNull final IOException e) {
            fail(e);
        }
    }

    @NonNull
    private List<Bundle> parseHere(final CollectionImporter ic,
                                   final Document document) {
        final List<Bundle> collection = new ArrayList<>();
        final Element root = document.getElementById("collectionContent");
        assertNotNull(root);

        final Element last = root.select("div.pagination > a").last();
        assertNotNull(last);
        assertEquals(3, Integer.parseInt(last.text()));


        final Elements rows = root.select("div.collectionRow");
        assertFalse(rows.isEmpty());

        for (final Element row : rows) {
            final String idAttr = row.id();
            if (!idAttr.isEmpty() && idAttr.startsWith(CollectionImporter.ROW_ID_ATTR)) {
                // ok, we should have a book.
                final Bundle cData = BundleMock.create();

                ic.parseRow(row, idAttr, cData);

                System.out.println(cData);

                if (!cData.isEmpty()) {
                    collection.add(cData);
                }
            }
        }
        return collection;
    }
}
