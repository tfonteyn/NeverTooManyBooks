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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KbNlBookHandlerTest
        extends Base {

    private static final String bookFilename = "/kbnl/kbnl-book-1.xml";
    private static final String comicFilename = "/kbnl/kbnl-comic-1.xml";

    private KbNlBookHandler bookHandler;
    private SAXParser saxParser;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        bookHandler = new KbNlBookHandler(rawData);
        saxParser = factory.newSAXParser();
    }

    @Test
    void parseComic()
            throws IOException, SAXException {

        try (InputStream in = this.getClass().getResourceAsStream(comicFilename)) {
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
        expectedAuthor = Author.from("Silvio Camboni");
        assertEquals(expectedAuthor, authors.get(0));
        expectedAuthor = Author.from("Denis-Pierre Filippi");
        assertEquals(expectedAuthor, authors.get(1));
        expectedAuthor = Author.from("Gaspard Yvan");
        assertEquals(expectedAuthor, authors.get(2));
        expectedAuthor = Author.from("Mariella Manfré");
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
    void parseBook()
            throws IOException, SAXException {

        try (InputStream in = this.getClass().getResourceAsStream(bookFilename)) {
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
        expectedAuthor = Author.from("Isaak Judovič Ozimov");
        assertEquals(expectedAuthor, authors.get(0));
        expectedAuthor = Author.from("Jack Kröner");
        assertEquals(expectedAuthor, authors.get(1));

        final List<Series> series = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(series);
        assertFalse(series.isEmpty());
        final Series expectedSeries;
        expectedSeries = new Series("Foundation-trilogie");
        expectedSeries.setNumber("1");
        assertEquals(expectedSeries, series.get(0));
    }
}
