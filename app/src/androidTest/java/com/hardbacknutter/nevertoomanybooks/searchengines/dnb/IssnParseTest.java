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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import android.util.Log;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.PublicationFrequency;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IssnParseTest
        extends BaseDBTest {

    private static final String TAG = "IssnParseTest";

    private static final String UTF_8 = "UTF-8";

    private DnbSearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Dnb.getConfig().setLogHttpGetRequests(true);
        searchEngine = (DnbSearchEngine) EngineId.Dnb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void m64er()
            throws IOException {
        final String issn = "0176-8824";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.zdb_issn_0176_8824;

        final Document document = loadDocument(resId, UTF_8, "", Parser.xmlParser());
        final Book book = new Book();
        searchEngine.parseFromIssn(context, document, issn, book);

        Log.d(TAG, book.toString());

        assertEquals("64er: das Magazin für Computer-Fans",
                     book.getString(DBKey.TITLE, null));
        assertEquals("ger", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Periodical", book.getString(DBKey.FORMAT, null));
        assertEquals("01768824", book.getString(DBKey.ISBN, null));

        assertEquals("010441638", book.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("ZDB50387-3", book.getIdentifierValue("DE-599").orElse(null));
        assertEquals("85119872", book.getIdentifierValue(Identifier.SID_OCLC).orElse(null));

        final List<Series> seriesList = book.getSeries();
        assertEquals(1, seriesList.size());
        final Series series = seriesList.get(0);
        assertEquals("64er: das Magazin für Computer-Fans", series.getTitle());
        assertEquals("01768824", series.getIdentifierValue(Identifier.SID_ISSN).orElse(null));

        final PublicationFrequency frequency = series.getPublicationFrequency();
        assertNotNull(frequency);
        assertEquals(PublicationFrequency.Type.Monthly, frequency.getType());
        assertEquals(1, frequency.getCadence());
        assertFalse(frequency.isOrdinal());

        final List<Author> authors = book.getAuthors();
        assertEquals(1, authors.size());
        assertEquals("Markt & Technik Verl. AG", authors.get(0).getFamilyName());

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals("Markt & Technik Verl. AG", publishers.get(0).getName());
    }
}
