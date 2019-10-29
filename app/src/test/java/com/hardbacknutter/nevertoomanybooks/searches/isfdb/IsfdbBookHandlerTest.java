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

import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.CommonSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * Test parsing the Jsoup Document for ISFDB data.
 * This is NOT a test for Jsoup.parse.
 * The document used is loaded locally and the result of a page save of 0-413-60010-6
 * <a href="http://www.isfdb.org/cgi-bin/pl.cgi?112781">
 * http://www.isfdb.org/cgi-bin/pl.cgi?112781</a>
 */
class IsfdbBookHandlerTest
        extends CommonSetup {

    /** This test does not 'live' fetch the doc, but this is the correct baseUri. */
    private static final String locationHeader = "http://www.isfdb.org/cgi-bin/pl.cgi?112781";
    /** instead we read this file locally. */
    private static final String filename = "/isfdb-book-1.html";

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();

        // Supposedly we should run two tests; i.e. true/false return.
        when(mSharedPreferences.getBoolean(eq(IsfdbManager.PREFS_SERIES_FROM_TOC), anyBoolean()))
                .thenReturn(true);
    }

    /**
     * We parse the Jsoup Document for ISFDB data. This is NOT a test for Jsoup.parse.
     */
    @Test
    void parse()
            throws IOException {

        Document doc;
        try (InputStream in = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(in);
            doc = Jsoup.parse(in, IsfdbManager.CHARSET_DECODE_PAGE, locationHeader);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        IsfdbBookHandler isfdbBookHandler = new IsfdbBookHandler(doc);
        // we've set the doc, so no internet download will be done.
        Bundle bookData = isfdbBookHandler.parseDoc(mBookData, false, false);

        assertFalse(bookData.isEmpty());

        assertEquals("Like Nothing on Earth", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals(112781, bookData.getLong(DBDefinitions.KEY_EID_ISFDB));
        // On the site: "Date: 1986-10-00". Our code substitutes "00" with "01"
        assertEquals("1986-10-01", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("0413600106", bookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("9780413600103", bookData.getString(IsfdbBookHandler.BookField.ISBN_2));
        assertEquals("Methuen", bookData.getString(DBDefinitions.KEY_PUBLISHER));
        assertEquals(1.95d, bookData.getDouble(DBDefinitions.KEY_PRICE_LISTED));
        assertEquals("GBP", bookData.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        assertEquals("159", bookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("pb", bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("COLLECTION", bookData.getString(IsfdbBookHandler.BookField.BOOK_TYPE));
        assertEquals(Book.TOC_MULTIPLE_WORKS | Book.TOC_MULTIPLE_AUTHORS,
                     bookData.getLong(DBDefinitions.KEY_TOC_BITMASK));

        assertEquals(13665857, bookData.getLong(DBDefinitions.KEY_EID_WORLDCAT));

        assertEquals("Month from Locus1", bookData.getString(DBDefinitions.KEY_DESCRIPTION));

        ArrayList<Author> authors = bookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
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


        ArrayList<TocEntry> toc = bookData.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
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

        //TODO: The test book does not contain
        // publisher series
        // publisher series #
        // series (if 'from-toc'==true)
    }
}
