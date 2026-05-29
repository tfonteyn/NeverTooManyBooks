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

package com.hardbacknutter.nevertoomanybooks.searchengines.goodreads;

import android.util.Log;

import java.io.IOException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorParseTest
        extends BaseDBTest {

    private static final String TAG = "AuthorParseTest";
    private static final String UTF_8 = "UTF-8";

    private GoodreadsAuthorResolver resolver;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        final GoodreadsSearchEngine searchEngine = (GoodreadsSearchEngine) EngineId.Goodreads.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        resolver = new GoodreadsAuthorResolver(context, searchEngine);
    }

    @Test
    void parse01()
            throws IOException {
        final String locationHeader = "https://www.goodreads.com/author/show/2965845.Frank_P_";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.goodreads_author_2965845;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        // limit to Goodreads only
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(AuthorResolverFactory.getKey(
                              EngineId.Goodreads, EngineId.Goodreads), true)
                      .putBoolean(AuthorResolverFactory.getKey(
                              EngineId.Goodreads, EngineId.OpenLibrary), false)
                      .apply();

        final Optional<String> oIv;
        final Author author = resolver.parse(context, document);
        assertNotNull(author);

        Log.d(TAG, author.toString());

        assertEquals("Pé", author.getFamilyName());
        assertEquals("Frank", author.getGivenNames());

        assertEquals("1956-07-15", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        assertEquals(1, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_GOODREADS);
        assertTrue(oIv.isPresent());
        assertEquals("2965845", oIv.get());

        final String pic = author.getTmpPictureFileSpec().orElse(null);
        assertNotNull(pic);
        assertTrue(pic.endsWith("_goodreads_2965845_0_.jpg"));
    }
}
