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

package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KbNl2Test
        extends JSoupBase {

    private static final String UTF_8 = "UTF-8";

    private KbNl2SearchEngine searchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        searchEngine = (KbNl2SearchEngine) Site.Type.Data
                .getSite(EngineId.KbNl).getSearchEngine();
        searchEngine.setCaller(new MockCancellable());
    }

    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://webggc.oclc.org/cbs/DB=2.37/SET=13/TTL=1/CMD?" +
                                      "ACT=SRCHA&IKT=1007&SRT=RLV&TRM=90-206-1247-6";
        final String filename = "/kbnl2/kbnl2-list.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, rawData);
        // System.out.println(rawData);
    }

    @Test
    void parse03()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://webggc.oclc.org" +
                                      "/cbs/DB=2.37/SET=12/TTL=1/CMD?" +
                                      "ACT=SRCHA&IKT=1007&SRT=RLV&TRM=9789463731454";
        final String filename = "/kbnl2/kbnl2-comic.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, rawData);
        // System.out.println(rawData);

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
        // yes, twice... this list has NOT been pruned, so this is correct.
        assertEquals(expectedAuthor, authors.get(1));

        expectedAuthor = new Author("Filippi", "Denis-Pierre");
        assertEquals(expectedAuthor, authors.get(2));
        expectedAuthor = new Author("Yvan", "Gaspard");
        assertEquals(expectedAuthor, authors.get(3));
        expectedAuthor = new Author("Manfré", "Mariella");
        assertEquals(expectedAuthor, authors.get(4));

        final List<Series> series = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(series);
        assertFalse(series.isEmpty());
        final Series expectedSeries;
        expectedSeries = new Series("De buitengewone reis");
        expectedSeries.setNumber("1");
        assertEquals(expectedSeries, series.get(0));
    }
}
