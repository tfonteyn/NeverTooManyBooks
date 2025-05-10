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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import android.util.Log;

import androidx.annotation.Nullable;

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

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AuthorParseTest
        extends BaseDBTest {

    private static final String TAG = "AuthorParseTest";
    private static final String UTF_8 = "UTF-8";

    private DnbSearchEngine searchEngine;
    private DnbAuthorResolver resolver;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (DnbSearchEngine) EngineId.Dnb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(context, true);

        resolver = (DnbAuthorResolver) DnbAuthorResolver.create(context, searchEngine);
    }

    @Test
    public void liveParse128409142()
            throws SearchException, CredentialsException, IOException {

        final Author author = new Author("Flix", "");
        author.setIdentifierValue(Identifier.SID_DNB, 128409142);

        final boolean resolved = resolver.resolve(context, author);
        assertTrue(resolved);

        parse128409142(author);
    }

    @Test
    public void parse128409142()
            throws SearchException, CredentialsException, IOException {

        final String locationHeader = "https://katalog.dnb.de/DE/resource.html?id=128409142&pr=0&sortA=bez&sortD=-dat&v=plist";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_author_flix_128409142;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        final Author author = resolver.parse(context, document);
        parse128409142(author);
    }

    private void parse128409142(@Nullable Author author) {
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("Flix", author.getFamilyName());
        assertEquals("", author.getGivenNames());

        assertEquals("1976", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        assertEquals(1, author.getIdentifiers().size());
        Optional<String> oIv;
        oIv = author.getIdentifierValue(Identifier.SID_DNB);
        assertTrue(oIv.isPresent());
        assertEquals("128409142", oIv.get());

        author = author.getRealAuthor();
        assertNotNull(author);

        assertEquals("Görmann", author.getFamilyName());
        assertEquals("Felix", author.getGivenNames());
        oIv = author.getIdentifierValue(Identifier.SID_DNB);
        assertTrue(oIv.isPresent());
        assertEquals("1216065012", oIv.get());
    }

    @Test
    public void parse1300021055()
            throws IOException, SearchException, CredentialsException {
        final String locationHeader = "https://katalog.dnb.de/DE/resource.html?id=1300021055&pr=0&sortA=bez&sortD=-dat&v=plist";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_author_1300021055;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        Author author = resolver.parse(context, document);
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("von Humboldt", author.getFamilyName());
        assertEquals("Dorothee", author.getGivenNames());
        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_DNB);
        assertTrue(oIv.isPresent());
        assertEquals("1300021055", oIv.get());
        author = author.getRealAuthor();
        assertNull(author);
    }

    @Test
    public void parse118678175()
            throws IOException, SearchException, CredentialsException {
        final String locationHeader = "https://katalog.dnb.de/DE/resource.html?hit=1&t=philip+dick&key=all&sp=auth&th=14&tk=8E76F448EDCF3C6474A171E4B7B6824CE00C0401&pr=0&sortA=bez&sortD=-dat&v=plist";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_author_118678175;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        Author author = resolver.parse(context, document);
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("Dick", author.getFamilyName());
        assertEquals("Philip K.", author.getGivenNames());

        assertEquals("1928", author.getBirthDate().orElse(null));
        assertEquals("1982", author.getDeathDate().orElse(null));


        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_DNB);
        assertTrue(oIv.isPresent());
        assertEquals("118678175", oIv.get());
        author = author.getRealAuthor();
        assertNull(author);
    }
}
