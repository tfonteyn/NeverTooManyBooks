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

package com.hardbacknutter.nevertoomanybooks.searchengines.dr;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline regression tests based on D&R's ISBN redirect response. */
class ParseTest {
    private DrSearchEngine engine;

    @BeforeEach
    void setup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        engine = EngineId.Dr.createSearchEngine(context);
    }

    private Document fixture() throws IOException {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        try (InputStream stream = context.getResources().openRawResource(
                com.hardbacknutter.nevertoomanybooks.test.R.raw.dr_9786052361795)) {
            return Jsoup.parse(stream, "UTF-8", "https://www.dr.com.tr/search?q=9786052361795");
        }
    }

    @Test
    void parsesExactEdition() throws IOException {
        final Book book = engine.parse(fixture(), ISBN.parse("9786052361795"));
        assertEquals("Milena'ya Mektuplar", book.getString(DBKey.TITLE));
        assertEquals("9786052361795", book.getRawProductCode());
        assertEquals("2018", book.getString(DBKey.PUBLICATION_DATE));
        assertEquals(2, book.getAuthors().size());
        assertEquals("Franz Kafka", book.getAuthors().get(0).getFormattedName(true));
        assertEquals(AuthorRole.WRITER, book.getAuthors().get(0).getRole());
        assertEquals("Itır Ilgaz", book.getAuthors().get(1).getFormattedName(true));
        assertEquals(AuthorRole.TRANSLATOR, book.getAuthors().get(1).getRole());
        assertEquals("İndigo Kitap", book.getPublishers().get(0).getName());
        // Missing fields must not be guessed from the country of the shop.
        assertTrue(book.getString(DBKey.LANGUAGE).isEmpty());
    }

    @Test
    void acceptsEquivalentIsbn10() throws IOException {
        assertEquals("9786052361795",
                     engine.parse(fixture(), ISBN.parse("6052361794")).getRawProductCode());
    }

    @Test
    void rejectsOtherEdition() throws IOException {
        assertTrue(engine.parse(fixture(), ISBN.parse("9789753638029")).isEmpty());
    }

    @Test
    void rejectsMissingBarcode() throws IOException {
        final Document document = fixture();
        document.select(".js-list-prd-property").remove();
        assertTrue(engine.parse(document, ISBN.parse("9786052361795")).isEmpty());
    }

    @Test
    void rejectsSearchPageWithRecommendations() throws IOException {
        final Document document = fixture();
        document.select("h1").remove();
        assertTrue(engine.parse(document, ISBN.parse("9786052361795")).isEmpty());
    }
}
