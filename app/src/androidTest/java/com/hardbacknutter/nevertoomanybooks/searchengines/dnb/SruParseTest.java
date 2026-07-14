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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
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
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("LongLine")
class SruParseTest
        extends BaseDBTest {

    private static final boolean FETCH_COVER_0 = false;


    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";
    private DnbSearchEngine searchEngine;
    private MoneyParser moneyParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (DnbSearchEngine) EngineId.Dnb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        moneyParser = new MoneyParser(siteLocale, allLocales);
    }

    @Test
    void parse9783453321892()
            throws IOException, CredentialsException, StorageException {

        final String locationHeader = "https://services.dnb.de/sru/dnb?version=1.1&operation=searchRetrieve&query=num%3D9783453321892&recordSchema=MARC21-xml&maximumRecords=1";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_sru_9783453321892;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());
        final Book book = new Book();
        searchEngine.parse(context, document, "9783453321892",
                           new boolean[]{FETCH_COVER_0, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Nemesis", book.getString(DBKey.TITLE, null));
        assertEquals("9783453321892", book.getString(DBKey.ISBN, null));
        assertEquals("1254682597", book.requireIdentifierValue(Identifier.SID_DNB));

        assertEquals("ger", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2023-01", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("526", book.getString(DBKey.PAGES, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(1, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Science Fiction"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Wilhelm Heyne Verlag", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;

        author = authors.get(0);
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("118646109", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));

        author = authors.get(1);
        assertEquals("Holicki", author.getFamilyName());
        assertEquals("Irene", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertEquals("133558215", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());

        assertEquals("Die Foundation-Saga", series.get(0).getTitle());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Dnb.getPreferenceKey()
                                          + "_9783453321892_0_.jpg"));
    }

    @Test
    void parse9783426226681()
            throws IOException, CredentialsException, StorageException {

        final String locationHeader = "https://services.dnb.de/sru/dnb?version=1.1&operation=searchRetrieve&query=num%3D9783426226681&recordSchema=MARC21-xml&maximumRecords=1";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_sru_9783426226681;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());
        final Book book = new Book();
        searchEngine.parse(context, document, "9783426226681",
                           new boolean[]{FETCH_COVER_0, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Totholz: was vergraben ist, ist nicht vergessen",
                     book.getString(DBKey.TITLE, null));
        assertEquals("9783426226681", book.getString(DBKey.ISBN, null));
        assertEquals("1308358113", book.requireIdentifierValue(Identifier.SID_DNB));

        assertEquals("ger", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2024-06", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("378", book.getString(DBKey.PAGES, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Krimis"));
        assertTrue(tags.contains("Thriller"));
        assertTrue(tags.contains("Spionage"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Knaur", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Föhr", authors.get(0).getFamilyName());
        assertEquals("Andreas", authors.get(0).getGivenNames());
        assertEquals(AuthorRole.WRITER, authors.get(0).getRole());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(0, series.size());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Dnb.getPreferenceKey()
                                          + "_9783426226681_0_.jpg"));
    }

    @Test
    void parse9783734163296()
            throws IOException, CredentialsException, StorageException {
        final String locationHeader = "https://services.dnb.de/sru/dnb?version=1.1&operation=searchRetrieve&query=num%3D9783734163296&recordSchema=MARC21-xml&maximumRecords=1";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_sru_9783734163296;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());
        final Book book = new Book();
        searchEngine.parse(context, document, "9783734163296",
                           new boolean[]{FETCH_COVER_0, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Teurer Sieg", book.getString(DBKey.TITLE, null));
        assertEquals("9783734163296", book.getString(DBKey.ISBN, null));
        assertEquals("1272077195", book.requireIdentifierValue(Identifier.SID_DNB));

        assertEquals("ger", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2023", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("747", book.getString(DBKey.PAGES, null));
        assertEquals("Lesser evil", book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("eng", book.getString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Science Fiction"));
        assertTrue(tags.contains("Fantasy"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Blanvalet", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Zahn", author.getFamilyName());
        assertEquals("Timothy", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        author = authors.get(1);
        assertEquals("Kasprzak", author.getFamilyName());
        assertEquals("Andreas", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());

        assertEquals("Star Wars Thrawn - der Aufstieg", series.get(0).getTitle());
        assertEquals("3", series.get(0).getNumber());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Dnb.getPreferenceKey()
                                          + "_9783734163296_0_.jpg"));
    }

    @Test
    void parse9783770411849()
            throws IOException, CredentialsException, StorageException {
        final String locationHeader = "https://services.dnb.de/sru/dnb?version=1.1&operation=searchRetrieve&query=num%3D9783770411849&recordSchema=MARC21-xml&maximumRecords=1";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_sru_9783770411849;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());
        final Book book = new Book();
        searchEngine.parse(context, document, "9783770411849",
                           new boolean[]{FETCH_COVER_0, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Die Grimm Brothers: Eine Lucky-Luke-Hommage von Flix und Reinhard Kleist",
                     book.getString(DBKey.TITLE, null));
        assertEquals("9783770411849", book.getString(DBKey.ISBN, null));
        assertEquals("1380522498", book.requireIdentifierValue(Identifier.SID_DNB));
        assertEquals("1548399680", book.requireIdentifierValue(Identifier.SID_OCLC));

        assertEquals("ger", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2026", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("48", book.getString(DBKey.PAGES, null));
        // These don't make sense, as there was NO original french version.
        // But the site returns them anyhow.
        assertEquals("Lucky Luke", book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("fre", book.getString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, null));

        assertEquals(17.0, book.getDouble(DBKey.PRICE_LISTED, moneyParser.getRealNumberParser()), 0);
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertEquals(0, book.getTags().size());

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Egmont Comic Collection", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Flix", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1976", author.getBirthDate().orElse(null));
        assertEquals("128409142", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("128409142", author.getIdentifierValue("DE-588").orElse(null));
        assertEquals("0000000368558513", author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));
        assertEquals("Q114237", author.getIdentifierValue(Identifier.SID_WIKIDATA).orElse(null));

        final Author realAuthor = author.getRealAuthor();
        assertNotNull(realAuthor);
        assertEquals("Görmann", realAuthor.getFamilyName());
        assertEquals("Felix", realAuthor.getGivenNames());
        assertEquals("1976", realAuthor.getBirthDate().orElse(null));
        assertEquals(AuthorRole.UNKNOWN, realAuthor.getRole());
        assertEquals("1216065012", realAuthor.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("1216065012", realAuthor.getIdentifierValue("DE-588").orElse(null));

        author = authors.get(1);
        assertEquals("Kleist", author.getFamilyName());
        assertEquals("Reinhard", author.getGivenNames());
        // This is incorrect, but its what the site return.
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1970-02-11", author.getBirthDate().orElse(null));
        assertEquals("120643480", author.getIdentifierValue(Identifier.SID_DNB).orElse(null));
        assertEquals("120643480", author.getIdentifierValue("DE-588").orElse(null));
        assertEquals("0000000116350679", author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Dnb.getPreferenceKey()
                                          + "_9783770411849_0_.jpg"));
    }
}
