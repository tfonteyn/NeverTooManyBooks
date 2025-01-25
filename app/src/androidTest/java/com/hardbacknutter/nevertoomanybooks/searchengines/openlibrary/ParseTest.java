/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.util.Log;

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
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

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

    /**
     * The site is rather unstable.... half of the time it fails to serve covers
     * and returns a "500".
     * Ignore any failing tests for covers...
     */
    private static final String SITE_COVERS_BROKEN_AGAIN = "site covers broken again";

    private OpenLibrarySearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException, SearchException, CredentialsException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (OpenLibrarySearchEngine) EngineId.OpenLibrary.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(context, true);

        // 2024-11-07: this is not functional yet... the username/password are not stored
        // when running as a test
        //        assertTrue("Username/password must be configured",
        //                   OpenLibraryAuth.getUsername(context).isPresent());
        //        // Force a login.
        //        PreferenceManager.getDefaultSharedPreferences(context)
        //                         .edit()
        //                         .putBoolean(OpenLibrarySearchEngine.PK_LOGIN_TO_SEARCH, true)
        //                         .apply();
        //        // Uses the above setting whether to login or not
        //        searchEngine.login(context);
    }

    @Test
    public void parse1()
            throws IOException, StorageException, SearchException, CredentialsException {
        // https://openlibrary.org/search.json?q=9780980200447&fields=key,editions
        // https://openlibrary.org/books/OL22853304M.json
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.openlibrary_9780980200447);

        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, book);

        assertNotNull(book);
        assertFalse(book.isEmpty());
        Log.d(TAG, book.toString());

        assertEquals("Slow reading", book.getString(DBKey.TITLE, null));
        assertEquals("9780980200447", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL22853304M", book.requireIdentifierValue(Identifier.SID_OPEN_LIBRARY));
        assertEquals("2008054742", book.requireIdentifierValue(Identifier.SID_LCCN));
        assertEquals("8071257", book.requireIdentifierValue(Identifier.SID_LIBRARY_THING));
        assertEquals("6383507", book.requireIdentifierValue(Identifier.SID_GOODREADS_BOOK));
        assertEquals("098020044X", book.requireIdentifierValue(Identifier.SID_ASIN));
        assertEquals("297222669", book.requireIdentifierValue(Identifier.SID_OCLC));
        assertEquals("4LQU1YwhY6kC", book.requireIdentifierValue(Identifier.SID_GOOGLE));

        assertEquals("Includes bibliographical references and index.",
                     book.getString(DBKey.DESCRIPTION, null));
        assertEquals("92", book.getString(DBKey.PAGES, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2009-03-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals(Book.ContentType.Collection.getId(), book.getLong(DBKey.BOOK_CONTENT_TYPE));

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

        // same for all toc entries
        assertEquals("Miedema", tocs.get(0).getPrimaryAuthor().getFamilyName());
        assertEquals("John", tocs.get(0).getPrimaryAuthor().getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, tocs.get(0).getPrimaryAuthor().getType());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(SITE_COVERS_BROKEN_AGAIN, 1, covers.size());
        //   "covers": [
        //    5546156
        //  ],
        assertTrue(covers.get(0).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_5546156_0_"));
    }

    @Test
    public void parse2()
            throws IOException, StorageException, SearchException, CredentialsException {

        // https://openlibrary.org/search.json?q=9780734418227&fields=key,editions
        // https://openlibrary.org/books/OL47304760M.json
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.openlibrary_9780734418227);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, book);

        assertNotNull(book);
        assertFalse(book.isEmpty());
        Log.d(TAG, book.toString());

        assertEquals("Wundersmith", book.getString(DBKey.TITLE, null));
        assertEquals("9780734418227", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL47304760M", book.requireIdentifierValue(Identifier.SID_OPEN_LIBRARY));

        assertEquals("Source title: Wundersmith: The Calling of Morrigan Crow",
                     book.getString(DBKey.DESCRIPTION, null));
        assertEquals("473", book.getString(DBKey.PAGES, null));
        assertEquals("paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));

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
        assertEquals(SITE_COVERS_BROKEN_AGAIN, 1, covers.size());
        // "covers": [
        //  13769253
        //  ],
        assertTrue(covers.get(0).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_13769253_0_"));
    }

    @Test
    public void parse3()
            throws IOException, StorageException, SearchException, CredentialsException {
        // https://openlibrary.org/search.json?q=9780141346830&fields=key,editions
        // https://openlibrary.org/books/OL28508809M.json
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.openlibrary_9780141346830);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, book);

        assertNotNull(book);
        assertFalse(book.isEmpty());
        Log.d(TAG, book.toString());

        assertEquals("Percy Jackson and the Battle of the Labyrinth",
                     book.getString(DBKey.TITLE, null));
        assertEquals("9780141346830", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL28508809M", book.requireIdentifierValue(Identifier.SID_OPEN_LIBRARY));

        assertEquals("2013", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("352", book.getString(DBKey.PAGES, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));

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

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(SITE_COVERS_BROKEN_AGAIN, 2, covers.size());
        //   "covers": [
        //    14615097,
        //    14615096,
        //    13011694
        //  ],
        assertTrue(covers.get(0).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_14615097_0_"));
        assertTrue(covers.get(1).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_14615096_1_"));
    }

    @Test
    public void parse4()
            throws IOException, StorageException, SearchException, CredentialsException {
        // https://openlibrary.org/search.json?q=9783103971422&fields=key,editions
        // https://openlibrary.org/books/OL36696710M.json
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.openlibrary_9783103971422);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, book);

        assertNotNull(book);
        assertFalse(book.isEmpty());
        Log.d(TAG, book.toString());

        assertEquals("Autokorrektur", book.getString(DBKey.TITLE, null));
        assertEquals("9783103971422", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL36696710M", book.requireIdentifierValue(Identifier.SID_OPEN_LIBRARY));
        assertEquals("1244449636", book.requireIdentifierValue(Identifier.SID_DNB));
        assertEquals("lzexzgEACAAJ", book.requireIdentifierValue(Identifier.SID_GOOGLE));
        assertEquals("1282184385", book.requireIdentifierValue(Identifier.SID_OCLC));

        assertEquals("2022-02-09", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("272", book.getString(DBKey.PAGES, null));
        assertEquals("ger", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals(Book.ContentType.Collection.getId(), book.getLong(DBKey.BOOK_CONTENT_TYPE));
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

        final List<TocEntry> tocs = book.getToc();
        assertNotNull(tocs);
        assertEquals(23, tocs.size());

        assertEquals("Bin ich der Wandel – oder warte ich auf ihn?", tocs.get(0).getTitle());
        assertEquals("Mobilität", tocs.get(1).getTitle());
        assertEquals("Was hat sich durch das Auto verdndert?", tocs.get(2).getTitle());
        assertEquals("#Autokorrektur-Fakten", tocs.get(3).getTitle());
        assertEquals("»Nicht-männliche« Mobilität", tocs.get(4).getTitle());
        assertEquals("Privilegien", tocs.get(5).getTitle());
        assertEquals("Lobbyismus", tocs.get(6).getTitle());
        assertEquals("Für eine wahlfreie Mobilität", tocs.get(7).getTitle());
        assertEquals("Raum", tocs.get(8).getTitle());
        assertEquals("Die Entwicklung des Raums", tocs.get(9).getTitle());
        assertEquals("Die autogerechte Stadt", tocs.get(10).getTitle());
        assertEquals("Ländlicher Raum", tocs.get(11).getTitle());
        assertEquals("Öffentlicher Raum", tocs.get(12).getTitle());
        assertEquals("Für einen lebenswerten Raum", tocs.get(13).getTitle());
        assertEquals("Mensch", tocs.get(14).getTitle());
        assertEquals("Menschen, die nicht Auto fahren wollen", tocs.get(15).getTitle());
        assertEquals("Menschen in Familien", tocs.get(16).getTitle());
        assertEquals("Menschen im ländlichen Raum", tocs.get(17).getTitle());
        assertEquals("Menschen in Armut", tocs.get(18).getTitle());
        assertEquals("Menschen mit Einschränkungen", tocs.get(19).getTitle());
        assertEquals("BIPoC und Transpersonen", tocs.get(20).getTitle());
        assertEquals("Menschen, die alt oder krank sind", tocs.get(21).getTitle());
        assertEquals("So geht Mobilität für alle!", tocs.get(22).getTitle());

        // same for all toc entries
        assertEquals("Diehl", tocs.get(0).getPrimaryAuthor().getFamilyName());
        assertEquals("Katja", tocs.get(0).getPrimaryAuthor().getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, tocs.get(0).getPrimaryAuthor().getType());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(SITE_COVERS_BROKEN_AGAIN, 1, covers.size());
        //   "covers": [
        //  12585189
        //]
        assertTrue(covers.get(0).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_12585189_0_"));
    }

    @Test
    public void parse5()
            throws IOException, StorageException, SearchException, CredentialsException {
        // https://openlibrary.org/search.json?q=9780553276329&fields=key,editions
        // https://openlibrary.org/books/OL7824144M.json
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.openlibrary_9780553276329);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);

        assertNotNull(book);
        assertFalse(book.isEmpty());
        Log.d(TAG, book.toString());

        assertEquals("Pacific Vortex!", book.getString(DBKey.TITLE, null));
        assertEquals("9780553276329", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("OL7824144M", book.requireIdentifierValue(Identifier.SID_OPEN_LIBRARY));
        assertEquals("361081", book.requireIdentifierValue(Identifier.SID_GOODREADS_BOOK));
        assertEquals("1182484", book.requireIdentifierValue(Identifier.SID_LIBRARY_THING));

        assertEquals("1984-10-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("1982", book.getString(DBKey.FIRST_PUBLICATION__DATE, null));
        assertEquals("270", book.getString(DBKey.PAGES, null));
        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Mass Market Paperback", book.getString(DBKey.FORMAT, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Bantam", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        Author author;
        assertNotNull(authors);
        assertEquals(1, authors.size());

        author = authors.get(0);
        assertEquals("Cussler", author.getFamilyName());
        assertEquals("Clive", author.getGivenNames());
        assertEquals(Author.TYPE_FOREWORD, author.getType());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        assertEquals("NUMA Files, 1; Dirk Pitt Adventures", allSeries.get(0).getTitle());
        assertEquals("1", allSeries.get(0).getNumber());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(SITE_COVERS_BROKEN_AGAIN, 1, covers.size());
        //  "covers": [
        //    368945
        //  ],
        assertTrue(covers.get(0).contains(EngineId.OpenLibrary.getPreferenceKey()
                                          + "_368945_0_"));
    }
}
