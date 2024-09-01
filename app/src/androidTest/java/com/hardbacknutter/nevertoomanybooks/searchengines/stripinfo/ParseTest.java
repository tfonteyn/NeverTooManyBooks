/*
 * @Copyright 2018-2024 HardBackNutter
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
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Network access for covers in all tests.
 * <p>
 * Full network access for {@link #parseMultiResult}.
 */
@SuppressWarnings("MissingJavadoc")
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";
    private StripInfoSearchEngine searchEngine;
    private final List<AuthorResolver> mockAuthorResolvers = List.of();

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (StripInfoSearchEngine) EngineId.StripInfoBe.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    public void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.stripinfo.be/reeks/strip/336348_Hauteville_House_14_De_37ste_parallel";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripinfo_336348_hauteville_house_14_de_37ste_parallel;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, book,
                           mockAuthorResolvers);
        // Log.d(TAG, book.toString());

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


        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                          + "_9789463064385_0_.jpg"));

        final List<String> backCovers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(backCovers);
        assertEquals(1, backCovers.size());
        assertTrue(backCovers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                              + "_9789463064385_1_.jpg"));
    }

    @Test
    public void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.stripinfo.be/reeks/strip/2060_De_boom_van_de_twee_lentes_1_De_boom_van_de_twee_lentes";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripinfo_2060_de_boom_van_de_twee_lentes_1_de_boom_van_de_twee_lentes;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, book,
                           mockAuthorResolvers);
        // Log.d(TAG, book.toString());

        assertEquals("De boom van de twee lentes", book.getString(DBKey.TITLE, null));
        assertEquals("905581315X", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2000", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("64", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Kleur", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("De boom van de twee lentes", series.getTitle());
        assertEquals("1", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Getekend", series.getTitle());
        assertEquals("", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(21, authors.size());
        int a = 0;
        Author author;
        author = authors.get(a++);
        assertEquals("Miel", author.getFamilyName());
        assertEquals("Rudi", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(a++);
        assertEquals("Batem", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Colman", author.getFamilyName());
        assertEquals("Stéphane", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Dany", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Derib", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Fournier", author.getFamilyName());
        assertEquals("Jean-Claude", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Frank", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Franz", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Geerts", author.getFamilyName());
        assertEquals("André", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Hardy", author.getFamilyName());
        assertEquals("Marc", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Hausman", author.getFamilyName());
        assertEquals("René", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Hermann", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Le Gall", author.getFamilyName());
        assertEquals("Frank", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Loisel", author.getFamilyName());
        assertEquals("Régis", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Maltaite", author.getFamilyName());
        assertEquals("Eric", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Mézières", author.getFamilyName());
        assertEquals("Jean-Claude", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Plessix", author.getFamilyName());
        assertEquals("Michel", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Roba", author.getFamilyName());
        assertEquals("Jean", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Walthéry", author.getFamilyName());
        assertEquals("François", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Wasterlain", author.getFamilyName());
        assertEquals("Marc", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        author = authors.get(a++);
        assertEquals("Will", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                          + "_905581315X_0_.jpg"));

        final List<String> backCovers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(backCovers);
        assertEquals(1, backCovers.size());
        assertTrue(backCovers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                              + "_905581315X_1_.jpg"));
    }

    @Test
    public void parse03()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.stripinfo.be/reeks/strip/181604_Okiya_het_huis_van_verboden_geneugten_1_Het_huis_van_verboden_geneugten";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripinfo_181604_mat_cover;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, book,
                           mockAuthorResolvers);
        // Log.d(TAG, book.toString());

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
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());

        assertTrue(covers.get(0).endsWith(EngineId.StripInfoBe.getPreferenceKey()
                                          + "_9789085522072_0_.jpg"));

        final List<String> backCovers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(backCovers);
        assertTrue(backCovers.isEmpty());
    }

    @Test
    public void parseIntegrale()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.stripinfo.be/reeks/strip/316016_Johan_en_Pirrewiet_INT_5_De_integrale_5";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripinfo_316016_johan_en_pirrewiet_int_5_de_integrale_5;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book,
                           mockAuthorResolvers);
        // Log.d(TAG, book.toString());

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
        assertEquals("16 De nacht van de magiërs", tocs.get(2).getTitle());
        assertEquals("17 De woestijnroos", tocs.get(3).getTitle());

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
    public void parseIntegrale2()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.stripinfo.be/reeks/strip/17030_Comanche_1_Red_Dust";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripinfo_17030_comanche_1_red_dust;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book,
                           mockAuthorResolvers);
        // Log.d(TAG, book.toString());

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
    public void parseFavReeks2()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.stripinfo.be/reeks/strip/8155_De_avonturen_van_de_3L_7_Spoken_in_de_grot";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripinfo_8155_de_avonturen_van_de_3l_7_spoken_in_de_grot;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book,
                           mockAuthorResolvers);
        // Log.d(TAG, book.toString());

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
    public void parseMultiResult()
            throws SearchException, CredentialsException, StorageException, IOException {

        final String locationHeader = "https://stripinfo.be/zoek/zoek?zoekstring=chant+du+pluvier";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripinfo_multi_result_le_chant_du_pluvier;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();

        // we've set the doc, but will redirect.. so an internet download WILL be done.
        searchEngine.processDocument(context, "9782756010830",
                                     document, new boolean[]{false, false}, book);

        assertFalse(book.isEmpty());
        // Log.d(TAG, book.toString());

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
    public void asEnGoud() {
        final ISBN barcode = new ISBN("9789069692739", true);
        assertTrue(barcode.isValid(true));

        final ISBN isbn = new ISBN("9069692736", true);
        assertFalse(isbn.isValid(true));
        assertFalse(isbn.isValid(false));
        assertTrue(isbn.isType(ISBN.Type.Invalid));

        final Book book = new Book();
        book.putString(DBKey.BOOK_ISBN, "9069692736");
        book.putString(StripInfoSearchEngine.SiteField.BARCODE, "9789069692739");
        searchEngine.processBarcode("9789069692739", book);

        assertEquals(book.getString(DBKey.BOOK_ISBN, "foo"), "9789069692739");
        assertFalse(book.contains(StripInfoSearchEngine.SiteField.BARCODE));
    }
}
