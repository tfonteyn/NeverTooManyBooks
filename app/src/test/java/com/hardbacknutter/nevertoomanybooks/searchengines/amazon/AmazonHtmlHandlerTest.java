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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

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
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmazonHtmlHandlerTest
        extends JSoupBase {

    private static final String UTF_8 = "UTF-8";

    private AmazonSearchEngine searchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        searchEngine = (AmazonSearchEngine) Site.Type.Data
                .getSite(EngineId.Amazon).getSearchEngine();
        searchEngine.setCaller(new MockCancellable());
    }

    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(Locale.UK);

        final String locationHeader = "https://www.amazon.co.uk/gp/product/0575090677";
        final String filename = "/amazon/0575090677.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, rawData);
        // System.out.println(rawData);

        assertEquals("Bone Silence", rawData.getString(DBKey.TITLE));
        assertEquals("978-0575090675", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("30 Jan. 2020", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("608", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Hardcover", rawData.getString(DBKey.FORMAT));
        assertEquals("English", rawData.getString(DBKey.LANGUAGE));
        assertEquals(14.49d, rawData.getDouble(DBKey.PRICE_LISTED));
        assertEquals(Money.GBP, rawData.getString(DBKey.PRICE_LISTED_CURRENCY));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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
        final String locationHeader = "https://www.amazon.co.uk/gp/product/1473210208";
        final String filename = "/amazon/1473210208.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, rawData);
        // System.out.println(rawData);

        assertEquals("The Medusa Chronicles", rawData.getString(DBKey.TITLE));
        assertEquals("978-1473210202", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals("12 Jan. 2017", rawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("336", rawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Paperback", rawData.getString(DBKey.FORMAT));
        assertEquals("English", rawData.getString(DBKey.LANGUAGE));
        assertEquals(5.84d, rawData.getDouble(DBKey.PRICE_LISTED));
        assertEquals(Money.GBP, rawData.getString(DBKey.PRICE_LISTED_CURRENCY));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Gollancz", allPublishers.get(0).getName());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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
        final String locationHeader = "https://www.amazon.fr/gp/product/2205057332";
        final String filename = "/amazon/2205057332.html";

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, rawData);
        // System.out.println(rawData);

        assertEquals("Le retour à la terre, 1 : La vraie vie",
                     rawData.getString(DBKey.TITLE));
        assertEquals("978-2205057331", rawData.getString(DBKey.BOOK_ISBN));
        assertEquals(12d, rawData.getDouble(DBKey.PRICE_LISTED));
        assertEquals(Money.EUR, rawData.getString(DBKey.PRICE_LISTED_CURRENCY));

        final ArrayList<Publisher> allPublishers = rawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Dargaud", allPublishers.get(0).getName());

        final ArrayList<Author> authors = rawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Jean-Yves", authors.get(0).getFamilyName());
        assertEquals("Ferri", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
        assertEquals("Manu", authors.get(1).getFamilyName());
        assertEquals("Larcenet", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_ARTIST, authors.get(1).getType());
    }
}
