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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

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
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";

    private BedethequeSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (BedethequeSearchEngine) EngineId.Bedetheque.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    public void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.bedetheque.com"
                                      + "/BD-Fond-du-monde-Tome-6-La-grande-terre-19401.html";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bedetheque_db_fond_du_monde_tome_6_la_grande_terre_19401;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, book, List.of());
        // Log.d(TAG, book.toString());

        assertEquals("La grande terre", book.getString(DBKey.TITLE, null));

        assertEquals("2002-10-01", book.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("2840557428", book.getString(DBKey.BOOK_ISBN, null));
        assertEquals("46", book.getString(DBKey.PAGE_COUNT, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Delcourt", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Le Fond du monde", series.getTitle());
        assertEquals("6", series.getNumber());

        final List<Author> authors = book.getAuthors();
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

        List<String> coverList;
        coverList = CoverFileSpecArray.getList(book, 0);
        assertNotNull(coverList);
        assertEquals(1, coverList.size());
        String cover;
        cover = coverList.get(0);
        assertTrue(cover.endsWith(searchEngine.getEngineId().getPreferenceKey()
                                  + "_2840557428_0_.jpg"));
        coverList = CoverFileSpecArray.getList(book, 1);
        assertNotNull(coverList);
        assertEquals(1, coverList.size());
        cover = coverList.get(0);
        assertTrue(cover.endsWith(searchEngine.getEngineId().getPreferenceKey() +
                                  "_2840557428_1_.jpg"));
    }
}
