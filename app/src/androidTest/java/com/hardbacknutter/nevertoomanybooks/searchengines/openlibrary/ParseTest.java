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
package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private OpenLibrarySearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (OpenLibrarySearchEngine) EngineId.OpenLibrary.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @NonNull
    private Book getBook(final int resId)
            throws IOException, StorageException, SearchException {
        final Book book = new Book();

        try (InputStream is = InstrumentationRegistry.getInstrumentation().getContext()
                                                     .getResources().openRawResource(resId)) {
            assertNotNull(is);
            final String response = searchEngine.readResponseStream(is);
            searchEngine.handleResponse(context, response, new boolean[]{true, true}, book);
        }
        return book;
    }

    @Test
    public void parse()
            throws IOException, SearchException, StorageException {

        // wget -O openlibrary_9780980200447 https://openlibrary.org/api/books?jscmd=data&format=json&bibkeys=ISBN:9780980200447

        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test
                                          .R.raw.openlibrary_9780980200447);

        assertNotNull(book);
        assertFalse(book.isEmpty());

        assertEquals("Slow reading", book.getString(DBKey.TITLE, null));
        assertEquals("9780980200447", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL22853304M", book.getString(DBKey.SID_OPEN_LIBRARY, null));
        assertEquals("2008054742", book.getString(DBKey.SID_LCCN, null));
        assertEquals(8071257L, book.getLong(DBKey.SID_LIBRARY_THING));
        assertEquals(6383507L, book.getLong(DBKey.SID_GOODREADS_BOOK));
        assertEquals("098020044X", book.getString(DBKey.SID_ASIN, null));
        assertEquals("297222669", book.getString(DBKey.SID_OCLC, null));

        assertEquals("Includes bibliographical references and index.",
                     book.getString(DBKey.DESCRIPTION, null));
        assertEquals("92", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("2009-03-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));


        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Litwin Books", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Miedema", authors.get(0).getFamilyName());
        assertEquals("John", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        final List<TocEntry> tocs = book.getToc();
        assertNotNull(tocs);
        assertEquals(5, tocs.size());

        assertEquals("The personal nature of slow reading", tocs.get(0).getTitle());
        assertEquals("Slow reading in an information ecology", tocs.get(1).getTitle());
        assertEquals("The slow movement and slow reading", tocs.get(2).getTitle());
        assertEquals("The psychology of slow reading", tocs.get(3).getTitle());
        assertEquals("The practice of slow reading.", tocs.get(4).getTitle());

        assertEquals("Miedema", tocs.get(0).getPrimaryAuthor().getFamilyName());
        assertEquals("John", tocs.get(0).getPrimaryAuthor().getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_9780980200447_0_.jpg"));

    }

    @Test
    public void parse2()
            throws IOException, SearchException, StorageException {

        // wget -O openlibrary_9780734418227.json https://openlibrary.org/api/books?jscmd=data&format=json&bibkeys=ISBN:9780734418227

        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test
                                          .R.raw.openlibrary_9780734418227);

        assertNotNull(book);
        assertFalse(book.isEmpty());

        assertEquals("Wundersmith", book.getString(DBKey.TITLE, null));
        assertEquals("9780734418227", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL47304760M", book.getString(DBKey.SID_OPEN_LIBRARY, null));
        assertEquals("Source title: Wundersmith: The Calling of Morrigan Crow",
                     book.getString(DBKey.DESCRIPTION, null));
        assertEquals("473", book.getString(DBKey.PAGE_COUNT, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Lothian Children's Books", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(0, authors.size());

        // "cover": {"small": "https://covers.openlibrary.org/b/id/13769253-S.jpg", "medium": "https://covers.openlibrary.org/b/id/13769253-M.jpg", "large": "https://covers.openlibrary.org/b/id/13769253-L.jpg"}}}
        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_9780734418227_0_.jpg"));
    }
}
