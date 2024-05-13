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
package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@SuppressWarnings("MissingJavadoc")
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private KbNlSearchEngine searchEngine;
    private SAXParser saxParser;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (KbNlSearchEngine) EngineId.KbNl.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        try {
            saxParser = SAXParserFactory.newInstance().newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
            fail(e.getMessage());
        }
    }

    @NonNull
    private Book getBook(final int resId)
            throws IOException, SAXException {
        final Book book = new Book();
        final KbNlBookHandler bookHandler = new KbNlBookHandler(searchEngine, book);
        // getContext(): we want the "androidTest" context which is where our test resources live
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext()
                                                     .getResources().openRawResource(resId)) {
            saxParser.parse(in, bookHandler);
        }
        return book;
    }

    @Test
    public void parseList01()
            throws IOException, SAXException {

        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test.R.raw.kbnl_list_1);

        assertEquals("SHW?FRST=1", book.getString(KbNlHandlerBase.BKEY_SHOW_URL, null));
    }

    @Test
    public void parseBook01()
            throws IOException, SAXException {

        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test.R.raw.kbnl_book_1);

        assertEquals("De Foundation", book.getString(DBKey.TITLE, null));

        assertEquals("1983", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("9022953351", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("geb.", book.getString(DBKey.FORMAT, null));
        assertEquals("156", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("nld", book.getString(DBKey.LANGUAGE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Bruna", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertFalse(authors.isEmpty());
        Author expectedAuthor;
        expectedAuthor = new Author("Ozimov", "Isaak Judovič");
        assertEquals(expectedAuthor, authors.get(0));
        expectedAuthor = new Author("Kröner", "Jack");
        assertEquals(expectedAuthor, authors.get(1));

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertFalse(series.isEmpty());
        final Series expectedSeries;
        expectedSeries = new Series("Foundation-trilogie");
        assertEquals(expectedSeries, series.get(0));
    }

    @Test
    public void parseComic()
            throws IOException, SAXException {

        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test.R.raw.kbnl_comic_1);

        assertEquals("De buitengewone reis", book.getString(DBKey.TITLE, null));

        assertEquals("2019", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("9789463731454", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("48", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("nld", book.getString(DBKey.LANGUAGE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Dark Dragon Books", allPublishers.get(0).getName());


        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertFalse(authors.isEmpty());
        Author expectedAuthor;
        expectedAuthor = new Author("Camboni", "Silvio");
        assertEquals(expectedAuthor, authors.get(0));
        expectedAuthor = new Author("Filippi", "Denis-Pierre");
        assertEquals(expectedAuthor, authors.get(1));
        expectedAuthor = new Author("Yvan", "Gaspard");
        assertEquals(expectedAuthor, authors.get(2));
        expectedAuthor = new Author("Manfré", "Mariella");
        assertEquals(expectedAuthor, authors.get(3));

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertFalse(series.isEmpty());
        final Series expectedSeries;
        expectedSeries = new Series("De buitengewone reis");
        expectedSeries.setNumber("1");
        assertEquals(expectedSeries, series.get(0));

    }

    @Test
    public void parseOldBook()
            throws IOException, SAXException {
        // Test an "old" book where the data is rather unstructured.
        // The parser will do a best-effort.
        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test.R.raw.kbnl_old_book);

        verify9020612476(book);
    }

    static void verify9020612476(@NonNull final Book book) {
        assertEquals("De Discus valt aan", book.getString(DBKey.TITLE, null));
        assertEquals("1973", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("9020612476", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("157", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("nld", book.getString(DBKey.LANGUAGE, null));
        assertEquals("zw. ill", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(2, allPublishers.size());

        assertEquals("Kluitman", allPublishers.get(0).getName());
        assertEquals("Koninklijke Bibliotheek", allPublishers.get(1).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());
        Author expectedAuthor;

        expectedAuthor = new Author("Feenstra", "Ruurd");
        expectedAuthor.setType(Author.TYPE_WRITER);
        assertEquals(expectedAuthor, authors.get(0));
        assertEquals(expectedAuthor.getType(), authors.get(0).getType());

        expectedAuthor = new Author("van Straaten", "Gerard");
        expectedAuthor.setType(Author.TYPE_ARTIST);
        assertEquals(expectedAuthor, authors.get(1));
        assertEquals(expectedAuthor.getType(), authors.get(1).getType());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());
        final Series expectedSeries;
        expectedSeries = new Series("Discus-serie");
        assertEquals(expectedSeries, series.get(0));
    }
}
