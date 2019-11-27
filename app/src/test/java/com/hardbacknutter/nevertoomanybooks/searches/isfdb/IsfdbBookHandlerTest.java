/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.CommonSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test parsing the Jsoup Document for ISFDB single-book data.
 */
class IsfdbBookHandlerTest
        extends CommonSetup {

    @Test
    void parse01() {

        String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?112781";
        String filename = "/isfdb/112781.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, IsfdbManager.CHARSET_DECODE_PAGE, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        IsfdbBookHandler isfdbBookHandler = new IsfdbBookHandler(mContext, doc);
        // we've set the doc, so no internet download will be done.
        try {
            mBookData = isfdbBookHandler.parseDoc(mBookData, false, false);
        } catch (@NonNull final SocketTimeoutException e) {
            fail(e);
        }

        assertFalse(mBookData.isEmpty());

        assertEquals("Like Nothing on Earth", mBookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals(112781L, mBookData.getLong(DBDefinitions.KEY_EID_ISFDB));
        // On the site: "Date: 1986-10-00". Our code substitutes "00" with "01"
        assertEquals("1986-10-01", mBookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("0413600106", mBookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("9780413600103", mBookData.getString(IsfdbBookHandler.BookField.ISBN_2));
        assertEquals(1.95d, mBookData.getDouble(DBDefinitions.KEY_PRICE_LISTED));
        assertEquals("GBP", mBookData.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        assertEquals("159", mBookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("pb", mBookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("COLLECTION", mBookData.getString(IsfdbBookHandler.BookField.BOOK_TYPE));
        assertEquals(Book.TOC_MULTIPLE_WORKS | Book.TOC_MULTIPLE_AUTHORS,
                     mBookData.getLong(DBDefinitions.KEY_TOC_BITMASK));

        assertEquals("13665857", mBookData.getString(DBDefinitions.KEY_EID_WORLDCAT));

        assertEquals("Month from Locus1", mBookData.getString(DBDefinitions.KEY_DESCRIPTION));

        ArrayList<Publisher> allPublishers = mBookData
                .getParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Methuen", allPublishers.get(0).getName());

        ArrayList<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
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

        ArrayList<TocEntry> toc = mBookData.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
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
        TocEntry entry = toc.get(3);
        assertEquals("Into Your Tent I'll Creep", entry.getTitle());
        assertEquals("1957", entry.getFirstPublication());
        // don't do this, the first pub date is read as a year-string only.
        //assertEquals("1957-01-01", entry.getFirstPublication());
        assertEquals("Russell", entry.getAuthor().getFamilyName());
        assertEquals("Eric Frank", entry.getAuthor().getGivenNames());
    }

    @Test
    void parse02() {

        String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?431964";
        String filename = "/isfdb/431964.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, IsfdbManager.CHARSET_DECODE_PAGE, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        IsfdbBookHandler isfdbBookHandler = new IsfdbBookHandler(mContext, doc);
        // we've set the doc, so no internet download will be done.
        try {
            mBookData = isfdbBookHandler.parseDoc(mBookData, true, false);
        } catch (@NonNull final SocketTimeoutException e) {
            fail(e);
        }

        assertFalse(mBookData.isEmpty());

        assertEquals("Mort", mBookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals(431964L, mBookData.getLong(DBDefinitions.KEY_EID_ISFDB));
        assertEquals("2013-11-07", mBookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("9781473200104", mBookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("1473200105", mBookData.getString(IsfdbBookHandler.BookField.ISBN_2));
        assertEquals(9.99d, mBookData.getDouble(DBDefinitions.KEY_PRICE_LISTED));
        assertEquals("GBP", mBookData.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        assertEquals("257", mBookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("hc", mBookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("NOVEL", mBookData.getString(IsfdbBookHandler.BookField.BOOK_TYPE));

        ArrayList<Publisher> allPublishers = mBookData
                .getParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        ArrayList<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Pratchett", authors.get(0).getFamilyName());
        assertEquals("Terry", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        assertEquals("McLaren", authors.get(1).getFamilyName());
        assertEquals("Joe", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_COVER_ARTIST, authors.get(1).getType());

        ArrayList<Series> series = mBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        assertNotNull(series);
        assertEquals(2, series.size());
        assertEquals("The Discworld Collector's Library", series.get(0).getTitle());
        assertEquals("Discworld", series.get(1).getTitle());
        assertEquals("4", series.get(1).getNumber());

        ArrayList<TocEntry> toc = mBookData.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
        assertNotNull(toc);
        assertEquals(1, toc.size());
        TocEntry entry = toc.get(0);
        assertEquals("Mort", entry.getTitle());
        assertEquals("1987", entry.getFirstPublication());
        assertEquals("Pratchett", entry.getAuthor().getFamilyName());
        assertEquals("Terry", entry.getAuthor().getGivenNames());
    }

    @Test
    void parse03() {

        String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?542125";
        String filename = "/isfdb/542125.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, IsfdbManager.CHARSET_DECODE_PAGE, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        IsfdbBookHandler isfdbBookHandler = new IsfdbBookHandler(mContext, doc);
        // we've set the doc, so no internet download will be done.
        try {
            mBookData = isfdbBookHandler.parseDoc(mBookData, true, false);
        } catch (@NonNull final SocketTimeoutException e) {
            fail(e);
        }

        assertFalse(mBookData.isEmpty());

        assertEquals("The Shepherd's Crown", mBookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals(542125L, mBookData.getLong(DBDefinitions.KEY_EID_ISFDB));
        assertEquals("2015-09-01", mBookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("9780062429995", mBookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("006242999X", mBookData.getString(IsfdbBookHandler.BookField.ISBN_2));
        assertEquals(11.99d, mBookData.getDouble(DBDefinitions.KEY_PRICE_LISTED));
        assertEquals("USD", mBookData.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        assertEquals("ebook", mBookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("NOVEL", mBookData.getString(IsfdbBookHandler.BookField.BOOK_TYPE));

        assertEquals("2015943558", mBookData.getString(DBDefinitions.KEY_EID_LCCN));
        assertEquals("B00W2EBY8O", mBookData.getString(DBDefinitions.KEY_EID_ASIN));

        ArrayList<Publisher> allPublishers = mBookData
                .getParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Harper", allPublishers.get(0).getName());

        ArrayList<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Pratchett", authors.get(0).getFamilyName());
        assertEquals("Terry", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        assertEquals("Tierney", authors.get(1).getFamilyName());
        assertEquals("Jim", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_COVER_ARTIST, authors.get(1).getType());

        ArrayList<Series> series = mBookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        assertNotNull(series);
        assertEquals(1, series.size());
        assertEquals("Tiffany Aching", series.get(0).getTitle());
        assertEquals("41", series.get(0).getNumber());

        ArrayList<TocEntry> toc = mBookData.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
        assertNotNull(toc);
        assertEquals(2, toc.size());
        TocEntry entry = toc.get(0);
        assertEquals("The Shepherd's Crown", entry.getTitle());
        assertEquals("2015", entry.getFirstPublication());
        assertEquals("Pratchett", entry.getAuthor().getFamilyName());
        assertEquals("Terry", entry.getAuthor().getGivenNames());
        entry = toc.get(1);
        assertEquals("Afterword (The Shepherd's Crown)", entry.getTitle());
        assertEquals("2015", entry.getFirstPublication());
        assertEquals("Wilkins", entry.getAuthor().getFamilyName());
        assertEquals("Rob", entry.getAuthor().getGivenNames());
    }
}
