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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmazonHtmlHandlerTest
        extends JSoupBase {

    private static final String TAG = "AmazonHtmlHandlerTest";
    private static final String UTF_8 = "UTF-8";

    private AmazonSearchEngine searchEngine;

    private Book book;

    @BeforeEach
    public void setup()
            throws Exception {
        super.setup();
        book = new Book(BundleMock.create());
        searchEngine = (AmazonSearchEngine) EngineId.Amazon.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.UK);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final String locationHeader = "https://www.amazon.co.uk/gp/product/0575090677";
        final String filename = "/amazon/0575090677.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // System.out.println(book);

        assertEquals("Bone Silence", book.getString(DBKey.TITLE, null));
        assertEquals("978-0575090675", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("30 Jan. 2020", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("608", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("English", book.getString(DBKey.LANGUAGE, null));
        assertEquals(14.49d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser));
        assertEquals(MoneyParser.GBP, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Reynolds", authors.get(0).getFamilyName());
        assertEquals("Alastair", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
    }

    @Test
    void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.UK);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final String locationHeader = "https://www.amazon.co.uk/gp/product/1473210208";
        final String filename = "/amazon/1473210208.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // System.out.println(book);

        assertEquals("The Medusa Chronicles", book.getString(DBKey.TITLE, null));
        assertEquals("978-1473210202", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("12 Jan. 2017", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("336", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("English", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.84d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser));
        assertEquals(MoneyParser.GBP, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Gollancz", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Reynolds", authors.get(0).getFamilyName());
        assertEquals("Alastair", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
        assertEquals("Baxter", authors.get(1).getFamilyName());
        assertEquals("Stephen", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(1).getType());
    }

    @Test
    void parse10()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.FRANCE);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final String locationHeader = "https://www.amazon.fr/gp/product/2205057332";
        final String filename = "/amazon/2205057332.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // System.out.println(book);

        assertEquals("Le retour à la terre, 1 : La vraie vie",
                     book.getString(DBKey.TITLE, null));
        assertEquals("978-2205057331", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals(12d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser));
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Dargaud", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Jean-Yves", authors.get(0).getFamilyName());
        assertEquals("Ferri", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
        assertEquals("Manu", authors.get(1).getFamilyName());
        assertEquals("Larcenet", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_ARTIST, authors.get(1).getType());
    }

    @Test
    void parse11()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.GERMANY);

        searchEngine = (AmazonSearchEngine) EngineId.Amazon.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final String locationHeader = "https://www.amazon.de/Siddhartha-indische-Dichtung-Hermann-Hesse/dp/3518366823/ref=sr_1_1?__mk_de_DE=%C3%85M%C3%85%C5%BD%C3%95%C3%91&crid=EM8GE4M54T6N&keywords=3518366823&qid=1679912159&sprefix=3518366823%2Caps%2C75&sr=8-1";
        final String filename = "/amazon/3518366823.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        System.out.println(book);
    }

    @Test
    void parse12()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.GERMANY);

        searchEngine = (AmazonSearchEngine) EngineId.Amazon.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        final String locationHeader = "https://www.amazon.com/Siddhartha-German-Hermann-Hesse/dp/3518366823/ref=sr_1_1?crid=1KWU787T6IGNO&keywords=3518366823&qid=1679916105&sprefix=%2Caps%2C150&sr=8-1";
        final String filename = "/amazon/3518366823-us.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        System.out.println(book);
    }
}
