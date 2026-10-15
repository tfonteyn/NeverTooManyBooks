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

package com.hardbacknutter.nevertoomanybooks.searchengines.kitapsec;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser checks without modifying the installed application's database or preferences. */
class ParseTest {
    private static final String ISBN_TEXT = "9786255701831";
    private static final String PRODUCT_URL = "https://www.kitapsec.com/Products/"
            + "Sarkac-4-Haraf-Yan-Boyamali-Ephesus-Yayinlari-953233.html";
    private KitapsecSearchEngine engine;

    @BeforeEach
    void setup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        engine = EngineId.Kitapsec.createSearchEngine(context);
    }

    private Document fixture(final boolean search) throws IOException {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final int id = search
                ? com.hardbacknutter.nevertoomanybooks.test.R.raw.kitapsec_search_9786255701831
                : com.hardbacknutter.nevertoomanybooks.test.R.raw.kitapsec_9786255701831;
        try (InputStream stream = context.getResources().openRawResource(id)) {
            return Jsoup.parse(stream, EngineId.Kitapsec.getCharSetName(), PRODUCT_URL);
        }
    }

    @Test
    void findsExactSearchResult() throws IOException {
        assertEquals(PRODUCT_URL, engine.parseSearchResult(fixture(true), ISBN.parse(ISBN_TEXT)));
    }

    @Test
    void rejectsDifferentSearchEdition() throws IOException {
        assertNull(engine.parseSearchResult(fixture(true), ISBN.parse("9786255701848")));
    }

    @Test
    void rejectsOffsiteProductLink() throws IOException {
        final Document document = fixture(true);
        document.select("meta[itemprop=url]")
                .attr("content", "https://example.com/Products/x.html");
        assertNull(engine.parseSearchResult(document, ISBN.parse(ISBN_TEXT)));
    }

    @Test
    void ignoresNonMatchingFirstResult() throws IOException {
        final Document document = fixture(true);
        final Element other = document.selectFirst(".Ks_UrunSatir").clone();
        other.select("meta[itemprop=sku]").attr("content", "9786255701848");
        document.selectFirst(".urunListeleDiv").prependChild(other);
        assertEquals(PRODUCT_URL, engine.parseSearchResult(document, ISBN.parse(ISBN_TEXT)));
    }

    @Test
    void parsesTurkishTextAndEdition() throws IOException {
        final Book book = engine.parse(fixture(false), ISBN.parse(ISBN_TEXT));
        assertEquals("Sarkaç 4: Haraf - Yan Boyamalı", book.getString(DBKey.TITLE));
        assertEquals(ISBN_TEXT, book.getRawProductCode());
        assertEquals("624", book.getPages());
        assertEquals("Ephesus Yayınları", book.getPublishers().get(0).getName());
        assertEquals(1, book.getAuthors().size());
        assertEquals("Maral Atmaca", book.getAuthors().get(0).getFormattedName(true));
        assertEquals(AuthorRole.WRITER, book.getAuthors().get(0).getRole());
        assertTrue(book.getString(DBKey.DESCRIPTION).contains("Örnek açıklama."));
        assertFalse(book.getString(DBKey.DESCRIPTION).contains("Başlık"));
        assertTrue(book.getString(DBKey.LANGUAGE).isEmpty());
    }

    @Test
    void rejectsWrongProductEdition() throws IOException {
        assertTrue(engine.parse(fixture(false), ISBN.parse("9786255701848")).isEmpty());
    }

    @Test
    void rejectsMissingBarcode() throws IOException {
        final Document document = fixture(false);
        document.select(".detayBilgiDivIc").remove();
        assertTrue(engine.parse(document, ISBN.parse(ISBN_TEXT)).isEmpty());
    }

    @Test
    void rejectsSearchPageAsProduct() throws IOException {
        assertTrue(engine.parse(fixture(true), ISBN.parse(ISBN_TEXT)).isEmpty());
    }
}
