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

package com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo;

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
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("MissingJavadoc")
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";

    private LastDodoSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (LastDodoSearchEngine) EngineId.LastDodoNl.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }


    @Test
    public void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.lastdodo.nl/nl/items/7323911-de-37ste-parallel";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.lastdodo_7323911_de_37ste_parallel;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book, null);
        // Log.d(TAG, book.toString());

        assertEquals("De 37ste parallel", book.getString(DBKey.TITLE, null));
        assertEquals("9789463064385", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2018-01-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("48", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Gekleurd", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Silvester", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Hauteville House", series.getTitle());
        assertEquals("14", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author = authors.get(1);
        assertEquals("Duval", author.getFamilyName());
        assertEquals("Fred", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(0);
        assertEquals("Gioux", author.getFamilyName());
        assertEquals("Thierry", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

//        author = authors.get(2);
//        assertEquals("Sayago", author.getFamilyName());
//        assertEquals("Nuria", author.getGivenNames());
//        assertEquals(Author.TYPE_COLORIST, author.getType());
    }

    /**
     * The Series title of this test data is "Venijn, Het" which needs converting to "Het Venijn".
     */
    @Test
    public void parse02()
            throws SearchException, CredentialsException, StorageException, IOException {

        final String locationHeader = "https://www.lastdodo.nl/nl/items/8838967-schoot-der-aarde";

        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.lastdodo_9789463943109_het_venijn;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book, null);
        // Log.d(TAG, book.toString());

        assertEquals("Schoot der aarde", book.getString(DBKey.TITLE, null));
        assertEquals("9789463943109", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("2021-01-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("64", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Gekleurd", book.getString(DBKey.COLOR, null));
        assertEquals("8838967", book.getString(DBKey.SID_LAST_DODO_NL, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Daedalus", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Het Venijn", series.getTitle());
        assertEquals("3", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Author author = authors.get(0);
        assertEquals("Astier", author.getFamilyName());
        assertEquals("Laurent", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_ARTIST, author.getType());
    }

}
