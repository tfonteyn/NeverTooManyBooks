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
package com.hardbacknutter.nevertoomanybooks.searchengines.isfdb;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/**
 * Test parsing the Jsoup Document for ISFDB single-book data.
 */
@SuppressWarnings("MissingJavadoc")
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private IsfdbSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (IsfdbSearchEngine) EngineId.Isfdb.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        // Override the default 'false'
        preferences.edit().putBoolean(IsfdbSearchEngine.PK_SERIES_FROM_TOC, true).apply();

        final boolean b = preferences.getBoolean(IsfdbSearchEngine.PK_SERIES_FROM_TOC, false);
        assertTrue(b);
    }

    @Test
    public void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?112781";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_112781;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                                               locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Like Nothing on Earth", book.getString(DBKey.TITLE, null));
        assertEquals("0413600106", book.getString(DBKey.ISBN, null));
        assertEquals("9780413600103", book.getString(IsfdbSearchEngine.SiteField.ISBN_2, null));
        assertEquals("112781", book.requireIdentifierValue(Identifier.SID_ISFDB));
        assertEquals("13665857", book.requireIdentifierValue(Identifier.SID_OCLC));

        assertEquals("1986-10", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(1.95d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.GBP, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
        assertEquals("159", book.getString(DBKey.PAGES, null));
        assertEquals("pb", book.getString(DBKey.FORMAT, null));
        assertEquals("COLLECTION", book.getString(IsfdbSearchEngine.SiteField.BOOK_TYPE, null));
        assertEquals(Book.ContentType.Anthology, book.getContentType());

        assertEquals("First published in Great Britain 1975 by Dobson Books Ltd." +
                     " This edition published 1986 by Methuen London Ltd. Month from Locus1",
                     book.getString(DBKey.DESCRIPTION, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Methuen", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Russell", authors.get(0).getFamilyName());
        assertEquals("Eric Frank", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        assertEquals("Oakes", authors.get(1).getFamilyName());
        assertEquals("Terry", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_COVER_ARTIST, authors.get(1).getType());

        // don't do this: we don't take authors from the TOC yet
//        assertEquals("Hugi", authors.get(1).getFamilyName());
//        assertEquals("Maurice G.", authors.get(1).getGivenNames());

        final List<TocEntry> toc = book.getToc();
        assertNotNull(toc);
        //7 • Allamagoosa • (1955) • short story by Eric Frank Russell
        //24 • Hobbyist • (1947) • novelette by Eric Frank Russell
        //65 • The Mechanical Mice • (1941) • novelette by Maurice G. Hugi and Eric Frank Russell
        //95 • Into Your Tent I'll Creep • (1957) • short story by Eric Frank Russell
        //106 • Nothing New • (1955) • short story by Eric Frank Russell
        //119 • Exposure • (1950) • short story by Eric Frank Russell
        //141 • Ultima Thule • (1951) • short story by Eric Frank Russell
        assertEquals(7, toc.size());

        final Optional<Integer> fpd;

        // just check one.
        final TocEntry tocEntry = toc.get(3);
        assertEquals("Into Your Tent I'll Creep", tocEntry.getTitle());
        fpd = tocEntry.getFirstPublicationDate().getYear();
        assertTrue(fpd.isPresent());
        assertEquals(1957, (long) fpd.get());
        assertEquals("Russell", tocEntry.getPrimaryAuthor().getFamilyName());
        assertEquals("Eric Frank", tocEntry.getPrimaryAuthor().getGivenNames());
    }

    @Test
    public void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?431964";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_431964;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                                               locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("Mort", book.getString(DBKey.TITLE, null));
        assertEquals("9781473200104", book.getString(DBKey.ISBN, null));
        assertEquals("1473200105", book.getString(IsfdbSearchEngine.SiteField.ISBN_2, null));
        assertEquals("431964", book.requireIdentifierValue(Identifier.SID_ISFDB));

        assertEquals("2013-11-07", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(9.99d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.GBP, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
        assertEquals("257", book.getString(DBKey.PAGES, null));
        assertEquals("hc", book.getString(DBKey.FORMAT, null));
        assertEquals("NOVEL", book.getString(IsfdbSearchEngine.SiteField.BOOK_TYPE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Pratchett", authors.get(0).getFamilyName());
        assertEquals("Terry", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        assertEquals("McLaren", authors.get(1).getFamilyName());
        assertEquals("Joe", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_COVER_ARTIST, authors.get(1).getType());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(2, series.size());
        // Pub. Series
        assertEquals("The Discworld Collector's Library", series.get(0).getTitle());
        // Series + nr from TOC
        assertEquals("Discworld", series.get(1).getTitle());
        assertEquals("4", series.get(1).getNumber());

        final List<TocEntry> toc = book.getToc();
        assertNotNull(toc);
        assertEquals(1, toc.size());

        final Optional<Integer> fpd;

        final TocEntry tocEntry = toc.get(0);
        assertEquals("Mort", tocEntry.getTitle());
        fpd = tocEntry.getFirstPublicationDate().getYear();
        assertTrue(fpd.isPresent());
        assertEquals(1987, (long) fpd.get());
        assertEquals("Pratchett", tocEntry.getPrimaryAuthor().getFamilyName());
        assertEquals("Terry", tocEntry.getPrimaryAuthor().getGivenNames());
    }

    @Test
    public void parse03()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?542125";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_542125;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                                               locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        assertEquals("The Shepherd's Crown", book.getString(DBKey.TITLE, null));
        assertEquals("9780062429995", book.getString(DBKey.ISBN, null));
        assertEquals("006242999X", book.getString(IsfdbSearchEngine.SiteField.ISBN_2, null));
        assertEquals("542125", book.requireIdentifierValue(Identifier.SID_ISFDB));
        assertEquals("2015943558", book.requireIdentifierValue(Identifier.SID_LCCN));
        assertEquals("B00W2EBY8O", book.requireIdentifierValue(Identifier.SID_ASIN));

        assertEquals("2015-09-01", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals(11.99d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals(MoneyParser.USD, book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
        assertEquals("ebook", book.getString(DBKey.FORMAT, null));
        assertEquals("NOVEL", book.getString(IsfdbSearchEngine.SiteField.BOOK_TYPE, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Harper", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Pratchett", authors.get(0).getFamilyName());
        assertEquals("Terry", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        assertEquals("Tierney", authors.get(1).getFamilyName());
        assertEquals("Jim", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_COVER_ARTIST, authors.get(1).getType());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());
        assertEquals("Discworld", series.get(0).getTitle());
        assertEquals("41", series.get(0).getNumber());

        final List<TocEntry> toc = book.getToc();
        assertNotNull(toc);
        assertEquals(2, toc.size());

        Optional<Integer> fpd;

        TocEntry tocEntry = toc.get(0);
        assertEquals("The Shepherd's Crown", tocEntry.getTitle());
        fpd = tocEntry.getFirstPublicationDate().getYear();
        assertTrue(fpd.isPresent());
        assertEquals(2015, (long) fpd.get());
        assertEquals("Pratchett", tocEntry.getPrimaryAuthor().getFamilyName());
        assertEquals("Terry", tocEntry.getPrimaryAuthor().getGivenNames());
        tocEntry = toc.get(1);
        assertEquals("Afterword (The Shepherd's Crown)", tocEntry.getTitle());
        fpd = tocEntry.getFirstPublicationDate().getYear();
        assertTrue(fpd.isPresent());
        assertEquals(2015, (long) fpd.get());
        assertEquals("Wilkins", tocEntry.getPrimaryAuthor().getFamilyName());
        assertEquals("Rob", tocEntry.getPrimaryAuthor().getGivenNames());
    }

    @Test
    public void parse04()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.isfdb.org/cgi-bin/pl.cgi?808391";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.isfdb_808391;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                                               locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        // Log.d(TAG, book.toString());

        // We're only interested in the price field to check if the Locale is working as expected.
        assertEquals(7.0d, book.getDouble(DBKey.PRICE_LISTED, realNumberParser), 0);
        assertEquals("DEM", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));
    }
}
