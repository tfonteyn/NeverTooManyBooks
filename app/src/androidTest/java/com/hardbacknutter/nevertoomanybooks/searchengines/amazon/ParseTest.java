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

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/** @noinspection MissingJavadoc */
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";

    private AmazonSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup();

        searchEngine = (AmazonSearchEngine) EngineId.Amazon.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    public void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.amazon.co.uk/gp/product/0575090677";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.amazon_0575090677;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Bone Silence", book.getString(DBKey.TITLE, null));
        assertEquals("978-0575090675", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2020-01-30", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("608", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("English", book.getString(DBKey.LANGUAGE, null));

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
    public void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.amazon.co.uk/gp/product/1473210208";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.amazon_1473210208;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("The Medusa Chronicles: Alastair Reynolds & Stephen Baxter",
                     book.getString(DBKey.TITLE, null));
        assertEquals("978-1473210202", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2017-01-12", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("336", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("English", book.getString(DBKey.LANGUAGE, null));

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
    public void parse10()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.amazon.fr/gp/product/2205057332";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.amazon_2205057332;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Le retour à la terre, 1 : La vraie vie",
                     book.getString(DBKey.TITLE, null));
        assertEquals("Français", book.getString(DBKey.LANGUAGE, null));
        assertEquals("978-2205057331", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals(13d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("DARGAUD", allPublishers.get(0).getName());

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
    public void parse11()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.amazon.de/gp/product/3518366823";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.amazon_3518366823;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Siddhartha. Eine indische Dichtung",
                     book.getString(DBKey.TITLE, null));
        assertEquals("978-3518366820", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("Deutsch", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Taschenbuch", book.getString(DBKey.FORMAT, null));
        assertEquals("3518366823", book.getString(DBKey.SID_ASIN, null));
        assertEquals(8d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
        assertEquals("1974-07-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Suhrkamp Verlag", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Hesse", authors.get(0).getFamilyName());
        assertEquals("Hermann", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
    }

    @Test
    public void parse12()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.amazon.com/gp/product/3518366823";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.amazon_3518366823_us;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Siddhartha", book.getString(DBKey.TITLE, null));
        assertEquals("978-3518366820", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("German", book.getString(DBKey.LANGUAGE, null));
        assertEquals("3518366823", book.getString(DBKey.SID_ASIN, null));
        assertEquals(13.19d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.USD, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
        assertEquals("1995-01-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Suhrkamp Verlag", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Hesse", authors.get(0).getFamilyName());
        assertEquals("Hermann", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
    }

    @Test
    public void parse20()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.amazon.es/gp/product/1107480558";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.amazon_1107480558;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Essential Grammar in Use. Fourth Edition. Book with Answers.",
                     book.getString(DBKey.TITLE, null));
        assertEquals("978-1107480551", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("Inglés", book.getString(DBKey.LANGUAGE, null));
        assertEquals("1107480558", book.getString(DBKey.SID_ASIN, null));
        assertEquals(22.36d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
        assertEquals("2015-04-20", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Cambridge University Press", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Murphy", authors.get(0).getFamilyName());
        assertEquals("Raymond", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
    }

    @Test
    public void parse21()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.amazon.es/gp/product/840827578X";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.amazon_840827578x;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("La rebelión de los buenos: Premio de Novela Fernando Lara 2023",
                     book.getString(DBKey.TITLE, null));
        assertEquals("978-8408275787", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("Tapa dura", book.getString(DBKey.FORMAT, null));
        assertEquals("Español", book.getString(DBKey.LANGUAGE, null));
        assertEquals("720 páginas", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("840827578X", book.getString(DBKey.SID_ASIN, null));
        assertEquals(21.75d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.EUR, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
        assertEquals("2023-06-14", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Editorial Planeta", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Santiago", authors.get(0).getFamilyName());
        assertEquals("Roberto", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());
        assertEquals("Autores Españoles e Iberoamericanos", series.get(0).getTitle());
    }
}
