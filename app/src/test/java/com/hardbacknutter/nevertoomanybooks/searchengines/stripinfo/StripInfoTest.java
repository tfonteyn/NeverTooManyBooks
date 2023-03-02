/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Network access for covers in all tests.
 * <p>
 * Full network access for {@link #parseMultiResult}.
 */
class StripInfoTest
        extends JSoupBase {

    private static final String TAG = "StripInfoTest";

    private static final String UTF_8 = "UTF-8";
    private StripInfoSearchEngine searchEngine;
    private Book book;
    private AuthorResolver mockAuthorResolver;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        book = new Book(BundleMock.create());
        searchEngine = (StripInfoSearchEngine) EngineId.StripInfoBe.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                      + "/336348_Hauteville_House_14_De_37ste_parallel";
        final String filename = "/stripinfo/336348_Hauteville_House_14_De_37ste_parallel.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{true, true}, book, mockAuthorResolver);
        // System.out.println(rawData);

        assertEquals("De 37ste parallel", book.getString(DBKey.TITLE, null));
        assertEquals("9789463064385", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2018", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("48", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Kleur", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Silvester", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Hauteville House", series.getTitle());
        assertEquals("14", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(3, authors.size());

        Author author = authors.get(0);
        assertEquals("Duval", author.getFamilyName());
        assertEquals("Fred", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(1);
        assertEquals("Gioux", author.getFamilyName());
        assertEquals("Thierry", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

        author = authors.get(2);
        assertEquals("Sayago", author.getFamilyName());
        assertEquals("Nuria", author.getGivenNames());
        assertEquals(Author.TYPE_COLORIST, author.getType());


        final List<String> covers = book.getStringArrayList(
                SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0]);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                          + "_9789463064385_0_.jpg"));

        final List<String> backCovers = book.getStringArrayList(
                SearchCoordinator.BKEY_FILE_SPEC_ARRAY[1]);
        assertNotNull(backCovers);
        assertEquals(1, backCovers.size());
        assertTrue(backCovers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                              + "_9789463064385_1_.jpg"));
    }

    @Test
    void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                      + "/2060_De_boom_van_de_twee_lentes_1"
                                      + "_De_boom_van_de_twee_lentes";
        final String filename = "/stripinfo/2060_De_boom_van_de_twee_lentes_1"
                                + "_De_boom_van_de_twee_lentes.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{true, true}, book, mockAuthorResolver);
        // System.out.println(rawData);

        assertEquals("De boom van de twee lentes", book.getString(DBKey.TITLE, null));
        assertEquals("905581315X", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2000", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("64", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Kleur", book.getString(DBKey.COLOR, null));

        final ArrayList<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        final ArrayList<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("De boom van de twee lentes", series.getTitle());
        assertEquals("1", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Getekend", series.getTitle());
        assertEquals("", series.getNumber());

        final ArrayList<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(20, authors.size());

        Author author = authors.get(0);
        assertEquals("Miel", author.getFamilyName());
        assertEquals("Rudi", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(1);
        assertEquals("Batem", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        // there are more...

        final List<String> covers = book.getStringArrayList(
                SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0]);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                          + "_905581315X_0_.jpg"));

        final List<String> backCovers = book.getStringArrayList(
                SearchCoordinator.BKEY_FILE_SPEC_ARRAY[1]);
        assertNotNull(backCovers);
        assertEquals(1, backCovers.size());
        assertTrue(backCovers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                              + "_905581315X_1_.jpg"));
    }

    @Test
    void parse03()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                      + "/181604_Okiya_het_huis_van_verboden_geneugten"
                                      + "_1_Het_huis_van_verboden_geneugten";
        final String filename = "/stripinfo/181604_mat_cover.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{true, true}, book, mockAuthorResolver);
        // System.out.println(rawData);

        assertEquals("Het huis van verboden geneugten",
                     book.getString(DBKey.TITLE, null));
        assertEquals("9789085522072", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2012", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("64", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Kleur", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Saga", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Okiya, het huis van verboden geneugten", series.getTitle());
        assertEquals("1", series.getNumber());


        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author = authors.get(0);
        assertEquals("Toth", author.getFamilyName());
        assertEquals("Jee Yun", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(1);
        assertEquals("Jung", author.getFamilyName());
        assertEquals("Jun Sik", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

        final List<String> covers = book.getStringArrayList(
                SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0]);
        assertNotNull(covers);
        assertEquals(1, covers.size());

        assertTrue(covers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                          + "_9789085522072_0_.jpg"));

        final List<String> backCovers = book.getStringArrayList(
                SearchCoordinator.BKEY_FILE_SPEC_ARRAY[1]);
        assertNotNull(backCovers);
        assertTrue(backCovers.isEmpty());
    }

    @Test
    void parseIntegrale()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://www.stripinfo.be/reeks/strip/"
                                      + "316016_Johan_en_Pirrewiet_INT_5_De_integrale_5";
        final String filename = "/stripinfo/316016_Johan_en_Pirrewiet_INT_5_De_integrale_5.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book,
                           mockAuthorResolver);
        // System.out.println(rawData);

        assertEquals("De integrale 5", book.getString(DBKey.TITLE, null));
        assertEquals("9789055819485", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2017", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("224", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Kleur", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        final List<TocEntry> tocs = book.getToc();
        assertNotNull(tocs);
        assertEquals(4, tocs.size());

        assertEquals("14 De horde van de raaf", tocs.get(0).getTitle());
        assertEquals("15 De troubadours van Steenbergen", tocs.get(1).getTitle());
        assertEquals("16 De nacht van de Magiërs", tocs.get(2).getTitle());
        assertEquals("17 De Woestijnroos", tocs.get(3).getTitle());

        assertEquals("Culliford", tocs.get(0).getPrimaryAuthor().getFamilyName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Johan en Pirrewiet", series.getTitle());
        assertEquals("INT 5", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(6, authors.size());

        Author author = authors.get(0);
        assertEquals("Culliford", author.getFamilyName());
        assertEquals("Thierry", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(3);
        assertEquals("Maury", author.getFamilyName());
        assertEquals("Alain", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        // there are more...
    }

    @Test
    void parseIntegrale2()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://www.stripinfo.be/reeks/strip/"
                                      + "17030_Comanche_1_Red_Dust";
        final String filename = "/stripinfo/17030_Comanche_1_Red_Dust.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book,
                           mockAuthorResolver);
        // System.out.println(rawData);

        assertEquals("Red Dust", book.getString(DBKey.TITLE, null));
        assertEquals("1972", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("48", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Kleur", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        final List<TocEntry> tocs = book.getToc();
        assertNotNull(tocs);
        assertEquals(3, tocs.size());

        assertEquals("1 De samenzwering", tocs.get(0).getTitle());
        assertEquals("2 De afrekening", tocs.get(1).getTitle());
        assertEquals("3 De ontmaskering", tocs.get(2).getTitle());

        assertEquals("Greg", tocs.get(0).getPrimaryAuthor().getFamilyName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Comanche", series.getTitle());
        assertEquals("1", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author = authors.get(0);
        assertEquals("Greg", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(1);
        assertEquals("Hermann", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
    }

    @Test
    void parseFavReeks2()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://www.stripinfo.be/reeks/strip/"
                                      + "8155_De_avonturen_van_de_3L_7_Spoken_in_de_grot";
        final String filename = "/stripinfo/8155_De_avonturen_van_de_3L_7_Spoken_in_de_grot.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book,
                           mockAuthorResolver);
        // System.out.println(rawData);

        assertEquals("Spoken in de grot", book.getString(DBKey.TITLE, null));
        assertEquals("1977", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Kleur", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        final List<TocEntry> tocs = book.getToc();
        assertNotNull(tocs);
        assertTrue(tocs.isEmpty());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("De avonturen van de 3L", series.getTitle());
        assertEquals("7", series.getNumber());
        series = allSeries.get(1);
        assertEquals("Favorietenreeks", series.getTitle());
        assertEquals("2.50", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author = authors.get(0);
        assertEquals("Duchateau", author.getFamilyName());
        assertEquals("André-Paul", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(1);
        assertEquals("Mittéï", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
    }


    /** Network access! */
    @Test
    void parseMultiResult()
            throws SearchException, CredentialsException, StorageException, IOException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://stripinfo.be/zoek/zoek?zoekstring=pluvi";
        final String filename = "/stripinfo/multi-result-pluvi.html";

        final Document document = loadDocument(filename, null, locationHeader);

        // we've set the doc, but will redirect.. so an internet download WILL be done.
        searchEngine.processDocument(context, "9782756010830",
                                     document, new boolean[]{false, false}, book);

        assertFalse(book.isEmpty());
        System.out.println(book);

        assertEquals("9782756010830", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("Le chant du pluvier", book.getString(DBKey.TITLE, null));
        assertEquals("2009", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("172", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Frans", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Kleur", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Delcourt", allPublishers.get(0).getName());

        final List<TocEntry> tocs = book.getToc();
        assertNotNull(tocs);
        assertTrue(tocs.isEmpty());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("Le chant du pluvier", series.getTitle());
        assertEquals("1", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Mirages", series.getTitle());
        assertEquals("", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(3, authors.size());

        Author author = authors.get(0);
        assertEquals("Béhé", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(1);
        assertEquals("Laprun", author.getFamilyName());
        assertEquals("Amandine", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(2);
        assertEquals("Surcouf", author.getFamilyName());
        assertEquals("Erwann", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST | Author.TYPE_COLORIST, author.getType());
    }

    // Geheime Driehoek nr 3, "As en Goud"
    // ISBN	9069692736 <-- incorrect
    // Barcode	9789069692739 <-- correct
    @Test
    void asEnGoud() {
        final ISBN barcode = new ISBN("9789069692739", true);
        assertTrue(barcode.isValid(true));

        final ISBN isbn = new ISBN("9069692736", true);
        assertFalse(isbn.isValid(true));
        assertFalse(isbn.isValid(false));
        assertTrue(isbn.isType(ISBN.Type.Invalid));

        book.clearData();
        book.putString(DBKey.BOOK_ISBN, "9069692736");
        book.putString(StripInfoSearchEngine.SiteField.BARCODE, "9789069692739");
        searchEngine.processBarcode("9789069692739", book);

        assertEquals(book.getString(DBKey.BOOK_ISBN, "foo"), "9789069692739");
        assertFalse(book.contains(StripInfoSearchEngine.SiteField.BARCODE));
    }
}
