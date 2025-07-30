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

package com.hardbacknutter.nevertoomanybooks.searchengines.bol;

import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
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
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@SuppressWarnings({"MissingJavadoc", "LongLine"})
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";
    private BolSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (BolSearchEngine) EngineId.Bol.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(context, true);

        // test data is pulled from the BE website
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(BolSearchEngine.PK_BOL_COUNTRY, "be")
                         .apply();
    }

    /** Network access! */
    @Test
    public void parseMultiResult01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.bol.com/be/nl/s/?searchtext=+9789056478193+";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_multi_1_result_9789056478193;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parseMultiResult(context, document, new boolean[]{false, false, false, false},
                                      book);
        Log.d(TAG, book.toString());

        assertEquals("nijntjes voorleesfeest", book.getString(DBKey.TITLE, null));
        assertEquals("9789056478193", book.getString(DBKey.ISBN, null));

        assertEquals("2019-01-31", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("144", book.getString(DBKey.PAGES, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);
        assertEquals(new Money(BigDecimal.valueOf(16.5d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

        // TEST: missing tags?
//        final List<Tag> bookTags = book.getTags();
//        Assert.assertEquals(2, bookTags.size());
//        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
//        assertTrue(tags.contains("Kinderboeken"));
//        assertTrue(tags.contains("Prentenboeken"));

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
        assertEquals(Author.TYPE_WRITER | Author.TYPE_ARTIST, author.getType());

    }

    /** Network access! */
    @Test
    public void parseMultiResult02()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.bol.com/be/nl/s/?searchtext=asimov%20foundation&suggestFragment=asimov";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_asimov_foundation;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parseMultiResult(context, document, new boolean[]{true, true, false, false},
                                      book);
        Log.d(TAG, book.toString());

        assertEquals("Foundation", book.getString(DBKey.TITLE, null));
        assertEquals("Foundation / Foundation And Empire / Second Foundation",
                     book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("9780008117498", book.getString(DBKey.ISBN, null));

        assertEquals("2016-09-22", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("240", book.getString(DBKey.PAGES, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("en", book.getString(DBKey.LANGUAGE, null));
        assertEquals(3.5f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);
        assertEquals(new Money(BigDecimal.valueOf(8.95d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

//  TEST: MISSING TAGS?
//        final List<Tag> bookTags = book.getTags();
//        Assert.assertEquals(2, bookTags.size());
//        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
//        assertTrue(tags.contains("Fantasy & Sciencefiction"));
//        assertTrue(tags.contains("Sciencefiction"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("HCOL", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;

        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9780008117498_0_.jpg"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(0, covers.size());
    }

    /**
     * be/nl + dutch book
     */
    @Test
    public void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader =
                "https://www.bol.com/be/nl/p/alter-ego/9300000135231911/?s2a=";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9789044652901_be_nl_dutch;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Alter ego", book.getString(DBKey.TITLE, null));
        assertEquals("9789044652895", book.getString(DBKey.ISBN, null));

        assertEquals("2023-04-06", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("416", book.getString(DBKey.PAGES, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(4.5f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);
        assertEquals(new Money(BigDecimal.valueOf(51.6d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

        final List<Tag> bookTags = book.getTags();
        Assert.assertEquals(7, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatuur & Romans"));
        assertTrue(tags.contains("Thrillers & Spanning"));
        assertTrue(tags.contains("Romance"));
        assertTrue(tags.contains("Psychologische thrillers"));
        assertTrue(tags.contains("Spanning"));
        assertTrue(tags.contains("Romantische thrillers"));
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
        assertEquals(Author.TYPE_WRITER, author.getType());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;

        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9789044652895_0_.jpg"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9789044652895_1_.jpg"));
    }

    @Test
    public void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.bol.com/be/nl/p/europa/9300000130411439/?promo=main_861_new_for_you___product_0_9300000130411439&bltgh=vwTKjOiKpqSmgLkGxPZJow.90_91.92.ProductImage";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9789044544725;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Europa", book.getString(DBKey.TITLE, null));
        assertEquals("9789044544725", book.getString(DBKey.ISBN, null));

        assertEquals("2023-03-14", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("408", book.getString(DBKey.PAGES, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(new Money(BigDecimal.valueOf(26.99d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

        final List<Tag> bookTags = book.getTags();
        Assert.assertEquals(3, bookTags.size());
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
        assertEquals(2, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Ash", author.getFamilyName());
        assertEquals("Timothy Garton", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(1);
        assertEquals("Pieters", author.getFamilyName());
        assertEquals("Inge", author.getGivenNames());
        assertEquals(Author.TYPE_TRANSLATOR, author.getType());

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
    public void parse03()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader =
                "https://www.bol.com/be/nl/p/nijntjes-voorleesfeest/9200000122271922/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9789056478193;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("nijntjes voorleesfeest", book.getString(DBKey.TITLE, null));
        assertEquals("9789056478193", book.getString(DBKey.ISBN, null));

        assertEquals("2019-01-31", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("144", book.getString(DBKey.PAGES, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);
        assertEquals(new Money(BigDecimal.valueOf(16.5d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

        final List<Tag> bookTags = book.getTags();
        Assert.assertEquals(2, bookTags.size());
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
        assertEquals(Author.TYPE_WRITER | Author.TYPE_ARTIST, author.getType());
    }

    /** The redirect from {@link #parseMultiResult02()} */
    @Test
    public void parse04()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader =
                "https://www.bol.com/be/nl/p/foundation-trilogy/1001004009994645/";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9781841593326;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Foundation Trilogy", book.getString(DBKey.TITLE, null));
        assertEquals("9781841593326", book.getString(DBKey.ISBN, null));

        assertEquals("2010-10-29", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("664", book.getString(DBKey.PAGES, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("en", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);
        assertEquals(new Money(BigDecimal.valueOf(18.28d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

        final List<Tag> bookTags = book.getTags();
        Assert.assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatuur & Romans"));
        assertTrue(tags.contains("Literaire romans"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Everyman'S Library", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(1);
        assertEquals("Dirda", author.getFamilyName());
        assertEquals("Michael", author.getGivenNames());
        assertEquals(Author.TYPE_EDITOR, author.getType());

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
    public void parse05()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader =
                "https://www.bol.com/be/nl/p/er-stromen-rivieren-in-de-lucht/9300000174936851/?bltgh=mY-mZ8dieLK1LnLXkKxH5g.hNfQd-6cIGnpHMA9c25Jow_0_24.29.ProductTitle";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9789046832073;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Er stromen rivieren in de lucht", book.getString(DBKey.TITLE, null));
        assertEquals("9789046832073", book.getString(DBKey.ISBN, null));

        assertEquals("2024-07-23", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("480", book.getString(DBKey.PAGES, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);
        assertEquals(new Money(BigDecimal.valueOf(24.99d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

        assertEquals(
                "'Wederom een rijke, roerende en actuele roman. [...] Een ingenieuze roman, waarmee Shafak niet alleen haar meesterschap onderstreept, maar vooral ook de grote kracht en waarde van literatuur in volle glorie toont.' het Parool ‘Haar nieuwe en wellicht ook meest ambitieuze roman tot nu toe’ De Morgen ‘Maak ruimte voor Shafak in je boekenkast. Maak ook ruimte voor haar in je hart. Je zult er geen spijt van krijgen.’ Arundhati Roy Londen, 1840. Arthur raakt gefascineerd door het oude Mesopotamië en in het bijzonder door het epische Gilgamesj-epos, over een hooghartige held die pas tot inkeer komt wanneer hij alles kwijt is. Turkije, 2014. De 10-jarige Narin moet vluchten voor isis, samen met haar oma, die uit een lange lijn van vrouwelijke zieners komt. Londen, 2018. Zaleekhah vindt troost in haar onderzoek naar rivieren, en komt via een vriendin in aanraking met een bijzondere oude taal. Wat de drie buitenstaanders door de eeuwen heen met elkaar verbindt, is het water, want: ‘Water bewaart alle herinneringen. Het zijn de mensen die vergeten.’ ‘Een van de belangrijkste schrijvers van dit moment.’ Independent ‘Iedereen zou Shafak moeten lezen.’ The Guardian ‘Een buitengewone roman, fris en zuiverend als de regen die op het metalen dak van ons leven slaat.’ Column McCann ‘Een meesterwerk.’ Ruth Ozeki ‘Shafaks verbeeldingskracht is een wonder: gedurfd, weergaloos en wijs.’ Katie Kitamura ‘Een moderne klassieker. Shafak is een van de grote schrijvers van onze tijd. Deze roman is verbazingwekkend, ingenieus en prachtig.’ Peter Frankopan",
                book.getString(DBKey.DESCRIPTION, null));

        final List<Tag> bookTags = book.getTags();
        Assert.assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatuur & Romans"));
        assertTrue(tags.contains("Historische romans"));

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Wereldbibliotheek", allPublishers.get(0).getName());

        Author author;
        author = authors.get(0);
        assertEquals("Shafak", author.getFamilyName());
        assertEquals("Elif", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());
        author = authors.get(1);
        assertEquals("Smits", author.getFamilyName());
        assertEquals("Manon", author.getGivenNames());
        assertEquals(Author.TYPE_TRANSLATOR, author.getType());

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
