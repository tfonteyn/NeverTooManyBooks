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

package com.hardbacknutter.nevertoomanybooks.searchengines.kitapsec;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser checks without modifying the installed application's database or preferences. */
class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private KitapsecSearchEngine searchEngine;
    private MoneyParser moneyParser;
    private RealNumberParser ratingNumberParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.BookFinder.getConfig().setHttpLoggingEnabled(true);
        searchEngine = EngineId.Kitapsec.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        moneyParser = new MoneyParser(siteLocale, allLocales);

        ratingNumberParser = new RealNumberParser(List.of(siteLocale));
    }

    @Test
    void multiResult()
            throws IOException {
        final String locationHeader = "https://www.kitapsec.com/Arama/index.php?a=9786255701831";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.kitapsec_multiresult_9786255701831;

        final Document document = loadHtml(resId, null, locationHeader);
        final String url = searchEngine.parseMultiResult(document, ISBN.parse("9786255701831"));

        assertEquals("https://www.kitapsec.com/Products"
                     + "/Sarkac-4-Haraf-Yan-Boyamali-Ephesus-Yayinlari-953233.html", url);
    }

    @Test
    void parse01()
            throws IOException, StorageException {

        final String locationHeader = "https://www.kitapsec.com/Products/Sarkac-4-Haraf-Yan-Boyamali-Ephesus-Yayinlari-953233.html";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.kitapsec_9786255701831;

        final Document document = loadHtml(resId, null, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Haraf - Yan Boyamalı", book.getString(DBKey.TITLE, null));
        assertEquals("9786255701831", book.getString(DBKey.ISBN, null));
        assertEquals("624", book.getString(DBKey.PAGES, null));
        assertEquals("tur", book.getString(DBKey.LANGUAGE, null));

        assertPriceListed(book, "619", "TRY", moneyParser);

        assertTrue(book.getString(DBKey.DESCRIPTION).startsWith("Farah, babasının ölümüyle"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Ephesus Yayınları", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        assertEquals("Sarkaç", allSeries.get(0).getTitle());
        assertEquals("4", allSeries.get(0).getNumber());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        assertEquals("Atmaca", authors.get(0).getFamilyName());
        assertEquals("Maral", authors.get(0).getGivenNames());
        assertEquals(AuthorRole.WRITER, authors.get(0).getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Kitapsec.getPreferenceKey()
                                          + "_9786255701831_0_.jpg"));
    }

    @Test
    void parse02()
            throws IOException, StorageException {

        final String locationHeader = "https://www.kitapsec.com/Products/Algernona-Cicekler-Daniel-Keyes-Koridor-Yayincilik-407251.html";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.kitapsec_9786054629862;

        final Document document = loadHtml(resId, null, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Algernona Çiçekler", book.getString(DBKey.TITLE, null));
        assertEquals("9786054629862", book.getString(DBKey.ISBN, null));
        assertEquals("325", book.getString(DBKey.PAGES, null));
        assertEquals("tur", book.getString(DBKey.LANGUAGE, null));
        assertEquals(4.5d, book.getFloat(DBKey.RATING, ratingNumberParser));

        assertPriceListed(book, "360", "TRY", moneyParser);

        assertTrue(book.getString(DBKey.DESCRIPTION)
                       .startsWith("Algernon’a Çiçekler, bugüne dek 27"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Koridor Yayıncılık", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        assertEquals("Keyes", authors.get(0).getFamilyName());
        assertEquals("Daniel", authors.get(0).getGivenNames());
        assertEquals(AuthorRole.WRITER, authors.get(0).getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.Kitapsec.getPreferenceKey()
                                          + "_9786054629862_0_.jpg"));
    }
}
