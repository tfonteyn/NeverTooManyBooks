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
package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenLibrarySearchEngineTest
        extends Base {

    private OpenLibrarySearchEngine mSearchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        mSearchEngine = (OpenLibrarySearchEngine) Site.Type.Data
                .getSite(SearchSites.OPEN_LIBRARY).getSearchEngine();
        mSearchEngine.setCaller(new MockCancellable());
    }

    @Test
    void parse()
            throws IOException, SearchException, StorageException {
        setLocale(Locale.UK);

        // https://openlibrary.org/api/books?jscmd=data&format=json&bibkeys=ISBN:9780980200447

        final String filename = "/openlibrary/9780980200447.json";

        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            final String response = mSearchEngine.readResponseStream(is);
            mSearchEngine.handleResponse(mContext, response, new boolean[]{false, false}, mRawData);
        }

        assertNotNull(mRawData);
        assertFalse(mRawData.isEmpty());

        assertEquals("Slow reading", mRawData.getString(DBKey.TITLE));
        assertEquals("9780980200447", mRawData.getString(DBKey.KEY_ISBN));
        assertEquals("OL22853304M", mRawData.getString(DBKey.SID_OPEN_LIBRARY));
        assertEquals("2008054742", mRawData.getString(DBKey.SID_LCCN));
        assertEquals(8071257L, mRawData.getLong(DBKey.SID_LIBRARY_THING));
        assertEquals(6383507L, mRawData.getLong(DBKey.SID_GOODREADS_BOOK));
        assertEquals("098020044X", mRawData.getString(DBKey.SID_ASIN));
        assertEquals("297222669", mRawData.getString(DBKey.SID_OCLC));

        assertEquals("Includes bibliographical references and index.",
                     mRawData.getString(DBKey.DESCRIPTION));
        assertEquals("92", mRawData.getString(DBKey.PAGES));
        assertEquals("2009-03-01", mRawData.getString(DBKey.DATE_BOOK_PUBLICATION));


        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Litwin Books", allPublishers.get(0).getName());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Miedema", authors.get(0).getFamilyName());
        assertEquals("John", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        final ArrayList<TocEntry> tocs = mRawData.getParcelableArrayList(Book.BKEY_TOC_LIST);
        assertNotNull(tocs);
        assertEquals(5, tocs.size());

        assertEquals("The personal nature of slow reading", tocs.get(0).getTitle());
        assertEquals("Slow reading in an information ecology", tocs.get(1).getTitle());
        assertEquals("The slow movement and slow reading", tocs.get(2).getTitle());
        assertEquals("The psychology of slow reading", tocs.get(3).getTitle());
        assertEquals("The practice of slow reading.", tocs.get(4).getTitle());

        assertEquals("Miedema", tocs.get(0).getPrimaryAuthor().getFamilyName());
        assertEquals("John", tocs.get(0).getPrimaryAuthor().getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());
    }
}
