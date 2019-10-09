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
package com.hardbacknutter.nevertoomanybooks.searches.stripinfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.hardbacknutter.nevertoomanybooks.BundleMock;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class stripinfo {

    /** After mapping, the expected format (presuming the test runs in english Locale). */
    private static final String bookType_paperback = "Paperback";
    /** After mapping, the expected format (presuming the test runs in english Locale). */
    private static final String bookType_hardcover = "Hardcover";

    @Mock
    Context mContext;
    @Mock
    SharedPreferences mSharedPreferences;
    @Mock
    Resources mResources;
    @Mock
    Configuration mConfiguration;

    @Mock
    Bundle mBundle;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        mBundle = BundleMock.mock();
        mContext = mock(Context.class);
        mResources = mock(Resources.class);
        mSharedPreferences = mock(SharedPreferences.class);
        mConfiguration = mock(Configuration.class);

        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.createConfigurationContext(any())).thenReturn(mContext);
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        when(mResources.getConfiguration()).thenReturn(mConfiguration);

        when(mContext.getString(R.string.book_format_paperback)).thenReturn(bookType_paperback);
        when(mContext.getString(R.string.book_format_hardcover)).thenReturn(bookType_hardcover);
    }

    @Test
    void parse01()
            throws IOException {

        String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                + "/336348_Hauteville_House_14_De_37ste_parallel";
        String filename = "/stripinfo-9789463064385.html";

        Document doc;
        try (InputStream in = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(in);
            doc = Jsoup.parse(in, "UTF-8", locationHeader);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        StripInfoBookHandler stripInfoBookHandler = new StripInfoBookHandler(doc);
        // we've set the doc, so no internet download will be done.
        Bundle bookData = stripInfoBookHandler.parseDoc(mBundle, false);

        assertFalse(bookData.isEmpty());
        System.out.println(bookData);

        assertEquals("De 37ste parallel", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("9789463064385", bookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("2018", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("48", bookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Hardcover", bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("Nederlands", bookData.getString(DBDefinitions.KEY_LANGUAGE));

        assertEquals("Silvester", bookData.getString(DBDefinitions.KEY_PUBLISHER));

        ArrayList<Series> allSeries = bookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("Hauteville House", series.getTitle());
        assertEquals("14", series.getNumber());

        ArrayList<Author> allAuthors = bookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        assertNotNull(allAuthors);
        assertEquals(3, allAuthors.size());

        Author author = allAuthors.get(0);
        assertEquals("Duval", author.getFamilyName());
        assertEquals("Fred", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = allAuthors.get(1);
        assertEquals("Gioux", author.getFamilyName());
        assertEquals("Thierry", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

        author = allAuthors.get(2);
        assertEquals("Sayago", author.getFamilyName());
        assertEquals("Nuria", author.getGivenNames());
        assertEquals(Author.TYPE_COLORIST, author.getType());
    }

    @Test
    void parse02()
            throws IOException {

        String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                + "/2060_De_boom_van_de_twee_lentes_1_De_boom_van_de_twee_lentes";
        String filename = "/stripinfo-905581315X.html";

        Document doc;
        try (InputStream in = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(in);
            doc = Jsoup.parse(in, null, locationHeader);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        StripInfoBookHandler stripInfoBookHandler = new StripInfoBookHandler(doc);
        // we've set the doc, so no internet download will be done.
        Bundle bookData = stripInfoBookHandler.parseDoc(mBundle, false);

        assertFalse(bookData.isEmpty());
        System.out.println(bookData);

        assertEquals("De boom van de twee lentes", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("905581315X", bookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("2000", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("64", bookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Softcover", bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("Nederlands", bookData.getString(DBDefinitions.KEY_LANGUAGE));

        assertEquals("Le Lombard", bookData.getString(DBDefinitions.KEY_PUBLISHER));

        ArrayList<Series> allSeries = bookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("De boom van de twee lentes", series.getTitle());
        assertEquals("1", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Getekend", series.getTitle());
        assertEquals("", series.getNumber());

        ArrayList<Author> allAuthors = bookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        assertNotNull(allAuthors);
        assertEquals(20, allAuthors.size());

        Author author = allAuthors.get(0);
        assertEquals("Miel", author.getFamilyName());
        assertEquals("Rudi", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = allAuthors.get(1);
        assertEquals("Batem", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        // there are more...
    }
}
