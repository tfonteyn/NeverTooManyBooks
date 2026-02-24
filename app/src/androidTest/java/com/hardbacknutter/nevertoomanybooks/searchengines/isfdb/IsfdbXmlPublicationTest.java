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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ENHANCE: this is experimental code. Parsing works, but reporting EOF is dodgy
 */
@SuppressWarnings("LongLine")
class IsfdbXmlPublicationTest
        extends BaseDBTest {

    private static final String TAG = "IsfdbXmlPublicationTest";

    private IsfdbSearchEngine searchEngine;
    private MoneyParser moneyParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (IsfdbSearchEngine) EngineId.Isfdb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        final SharedPreferences preferences = ServiceLocator.getInstance().getSharedPreferences();
        // Override the default 'false'
        preferences.edit().putBoolean(IsfdbSearchEngine.PK_SERIES_FROM_TOC, true).apply();
        final boolean b = preferences.getBoolean(IsfdbSearchEngine.PK_SERIES_FROM_TOC, false);
        assertTrue(b);

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        moneyParser = new MoneyParser(siteLocale, allLocales);
    }

    @Test
    void singleByExtId()
            throws ParserConfigurationException, IOException, SAXException {
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_425189;

        final IsfdbPublicationListHandler listHandler =
                new IsfdbPublicationListHandler(context, searchEngine,
                                                new boolean[]{true}, 1);

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final SAXParser parser = factory.newSAXParser();
        // getContext(): we want the "androidTest" context which is where our test resources live
        try (InputStream is = InstrumentationRegistry.getInstrumentation().getContext()
                                                     .getResources().openRawResource(resId)) {
            // The ISFDB site returns XML with the encoding "iso-8859-1"
            // i.e. with <?xml version="1.0" encoding="iso-8859-1" ?>
            // but for Android seems to ignore this and defaults to UTF-8
            // So wrap in InputSource and manually set the encoding.
            final InputSource inputSource = new InputSource(is);
            inputSource.setEncoding("iso-8859-1");

            // The parser is EXPECTED to throw a new SAXException(new EOFException())
            // when it is done. This is working FINE in the actual app code.
            // However, for a reason not understood, this test will always throw EOFException
            // instead of the expected SAXException.
            parser.parse(inputSource, listHandler);

        } catch (@NonNull final SAXException | EOFException e) {
            Log.e(TAG, "", e);
            // java.io.EOFException
            //at com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbPublicationListHandler.endElement(IsfdbPublicationListHandler.java:249)
            //at org.apache.harmony.xml.ExpatParser.endElement(ExpatParser.java:160)
            //at org.apache.harmony.xml.ExpatParser.appendBytes(Native Method)
            //at org.apache.harmony.xml.ExpatParser.parseFragment(ExpatParser.java:517)
            //at org.apache.harmony.xml.ExpatParser.parseDocument(ExpatParser.java:478)
            //at org.apache.harmony.xml.ExpatReader.parse(ExpatReader.java:316)
            //at org.apache.harmony.xml.ExpatReader.parse(ExpatReader.java:279)
            //at javax.xml.parsers.SAXParser.parse(SAXParser.java:390)
            //at com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbXmlPublicationTest.singleByExtId(IsfdbXmlPublicationTest.java:110)
            //at java.lang.reflect.Method.invoke(Native Method)
            //at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
            // ...
        }

        final List<Book> result = listHandler.getResult();
        //Log.d(TAG, result.toString());

        assertEquals(1, result.size());

        final Book book = result.get(0);
        assertNotNull(book);

        assertEquals("Triplanetary", book.getString(DBKey.TITLE, null));
        assertEquals("0491001576", book.getString(DBKey.ISBN, null));
        assertEquals("425189", book.requireIdentifierValue(Identifier.SID_ISFDB));
        assertEquals("16190406", book.requireIdentifierValue(Identifier.SID_OCLC));

        assertEquals("eng", book.getString(DBKey.LANGUAGE, null));
        assertEquals("hc", book.getString(DBKey.FORMAT, null));
        assertEquals("1971-02-15", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("254", book.getString(DBKey.PAGES, null));
        assertEquals("Data from OCLC. Publication date from Amazon.co.uk",
                     book.getString(DBKey.DESCRIPTION, null));

        assertEquals("NOVEL", book.getString(IsfdbSearchEngine.SiteField.BOOK_TYPE, null));
        assertEquals("TRPLNTRLBK1971", book.getString(IsfdbSearchEngine.SiteField.BOOK_TAG, null));

        assertEquals(1.75d, book.getDouble(DBKey.PRICE_LISTED,
                                           moneyParser.getRealNumberParser()), 0);
        assertEquals(MoneyParser.GBP, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        final List<Publisher> publishers = book.getPublishers();
        assertEquals(1, publishers.size());
        final Publisher publisher = publishers.get(0);
        assertEquals("W. H. Allen", publisher.getName());

        final List<Author> authors = book.getAuthors();
        assertEquals(2, authors.size());

        Author author;
        author = authors.get(0);
        assertEquals("Smith", author.getFamilyName());
        assertEquals("E. E. 'Doc'", author.getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());
        author = authors.get(1);
        assertEquals("Reilly", author.getFamilyName());
        assertEquals("Ken", author.getGivenNames());
        assertEquals(AuthorRole.COVER_ARTIST, author.getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Isfdb.getPreferenceKey()
                                          + "_0491001576_0_.jpg"));
    }
}
