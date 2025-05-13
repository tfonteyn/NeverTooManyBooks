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
package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

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
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("MissingJavadoc")
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private KbNlSearchEngine searchEngine;
    private SAXParser saxParser;


    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (KbNlSearchEngine) EngineId.KbNl.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        try {
            saxParser = SAXParserFactory.newInstance().newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
            fail(e.getMessage());
        }
    }

    @NonNull
    private Book getBook(final int resId)
            throws IOException, SAXException {
        final Book book = new Book();
        final KbNlBookHandler bookHandler = new KbNlBookHandler(searchEngine, book);
        // getContext(): we want the "androidTest" context which is where our test resources live
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext()
                                                     .getResources().openRawResource(resId)) {
            saxParser.parse(in, bookHandler);
        }
        return book;
    }

    @Test
    public void parseList01()
            throws IOException, SAXException {

        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test.R.raw.kbnl_list_1);

        assertEquals("SHW?FRST=1", book.getString(KbNlHandlerBase.BKEY_SHOW_URL, null));
    }

    @Test
    public void parseBook01()
            throws IOException, SAXException {

        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test.R.raw.kbnl_book_1);
        Log.d(TAG, book.toString());

        assertEquals("De Foundation", book.getString(DBKey.TITLE, null));

        assertEquals("1983", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("9022953351", book.getString(DBKey.ISBN, null));
        assertEquals("833191217", book.requireIdentifierValue(Identifier.SID_KBNL));

        assertEquals("geb.", book.getString(DBKey.FORMAT, null));
        assertEquals("156", book.getString(DBKey.PAGES, null));
        assertEquals("nld", book.getString(DBKey.LANGUAGE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Bruna", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertFalse(authors.isEmpty());
        Optional<String> oIv;
        Author author;

        author = authors.get(0);
        Assert.assertEquals("Ozimov", author.getFamilyName());
        Assert.assertEquals("Isaak Judovič", author.getGivenNames());
        assertEquals("1920", author.getBirthDate().orElse(null));
        assertEquals("1992", author.getDeathDate().orElse(null));
        Assert.assertEquals(Author.TYPE_WRITER, author.getType());
        oIv = author.getIdentifierValue(Identifier.SID_KBNL);
        assertTrue(oIv.isPresent());
        Assert.assertEquals("068561504", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000122590564", oIv.get());

        author = authors.get(1);
        Assert.assertEquals("Kröner", author.getFamilyName());
        Assert.assertEquals("Jack", author.getGivenNames());
        assertEquals("1920", author.getBirthDate().orElse(null));
        assertEquals("1997", author.getDeathDate().orElse(null));
        Assert.assertEquals(Author.TYPE_CONTRIBUTOR, author.getType());
        oIv = author.getIdentifierValue(Identifier.SID_KBNL);
        assertTrue(oIv.isPresent());
        Assert.assertEquals("072822333", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000047024908", oIv.get());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertFalse(series.isEmpty());
        final Series expectedSeries;
        expectedSeries = new Series("Foundation-trilogie");
        assertEquals(expectedSeries, series.get(0));
    }

    @Test
    public void parseComic()
            throws IOException, SAXException {

        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test.R.raw.kbnl_comic_1);

        assertEquals("De buitengewone reis", book.getString(DBKey.TITLE, null));
        assertEquals("9789463731454", book.getString(DBKey.ISBN, null));
        assertEquals("422449148", book.requireIdentifierValue(Identifier.SID_KBNL));

        assertEquals("2019", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("48", book.getString(DBKey.PAGES, null));
        assertEquals("nld", book.getString(DBKey.LANGUAGE, null));
        assertEquals("gekleurde illustraties", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Dark Dragon Books", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(4, authors.size());

        Optional<String> oIv;
        Author author;

        author = authors.get(0);
        assertEquals("Camboni", author.getFamilyName());
        assertEquals("Silvio", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        assertEquals("1967", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertNull(author.getRealAuthor());
        oIv = author.getIdentifierValue(Identifier.SID_KBNL);
        assertTrue(oIv.isPresent());
        Assert.assertEquals("299374009", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000000958826", oIv.get());

        author = authors.get(1);
        assertEquals("Filippi", author.getFamilyName());
        assertEquals("Denis-Pierre", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());
        assertEquals("1972", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertNull(author.getRealAuthor());
        oIv = author.getIdentifierValue(Identifier.SID_KBNL);
        assertTrue(oIv.isPresent());
        Assert.assertEquals("296443417", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000117583538", oIv.get());

        author = authors.get(2);
        assertEquals("Yvan", author.getFamilyName());
        assertEquals("Gaspard", author.getGivenNames());
        assertEquals(Author.TYPE_COLORIST, author.getType());
        assertEquals("1988", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertNull(author.getRealAuthor());
        oIv = author.getIdentifierValue(Identifier.SID_KBNL);
        assertTrue(oIv.isPresent());
        Assert.assertEquals("418237638", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000465172890", oIv.get());

        author = authors.get(3);
        assertEquals("Manfré", author.getFamilyName());
        assertEquals("Mariella", author.getGivenNames());
        assertEquals(Author.TYPE_TRANSLATOR, author.getType());
        assertNull(author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertNull(author.getRealAuthor());
        oIv = author.getIdentifierValue(Identifier.SID_KBNL);
        assertTrue(oIv.isPresent());
        Assert.assertEquals("377277630", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000443816392", oIv.get());
        author = new Author("", "");

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        final Series series;

        series = allSeries.get(0);
        assertEquals("De buitengewone reis", series.getTitle());
        assertEquals("1", series.getNumber());
    }

    @Test
    public void MultiResult()
            throws SearchException, CredentialsException, StorageException {

        // this will first hit a multi-result page, take the first book, and fetch that.
        final Book book = ((SearchEngine.ByIsbn) searchEngine)
                .searchByIsbn(context, "9020612476", new boolean[]{false, false});

        verify9020612476(book);
    }

    @Test
    public void parseOldBook()
            throws IOException, SAXException {
        // Test an "old" book where the data is rather unstructured.
        // The parser will do a best-effort.
        final Book book = getBook(com.hardbacknutter.nevertoomanybooks.test.R.raw.kbnl_old_book);

        verify9020612476(book);
    }

    private static void verify9020612476(@NonNull final Book book) {
        assertEquals("De Discus valt aan", book.getString(DBKey.TITLE, null));
        assertEquals("9020612476", book.getString(DBKey.ISBN, null));
        assertEquals("428377971", book.requireIdentifierValue(Identifier.SID_KBNL));

        assertEquals("1973", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("157", book.getString(DBKey.PAGES, null));
        assertEquals("nld", book.getString(DBKey.LANGUAGE, null));
        assertEquals("zw. ill", book.getString(DBKey.COLOR, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(2, allPublishers.size());

        assertEquals("Kluitman", allPublishers.get(0).getName());
        assertEquals("Koninklijke Bibliotheek", allPublishers.get(1).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Optional<String> oIv;
        Author author;

        author = authors.get(0);
        assertEquals("Feenstra", author.getFamilyName());
        assertEquals("Ruurd", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());
        assertEquals("1904", author.getBirthDate().orElse(null));
        assertEquals("1974", author.getDeathDate().orElse(null));
        assertNull(author.getRealAuthor());
        oIv = author.getIdentifierValue(Identifier.SID_KBNL);
        assertTrue(oIv.isPresent());
        Assert.assertEquals("068852002", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000021733650", oIv.get());

        author = authors.get(1);
        assertEquals("van Straaten", author.getFamilyName());
        assertEquals("Gerard", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        assertEquals("1924", author.getBirthDate().orElse(null));
        assertEquals("2011", author.getDeathDate().orElse(null));
        assertNull(author.getRealAuthor());
        oIv = author.getIdentifierValue(Identifier.SID_KBNL);
        assertTrue(oIv.isPresent());
        Assert.assertEquals("068862334", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000368889806", oIv.get());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());
        final Series expectedSeries;
        expectedSeries = new Series("Discus-serie");
        assertEquals(expectedSeries, series.get(0));
    }

    @Test
    public void parseAuthor01() {
        final String s = "Isaak Judovič Ozimov (1920-1992) (ISNI 0000 0001 2259 0564)";
        final Book book = new Book();
        final KbNlBookHandler kbNlBookHandler = new KbNlBookHandler(searchEngine, book);

        final List<CurrentData> currentData = List.of(new CurrentData(s, null));
        kbNlBookHandler.parseAuthor(currentData, 0);

        final List<Author> authors = book.getAuthors();
        assertEquals(1, authors.size());

        Optional<String> oIv;
        final Author author = authors.get(0);

        assertEquals("Ozimov", author.getFamilyName());
        assertEquals("Isaak Judovič", author.getGivenNames());
        assertEquals("1920", author.getBirthDate().orElse(null));
        assertEquals("1992", author.getDeathDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000122590564", oIv.get());
    }
}
