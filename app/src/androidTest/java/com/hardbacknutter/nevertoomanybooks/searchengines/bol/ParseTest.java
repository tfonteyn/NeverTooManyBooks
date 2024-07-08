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

package com.hardbacknutter.nevertoomanybooks.searchengines.bol;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

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
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("MissingJavadoc")
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
        searchEngine.parseMultiResult(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("nijntjes voorleesfeest", book.getString(DBKey.TITLE, null));
        assertEquals("9789056478193", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2019-01-31", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("144", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        // Don't check, this test is a dynamic download
//        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser));
        // Don't check price, this test is a dynamic download
//        assertEquals(new Money(BigDecimal.valueOf(16.5d), Money.EURO),
//                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

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
        searchEngine.parseMultiResult(context, document, new boolean[]{true, true}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Foundation Trilogy", book.getString(DBKey.TITLE, null));
        assertEquals("9781841593326", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2010-10-29", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("664", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("en", book.getString(DBKey.LANGUAGE, null));
        // Don't check, this test is a dynamic download
//        assertEquals(4.8f, book.getFloat(DBKey.RATING, realNumberParser));
        // Don't check, this test is a dynamic download
//        assertEquals(new Money(BigDecimal.valueOf(18.07d), Money.EURO),
//                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

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

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Bol.getPreferenceKey()
                                          + "_9781841593326_0_.jpg"));

        final List<String> backCovers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(backCovers);
        assertEquals(0, backCovers.size());
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
        searchEngine.parse(context, document, new boolean[]{true, true}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Alter Ego", book.getString(DBKey.TITLE, null));
        assertEquals("9789044652901", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2023-03-28", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("400", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser));
        assertEquals(new Money(BigDecimal.valueOf(22.99d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

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

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Bol.getPreferenceKey()
                                          + "_9789044652901_0_.jpg"));

        final List<String> backCovers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(backCovers);
        assertEquals(1, backCovers.size());
        assertTrue(backCovers.get(0).endsWith(EngineId.Bol.getPreferenceKey()
                                              + "_9789044652901_1_.jpg"));
    }

    /**
     * be/fr + dutch book
     */
    @Test
    public void parse01fr()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader =
                "https://www.bol.com/be/fr/p/alter-ego/9300000135231911/?s2a=";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bol_9789044652901_nl_fr;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());
        // there won't be a title!
        assertNotEquals("Alter Ego", book.getString(DBKey.TITLE, null));
//        assertEquals("9789044652901", book.getString(DBKey.BOOK_ISBN, null));
//        assertEquals("2023-03-28", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
//        assertEquals("400", book.getString(DBKey.PAGE_COUNT, null));
//        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
//        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
//        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser));
//
//        final List<Publisher> allPublishers = book.getPublishers();
//        assertNotNull(allPublishers);
//        assertEquals(1, allPublishers.size());
//        assertEquals("Prometheus", allPublishers.get(0).getName());
//
//        final List<Author> authors = book.getAuthors();
//        assertNotNull(authors);
//        assertEquals(1, authors.size());
//
//        final Author author = authors.get(0);
//        assertEquals("Verhoef", author.getFamilyName());
//        assertEquals("Esther", author.getGivenNames());
//        assertEquals(Author.TYPE_UNKNOWN, author.getType());
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
        searchEngine.parse(context, document, new boolean[]{true, true}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Europa", book.getString(DBKey.TITLE, null));
        assertEquals("9789044544725", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2023-03-14", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("408", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(new Money(BigDecimal.valueOf(26.99d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

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

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Bol.getPreferenceKey()
                                          + "_9789044544725_0_.jpg"));

        final List<String> backCovers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(backCovers);
        assertEquals(1, backCovers.size());
        assertTrue(backCovers.get(0).endsWith(EngineId.Bol.getPreferenceKey()
                                              + "_9789044544725_1_.jpg"));
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
        searchEngine.parse(context, document, new boolean[]{true, true}, book);
        // Log.d(TAG, book.toString());

        assertEquals("nijntjes voorleesfeest", book.getString(DBKey.TITLE, null));
        assertEquals("9789056478193", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2019-01-31", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("144", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser));
        assertEquals(new Money(BigDecimal.valueOf(16.5d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

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
        searchEngine.parse(context, document, new boolean[]{true, true}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Foundation Trilogy", book.getString(DBKey.TITLE, null));
        assertEquals("9781841593326", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2010-10-29", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("664", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("en", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser));
        assertEquals(new Money(BigDecimal.valueOf(18.09d), Money.EURO),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

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

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Bol.getPreferenceKey()
                                          + "_9781841593326_0_.jpg"));

        final List<String> backCovers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(backCovers);
        assertEquals(0, backCovers.size());
    }
}
