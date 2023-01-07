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
package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KbNlBookHandlerTest
        extends Base {

    private static final String file_list = "/kbnl/kbnl-list-1.xml";
    private static final String file_book_1 = "/kbnl/kbnl-book-1.xml";
    private static final String file_comic_1 = "/kbnl/kbnl-comic-1.xml";
    private static final String file_old_book = "/kbnl/kbnl-old-book.xml";

    private KbNlBookHandler bookHandler;
    private SAXParser saxParser;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        bookHandler = new KbNlBookHandler(context, rawData);
        saxParser = factory.newSAXParser();
    }

    @Test
    void parseList01()
            throws IOException, SAXException {
        try (InputStream in = this.getClass().getResourceAsStream(file_list)) {
            saxParser.parse(in, bookHandler);
        }

        assertEquals("SHW?FRST=1", rawData.getString(KbNlHandlerBase.BKEY_SHOW_URL));
    }


    @Test
    void parseBook01()
            throws IOException, SAXException {

        try (InputStream in = this.getClass().getResourceAsStream(file_book_1)) {
            saxParser.parse(in, bookHandler);
        }

        assertEquals("De Foundation", rawData.getString(DBKey.TITLE));

        assertEquals("1983", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("9022953351", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("geb.", rawData.getString(DBKey.FORMAT));
        assertEquals("156", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Bruna", allPublishers.get(0).getName());

        final List<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertFalse(authors.isEmpty());
        Author expectedAuthor;
        expectedAuthor = new Author("Ozimov", "Isaak Judovič");
        assertEquals(expectedAuthor, authors.get(0));
        expectedAuthor = new Author("Kröner", "Jack");
        assertEquals(expectedAuthor, authors.get(1));

        final List<Series> series = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(series);
        assertFalse(series.isEmpty());
        final Series expectedSeries;
        expectedSeries = new Series("Foundation-trilogie");
        assertEquals(expectedSeries, series.get(0));
    }

    @Test
    void parseComic()
            throws IOException, SAXException {

        try (InputStream in = this.getClass().getResourceAsStream(file_comic_1)) {
            saxParser.parse(in, bookHandler);
        }

        assertEquals("De buitengewone reis", rawData.getString(DBKey.TITLE));

        assertEquals("2019", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("9789463731454", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("paperback", rawData.getString(DBKey.FORMAT));
        assertEquals("48", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Dark Dragon Books", allPublishers.get(0).getName());


        final List<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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

        final List<Series> series = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(series);
        assertFalse(series.isEmpty());
        final Series expectedSeries;
        expectedSeries = new Series("De buitengewone reis");
        expectedSeries.setNumber("1");
        assertEquals(expectedSeries, series.get(0));

    }

    @Test
    void parseOldBook()
            throws IOException, SAXException {
        // Test an "old" book where the data is rather unstructured.
        // The parser will do a best-effort.
        try (InputStream in = this.getClass().getResourceAsStream(file_old_book)) {
            saxParser.parse(in, bookHandler);
        }

        assertEquals("De Discus valt aan", rawData.getString(DBKey.TITLE));
        assertEquals("1973", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("9020612476", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("157", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("nld", rawData.getString(DBKey.LANGUAGE));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(2, allPublishers.size());

        assertEquals("Kluitman", allPublishers.get(0).getName());
        assertEquals("Koninklijke Bibliotheek", allPublishers.get(1).getName());

        final List<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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

        final List<Series> series = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(series);
        assertEquals(1, series.size());
        final Series expectedSeries;
        expectedSeries = new Series("Discus-serie");
        assertEquals(expectedSeries, series.get(0));
    }
}
