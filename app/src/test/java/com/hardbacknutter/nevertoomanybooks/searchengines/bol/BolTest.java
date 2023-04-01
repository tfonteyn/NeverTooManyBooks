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

package com.hardbacknutter.nevertoomanybooks.searchengines.bol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
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

public class BolTest
        extends JSoupBase {

    private static final String TAG = "BolTest";
    private static final String UTF_8 = "UTF-8";
    private BolSearchEngine searchEngine;
    private Book book;

    @BeforeEach
    public void setup()
            throws Exception {
        super.setup();
        book = new Book(BundleMock.create());
        searchEngine = (BolSearchEngine) EngineId.Bol.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    /**
     * be/nl + dutch book
     */
    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://www.bol.com/be/nl/p/alter-ego/9300000135231911/?s2a=";
        final String filename = "/bol/9789044652901-be-nl-dutch.html";

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        //System.out.println(book);

        assertEquals("Alter Ego", book.getString(DBKey.TITLE, null));
        assertEquals("9789044652901", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2023-03-28", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("400", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser));

        final ArrayList<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Prometheus", allPublishers.get(0).getName());

        final ArrayList<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Author author = authors.get(0);
        assertEquals("Verhoef", author.getFamilyName());
        assertEquals("Esther", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());
    }

    /**
     * be/fr + dutch book
     */
    @Test
    void parse01fr()
            throws SearchException, IOException, CredentialsException, StorageException {
        setLocale(searchEngine.getLocale(context));
        final String locationHeader = "https://www.bol.com/be/fr/p/alter-ego/9300000135231911/?s2a=";
        final String filename = "/bol/9789044652901-nl-fr.html";

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(filename, UTF_8, locationHeader);
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        System.out.println(book);
        // there won't be a title!

//        assertEquals("Alter Ego", book.getString(DBKey.TITLE, null));
//        assertEquals("9789044652901", book.getString(DBKey.BOOK_ISBN, null));
//        assertEquals("2023-03-28", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
//        assertEquals("400", book.getString(DBKey.PAGE_COUNT, null));
//        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
//        assertEquals("nl", book.getString(DBKey.LANGUAGE, null));
//        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser));
//
//        final ArrayList<Publisher> allPublishers = book.getPublishers();
//        assertNotNull(allPublishers);
//        assertEquals(1, allPublishers.size());
//        assertEquals("Prometheus", allPublishers.get(0).getName());
//
//        final ArrayList<Author> authors = book.getAuthors();
//        assertNotNull(authors);
//        assertEquals(1, authors.size());
//
//        final Author author = authors.get(0);
//        assertEquals("Verhoef", author.getFamilyName());
//        assertEquals("Esther", author.getGivenNames());
//        assertEquals(Author.TYPE_UNKNOWN, author.getType());
    }

}
