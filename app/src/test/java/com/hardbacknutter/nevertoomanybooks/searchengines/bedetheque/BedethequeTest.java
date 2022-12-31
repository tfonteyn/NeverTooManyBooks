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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BedethequeTest
        extends JSoupBase {

    private static final String UTF_8 = "UTF-8";

    private BedethequeSearchEngine searchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        searchEngine = (BedethequeSearchEngine) Site.Type.Data
                .getSite(EngineId.Bedetheque).getSearchEngine();
        searchEngine.setCaller(new MockCancellable());
    }

    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://www.bedetheque.com"
                                      + "/BD-Fond-du-monde-Tome-6-La-grande-terre-19401.html";
        final String filename = "/bedetheque/BD-Fond-du-monde-Tome-6-La-grande-terre-19401.html";

        loadData(context, searchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{true, true});

        assertEquals("La grande terre", rawData.getString(DBKey.TITLE));

        assertEquals("2002-10", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("Grand format", rawData.getString(DBKey.FORMAT));
        assertEquals("2840557428", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("46", rawData.getString(DBKey.PAGE_COUNT));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Delcourt", allPublishers.get(0).getName());

        final ArrayList<Series> allSeries = rawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Le Fond du monde", series.getTitle());
        assertEquals("6", series.getNumber());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertEquals(4, authors.size());

        Author author = authors.get(0);
        assertEquals("Corbeyran", author.getFamilyName());
        assertEquals("Éric", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(1);
        assertEquals("Falque", author.getFamilyName());
        assertEquals("Denis", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

        author = authors.get(2);
        assertEquals("Araldi", author.getFamilyName());
        assertEquals("Christophe", author.getGivenNames());
        assertEquals(Author.TYPE_COLORIST, author.getType());

        author = authors.get(3);
        assertEquals("Frémion", author.getFamilyName());
        assertEquals("Yves", author.getGivenNames());
        assertEquals(Author.TYPE_FOREWORD, author.getType());

        assertTrue(rawData.getStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0])
                          .get(0).endsWith("1672259114746_bedetheque_2840557428_0_.jpg"));
    }
}
