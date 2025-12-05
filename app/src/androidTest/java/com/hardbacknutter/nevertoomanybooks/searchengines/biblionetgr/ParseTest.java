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

package com.hardbacknutter.nevertoomanybooks.searchengines.biblionetgr;

import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";

    private BiblionetGrSearchEngine searchEngine;
    private RealNumberParser realNumberParser;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (BiblionetGrSearchEngine) EngineId.BiblionetGr.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                         .putBoolean("biblionetgr.resolve.authors.wikidata", true)
                         .apply();

        realNumberParser = new RealNumberParser(List.of(searchEngine.getLocale(context)));
    }

    @Test
    public void parse9789603211495()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader =
                "https://biblionet.gr/%CE%B7-%CE%B3%CE%B1%CE%BB%CE%B5%CF%81%CE%B1-%CF%84%CE%BF%CF%85-%CE%BF%CE%B2%CE%B5%CE%BB%CE%B9%CE%BE-607";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.biblionetgr_9789603211495;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());


        assertEquals("Η γαλέρα του Οβελίξ", book.getString(DBKey.TITLE, null));
        assertEquals("La Galère d'Obélix", book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("9789603211495", book.getString(DBKey.ISBN, null));

        assertEquals("2008-01", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("Μαλακό εξώφυλλο", book.getString(DBKey.FORMAT, null));
        assertEquals("48", book.getString(DBKey.PAGES, null));

        final String description = book.getDescription();
        assertTrue(description.startsWith("Η γαλέρα του αυτοκράτορα καταλαμβάνεται"));
        assertTrue(description.endsWith("ξαναβρεί την όρεξή του ;"));

        final Money listPrice = book.getMoney(DBKey.PRICE_LISTED, realNumberParser);
        assertNotNull(listPrice);
        assertEquals(BigDecimal.valueOf(3.30d), listPrice.getValue());
        assertEquals(Money.EURO, listPrice.getCurrency());

        final List<Tag> bookTags = book.getTags();
        assertEquals(1, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Κόμικς"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Μαμούθ Comix", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Optional<String> oIv;
        Author author;
        author = authors.get(0);
        assertEquals("Goscinny", author.getFamilyName());
        assertEquals("René", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());
        assertEquals("1926-08-14", author.getBirthDate().orElse(null));
        assertEquals("1977-11-05", author.getDeathDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q192214", oIv.get());

        author = authors.get(1);
        assertEquals("Uderzo", author.getFamilyName());
        assertEquals("Albert", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_ARTIST, author.getType());
        assertEquals("1927-04-25", author.getBirthDate().orElse(null));
        assertEquals("2020-03-24", author.getDeathDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q206685", oIv.get());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Asterix", series.getTitle());
        assertEquals("", series.getNumber());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BiblionetGr.getPreferenceKey()
                                          + "_9789603211495_0_.jpg"));
    }
}
