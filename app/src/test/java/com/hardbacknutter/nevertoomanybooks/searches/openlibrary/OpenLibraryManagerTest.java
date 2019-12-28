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
package com.hardbacknutter.nevertoomanybooks.searches.openlibrary;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.hardbacknutter.nevertoomanybooks.BundleMock;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class OpenLibraryManagerTest {

    @Mock
    protected Bundle mBookData;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        mBookData = BundleMock.mock();
    }

    @Test
    void parse() {

        // https://openlibrary.org/api/books?jscmd=data&format=json&bibkeys=ISBN:9780980200447

        String filename = "/openlibrary/9780980200447.json";

        JSONObject json;

        OpenLibraryManager m = new OpenLibraryManager();
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            String response = m.readResponseStream(is);
            json = new JSONObject(response);
            boolean[] fetchThumbnail = {false, false};
            mBookData = m.handleResponse(mBookData, json, fetchThumbnail);

        } catch (@NonNull final IOException | JSONException e) {
            fail(e);
        }

        assertNotNull(mBookData);
        assertFalse(mBookData.isEmpty());

        assertEquals("Slow reading",
                     mBookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("9780980200447", mBookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("OL22853304M", mBookData.getString(DBDefinitions.KEY_EID_OPEN_LIBRARY));
        assertEquals("2008054742", mBookData.getString(DBDefinitions.KEY_EID_LCCN));
        assertEquals(8071257L, mBookData.getLong(DBDefinitions.KEY_EID_LIBRARY_THING));
        assertEquals(6383507L, mBookData.getLong(DBDefinitions.KEY_EID_GOODREADS_BOOK));
        assertEquals("098020044X", mBookData.getString(DBDefinitions.KEY_EID_ASIN));
        assertEquals("297222669", mBookData.getString(DBDefinitions.KEY_EID_WORLDCAT));

        assertEquals("Includes bibliographical references and index.",
                     mBookData.getString(DBDefinitions.KEY_DESCRIPTION));
        assertEquals("92", mBookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("March 2009", mBookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));


        ArrayList<Publisher> allPublishers = mBookData
                .getParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Litwin Books", allPublishers.get(0).getName());

        ArrayList<Author> authors = mBookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Miedema", authors.get(0).getFamilyName());
        assertEquals("John", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

        ArrayList<TocEntry> tocs = mBookData.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
        assertNotNull(tocs);
        assertEquals(5, tocs.size());

        assertEquals("The personal nature of slow reading", tocs.get(0).getTitle());
        assertEquals("Slow reading in an information ecology", tocs.get(1).getTitle());
        assertEquals("The slow movement and slow reading", tocs.get(2).getTitle());
        assertEquals("The psychology of slow reading", tocs.get(3).getTitle());
        assertEquals("The practice of slow reading.", tocs.get(4).getTitle());

        assertEquals("Miedema", tocs.get(0).getAuthor().getFamilyName());
        assertEquals("John", tocs.get(0).getAuthor().getGivenNames());
        assertEquals(Author.TYPE_UNKNOWN, authors.get(0).getType());

    }
}
