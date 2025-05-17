/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import android.util.Log;

import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings({"MissingJavadoc", "FieldCanBeLocal"})
public class AuthorParseTest
        extends BaseDBTest {

    private static final String TAG = "AuthorParseTest";

    private OpenLibrarySearchEngine searchEngine;
    private OpenLibraryAuthorResolver resolver;
    private AuthorParser authorParser;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (OpenLibrarySearchEngine) EngineId.OpenLibrary.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        resolver = (OpenLibraryAuthorResolver) OpenLibraryAuthorResolver
                .create(context, searchEngine);
        authorParser = new AuthorParser(context, searchEngine);
    }

    @Test
    public void parse_ol20187a()
            throws IOException {

        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.openlibrary_author_ol20187a);

        final Author author = authorParser.parse(context, document);
        assertNotNull(author);
        Log.d(TAG, author.toString());

        assertEquals("Vonnegut", author.getFamilyName());
        assertEquals("Kurt", author.getGivenNames());
        assertEquals("1922-11-11", author.getBirthDate().orElse(null));
        assertEquals("2007-04-11", author.getDeathDate().orElse(null));

        assertEquals(4, author.getIdentifiers().size());
        Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY);
        assertTrue(oIv.isPresent());
        assertEquals("OL20187A", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000121386537", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q49074", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_VIAF);
        assertTrue(oIv.isPresent());
        assertEquals("71398958", oIv.get());

        assertNull(author.getRealAuthor());

    }

    @Test
    public void parse_ps_ol2677446a()
            throws IOException {

        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.openlibrary_author_ps_ol2677446a);

        Author author = authorParser.parse(context, document);
        assertNotNull(author);
        Log.d(TAG, author.toString());

        assertEquals("Tiptree Jr.", author.getFamilyName());
        assertEquals("James", author.getGivenNames());

        assertEquals("1915-08-24", author.getBirthDate().orElse(null));
        assertEquals("1987-05-19", author.getDeathDate().orElse(null));

        assertEquals(4, author.getIdentifiers().size());
        Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY);
        assertTrue(oIv.isPresent());
        assertEquals("OL2677446A", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000081419268", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q234928", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_VIAF);
        assertTrue(oIv.isPresent());
        assertEquals("62099890", oIv.get());

        author = author.getRealAuthor();
        assertNotNull(author);
        assertEquals("Sheldon", author.getFamilyName());
        assertEquals("Alice Bradley", author.getGivenNames());

    }

    @Test
    public void liveSearchAsimov_with_sid()
            throws SearchException, CredentialsException {
        final Author author = new Author("Asimov", "Isaac");
        // force resolving using the sid
        author.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, "OL34221A");

        final boolean resolved = resolver.resolve(context, author);
        assertTrue(resolved);
        Log.d(TAG, author.toString());

        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertEquals("1920-01-02", author.getBirthDate().orElse(null));
        assertEquals("1992-04-06", author.getDeathDate().orElse(null));

        assertEquals(4, author.getIdentifiers().size());
        Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY);
        assertTrue(oIv.isPresent());
        assertEquals("OL34221A", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000122590564", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q34981", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_VIAF);
        assertTrue(oIv.isPresent());
        assertEquals("24597135", oIv.get());

        final String pic = author.getTmpPictureFileSpec().orElse(null);
        assertNotNull(pic);
        assertTrue(pic.endsWith("_openlibrary_OL34221A_0_.jpg"));
    }

    @Test
    public void liveSearchAsimov_without_sid()
            throws SearchException, CredentialsException {
        final Author author = new Author("Asimov", "Isaac");
        // no sid: force resolving using a query

        final boolean resolved = resolver.resolve(context, author);
        assertTrue(resolved);
        Log.d(TAG, author.toString());

        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertEquals("1920-01-02", author.getBirthDate().orElse(null));
        assertEquals("1992-04-06", author.getDeathDate().orElse(null));

        assertEquals(1, author.getIdentifiers().size());
        Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_OPEN_LIBRARY);
        assertTrue(oIv.isPresent());
        assertEquals("OL34221A", oIv.get());

        final String pic = author.getTmpPictureFileSpec().orElse(null);
        assertNotNull(pic);
        assertTrue(pic.endsWith("_openlibrary_OL34221A_0_.jpg"));
    }
}
