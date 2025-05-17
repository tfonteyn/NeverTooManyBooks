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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

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
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings({"MissingJavadoc","LongLine"})
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
        searchEngine.parse(context, document, new boolean[]{true, true}, null, book);

        assertFalse(book.isEmpty());

        Log.d(TAG, book.toString());

        assertEquals("La grande terre", book.getString(DBKey.TITLE, null));
        assertEquals("2840557428", book.getString(DBKey.ISBN, null));
        assertEquals("19401", book.requireIdentifierValue(Identifier.SID_BEDETHEQUE));

        assertEquals("2002-10", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("46", book.getString(DBKey.PAGES, null));

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

    @Test
    public void isbnExactEdition01()
            throws SearchException, CredentialsException, StorageException, IOException {

        // Blondin et Cirage:  Les soucoupes volantes
        final String locationHeader = "https://www.bedetheque.com/BD-Blondin-et-Cirage-Tome-9a1978-01-Les-soucoupes-volantes-18770.html";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bedetheque_multi_edition_blondin_cirage;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, null, book);

        assertFalse(book.isEmpty());

        Log.d(TAG, book.toString());

        assertEquals("Les soucoupes volantes", book.getString(DBKey.TITLE, null));
        assertNull(book.getString(DBKey.ISBN, null));
        assertEquals("18770", book.requireIdentifierValue(Identifier.SID_BEDETHEQUE));

        assertEquals("1956-01", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("64", book.getString(DBKey.PAGES, null));
        assertEquals("Quadrichromie", book.getString(DBKey.COLOR, null));
        assertEquals("fra", book.getString(DBKey.LANGUAGE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Dupuis", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Blondin et Cirage", series.getTitle());
        assertEquals("9", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        Author author = authors.get(0);
        assertEquals("Jijé", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_ARTIST, author.getType());
        assertEquals("1914-01-13", author.getBirthDate().orElse(null));
        assertEquals("1980-06-19", author.getDeathDate().orElse(null));
        assertEquals("367", author.getIdentifierValue(Identifier.SID_BEDETHEQUE).orElse(null));

        author = author.getRealAuthor();
        assertNotNull(author);
        assertEquals("Gillain", author.getFamilyName());
        assertEquals("Joseph", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());
        assertEquals("1914-01-13", author.getBirthDate().orElse(null));
        assertEquals("1980-06-19", author.getDeathDate().orElse(null));
        assertEquals("367", author.getIdentifierValue(Identifier.SID_BEDETHEQUE).orElse(null));

        List<String> coverList;
        coverList = CoverFileSpecArray.getList(book, 0);
        assertNotNull(coverList);
        assertEquals(1, coverList.size());
        String cover;
        cover = coverList.get(0);
        assertTrue(cover.endsWith(searchEngine.getEngineId().getPreferenceKey()
                                  + "__0_.jpg"));
        coverList = CoverFileSpecArray.getList(book, 1);
        assertNotNull(coverList);
        assertEquals(1, coverList.size());
        cover = coverList.get(0);
        assertTrue(cover.endsWith(searchEngine.getEngineId().getPreferenceKey() +
                                  "__1_.jpg"));
    }

    @Test
    public void isbnLaterEdition01()
            throws SearchException, CredentialsException, StorageException, IOException {

        // Blondin et Cirage:  Les soucoupes volantes
        // but a later edition, Collection : Péchés de jeunesse
        final String locationHeader = "https://www.bedetheque.com/BD-Blondin-et-Cirage-Tome-9a1978-01-Les-soucoupes-volantes-18770.html";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bedetheque_multi_edition_blondin_cirage;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true}, "280010578X", book);

        assertFalse(book.isEmpty());

        Log.d(TAG, book.toString());

        assertEquals("Les soucoupes volantes", book.getString(DBKey.TITLE, null));
        assertEquals("280010578X", book.getString(DBKey.ISBN, null));
        assertEquals("9318", book.requireIdentifierValue(Identifier.SID_BEDETHEQUE));

        assertEquals("1978-01", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("45", book.getString(DBKey.PAGES, null));
        assertEquals("Quadrichromie", book.getString(DBKey.COLOR, null));
        assertEquals("fra", book.getString(DBKey.LANGUAGE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Dupuis", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("Blondin et Cirage", series.getTitle());
        assertEquals("9", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author = authors.get(0);
        assertEquals("Jijé", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER | Author.TYPE_ARTIST, author.getType());
        assertEquals("1914-01-13", author.getBirthDate().orElse(null));
        assertEquals("1980-06-19", author.getDeathDate().orElse(null));
        assertEquals("367", author.getIdentifierValue(Identifier.SID_BEDETHEQUE).orElse(null));

        author = author.getRealAuthor();
        assertNotNull(author);
        assertEquals("Gillain", author.getFamilyName());
        assertEquals("Joseph", author.getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, author.getType());
        assertEquals("1914-01-13", author.getBirthDate().orElse(null));
        assertEquals("1980-06-19", author.getDeathDate().orElse(null));
        assertEquals("367", author.getIdentifierValue(Identifier.SID_BEDETHEQUE).orElse(null));

        author = authors.get(1);
        assertEquals("Roque", author.getFamilyName());
        assertEquals("Carlos", author.getGivenNames());
        assertEquals("1936-04-12", author.getBirthDate().orElse(null));
        assertEquals("2006-07-27", author.getDeathDate().orElse(null));
        assertEquals(Author.TYPE_CONTRIBUTOR, author.getType());
        assertEquals("32388", author.getIdentifierValue(Identifier.SID_BEDETHEQUE).orElse(null));

        List<String> coverList;
        coverList = CoverFileSpecArray.getList(book, 0);
        assertNotNull(coverList);
        assertEquals(1, coverList.size());
        String cover;
        cover = coverList.get(0);
        assertTrue(cover.endsWith(searchEngine.getEngineId().getPreferenceKey()
                                  + "_280010578X_0_.jpg"));
        coverList = CoverFileSpecArray.getList(book, 1);
        assertNotNull(coverList);
        assertEquals(1, coverList.size());
        cover = coverList.get(0);
        assertTrue(cover.endsWith(searchEngine.getEngineId().getPreferenceKey() +
                                  "_280010578X_1_.jpg"));
    }
}
