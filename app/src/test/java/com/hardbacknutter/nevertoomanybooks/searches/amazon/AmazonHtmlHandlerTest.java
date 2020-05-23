/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.amazon;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.CommonSetup;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AmazonHtmlHandlerTest
        extends CommonSetup {

    private SearchEngine mSearchEngine;

    @BeforeEach
    public void setUp() {
        super.setUp();
        mSearchEngine = new AmazonSearchEngine();
        mSearchEngine.setCaller(new DummyCaller());
    }

    @Test
    void parse01() {
        setLocale(Locale.UK);

        final String locationHeader = "https://www.amazon.co.uk/gp/product/0575090677";
        final String filename = "/amazon/0575090677.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, "UTF-8", locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        final AmazonHtmlHandler handler = new AmazonHtmlHandler(mSearchEngine, mContext, doc);
        // we've set the doc, so no internet download will be done.
        final boolean[] fetchThumbnail = {false, false};
        mRawData = handler.parseDoc(fetchThumbnail, mRawData);

        assertFalse(mRawData.isEmpty());

        System.out.println(mRawData);

        assertEquals("Bone Silence", mRawData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("978-0575090675", mRawData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("30 Jan. 2020", mRawData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("608", mRawData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Hardcover", mRawData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("eng", mRawData.getString(DBDefinitions.KEY_LANGUAGE));
        assertEquals(14.49d, mRawData.getDouble(DBDefinitions.KEY_PRICE_LISTED));
        assertEquals("GBP", mRawData.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));

        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Reynolds", authors.get(0).getFamilyName());
        assertEquals("Alastair", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
    }

    @Test
    void parse02() {
        setLocale(Locale.UK);
        final String locationHeader = "https://www.amazon.co.uk/gp/product/1473210208";
        final String filename = "/amazon/1473210208.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, "UTF-8", locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        final AmazonHtmlHandler handler = new AmazonHtmlHandler(mSearchEngine, mContext, doc);

        // we've set the doc, so no internet download will be done.
        final boolean[] fetchThumbnail = {false, false};
        mRawData = handler.parseDoc(fetchThumbnail, mRawData);

        assertFalse(mRawData.isEmpty());

        System.out.println(mRawData);

        assertEquals("The Medusa Chronicles", mRawData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("978-1473210202", mRawData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("12 Jan. 2017", mRawData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("336", mRawData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Paperback", mRawData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("eng", mRawData.getString(DBDefinitions.KEY_LANGUAGE));
        assertEquals(5.84d, mRawData.getDouble(DBDefinitions.KEY_PRICE_LISTED));
        assertEquals("GBP", mRawData.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));

        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Gollancz", allPublishers.get(0).getName());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Reynolds", authors.get(0).getFamilyName());
        assertEquals("Alastair", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
        assertEquals("Baxter", authors.get(1).getFamilyName());
        assertEquals("Stephen", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(1).getType());
    }
}
