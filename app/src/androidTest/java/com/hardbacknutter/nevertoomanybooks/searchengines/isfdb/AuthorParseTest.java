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

import androidx.preference.PreferenceManager;

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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
            throws SearchException, CredentialsException {

        //final String locationHeader = "https://www.isfdb.org/cgi-bin/ea.cgi?49";

        final Author author = new Author("", "");
        author.setIdentifierValue(Identifier.SID_ISFDB, 49);
        resolver.resolve(context, author);

        Log.d(TAG, author.toString());

        assertEquals("Robinson", author.getFamilyName());
        assertEquals("Kim Stanley", author.getGivenNames());

        assertEquals(1, author.getIdentifiers().size());
        final Optional<String> oIv = author.getIdentifierValue(Identifier.SID_ISFDB);
        assertTrue(oIv.isPresent());
        assertEquals("49", oIv.get());

        assertNull(author.getRealAuthor());
    }

    @Test
    public void parsePaulFrench()
            throws SearchException, CredentialsException {

        Author author = new Author("French", "Paul");
        resolver.resolve(context, author);

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

        assertEquals(1, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_ISFDB);
        assertTrue(oIv.isPresent());
        assertEquals("5", oIv.get());
    }
}
