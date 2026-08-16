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
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.PublicationFrequency;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IisnParseTest
        extends BaseDBTest {

    private static final String TAG = "IisnParseTest";

    private WikidataSearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Wikidata.getConfig().setLogHttpGetRequests(true);
        searchEngine = EngineId.Wikidata.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void mfsf()
            throws IOException {

        final ProductCode pc = ISBN.parse("0024-984X");
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.wikidata_issn_fsf);
        final Book book = new Book();
        searchEngine.parseFromIssn(context, document, pc, book);

        Log.d(TAG, book.toString());

        assertEquals("The Magazine of Fantasy & Science Fiction",
                     book.getString(DBKey.TITLE, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("digest size", book.getString(DBKey.FORMAT, null));
        assertEquals("0024984X", book.getString(DBKey.ISBN, null));

        assertEquals("Q937202", book.getIdentifierValue(Identifier.SID_WIKIDATA).orElse(null));
        assertEquals("179825835", book.getIdentifierValue(Identifier.SID_VIAF).orElse(null));
        assertEquals("20325", book.getIdentifierValue(Identifier.SID_ISFDB).orElse(null));
        assertEquals("4678619-3", book.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("n80014289", book.getIdentifierValue(Identifier.SID_LCCN).orElse(null));
        assertEquals("18737979", book.getIdentifierValue(Identifier.SID_OCLC).orElse(null));
        // and many more...

        final List<Series> seriesList = book.getSeries();
        assertEquals(1, seriesList.size());
        final Series series = seriesList.get(0);
        assertEquals("The Magazine of Fantasy & Science Fiction", series.getTitle());
        assertEquals("0024984X", series.getIdentifierValue(Identifier.SID_ISSN).orElse(null));

        final PublicationFrequency frequency = series.getPublicationFrequency();
        assertNotNull(frequency);
        assertEquals(PublicationFrequency.Type.Monthly, frequency.getType());
        assertEquals(2, frequency.getCadence());
        assertFalse(frequency.isOrdinal());

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(0, publishers.size());
        final List<Author> authors = book.getAuthors();
        assertEquals(0, authors.size());
    }

    @Test
    void guardian()
            throws IOException {

        final ProductCode pc = ISBN.parse("0261-3077");
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.wikidata_issn_guardian);
        final Book book = new Book();
        searchEngine.parseFromIssn(context, document, pc, book);

        Log.d(TAG, book.toString());

        assertEquals("The Guardian",
                     book.getString(DBKey.TITLE, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("tabloid", book.getString(DBKey.FORMAT, null));
        assertEquals("02613077", book.getString(DBKey.ISBN, null));

        assertEquals("Q11148", book.getIdentifierValue(Identifier.SID_WIKIDATA).orElse(null));
        // the -X is a checksum, but dbn.de accepts that just fine
        assertEquals("4158503-3", book.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("60623878", book.getIdentifierValue(Identifier.SID_OCLC).orElse(null));
        // and many more...

        final List<Series> seriesList = book.getSeries();
        assertEquals(1, seriesList.size());
        final Series series = seriesList.get(0);
        assertEquals("The Guardian", series.getTitle());
        assertEquals("02613077", series.getIdentifierValue(Identifier.SID_ISSN).orElse(null));

        final PublicationFrequency frequency = series.getPublicationFrequency();
        assertNotNull(frequency);
        assertEquals(PublicationFrequency.Type.Daily, frequency.getType());
        assertEquals(1, frequency.getCadence());
        assertFalse(frequency.isOrdinal());

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals("Guardian News and Media Ltd.", publishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertEquals(1, authors.size());
        final Author editor = authors.get(0);
        assertEquals("Viner", editor.getFamilyName());
        assertEquals("Katharine", editor.getGivenNames());
        assertEquals(AuthorRole.EDITOR, editor.getRole());
    }

    @Test
    void nature()
            throws IOException {

        final ProductCode pc = ISBN.parse("0028-0836");
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.wikidata_issn_nature);
        final Book book = new Book();
        searchEngine.parseFromIssn(context, document, pc, book);

        Log.d(TAG, book.toString());

        assertEquals("Nature",
                     book.getString(DBKey.TITLE, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("00280836", book.getString(DBKey.ISBN, null));

        assertEquals("Q180445", book.getIdentifierValue(Identifier.SID_WIKIDATA).orElse(null));
        assertEquals("01586310", book.getIdentifierValue(Identifier.SID_OCLC).orElse(null));
        // the -X is a checksum, but dbn.de accepts that just fine
        assertEquals("4499779-6", book.getIdentifierValue(Identifier.SID_DNB).orElse(null));

        final List<Series> seriesList = book.getSeries();
        assertEquals(1, seriesList.size());
        final Series series = seriesList.get(0);
        assertEquals("Nature", series.getTitle());
        assertEquals("00280836", series.getIdentifierValue(Identifier.SID_ISSN).orElse(null));

        final PublicationFrequency frequency = series.getPublicationFrequency();
        assertNotNull(frequency);
        assertEquals(PublicationFrequency.Type.Weekly, frequency.getType());
        assertEquals(1, frequency.getCadence());
        assertFalse(frequency.isOrdinal());

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        assertEquals("Springer Science+Business Media", publishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertEquals(1, authors.size());
        final Author editor = authors.get(0);
        assertEquals("Skipper", editor.getFamilyName());
        assertEquals("Magdalena", editor.getGivenNames());
        assertEquals(AuthorRole.EDITOR, editor.getRole());
    }
}
