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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import junit.framework.TestCase;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";
    private DnbSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (DnbSearchEngine) EngineId.Dnb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    public void parse9783453321892()
            throws IOException, SearchException, CredentialsException, StorageException {

        final String locationHeader = "https://katalog.dnb.de/DE/list.html?t=978-3-453-32189-2&v=plist&key.GROUP=1&sortA=bez&sortD=-dat&key=all&pr=0";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_9783453321892;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        //Log.d(TAG, book.toString());

        assertEquals("Nemesis", book.getString(DBKey.TITLE, null));
        assertEquals("9783453321892", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("1254682597", book.getString(Identifier.SID_DNB, null));

        assertEquals("deu", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2023-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("526", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Science Fiction", book.getString(DBKey.GENRE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        TestCase.assertEquals(1, allPublishers.size());

        TestCase.assertEquals("Wilhelm Heyne Verlag", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Asimov", authors.get(0).getFamilyName());
        assertEquals("Isaac", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());

        assertEquals("Holicki", authors.get(1).getFamilyName());
        assertEquals("Irene", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_TRANSLATOR, authors.get(1).getType());

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
    public void parse9783426226681()
            throws IOException, SearchException, CredentialsException, StorageException {

        final String locationHeader = "https://katalog.dnb.de/EN/list.html?key=num&key.GROUP=1&t=978-3-426-22668-1&sortD=-dat&sortA=bez&pr=0&v=plist&submit.x=19&submit.y=24";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_9783426226681;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        //Log.d(TAG, book.toString());

        assertEquals("Totholz : was vergraben ist, ist nicht vergessen",
                     book.getString(DBKey.TITLE, null));
        assertEquals("9783426226681", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("1308358113", book.getString(Identifier.SID_DNB, null));

        assertEquals("deu", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2024-06", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("378", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Krimis, Thriller, Spionage", book.getString(DBKey.GENRE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        TestCase.assertEquals(1, allPublishers.size());

        TestCase.assertEquals("Knaur", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Föhr", authors.get(0).getFamilyName());
        assertEquals("Andreas", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());

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
    public void parse9783734163296()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://katalog.dnb.de/DE/list.html?key=all&key.GROUP=1&t=9783734163296&sortD=-dat&sortA=bez&v=plist&submit.x=24&submit.y=20&pr=0";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_9783734163296;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        //Log.d(TAG, book.toString());

        assertEquals("Teurer Sieg", book.getString(DBKey.TITLE, null));
        assertEquals("9783734163296", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("1272077195", book.getString(Identifier.SID_DNB, null));

        assertEquals("deu", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2023", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("747", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Science Fiction, Fantasy", book.getString(DBKey.GENRE, null));
        assertEquals("Lesser evil", book.getString(DBKey.TITLE_ORIGINAL_LANG, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        TestCase.assertEquals(1, allPublishers.size());

        TestCase.assertEquals("Blanvalet", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Zahn", author.getFamilyName());
        assertEquals("Timothy", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());
        author = authors.get(1);
        assertEquals("Kasprzak", author.getFamilyName());
        assertEquals("Andreas", author.getGivenNames());
        assertEquals(Author.TYPE_TRANSLATOR, author.getType());

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

    /**
     * Does live lookups to the website !
     */
    @Test
    public void parseMulti()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader = "https://katalog.dnb.de/DE/list.html?key=all&key.GROUP=1&t=asimov+nemesis&sortD=-dat&sortA=bez&pr=0&v=plist&submit.x=0&submit.y=0";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.dnb_multi_asimov_nemesis;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();

        searchEngine.parseMultiResult(context, document, new boolean[]{true, false}, book);
        //Log.d(TAG, book.toString());

        assertEquals("Nemesis", book.getString(DBKey.TITLE, null));
        assertEquals("9783641285166", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("1278243054", book.getString(Identifier.SID_DNB, null));

        assertEquals("deu", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2023", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("528", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("ebook", book.getString(DBKey.FORMAT, null));
        assertEquals("Science Fiction", book.getString(DBKey.GENRE, null));

        // We parsed correctly, "Werk: NN", presumably bad-data on the site?
        assertEquals("NN", book.getString(DBKey.TITLE_ORIGINAL_LANG, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        TestCase.assertEquals(1, allPublishers.size());

        TestCase.assertEquals("Heyne Verlag", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Asimov", authors.get(0).getFamilyName());
        assertEquals("Isaac", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());

        assertEquals("Holicki", authors.get(1).getFamilyName());
        assertEquals("Irene", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_TRANSLATOR, authors.get(1).getType());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());

        assertEquals("Roboter und Foundation – der Zyklus", series.get(0).getTitle());
        assertEquals("17", series.get(0).getNumber());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Dnb.getPreferenceKey()
                                          + "_9783641285166_0_.jpg"));
    }
}
