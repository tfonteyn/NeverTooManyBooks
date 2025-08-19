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

package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

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

@SuppressWarnings({"MissingJavadoc","LongLine", "FieldCanBeLocal"})
public class AuthorParseTest
        extends BaseDBTest {

    private static final String TAG = "AuthorParseTest";

    private IsfdbSearchEngine searchEngine;
    private IsfdbAuthorResolver resolver;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (IsfdbSearchEngine) EngineId.Isfdb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        resolver = (IsfdbAuthorResolver) IsfdbAuthorResolver.create(context, searchEngine);

        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        // Override the default 'false'
        preferences.edit().putBoolean(IsfdbSearchEngine.PK_SERIES_FROM_TOC, true).apply();

        final boolean b = preferences.getBoolean(IsfdbSearchEngine.PK_SERIES_FROM_TOC, false);
        assertTrue(b);
    }

    @Test
    public void parse49()
            throws IOException, SearchException, CredentialsException {

        final String locationHeader = "https://www.isfdb.org/cgi-bin/ea.cgi?49";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_author_49;

        final Document document = loadDocument(resId, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                                               locationHeader);

        final Author author = resolver.parse(context, document, "49");
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("Robinson", author.getFamilyName());
        assertEquals("Kim Stanley", author.getGivenNames());

        assertEquals("1952-03-23", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        assertEquals(1, author.getIdentifiers().size());
        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_ISFDB);
        assertTrue(oIv.isPresent());
        assertEquals("49", oIv.get());

        assertNull(author.getRealAuthor());

        final String pic = author.getTmpPictureFileSpec().orElse(null);
        assertNotNull(pic);
        assertTrue(pic.endsWith("_isfdb_49_0_.jpg"));
    }

    @Test
    public void liveParse49()
            throws SearchException, CredentialsException {

        final Author author = new Author("Robinson", "Kim Stanley");
        author.setIdentifierValue(Identifier.SID_ISFDB, 49);
        final boolean modified = resolver.resolve(context, author);
        assertTrue(modified);

        Log.d(TAG, author.toString());

        assertEquals("Robinson", author.getFamilyName());
        assertEquals("Kim Stanley", author.getGivenNames());

        assertEquals("1952-03-23", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        assertEquals(1, author.getIdentifiers().size());
        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_ISFDB);
        assertTrue(oIv.isPresent());
        assertEquals("49", oIv.get());

        assertNull(author.getRealAuthor());
    }

    @Test
    public void parsePaulFrench()
            throws SearchException, CredentialsException, IOException {

        final String locationHeader = "https://www.isfdb.org/cgi-bin/ea.cgi?3358";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_author_3358;

        final Document document = loadDocument(resId, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                                               locationHeader);

        final Author author = resolver.parse(context, document, "3358");
        parsePaulFrench(author);
    }

    @Test
    public void liveParsePaulFrench()
            throws SearchException, CredentialsException {

        final Author author = new Author("French", "Paul");
        author.setIdentifierValue(Identifier.SID_ISFDB, "3358");

        final boolean modified = resolver.resolve(context, author);
        assertTrue(modified);

        parsePaulFrench(author);
    }

    private void parsePaulFrench(@Nullable Author author) {
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("French", author.getFamilyName());
        assertEquals("Paul", author.getGivenNames());

        Optional<String> oIv;

        assertEquals(1, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_ISFDB);
        assertTrue(oIv.isPresent());
        assertEquals("3358", oIv.get());

        author = author.getRealAuthor();
        assertNotNull(author);

        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());

        assertEquals("1920-01-02", author.getBirthDate().orElse(null));
        assertEquals("1992-04-06", author.getDeathDate().orElse(null));

        assertEquals(1, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_ISFDB);
        assertTrue(oIv.isPresent());
        assertEquals("5", oIv.get());
    }


}
