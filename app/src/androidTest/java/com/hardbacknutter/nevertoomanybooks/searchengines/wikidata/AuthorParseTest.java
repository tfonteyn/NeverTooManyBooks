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

package com.hardbacknutter.nevertoomanybooks.searchengines.wikidata;

import android.util.Log;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorParseTest
        extends BaseDBTest {

    private static final String TAG = "AuthorParseTest";

    private WikidataAuthorResolver resolver;
    private WikidataAuthorParser authorParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Wikidata.getConfig().setLogHttpGetRequests(true);
        final WikidataSearchEngine searchEngine = EngineId.Wikidata.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        resolver = new WikidataAuthorResolver(context, searchEngine);
        authorParser = new WikidataAuthorParser(context, searchEngine);
    }

    @Test
    void parse_q42()
            throws IOException {

        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.wikidata_author_q42);

        final Author author = authorParser.parse(context, "en", document, "Q42");
        assertNotNull(author);
        Log.d(TAG, author.toString());

        assertEquals("Adams", author.getFamilyName());
        assertEquals("Douglas", author.getGivenNames());
        assertEquals("1952-03-11", author.getBirthDate().orElse(null));
        assertEquals("2001-05-11", author.getDeathDate().orElse(null));

        assertNull(author.getImageUuid().orElse(null));
        assertTrue(author.getTmpPictureFileSpec().orElse("").endsWith("_wikidata_Q42_0_.jpg"));

        final List<Identifier.Value> identifiers = author.getIdentifiers();
        assertEquals(19, identifiers.size());
        assertEquals("Q42", author.getIdentifierValue(Identifier.SID_WIKIDATA).orElse(null));

        assertEquals("B000AQ2A84", author.getIdentifierValue(Identifier.SID_ASIN).orElse(null));
        assertEquals("cb11888092r", author.getIdentifierValue(Identifier.SID_BNF).orElse(null));
        assertEquals("75", author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH).orElse(null));
        assertEquals("119033364", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("25", author.getIdentifierValue(Identifier.SID_FANTLAB).orElse(null));
        assertEquals("4", author.getIdentifierValue(Identifier.SID_GOODREADS).orElse(null));
        assertEquals("122", author.getIdentifierValue(Identifier.SID_ISFDB).orElse(null));
        assertEquals("0000000080456315",
                     author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));
        assertEquals("068744307", author.getIdentifierValue(Identifier.SID_KBNL).orElse(null));
        assertEquals("14651562", author.getIdentifierValue(Identifier.SID_KBR).orElse(null));
        assertEquals("n80076765", author.getIdentifierValue(Identifier.SID_LCCN).orElse(null));
        assertEquals("adamsdouglas-1",
                     author.getIdentifierValue(Identifier.SID_LIBRARY_THING).orElse(null));
        assertEquals("10014", author.getIdentifierValue(Identifier.SID_NILF).orElse(null));
        assertEquals("143", author.getIdentifierValue(Identifier.SID_NOOSFERE).orElse(null));
        assertEquals("E39PBJxhkQGbbdgtx94vJRmrv3",
                     author.getIdentifierValue(Identifier.SID_OCLC).orElse(null));
        assertEquals("OL272947A",
                     author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY).orElse(null));
        assertEquals("68537", author.getIdentifierValue(Identifier.SID_PORBASE).orElse(null));
        assertEquals("113230702", author.getIdentifierValue(Identifier.SID_VIAF).orElse(null));
    }

    @Test
    void asimov()
            throws SearchException, CredentialsException {
        final Author author = new Author("Asimov", "Isaac");

        final boolean resolved = resolver.resolve(context, author);
        assertTrue(resolved);
        Log.d(TAG, author.toString());

        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertEquals("1920-01-02", author.getBirthDate().orElse(null));
        assertEquals("1992-04-06", author.getDeathDate().orElse(null));

        assertNull(author.getImageUuid().orElse(null));
        assertTrue(author.getTmpPictureFileSpec().orElse("").endsWith("_wikidata_Q34981_0_.jpg"));

        final List<Identifier.Value> identifiers = author.getIdentifiers();
        assertEquals(21, identifiers.size());
        assertEquals("Q34981", author.getIdentifierValue(Identifier.SID_WIKIDATA).orElse(null));

        assertEquals("46170", author.getIdentifierValue(Identifier.SID_BEDETHEQUE).orElse(null));
        assertEquals("cb118892827", author.getIdentifierValue(Identifier.SID_BNF).orElse(null));
        assertEquals("79", author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH).orElse(null));
        assertEquals("118646109", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("4556848", author.getIdentifierValue(Identifier.SID_DOUBAN).orElse(null));
        assertEquals("6", author.getIdentifierValue(Identifier.SID_FANTLAB).orElse(null));
        assertEquals("16667", author.getIdentifierValue(Identifier.SID_GOODREADS).orElse(null));
        assertEquals("5", author.getIdentifierValue(Identifier.SID_ISFDB).orElse(null));
        assertEquals("0000000122590564",
                     author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));
        assertEquals("068561504", author.getIdentifierValue(Identifier.SID_KBNL).orElse(null));
        assertEquals("14589377", author.getIdentifierValue(Identifier.SID_KBR).orElse(null));
        assertEquals("n80126289", author.getIdentifierValue(Identifier.SID_LCCN).orElse(null));
        assertEquals("asimovisaac",
                     author.getIdentifierValue(Identifier.SID_LIBRARY_THING).orElse(null));
        assertEquals("10133", author.getIdentifierValue(Identifier.SID_NILF).orElse(null));
        assertEquals("16", author.getIdentifierValue(Identifier.SID_NOOSFERE).orElse(null));
        assertEquals("E39PBJbWxdkyxFMBK6D7rM4jG3",
                     author.getIdentifierValue(Identifier.SID_OCLC).orElse(null));
        assertEquals("OL34221A",
                     author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY).orElse(null));
        assertEquals("7924", author.getIdentifierValue(Identifier.SID_PORBASE).orElse(null));
        assertEquals("24597135", author.getIdentifierValue(Identifier.SID_VIAF).orElse(null));
    }
}
