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

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SruAuthorParseTest
        extends BaseDBTest {

    private static final String TAG = "SruAuthorParseTest";
    private static final String UTF_8 = "UTF-8";

    private DnbAuthorResolver resolver;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Dnb.getConfig().setLogHttpGetRequests(true);
        final DnbSearchEngine searchEngine = (DnbSearchEngine) EngineId.Dnb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        resolver = new DnbAuthorResolver(searchEngine);
    }

    @Test
    void parse118646109()
            throws IOException, SearchException, CredentialsException {
        final String locationHeader = "https://services.dnb.de/sru/authorities?version=1.1&operation=searchRetrieve&query=nid%3D118646109&recordSchema=MARC21-xml&maximumRecords=1";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_sru_author_118646109;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());

        Author author = resolver.parse(document);
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());

        assertEquals("1920-01-02", author.getBirthDate().orElse(null));
        assertEquals("1992-04-06", author.getDeathDate().orElse(null));

        assertEquals("118646109", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        author = author.getRealAuthor();
        assertNull(author);
    }

    @Test
    void parse118678175()
            throws IOException, SearchException, CredentialsException {
        final String locationHeader = "https://services.dnb.de/sru/authorities?version=1.1&operation=searchRetrieve&query=nid%3D118678175&recordSchema=MARC21-xml";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_sru_author_118678175;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());

        Author author = resolver.parse(document);
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("Dick", author.getFamilyName());
        assertEquals("Philip K.", author.getGivenNames());

        assertEquals("1928-12-16", author.getBirthDate().orElse(null));
        assertEquals("1982-03-02", author.getDeathDate().orElse(null));

        assertEquals("118678175", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        author = author.getRealAuthor();
        assertNull(author);
    }

    @Test
    void parse1300021055()
            throws IOException, SearchException, CredentialsException {
        final String locationHeader = "https://services.dnb.de/sru/authorities?version=1.1&operation=searchRetrieve&query=nid%3D1300021055&recordSchema=MARC21-xml";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_sru_author_1300021055;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());

        Author author = resolver.parse(document);
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("von Humboldt", author.getFamilyName());
        assertEquals("Dorothee", author.getGivenNames());

        assertEquals("1300021055", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        author = author.getRealAuthor();
        assertNull(author);
    }

    @Test
    void parse128409142()
            throws SearchException, CredentialsException, IOException {

        final String locationHeader = "https://services.dnb.de/sru/authorities?version=1.1&operation=searchRetrieve&query=nid%3D128409142&recordSchema=MARC21-xml";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_sru_author_128409142;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());

        final Author author = resolver.parse(document);
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("Flix", author.getFamilyName());
        assertEquals("", author.getGivenNames());

        assertEquals("1976", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        assertEquals(3, author.getIdentifiers().size());
        assertEquals("128409142", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("128409142", author.getIdentifierValue("DE-588").orElse(null));
        assertEquals("Q114237", author.getIdentifierValue(Identifier.SID_WIKIDATA).orElse(null));

        final Author realAuthor = author.getRealAuthor();
        assertNotNull(realAuthor);

        assertEquals("Görmann", realAuthor.getFamilyName());
        assertEquals("Felix", realAuthor.getGivenNames());
        assertEquals("1976", realAuthor.getBirthDate().orElse(null));
        assertNull(realAuthor.getDeathDate().orElse(null));

        assertEquals(2, realAuthor.getIdentifiers().size());
        assertEquals("1216065012", realAuthor.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("1216065012", realAuthor.getIdentifierValue("DE-588").orElse(null));
    }
}
