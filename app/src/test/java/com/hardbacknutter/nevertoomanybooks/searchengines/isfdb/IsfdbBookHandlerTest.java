/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

import static com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine.PK_SERIES_FROM_TOC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test parsing the Jsoup Document for ISFDB single-book data.
 */
class IsfdbBookHandlerTest
        extends JSoupBase {

    private IsfdbSearchEngine mSearchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        mSearchEngine = (IsfdbSearchEngine) Site.Type.Data
                .getSite(SearchSites.ISFDB).getSearchEngine();
        mSearchEngine.setCaller(new MockCancellable());

        // Override the default 'false'
        mMockPreferences.edit().putBoolean(PK_SERIES_FROM_TOC, true).apply();

        final boolean b = PreferenceManager.getDefaultSharedPreferences(mContext)
                                           .getBoolean(PK_SERIES_FROM_TOC, false);
        assertTrue(b);
    }

    @Test
    void parse01() {
        setLocale(Locale.UK);
        final String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?112781";
        final String filename = "/isfdb/112781.html";

        loadData(mContext, mSearchEngine, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                 locationHeader, filename, new boolean[]{false, false});

        assertEquals("Like Nothing on Earth", mRawData.getString(DBKey.TITLE));
        assertEquals(112781L, mRawData.getLong(DBKey.SID_ISFDB));
        // On the site: "Date: 1986-10-00". Our code substitutes "00" with "01"
        assertEquals("1986-10-01", mRawData.getString(DBKey.DATE_BOOK_PUBLICATION));
        assertEquals("0413600106", mRawData.getString(DBKey.KEY_ISBN));
        assertEquals("9780413600103", mRawData.getString(IsfdbSearchEngine.SiteField.ISBN_2));
        assertEquals(1.95d, mRawData.getDouble(DBKey.PRICE_LISTED));
        assertEquals(Money.GBP, mRawData.getString(DBKey.PRICE_LISTED_CURRENCY));
        assertEquals("159", mRawData.getString(DBKey.PAGES));
        assertEquals("pb", mRawData.getString(DBKey.BOOK_FORMAT));
        assertEquals("COLLECTION", mRawData.getString(IsfdbSearchEngine.SiteField.BOOK_TYPE));
        assertEquals(Book.ContentType.Anthology.value, mRawData.getLong(DBKey.BITMASK_TOC));

        assertEquals("13665857", mRawData.getString(DBKey.SID_OCLC));

        assertEquals("Month from Locus1", mRawData.getString(DBKey.DESCRIPTION));

        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Methuen", allPublishers.get(0).getName());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
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

        final ArrayList<TocEntry> toc = mRawData.getParcelableArrayList(Book.BKEY_TOC_LIST);
        assertNotNull(toc);
        //7 • Allamagoosa • (1955) • short story by Eric Frank Russell
        //24 • Hobbyist • (1947) • novelette by Eric Frank Russell
        //65 • The Mechanical Mice • (1941) • novelette by Maurice G. Hugi and Eric Frank Russell
        //95 • Into Your Tent I'll Creep • (1957) • short story by Eric Frank Russell
        //106 • Nothing New • (1955) • short story by Eric Frank Russell
        //119 • Exposure • (1950) • short story by Eric Frank Russell
        //141 • Ultima Thule • (1951) • short story by Eric Frank Russell
        assertEquals(7, toc.size());
        // just check one.
        final TocEntry entry = toc.get(3);
        assertEquals("Into Your Tent I'll Creep", entry.getTitle());
        assertEquals(1957, entry.getFirstPublicationDate().getYearValue());
        // don't do this, the first pub date is read as a year-string only.
        //assertEquals("1957-01-01", entry.getFirstPublication());
        assertEquals("Russell", entry.getPrimaryAuthor().getFamilyName());
        assertEquals("Eric Frank", entry.getPrimaryAuthor().getGivenNames());
    }

    @Test
    void parse02() {
        setLocale(Locale.UK);
        final String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?431964";
        final String filename = "/isfdb/431964.html";

        loadData(mContext, mSearchEngine, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                 locationHeader, filename, new boolean[]{false, false});

        assertEquals("Mort", mRawData.getString(DBKey.TITLE));
        assertEquals(431964L, mRawData.getLong(DBKey.SID_ISFDB));
        assertEquals("2013-11-07", mRawData.getString(DBKey.DATE_BOOK_PUBLICATION));
        assertEquals("9781473200104", mRawData.getString(DBKey.KEY_ISBN));
        assertEquals("1473200105", mRawData.getString(IsfdbSearchEngine.SiteField.ISBN_2));
        assertEquals(9.99d, mRawData.getDouble(DBKey.PRICE_LISTED));
        assertEquals(Money.GBP, mRawData.getString(DBKey.PRICE_LISTED_CURRENCY));
        assertEquals("257", mRawData.getString(DBKey.PAGES));
        assertEquals("hc", mRawData.getString(DBKey.BOOK_FORMAT));
        assertEquals("NOVEL", mRawData.getString(IsfdbSearchEngine.SiteField.BOOK_TYPE));

        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Pratchett", authors.get(0).getFamilyName());
        assertEquals("Terry", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        assertEquals("McLaren", authors.get(1).getFamilyName());
        assertEquals("Joe", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_COVER_ARTIST, authors.get(1).getType());

        final ArrayList<Series> series = mRawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(series);
        assertEquals(2, series.size());
        // Pub. Series
        assertEquals("The Discworld Collector's Library", series.get(0).getTitle());
        // Series + nr from TOC
        assertEquals("Discworld", series.get(1).getTitle());
        assertEquals("4", series.get(1).getNumber());

        final ArrayList<TocEntry> toc = mRawData.getParcelableArrayList(Book.BKEY_TOC_LIST);
        assertNotNull(toc);
        assertEquals(1, toc.size());
        final TocEntry entry = toc.get(0);
        assertEquals("Mort", entry.getTitle());
        assertEquals(1987, entry.getFirstPublicationDate().getYearValue());
        assertEquals("Pratchett", entry.getPrimaryAuthor().getFamilyName());
        assertEquals("Terry", entry.getPrimaryAuthor().getGivenNames());
    }

    @Test
    void parse03() {
        setLocale(Locale.UK);
        final String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?542125";
        final String filename = "/isfdb/542125.html";

        loadData(mContext, mSearchEngine, IsfdbSearchEngine.CHARSET_DECODE_PAGE,
                 locationHeader, filename, new boolean[]{false, false});

        assertEquals("The Shepherd's Crown", mRawData.getString(DBKey.TITLE));
        assertEquals(542125L, mRawData.getLong(DBKey.SID_ISFDB));
        assertEquals("2015-09-01", mRawData.getString(DBKey.DATE_BOOK_PUBLICATION));
        assertEquals("9780062429995", mRawData.getString(DBKey.KEY_ISBN));
        assertEquals("006242999X", mRawData.getString(IsfdbSearchEngine.SiteField.ISBN_2));
        assertEquals(11.99d, mRawData.getDouble(DBKey.PRICE_LISTED));
        assertEquals(Money.USD, mRawData.getString(DBKey.PRICE_LISTED_CURRENCY));
        assertEquals("ebook", mRawData.getString(DBKey.BOOK_FORMAT));
        assertEquals("NOVEL", mRawData.getString(IsfdbSearchEngine.SiteField.BOOK_TYPE));

        assertEquals("2015943558", mRawData.getString(DBKey.SID_LCCN));
        assertEquals("B00W2EBY8O", mRawData.getString(DBKey.SID_ASIN));

        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Harper", allPublishers.get(0).getName());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Pratchett", authors.get(0).getFamilyName());
        assertEquals("Terry", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        assertEquals("Tierney", authors.get(1).getFamilyName());
        assertEquals("Jim", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_COVER_ARTIST, authors.get(1).getType());

        final ArrayList<Series> series = mRawData.getParcelableArrayList(Book.BKEY_SERIES_LIST);
        assertNotNull(series);
        assertEquals(1, series.size());
        assertEquals("Tiffany Aching", series.get(0).getTitle());
        assertEquals("41", series.get(0).getNumber());

        final ArrayList<TocEntry> toc = mRawData.getParcelableArrayList(Book.BKEY_TOC_LIST);
        assertNotNull(toc);
        assertEquals(2, toc.size());
        TocEntry entry = toc.get(0);
        assertEquals("The Shepherd's Crown", entry.getTitle());
        assertEquals(2015, entry.getFirstPublicationDate().getYearValue());
        assertEquals("Pratchett", entry.getPrimaryAuthor().getFamilyName());
        assertEquals("Terry", entry.getPrimaryAuthor().getGivenNames());
        entry = toc.get(1);
        assertEquals("Afterword (The Shepherd's Crown)", entry.getTitle());
        assertEquals(2015, entry.getFirstPublicationDate().getYearValue());
        assertEquals("Wilkins", entry.getPrimaryAuthor().getFamilyName());
        assertEquals("Rob", entry.getPrimaryAuthor().getGivenNames());
    }
}
