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
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searches.CommonSetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AmazonHtmlHandlerTest
        extends CommonSetup {

    @Test
    void parse01() {

        String locationHeader = "https://www.amazon.co.uk/gp/product/0575090677";
        String filename = "/amazon/0575090677.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, "UTF-8", locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        AmazonHtmlHandler handler = new AmazonHtmlHandler(doc);
        // we've set the doc, so no internet download will be done.
        try {
            boolean[] fetchThumbnail = {false, false};
            mBookData = handler.parseDoc(mContext, fetchThumbnail, mBookData);
        } catch (@NonNull final SocketTimeoutException e) {
            fail(e);
        }

        assertFalse(mBookData.isEmpty());

        System.out.println(mBookData);

        assertEquals("Bone Silence", mBookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("978-0575090675", mBookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("30 Jan. 2020", mBookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("608", mBookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Hardcover", mBookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("English", mBookData.getString(DBDefinitions.KEY_LANGUAGE));

        ArrayList<Publisher> allPublishers = mBookData
                .getParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        ArrayList<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Reynolds", authors.get(0).getFamilyName());
        assertEquals("Alastair", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
    }

    @Test
    void parse02() {

        String locationHeader = "https://www.amazon.co.uk/gp/product/1473210208";
        String filename = "/amazon/1473210208.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, "UTF-8", locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        AmazonHtmlHandler handler = new AmazonHtmlHandler(doc);
        // we've set the doc, so no internet download will be done.
        try {
            boolean[] fetchThumbnail = {false, false};
            mBookData = handler.parseDoc(mContext, fetchThumbnail, mBookData);
        } catch (@NonNull final SocketTimeoutException e) {
            fail(e);
        }

        assertFalse(mBookData.isEmpty());

        System.out.println(mBookData);

        assertEquals("The Medusa Chronicles", mBookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("978-1473210202", mBookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("12 Jan. 2017", mBookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("336", mBookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Paperback", mBookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("English", mBookData.getString(DBDefinitions.KEY_LANGUAGE));

        ArrayList<Publisher> allPublishers = mBookData
                .getParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Gollancz", allPublishers.get(0).getName());

        ArrayList<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
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
