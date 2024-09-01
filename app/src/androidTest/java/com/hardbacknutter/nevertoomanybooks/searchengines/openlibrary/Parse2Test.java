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
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test for the newer {@link OpenLibrary2SearchEngine} implementation.
 * <p>
 * This test will obviously fail if {@link EngineId#OpenLibrary}
 * is not configured to use {@link OpenLibrary2SearchEngine}.
 */
@SuppressWarnings("MissingJavadoc")
public class Parse2Test
        extends BaseDBTest {

    private static final String TAG = "Parse2Test";

    private OpenLibrary2SearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (OpenLibrary2SearchEngine) EngineId.OpenLibrary.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @NonNull
    private Book getBook(final int resId)
            throws IOException, StorageException {
        final Book book = new Book();

        // getContext(): we want the "androidTest" context which is where our test resources live
        try (InputStream is = InstrumentationRegistry.getInstrumentation().getContext()
                                                     .getResources().openRawResource(resId)) {
            assertNotNull(is);
            final String response = searchEngine.readResponseStream(is);
            searchEngine.parse(context, new JSONObject(response),
                               new boolean[]{true, true}, book);
        }
        return book;
    }

    @Test
    public void parse1()
            throws IOException, StorageException {
        // https://openlibrary.org/search.json?q=9780980200447&fields=key,editions
        // https://openlibrary.org/books/OL22853304M.json
        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test
                                          .R.raw.openlibrary2_9780980200447);

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
        assertEquals("Paperback", book.getString(DBKey.FORMAT));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Litwin Books", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        Author author;
        assertNotNull(authors);
        assertEquals(String.valueOf(authors), 3, authors.size());

        author = authors.get(0);
        assertEquals("Miedema", author.getFamilyName());
        assertEquals("John", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = authors.get(1);
        assertEquals("Miedema.", author.getFamilyName());
        assertEquals("John", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = authors.get(2);
        assertEquals("Ekholm", author.getFamilyName());
        assertEquals("C.", author.getGivenNames());
        assertEquals(Author.TYPE_COVER_ARTIST, author.getType());


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
        assertEquals(Author.TYPE_UNKNOWN, tocs.get(0).getPrimaryAuthor().getType());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_5546156_0_"));
    }

    @Test
    public void parse2()
            throws IOException, StorageException {

        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test
                                          .R.raw.openlibrary2_9780734418227);

        assertNotNull(book);
        assertFalse(book.isEmpty());

        assertEquals("Wundersmith", book.getString(DBKey.TITLE, null));
        assertEquals("9780734418227", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL47304760M", book.getString(DBKey.SID_OPEN_LIBRARY, null));
        assertEquals("Source title: Wundersmith: The Calling of Morrigan Crow",
                     book.getString(DBKey.DESCRIPTION, null));
        assertEquals("473", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("paperback", book.getString(DBKey.FORMAT));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Lothian Children's Books", allPublishers.get(0).getName());

        // There are NO authors
        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(0, authors.size());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());
        assertEquals("Nevermoor", allSeries.get(0).getTitle());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
    }

    @Test
    public void parse3()
            throws IOException, StorageException {
        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test
                                          .R.raw.openlibrary2_9780141346830);

        assertNotNull(book);
        assertFalse(book.isEmpty());

        assertEquals("Percy Jackson and the Battle of the Labyrinth",
                     book.getString(DBKey.TITLE, null));
        assertEquals("9780141346830", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL28508809M", book.getString(DBKey.SID_OPEN_LIBRARY, null));
        assertEquals("2013", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("352", book.getString(DBKey.PAGE_COUNT, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Puffin", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Riordan", authors.get(0).getFamilyName());
        assertEquals("Rick", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        //   "covers": [
        //    14615097,
        //    14615096,
        //    13011694
        //  ],
        List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_14615097_0_"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_14615096_1_"));
    }


    @Test
    public void parse4()
            throws IOException, StorageException {
        // https://openlibrary.org/search.json?q=9783103971422&fields=key,editions
        // https://openlibrary.org/books/OL36696710M.json
        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test
                                          .R.raw.openlibrary2_9783103971422);

        assertNotNull(book);
        assertFalse(book.isEmpty());

        assertEquals("Autokorrektur", book.getString(DBKey.TITLE, null));
        assertEquals("9783103971422", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL36696710M", book.getString(DBKey.SID_OPEN_LIBRARY, null));
        assertEquals("2022-02-09", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("272", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("ger", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("Mit zahlreichen farbigen Illustrationen",
                     book.getString(DBKey.DESCRIPTION, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("S. Fischer Verlag", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        Author author;
        assertNotNull(authors);
        assertEquals(2, authors.size());

        author = authors.get(0);
        assertEquals("Diehl", author.getFamilyName());
        assertEquals("Katja", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        author = authors.get(1);
        assertEquals("Reich", author.getFamilyName());
        assertEquals("Doris", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

        //   "covers": [
        //    14615097,
        //    14615096,
        //    13011694
        //  ],
        List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_12585189_0_"));
    }
}
