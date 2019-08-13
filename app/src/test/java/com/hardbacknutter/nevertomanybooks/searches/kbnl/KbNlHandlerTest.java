/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.searches.kbnl;

import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertomanybooks.BundleMock;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.entities.Author;
import com.hardbacknutter.nevertomanybooks.entities.Series;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KbNlHandlerTest {

    private static final String bookFilename = "/kbnl-book-1.xml";
    private static final String comicFilename = "/kbnl-comic-1.xml";

    @Mock
    Bundle mBookData;

    private KbNlHandler mHandler;
    private SAXParser mParser;

    @BeforeEach
    void setUp()
            throws ParserConfigurationException, SAXException {
        MockitoAnnotations.initMocks(this);
        mBookData = BundleMock.mock();

        SAXParserFactory factory = SAXParserFactory.newInstance();
        mHandler = new KbNlHandler(mBookData);
        mParser = factory.newSAXParser();
    }

    @Test
    void parseComic()
            throws IOException, SAXException {

        try (InputStream in = this.getClass().getResourceAsStream(comicFilename)) {
            mParser.parse(in, mHandler);
        }

        assertEquals("De buitengewone reis", mBookData.getString(DBDefinitions.KEY_TITLE));

        assertEquals("2019", mBookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("9789463731454", mBookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("paperback", mBookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("48", mBookData.getString(DBDefinitions.KEY_PAGES));

        assertEquals("Dark Dragon Books", mBookData.getString(DBDefinitions.KEY_PUBLISHER));


        List<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        Author expectedAuthor;
        expectedAuthor = Author.fromString("Silvio Camboni");
        assertEquals(expectedAuthor, authors.get(0));
        expectedAuthor = Author.fromString("Denis-Pierre Filippi");
        assertEquals(expectedAuthor, authors.get(1));
        expectedAuthor = Author.fromString("Gaspard Yvan");
        assertEquals(expectedAuthor, authors.get(2));
        expectedAuthor = Author.fromString("Mariella Manfré");
        assertEquals(expectedAuthor, authors.get(3));

        List<Series> series = mBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        Series expectedSeries;
        expectedSeries = new Series("De buitengewone reis");
        expectedSeries.setNumber("1");
        assertEquals(expectedSeries, series.get(0));

    }

    @Test
    void parseBook()
            throws IOException, SAXException {

        try (InputStream in = this.getClass().getResourceAsStream(bookFilename)) {
            mParser.parse(in, mHandler);
        }

        assertEquals("De Foundation", mBookData.getString(DBDefinitions.KEY_TITLE));

        assertEquals("1983", mBookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("9022953351", mBookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("geb.", mBookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("156", mBookData.getString(DBDefinitions.KEY_PAGES));

        assertEquals("Bruna", mBookData.getString(DBDefinitions.KEY_PUBLISHER));

        List<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        Author expectedAuthor;
        expectedAuthor = Author.fromString("Isaak Judovič Ozimov");
        assertEquals(expectedAuthor, authors.get(0));
        expectedAuthor = Author.fromString("Jack Kröner");
        assertEquals(expectedAuthor, authors.get(1));

        List<Series> series = mBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        Series expectedSeries;
        expectedSeries = new Series("Foundation-trilogie");
        expectedSeries.setNumber("1");
        assertEquals(expectedSeries, series.get(0));

    }
}