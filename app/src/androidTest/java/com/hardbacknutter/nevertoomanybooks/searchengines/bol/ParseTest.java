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

package com.hardbacknutter.nevertoomanybooks.searchengines.bol;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
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
    private BolSearchEngine searchEngine;
    private RealNumberParser ratingNumberParser;
    private MoneyParser moneyParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Bol.getConfig().setLogHttpGetRequests(true);
        searchEngine = EngineId.Bol.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        // test data is pulled from the BE website
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putString(BolSearchEngine.PK_BOL_COUNTRY, "be")
                      .apply();

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        ratingNumberParser = new RealNumberParser(allLocales);
        moneyParser = new MoneyParser(siteLocale, allLocales);
    }

    @Test
    void parseMultiResult01()
            throws SearchException, IOException {

        final String locationHeader = "https://www.bol.com/be/nl/s/?searchtext=+9789056478193+";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_multi_1_result_9789056478193;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        assertEquals("https://www.bol.com/be/nl/p/nijntjes-voorleesfeest/9200000122271922/",
                     searchEngine.parseMultiResult(document));
    }

    @Test
    void parseMultiResult02()
            throws SearchException, IOException {

        final String locationHeader = "https://www.bol.com/be/nl/s/?searchtext=asimov%20foundation&suggestFragment=asimov";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_asimov_foundation;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        assertEquals("https://www.bol.com/be/nl/p/foundation/9200000037157117/",
                     searchEngine.parseMultiResult(document));
    }

    /**
     * be/nl + dutch book
     *
     * <pre>
     *     {"@type":"Book","name":"Alter ego","@description":"<b>Genomineerd voor de NS Publieksprijs 2023</b> Het leven lacht Lynn eindelijk toe. Ze is getrouwd met de twintig jaar oudere Camiel Storm, een bekende chef-kok, en verzorgt met verve de pr voor diens sterrenrestaurant De Luwte. Voor de buitenwereld vormen de twee een succesvol powerkoppel. Maar terwijl Camiel avond na avond de sterren van de hemel staat te koken, onderhoudt Lynn een passievolle affaire met de jonge, opvliegende Laurens. Door ambitieuze uitbreidingsplannen komt het huwelijk verder onder druk te staan. En wanneer er onverklaarbare dingen gebeuren in en rond de villa van Camiel en Lynn, kan Lynn bij niemand terecht. Esther Verhoef is een van de succesvolste en veelzijdigste schrijvers van Nederland. Zij is onze meest bekroonde en genomineerde thrillerauteur. Van haar psychologische thrillers en romans zijn in eigen land ruim 2,9 miljoen exemplaren verkocht. ‘Esther Verhoef hoort duidelijk tot de schrijvende elite in ons land.’ Algemeen Dagblad ‘Als je de adrenaline van de recensent nog tot diep in de nacht laat stromen, is dat vakwerk.’ De Volkskrant ‘Verhoef bewijst keer op keer dat zij in Nederland de koningin van het genre genoemd mag worden.’ Vrij Nederland Over De Nachtdienst: ‘De schrijfster doet haar langjarige reputatie als “koningin van de Nederlandse thriller” opnieuw eer aan.’ De Telegraaf ‘De Nachtdienst is geraffineerd opgebouwd en nagelbijtend spannend.’ Margriet","url":"https://www.bol.com/be/nl/p/alter-ego/9300000135231906/","bookFormat":"https://schema.org/Paperback","isbn":"9789044652901","numberOfPages":"416","offers":{"@type":"Offer","price":"22.99","priceCurrency":"EUR","itemCondition":"https://schema.org/NewCondition","availability":"InStock","seller":{"@type":"Organization","name":"bol"}},"datePublished":"2023-03-28"}
     * </pre>
     */
    @Test
    void parse01()
            throws IOException, StorageException, SearchException {

        final String locationHeader =
                "https://www.bol.com/be/nl/p/alter-ego/9300000135231906/?cid=1786008557620-6794174876919";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9789044652901_be_nl_dutch;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, ISBN.parse("9789044652901"),
                           document, new boolean[]{true, true, false, false},
                           book);
        Log.d(TAG, book.toString());

        assertEquals("Alter ego", book.getString(DBKey.TITLE, null));
        assertEquals("9789044652901", book.getString(DBKey.ISBN, null));

        assertEquals("2023-03-28", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("416", book.getString(DBKey.PAGES, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(4.5f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertPriceListed(book, "22.99", MoneyParser.EUR, moneyParser);

        assertTrue(book.getString(DBKey.DESCRIPTION)
                       .startsWith("<b>Genomineerd voor de NS Publieks"));

        final List<Tag> bookTags = book.getTags();
        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatuur & Romans"));
        assertTrue(tags.contains("Thrillers & Spanning"));
        assertTrue(tags.contains("Literaire thrillers"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Prometheus", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Author author = authors.get(0);
        assertEquals("Verhoef", author.getFamilyName());
        assertEquals("Esther", author.getGivenNames());
        //assertEquals(AuthorRole.WRITER, author.getRole());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;

        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9789044652901_0_.jpg"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9789044652901_1_.jpg"));
    }

    @Test
    void parse02()
            throws IOException, StorageException, SearchException {

        final String locationHeader = "https://www.bol.com/be/nl/p/europa/9300000130411439/?promo=main_861_new_for_you___product_0_9300000130411439&bltgh=vwTKjOiKpqSmgLkGxPZJow.90_91.92.ProductImage";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9789044544725;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, ISBN.parse("9789044544725"),
                           document, new boolean[]{true, true, false, false},
                           book);
        Log.d(TAG, book.toString());

        assertEquals("Europa", book.getString(DBKey.TITLE, null));
        assertEquals("9789044544725", book.getString(DBKey.ISBN, null));

        assertEquals("2023-03-14", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("408", book.getString(DBKey.PAGES, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertPriceListed(book, "27.99", MoneyParser.EUR, moneyParser);

        assertTrue(book.getString(DBKey.DESCRIPTION)
                       .startsWith("<p><strong>‘Timothy Garton Ash beschrijft"));

        final List<Tag> bookTags = book.getTags();
        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Geschiedenis"));
        assertTrue(tags.contains("Europa"));
        assertTrue(tags.contains("Regio's & Landen"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("de Geus", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Ash", author.getFamilyName());
        assertEquals("Timothy Garton", author.getGivenNames());
//        assertEquals(AuthorRole.WRITER, author.getRole());

//        author = authors.get(1);
//        assertEquals("Pieters", author.getFamilyName());
//        assertEquals("Inge", author.getGivenNames());
//        assertEquals(AuthorRole.TRANSLATOR, author.getRole());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;

        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9789044544725_0_.jpg"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9789044544725_1_.jpg"));
    }

    /** The redirect from {@link #parseMultiResult01()} */
    @Test
    void parse03()
            throws IOException, StorageException, SearchException {

        final String locationHeader =
                "https://www.bol.com/be/nl/p/nijntjes-voorleesfeest/9200000122271922/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9789056478193;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, ISBN.parse("9789056478193"),
                           document, new boolean[]{true, true, false, false},
                           book);
        Log.d(TAG, book.toString());

        assertEquals("nijntjes voorleesfeest", book.getString(DBKey.TITLE, null));
        assertEquals("9789056478193", book.getString(DBKey.ISBN, null));

        assertEquals("2019-01-31", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("144", book.getString(DBKey.PAGES, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertPriceListed(book, "16.5", MoneyParser.EUR, moneyParser);

        final List<Tag> bookTags = book.getTags();
        assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Kinderboeken"));
        assertTrue(tags.contains("Prentenboeken"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Mercis Publishing B.V.", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Author author = authors.get(0);
        assertEquals("Bruna", author.getFamilyName());
        assertEquals("Dick", author.getGivenNames());
        //assertEquals(AuthorRole.WRITER | AuthorRole.ARTIST, author.getRole());
    }

    /** The redirect from {@link #parseMultiResult02()} */
    @Test
    void parse04()
            throws IOException, StorageException, SearchException {

        final String locationHeader =
                "https://www.bol.com/be/nl/p/foundation-trilogy/1001004009994645/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9781841593326;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, ISBN.parse("9781841593326"),
                           document, new boolean[]{true, true, false, false},
                           book);
        Log.d(TAG, book.toString());

        assertEquals("Foundation Trilogy", book.getString(DBKey.TITLE, null));
        assertEquals("9781841593326", book.getString(DBKey.ISBN, null));

        assertEquals("2010-10-29", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("664", book.getString(DBKey.PAGES, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("en", book.getString(DBKey.LANGUAGE, null));
        assertEquals(4.8f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertPriceListed(book, "24.07", MoneyParser.EUR, moneyParser);

        assertTrue(book.getString(DBKey.DESCRIPTION)
                       .startsWith("It is the story of the Galactic Empire, crumbling"));

        final List<Tag> bookTags = book.getTags();
        assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatuur & Romans"));
        assertTrue(tags.contains("Literaire romans"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Everyman'S Library", allPublishers.get(0).getName());
        //assertEquals("Penguin Random House UK", allPublishers.get(1).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        //assertEquals(AuthorRole.WRITER, author.getRole());

//        author = authors.get(1);
//        assertEquals("Dirda", author.getFamilyName());
//        assertEquals("Michael", author.getGivenNames());
//        assertEquals(AuthorRole.EDITOR, author.getRole());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;

        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9781841593326_0_.jpg"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(0, covers.size());
    }

    @Test
    void parse05()
            throws IOException, StorageException, SearchException {

        final String locationHeader =
                "https://www.bol.com/be/nl/p/er-stromen-rivieren-in-de-lucht/9300000174936851/?bltgh=mY-mZ8dieLK1LnLXkKxH5g.hNfQd-6cIGnpHMA9c25Jow_0_24.29.ProductTitle";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9789046832073;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, ISBN.parse("9789046832073"),
                           document, new boolean[]{true, true, false, false},
                           book);
        Log.d(TAG, book.toString());

        assertEquals("Er stromen rivieren in de lucht", book.getString(DBKey.TITLE, null));
        assertEquals("9789046832073", book.getString(DBKey.ISBN, null));

        assertEquals("2024-07-23", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("480", book.getString(DBKey.PAGES, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(4.5f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertPriceListed(book, "22.49", MoneyParser.EUR, moneyParser);

        assertTrue(book.getString(DBKey.DESCRIPTION)
                       .startsWith("<p>'Wederom een rijke, roerende en actuele roman"));

        final List<Tag> bookTags = book.getTags();
        assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatuur & Romans"));
        assertTrue(tags.contains("Historische romans"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Wereldbibliotheek", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Shafak", author.getFamilyName());
        assertEquals("Elif", author.getGivenNames());
//        assertEquals(AuthorRole.WRITER, author.getRole());
//        author = authors.get(1);
//        assertEquals("Smits", author.getFamilyName());
//        assertEquals("Manon", author.getGivenNames());
//        assertEquals(AuthorRole.TRANSLATOR, author.getRole());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;

        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9789046832073_0_.jpg"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9789046832073_1_.jpg"));
    }
}
