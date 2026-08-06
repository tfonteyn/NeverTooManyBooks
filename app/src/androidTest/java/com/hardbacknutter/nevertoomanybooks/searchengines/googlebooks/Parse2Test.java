/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.googlebooks;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Parse2Test
        extends BaseDBTest {

    private static final String TAG = "Parse2Test";

    private GoogleBooksSearchEngine searchEngine;
    private MoneyParser moneyParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (GoogleBooksSearchEngine) EngineId.GoogleBooks.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        moneyParser = new MoneyParser(siteLocale, allLocales);
    }

    @Test
    void parse1()
            throws IOException, StorageException {
        // https://www.googleapis.com/books/v1/volumes?q=isbn:9781857989380
        final Book book = new Book();
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.googlebooks_9781857989380);
        // Grab the first one found
        final JSONObject edition = document.getJSONArray("items").getJSONObject(0);
        searchEngine.parse(context, edition, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Flowers for Algernon", book.getString(DBKey.TITLE, null));
        assertEquals("9781857989380", book.getString(DBKey.ISBN, null));
        assertEquals("64tuPwAACAAJ", book.requireIdentifierValue(Identifier.SID_GOOGLE));

        assertEquals("en", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2000", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("216", book.getString(DBKey.PAGES, null));

        // It's explicitly set as "isEbook=false"
        assertNull(book.getString(DBKey.FORMAT, null));

        assertEquals("The classic novel about a daring experiment in human intelligence"
                     + " Charlie Gordon, IQ 68, is a floor sweeper and the gentle butt of"
                     + " everyone's jokes - until an experiment in the enhancement of human"
                     + " intelligence turns him into a genius. But then Algernon, the mouse"
                     + " whose triumphal experimental tranformation preceded his, fades and"
                     + " dies, and Charlie has to face the possibility that his salvation"
                     + " was only temporary.",
                     book.getString(DBKey.DESCRIPTION, null));

        final List<Tag> bookTags = book.getTags();
        assertEquals(1, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Fiction"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        final Author author;
        assertNotNull(authors);
        assertEquals(1, authors.size(), String.valueOf(authors));

        author = authors.get(0);
        assertEquals("Keyes", author.getFamilyName());
        assertEquals("Daniel", author.getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(EngineId.GoogleBooks.getPreferenceKey()
                                          + "_9781857989380_0_"));
    }

    @Test
    void parse2()
            throws IOException, StorageException {

        // https://www.googleapis.com/books/v1/volumes?q=isbn:9780007499793
        final Book book = new Book();
        final JSONObject document = loadJSONObject(com.hardbacknutter.nevertoomanybooks.test
                                                           .R.raw.googlebooks_9780007499793);
        // Grab the first one found
        final JSONObject edition = document.getJSONArray("items").getJSONObject(0);
        searchEngine.parse(context, edition, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Space", book.getString(DBKey.TITLE, null));
        assertEquals("9780007499793", book.getString(DBKey.ISBN, null));
        assertEquals("lDihJsa19_gC", book.requireIdentifierValue(Identifier.SID_GOOGLE));

        assertEquals("en", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2012-11-22", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("441", book.getString(DBKey.PAGES, null));
        assertEquals("eBook", book.getString(DBKey.FORMAT, null));

        assertEquals("2020. Fueled by an insatiable curiosity, Reid Malenfant ventures"
                     + " to the far edge of the solar system, where he discovers a strange artifact"
                     + " left behind by an alien civilization: A gateway that functions as a "
                     + "kind of quantum transporter, allowing virtually instantaneous travel over"
                     + " the vast distances of interstellar space.",
                     book.getString(DBKey.DESCRIPTION, null));

        assertPriceListed(book, "5.49", MoneyParser.GBP, moneyParser);

        final List<Tag> bookTags = book.getTags();
        assertEquals(1, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Fiction"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("HarperCollins UK", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        final Author author;
        assertNotNull(authors);
        assertEquals(1, authors.size(), String.valueOf(authors));

        author = authors.get(0);
        assertEquals("Baxter", author.getFamilyName());
        assertEquals("Stephen", author.getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(EngineId.GoogleBooks.getPreferenceKey()
                                          + "_9780007499793_0_"));
    }
}
