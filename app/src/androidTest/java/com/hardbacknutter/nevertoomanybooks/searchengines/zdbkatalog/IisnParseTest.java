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

package com.hardbacknutter.nevertoomanybooks.searchengines.zdbkatalog;

import android.util.Log;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IisnParseTest
        extends BaseDBTest {

    private static final String TAG = "IisnParseTest";

    private static final String UTF_8 = "UTF-8";

    private ZdbKatalogSearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine =
                (ZdbKatalogSearchEngine) EngineId.ZdbKatalog.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);
    }

    @Test
    void m64er()
            throws IOException {
        final ProductCode pc = ISBN.parse("0176-8824");

        final String locationHeader = "https://zdb-katalog.de";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.zdb_issn_0176_8824;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parseIssn(context, document, pc, book);

        Log.d(TAG, book.toString());

        assertEquals("64er : das Magazin für Computer-Fans",
                     book.getString(DBKey.TITLE, null));
        assertEquals("German",
                     book.getString(DBKey.LANGUAGE, null));
        assertEquals("journal",
                     book.getString(DBKey.FORMAT, null));
        assertEquals("01768824",
                     book.getString(DBKey.ISBN, null));

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());

        assertEquals("Haar b. München : Markt & Technik Verl. AG", publishers.get(0).getName());

        assertEquals("010441638", book.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("85119872", book.getIdentifierValue(Identifier.SID_OCLC).orElse(null));

    }
}
