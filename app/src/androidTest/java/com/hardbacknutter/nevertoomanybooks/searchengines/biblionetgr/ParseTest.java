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

package com.hardbacknutter.nevertoomanybooks.searchengines.biblionetgr;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("LongLine")
class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";

    private BiblionetGrSearchEngine searchEngine;
    private MoneyParser moneyParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.BiblionetGr.getConfig().setLogHttpGetRequests(true);
        searchEngine = (BiblionetGrSearchEngine) EngineId.BiblionetGr.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(EngineId.BiblionetGr.getPreferenceKey()
                                  + ".resolve.authors.wikidata", true)
                      .apply();

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        moneyParser = new MoneyParser(siteLocale, allLocales);
    }

    @Test
    void parseMultiResult()
            throws IOException {
        final String locationHeader =
                "https://biblionet.gr/%CF%83%CF%85%CE%BD%CE%B8%CE%B5%CF%84%CE%B7-%CE%B1%CE%BD%CE%B1%CE%B6%CE%B7%CF%84%CE%B7%CF%83%CE%B7?preselect_filter=books&q=%CF%80%CF%81%CE%B9%CE%B3%CE%BA%CE%B9%CF%80%CE%AD%CF%83%CE%B1";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.biblionetgr_multi_result;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        assertEquals("https://biblionet.gr/η-πριγκιπεσα-ιζαμπω-614456",
                     searchEngine.parseMultiResult(document));
    }

    @Test
    void parse9789603211495()
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

        assertPriceListed(book, "3.30", MoneyParser.EUR, moneyParser);

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
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1926-08-14", author.getBirthDate().orElse(null));
        assertEquals("1977-11-05", author.getDeathDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q192214", oIv.get());

        author = authors.get(1);
        assertEquals("Uderzo", author.getFamilyName());
        assertEquals("Albert", author.getGivenNames());
        assertEquals(AuthorRole.WRITER | AuthorRole.ARTIST, author.getRole());
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

    @Test
    void parse9789602489147()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader =
                "https://biblionet.gr/%CE%BF-%CE%BA%CF%89%CE%BD%CF%83%CF%84%CE%B1%CE%BD%CF%84%CE%B9%CE%BD%CE%BF%CF%82-%CF%87%CE%B1%CF%84%CE%B6%CE%BF%CF%80%CE%BF%CF%85%CE%BB%CE%BF%CF%82-%CF%89%CF%82-%CF%83%CF%85%CE%B3%CE%B3%CF%81%CE%B1%CF%86%CE%B5%CE%B1%CF%82-%CE%BA%CE%B1%CE%B9-%CE%B8%CE%B5%CF%89%CF%81%CE%B7%CF%84%CE%B9%CE%BA%CE%BF%CF%82-10323";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.biblionetgr_9789602489147;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals(
                "Ο Κωνσταντίνος Χατζόπουλος ως συγγραφέας και θεωρητικός - Πρακτικά επιστημονικό συμποσίου: Αγρίνιο, 14-17 Μαΐου 1993, Δημοτικό θέατρο",
                book.getString(DBKey.TITLE, null));
        assertEquals("9789602489147", book.getString(DBKey.ISBN, null));

        assertEquals("1998-06", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("1998-06", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));
        assertEquals("Μαλακό εξώφυλλο", book.getString(DBKey.FORMAT, null));
        assertEquals("603", book.getString(DBKey.PAGES, null));
        assertEquals("ell", book.getString(DBKey.LANGUAGE, null));

        assertTrue(book.getDescription().startsWith("Εισηγητές: Καρβέλης, Τάκης - Ιλίνσκαγια,"));

        assertPriceListed(book, "31.8", MoneyParser.EUR, moneyParser);

        final List<Tag> bookTags = book.getTags();
        assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Συγγραφείς, Έλληνες"));
        assertTrue(tags.contains("Νεοελληνική λογοτεχνία - Ερμηνεία και κριτική"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Εκδόσεις Δωδώνη", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(6, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Καψωμένος", author.getFamilyName());
        assertEquals("Ερατοσθένης", author.getGivenNames());
        assertEquals(AuthorRole.EDITOR, author.getRole());

        author = authors.get(1);
        assertEquals("Τζούλης", author.getFamilyName());
        assertEquals("Χρήστος", author.getGivenNames());
        assertEquals(AuthorRole.EDITOR, author.getRole());

        author = authors.get(2);
        assertEquals("Δανιήλ", author.getFamilyName());
        assertEquals("Χρήστος", author.getGivenNames());
        assertEquals(AuthorRole.EDITOR, author.getRole());

        author = authors.get(3);
        assertEquals("Δήμος Αγρινίου", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        author = authors.get(4);
        assertEquals("Φιλολογικός Όμιλος Αγρινίου \"Κώστας Χατζόπουλος\"", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        author = authors.get(5);
        assertEquals("Πανεπιστήμιο Ιωαννίνων. Φιλοσοφική Σχολή", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BiblionetGr.getPreferenceKey()
                                          + "_9789602489147_0_.jpg"));

    }
}
