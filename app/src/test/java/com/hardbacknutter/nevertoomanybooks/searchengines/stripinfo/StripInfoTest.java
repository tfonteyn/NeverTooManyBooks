/*
 * @Copyright 2018-2022 HardBackNutter
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Network access for covers in all tests.
 * <p>
 * Full network access for {@link #parseMultiResult}.
 */
class StripInfoTest
        extends JSoupBase {

    private static final String UTF_8 = "UTF-8";
    private StripInfoSearchEngine searchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        searchEngine = (StripInfoSearchEngine) Site.Type.Data
                .getSite(EngineId.StripInfoBe).getSearchEngine();
        searchEngine.setCaller(new MockCancellable());
    }

    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                      + "/336348_Hauteville_House_14_De_37ste_parallel";
        final String filename = "/stripinfo/336348_Hauteville_House_14_De_37ste_parallel.html";

        loadData(context, searchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{true, true});

        assertEquals("De 37ste parallel", rawData.getString(DBKey.TITLE));
        assertEquals("9789463064385", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("2018", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("48", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Hardcover", rawData.getString(DBKey.FORMAT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));
        assertEquals("Kleur", rawData.getString(DBKey.COLOR));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Silvester", allPublishers.get(0).getName());

        final ArrayList<Series> allSeries = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Hauteville House", series.getTitle());
        assertEquals("14", series.getNumber());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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


        final ArrayList<String> covers =
                rawData.getStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0]);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                          + "_9789463064385_0_.jpg"));

        final ArrayList<String> backCovers =
                rawData.getStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[1]);
        assertNotNull(backCovers);
        assertEquals(1, backCovers.size());
        assertTrue(backCovers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                              + "_9789463064385_1_.jpg"));
    }

    @Test
    void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                      + "/2060_De_boom_van_de_twee_lentes_1"
                                      + "_De_boom_van_de_twee_lentes";
        final String filename = "/stripinfo/2060_De_boom_van_de_twee_lentes_1"
                                + "_De_boom_van_de_twee_lentes.html";

        loadData(context, searchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{true, true});

        assertEquals("De boom van de twee lentes", rawData.getString(DBKey.TITLE));
        assertEquals("905581315X", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("2000", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("64", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Softcover", rawData.getString(DBKey.FORMAT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));
        assertEquals("Kleur", rawData.getString(DBKey.COLOR));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        final ArrayList<Series> allSeries = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("De boom van de twee lentes", series.getTitle());
        assertEquals("1", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Getekend", series.getTitle());
        assertEquals("", series.getNumber());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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

        final ArrayList<String> covers =
                rawData.getStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0]);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                          + "_905581315X_0_.jpg"));

        final ArrayList<String> backCovers =
                rawData.getStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[1]);
        assertNotNull(backCovers);
        assertEquals(1, backCovers.size());
        assertTrue(backCovers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                              + "_905581315X_1_.jpg"));
    }

    @Test
    void parse03()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                      + "/181604_Okiya_het_huis_van_verboden_geneugten"
                                      + "_1_Het_huis_van_verboden_geneugten";
        final String filename = "/stripinfo/181604_mat_cover.html";

        loadData(context, searchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{true, false});

        assertEquals("Het huis van verboden geneugten",
                     rawData.getString(DBKey.TITLE));
        assertEquals("9789085522072", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("2012", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("64", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Hardcover", rawData.getString(DBKey.FORMAT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));
        assertEquals("Kleur", rawData.getString(DBKey.COLOR));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Saga", allPublishers.get(0).getName());

        final ArrayList<Series> allSeries = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Okiya, het huis van verboden geneugten", series.getTitle());
        assertEquals("1", series.getNumber());


        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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

        final ArrayList<String> covers =
                rawData.getStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0]);
        assertNotNull(covers);
        assertEquals(1, covers.size());

        assertTrue(covers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                          + "_9789085522072_0_.jpg"));

        final ArrayList<String> backCovers =
                rawData.getStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[1]);
        assertNull(backCovers);
    }

    @Test
    void parseIntegrale()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://www.stripinfo.be/reeks/strip/"
                                      + "316016_Johan_en_Pirrewiet_INT_5_De_integrale_5";
        final String filename = "/stripinfo/316016_Johan_en_Pirrewiet_INT_5_De_integrale_5.html";

        loadData(context, searchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{false, false});

        assertEquals("De integrale 5", rawData.getString(DBKey.TITLE));
        assertEquals("9789055819485", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("2017", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("224", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Hardcover", rawData.getString(DBKey.FORMAT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));
        assertEquals("Kleur", rawData.getString(DBKey.COLOR));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        final ArrayList<TocEntry> tocs = rawData.getParcelableArrayList(Book.BKEY_TOC_LIST);
        assertNotNull(tocs);
        assertEquals(4, tocs.size());

        assertEquals("14 De horde van de raaf", tocs.get(0).getTitle());
        assertEquals("15 De troubadours van Steenbergen", tocs.get(1).getTitle());
        assertEquals("16 De nacht van de Magiërs", tocs.get(2).getTitle());
        assertEquals("17 De Woestijnroos", tocs.get(3).getTitle());

        assertEquals("Culliford", tocs.get(0).getPrimaryAuthor().getFamilyName());

        final ArrayList<Series> allSeries = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Johan en Pirrewiet", series.getTitle());
        assertEquals("INT 5", series.getNumber());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://www.stripinfo.be/reeks/strip/"
                                      + "17030_Comanche_1_Red_Dust";
        final String filename = "/stripinfo/17030_Comanche_1_Red_Dust.html";

        loadData(context, searchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{false, false});

        assertEquals("Red Dust", rawData.getString(DBKey.TITLE));
        assertEquals("1972", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("48", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Softcover", rawData.getString(DBKey.FORMAT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));
        assertEquals("Kleur", rawData.getString(DBKey.COLOR));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        final ArrayList<TocEntry> tocs = rawData.getParcelableArrayList(Book.BKEY_TOC_LIST);
        assertNotNull(tocs);
        assertEquals(3, tocs.size());

        assertEquals("1 De samenzwering", tocs.get(0).getTitle());
        assertEquals("2 De afrekening", tocs.get(1).getTitle());
        assertEquals("3 De ontmaskering", tocs.get(2).getTitle());

        assertEquals("Greg", tocs.get(0).getPrimaryAuthor().getFamilyName());

        final ArrayList<Series> allSeries = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Comanche", series.getTitle());
        assertEquals("1", series.getNumber());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://www.stripinfo.be/reeks/strip/"
                                      + "8155_De_avonturen_van_de_3L_7_Spoken_in_de_grot";
        final String filename = "/stripinfo/8155_De_avonturen_van_de_3L_7_Spoken_in_de_grot.html";

        loadData(context, searchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{false, false});

        assertEquals("Spoken in de grot", rawData.getString(DBKey.TITLE));
        assertEquals("1977", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("Softcover", rawData.getString(DBKey.FORMAT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));
        assertEquals("Kleur", rawData.getString(DBKey.COLOR));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        final ArrayList<TocEntry> tocs = rawData.getParcelableArrayList(Book.BKEY_TOC_LIST);
        assertNull(tocs);

        final ArrayList<Series> allSeries = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("De avonturen van de 3L", series.getTitle());
        assertEquals("7", series.getNumber());
        series = allSeries.get(1);
        assertEquals("Favorietenreeks", series.getTitle());
        assertEquals("2.50", series.getNumber());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://stripinfo.be/zoek/zoek?zoekstring=pluvi";
        final String filename = "/stripinfo/multi-result-pluvi.html";

        Document document = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, null, locationHeader);
        }

        assertNotNull(document);
        assertTrue(document.hasText());

        // we've set the doc, but will redirect.. so an internet download WILL be done.
        searchEngine.processDocument(context, "9782756010830",
                                     document, new boolean[]{false, false}, rawData);

        assertFalse(rawData.isEmpty());
        System.out.println(rawData);

        assertEquals("9782756010830", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("Le chant du pluvier", rawData.getString(DBKey.TITLE));
        assertEquals("2009", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("172", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Hardcover", rawData.getString(DBKey.FORMAT));
        assertEquals("fra", rawData.getString(DBKey.LANGUAGE));
        assertEquals("Kleur", rawData.getString(DBKey.COLOR));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Delcourt", allPublishers.get(0).getName());

        final ArrayList<TocEntry> tocs = rawData.getParcelableArrayList(Book.BKEY_TOC_LIST);
        assertNull(tocs);

        final ArrayList<Series> allSeries = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("Le chant du pluvier", series.getTitle());
        assertEquals("1", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Mirages", series.getTitle());
        assertEquals("", series.getNumber());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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

        rawData.clear();
        rawData.putString(DBKey.BOOK_ISBN, "9069692736");
        rawData.putString(StripInfoSearchEngine.SiteField.BARCODE, "9789069692739");
        searchEngine.processBarcode("9789069692739", rawData);

        assertEquals(rawData.getString(DBKey.BOOK_ISBN, "foo"), "9789069692739");
        assertFalse(rawData.containsKey(StripInfoSearchEngine.SiteField.BARCODE));
    }
}
