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

package com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);
    }

    @Test
    public void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.lastdodo.nl/nl/items/7323911-de-37ste-parallel";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.lastdodo_7323911_de_37ste_parallel;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true, true, true}, book);
        Log.d(TAG, book.toString());

        assertEquals("De 37ste parallel", book.getString(DBKey.TITLE, null));
        assertEquals("9789463064385", book.getString(DBKey.ISBN, null));
        assertEquals("7323911", book.requireIdentifierValue(Identifier.SID_LAST_DODO_NL));

        assertEquals("2018", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("48", book.getString(DBKey.PAGES, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Gekleurd", book.getString(DBKey.COLOR, null));

        assertEquals(
                "Storyboard: Emem. Ook uitgegeven als beurseditie met stofomslag & gesigneerde en ongenummerde ex libris op het Stripfestival Breda sep 2018/Dutch Comic Con wintereditie nov 2018 op 50 exemplaren (afbeelding 3).",
                book.getString(DBKey.DESCRIPTION, null));

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
        assertEquals(4, authors.size());

        Optional<String> oIv;
        Author author;

        author = authors.get(0);
        assertEquals("Gioux", author.getFamilyName());
        assertEquals("Thierry", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals("1960-05-05", author.getBirthDate().orElse(null));
        assertTrue(author.getTmpPictureFileSpec().get().endsWith("_bedetheque_1949_0_.jpg"));
        assertEquals(2, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_LAST_DODO_NL);
        assertTrue(oIv.isPresent());
        assertEquals("2459", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("1949", oIv.get());

        author = authors.get(1);
        assertEquals("Duval", author.getFamilyName());
        assertEquals("Fred", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1965-01-05", author.getBirthDate().orElse(null));
        assertTrue(author.getTmpPictureFileSpec().get().endsWith("_bedetheque_58_0_.jpg"));
        assertEquals(2, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_LAST_DODO_NL);
        assertTrue(oIv.isPresent());
        assertEquals("4716", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("58", oIv.get());

        author = authors.get(2);
        assertEquals("Produkties", author.getFamilyName());
        assertEquals("Van der Heide", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertEquals(1, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_LAST_DODO_NL);
        assertTrue(oIv.isPresent());
        assertEquals("5446247", oIv.get());

        author = authors.get(3);
        assertEquals("Sayago", author.getFamilyName());
        assertEquals("Nuria", author.getGivenNames());
        assertEquals(AuthorRole.COLORIST, author.getRole());
        assertTrue(author.getTmpPictureFileSpec().get().endsWith("_bedetheque_30795_0_.jpg"));
        assertEquals(2, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_LAST_DODO_NL);
        assertTrue(oIv.isPresent());
        assertEquals("5548155", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("30795", oIv.get());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;
        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(preferenceKey + "_9789463064385_0_"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(preferenceKey + "_9789463064385_1_"));

        covers = CoverFileSpecArray.getList(book, 2);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(preferenceKey + "_9789463064385_2_"));

        covers = CoverFileSpecArray.getList(book, 3);
        assertNotNull(covers);
        assertEquals(0, covers.size());
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
        searchEngine.parse(context, document, new boolean[]{true, true, true, true}, book);
        Log.d(TAG, book.toString());

        assertEquals("Schoot der aarde", book.getString(DBKey.TITLE, null));
        assertEquals("9789463943109", book.getString(DBKey.ISBN, null));
        assertEquals("8838967", book.requireIdentifierValue(Identifier.SID_LAST_DODO_NL));

        assertEquals("2021", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("64", book.getString(DBKey.PAGES, null));
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Gekleurd", book.getString(DBKey.COLOR, null));

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
        assertEquals(3, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Astier", author.getFamilyName());
        assertEquals("Laurent", author.getGivenNames());
        assertEquals(AuthorRole.WRITER | AuthorRole.ARTIST, author.getRole());

        author = authors.get(1);
        assertEquals("Van Tilburgh", author.getFamilyName());
        assertEquals("Dieter", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());

        author = authors.get(2);
        assertEquals("Astier", author.getFamilyName());
        assertEquals("Stéphane", author.getGivenNames());
        assertEquals(AuthorRole.COLORIST, author.getRole());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;
        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(preferenceKey + "_9789463943109_0_"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(preferenceKey + "_9789463943109_1_"));
    }

    /**
     * 3 images
     */
    @Test
    public void parse03()
            throws SearchException, CredentialsException, StorageException, IOException {

        final String locationHeader = "https://www.lastdodo.nl/nl/items/37600-sioban";

        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.lastdodo_2871290733_sioban;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, true, true, true}, book);
        Log.d(TAG, book.toString());

        assertEquals("Sioban", book.getString(DBKey.TITLE, null));
        assertEquals("2871290733", book.getString(DBKey.ISBN, null));
        assertEquals("37600", book.requireIdentifierValue(Identifier.SID_LAST_DODO_NL));

        assertEquals("1993", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));
        assertEquals("Nederlands", book.getString(DBKey.LANGUAGE, null));
        assertEquals("Ongekleurd", book.getString(DBKey.COLOR, null));
        assertEquals("430", book.getString(DBKey.PRINT_RUN, null));

        assertEquals(
                "430 exemplaren genummerd en gesigneerd met een gevouwen kleurenillustratie. + 25 exemplaren niet bestemd voor handel genummerd en gesigneerd.",
                book.getString(DBKey.DESCRIPTION, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Dargaud", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series = allSeries.get(0);
        assertEquals("De Klaagzang van de verloren gewesten", series.getTitle());
        assertEquals("1|a", series.getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Rosinski (Rosek)", author.getFamilyName());
        assertEquals("Grzegorz", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals(1, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_LAST_DODO_NL);
        assertTrue(oIv.isPresent());
        assertEquals("2174", oIv.get());

        author = authors.get(1);
        assertEquals("Dufaux", author.getFamilyName());
        assertEquals("Jean", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1949-06-07", author.getBirthDate().orElse(null));
        assertTrue(author.getTmpPictureFileSpec().get().endsWith("_bedetheque_53_0_.jpg"));
        assertEquals(2, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_LAST_DODO_NL);
        assertTrue(oIv.isPresent());
        assertEquals("2138", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_BEDETHEQUE);
        assertTrue(oIv.isPresent());
        assertEquals("53", oIv.get());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;
        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(preferenceKey + "_2871290733_0_"));

        covers = CoverFileSpecArray.getList(book, 1);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(preferenceKey + "_2871290733_1_"));

        covers = CoverFileSpecArray.getList(book, 2);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).contains(preferenceKey + "_2871290733_2_"));

        covers = CoverFileSpecArray.getList(book, 3);
        assertNotNull(covers);
        assertEquals(0, covers.size());
    }
}
