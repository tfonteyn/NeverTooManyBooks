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

package com.hardbacknutter.nevertoomanybooks.searchengines.googlebooks;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
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
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("MissingJavadoc")
public class Parse2Test
        extends BaseDBTest {

    private static final String TAG = "Parse2Test";

    private GoogleBooks2SearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (GoogleBooks2SearchEngine) EngineId.GoogleBooks.createSearchEngine(context);
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

            final JSONObject document = new JSONObject(response);

            // Grab the first one found
            final JSONObject edition = document.getJSONArray("items")
                                               .getJSONObject(0);
            searchEngine.parse(context, edition,
                               new boolean[]{true, true}, book);
        }
        return book;
    }

    @Test
    public void parse1()
            throws IOException, StorageException {
        // https://www.googleapis.com/books/v1/volumes?q=isbn:9781857989380
        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test
                                          .R.raw.googlebooks_9781857989380);

        assertNotNull(book);
        assertFalse(book.isEmpty());

        //Log.d(TAG, book.toString());

        assertEquals("Flowers for Algernon", book.getString(DBKey.TITLE, null));
        assertEquals("9781857989380", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("64tuPwAACAAJ", book.getString(DBKey.SID_GOOGLE, null));
        assertEquals("en", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2000", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("216", book.getString(DBKey.PAGE_COUNT, null));
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

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        final Author author;
        assertNotNull(authors);
        assertEquals(String.valueOf(authors), 1, authors.size());

        author = authors.get(0);
        assertEquals("Keyes", author.getFamilyName());
        assertEquals("Daniel", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(EngineId.GoogleBooks.getPreferenceKey()
                                          + "_9781857989380_0_"));
    }

    @Test
    public void parse2()
            throws IOException, StorageException {

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        // https://www.googleapis.com/books/v1/volumes?q=isbn:9780007499793
        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test
                                          .R.raw.googlebooks_9780007499793);

        assertNotNull(book);
        assertFalse(book.isEmpty());

        Log.d(TAG, book.toString());

        assertEquals("Space", book.getString(DBKey.TITLE, null));
        assertEquals("9780007499793", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("lDihJsa19_gC", book.getString(DBKey.SID_GOOGLE, null));
        assertEquals("en", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2012-11-22", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("441", book.getString(DBKey.PAGE_COUNT, null));
        assertEquals("ebook", book.getString(DBKey.FORMAT, null));

        assertEquals("2020. Fueled by an insatiable curiosity, Reid Malenfant ventures"
                     + " to the far edge of the solar system, where he discovers a strange artifact"
                     + " left behind by an alien civilization: A gateway that functions as a "
                     + "kind of quantum transporter, allowing virtually instantaneous travel over"
                     + " the vast distances of interstellar space.",
                     book.getString(DBKey.DESCRIPTION, null));

        assertEquals(new Money(BigDecimal.valueOf(5.49d),
                               Currency.getInstance("GBP")),
                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("HarperCollins UK", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        final Author author;
        assertNotNull(authors);
        assertEquals(String.valueOf(authors), 1, authors.size());

        author = authors.get(0);
        assertEquals("Baxter", author.getFamilyName());
        assertEquals("Stephen", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(EngineId.GoogleBooks.getPreferenceKey()
                                          + "_9780007499793_0_"));
    }
}
