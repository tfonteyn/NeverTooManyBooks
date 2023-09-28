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

package com.hardbacknutter.nevertoomanybooks.searchengines.bookfinder;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** @noinspection MissingJavadoc */
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";

    private BookFinderSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup();

        searchEngine = (BookFinderSearchEngine) EngineId.BookFinder.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    public void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.bookfinder.com/isbn/9780441020348/?st=sr&ac=qr&mode=basic&author=&title=&isbn=978-0-441-02034-8&lang=en&destination=gb&currency=USD&binding=*&keywords=&publisher=&min_year=&max_year=&minprice=&maxprice=";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bookfinder_9780441020348;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Rule 34", book.getString(DBKey.TITLE, null));
        assertEquals("9780441020348", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2011-01-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("English", book.getString(DBKey.LANGUAGE, null));
        assertEquals(3.75f, book.getFloat(DBKey.RATING, realNumberParser), 0);

        assertEquals("<b>\"The most spectacular science fiction writer of recent years"
                     + "\" (Vernor Vinge, author of <i>Rainbows End</i>)"
                     + " presents a near-future thriller. <br><br>\n"
                     + "  Detective Inspector Liz Kavanaugh is head of the Rule 34 Squad,"
                     + " monitoring the Internet to determine whether people are engaging in"
                     + " harmless fantasies or illegal activities."
                     + " Three ex-con spammers have been murdered, and Liz must uncover the"
                     + " link between them before these homicides go viral.<br><br></b>",
                     book.getString(DBKey.DESCRIPTION));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Ace", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Stross", authors.get(0).getFamilyName());
        assertEquals("Charles", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        final List<String> covers = book.getCoverFileSpecList(0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BookFinder.getPreferenceKey()
                                          + "_9780441020348_0_.jpg"));
    }
}
