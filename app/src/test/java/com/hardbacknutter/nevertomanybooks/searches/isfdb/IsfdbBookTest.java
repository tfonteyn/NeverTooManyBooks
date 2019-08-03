package com.hardbacknutter.nevertomanybooks.searches.isfdb;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import com.hardbacknutter.nevertomanybooks.BundleMock;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.entities.Author;
import com.hardbacknutter.nevertomanybooks.entities.TocEntry;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IsfdbBookTest {

    @SuppressWarnings("FieldCanBeLocal")
    private final String baseUri = "http://www.isfdb.org";
    @SuppressWarnings("FieldCanBeLocal")
    private final String filename = "/isfdb-valid-book.html";
    private final String bookType = "Paperback";

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
        when(mContext.createConfigurationContext(anyObject())).thenReturn(mContext);
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);

        when(mResources.getString(anyInt())).thenReturn(bookType);
        when(mResources.getConfiguration()).thenReturn(mConfiguration);

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
            doc = Jsoup.parse(in, IsfdbManager.CHARSET_DECODE_PAGE, baseUri);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        IsfdbBook isfdbBook = new IsfdbBook(doc);
        // we've set the doc, so no internet download will be done.
        Bundle bookData = isfdbBook.parseDoc(mBundle, false, mContext);

        assertFalse(bookData.isEmpty());

        assertEquals("Like Nothing on Earth", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals(112781, bookData.getLong(DBDefinitions.KEY_ISFDB_ID));
        assertEquals("1986-10-01", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("0413600106", bookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("9780413600103", bookData.getString(IsfdbBook.BookField.ISBN_2));
        assertEquals("Methuen", bookData.getString(DBDefinitions.KEY_PUBLISHER));
        assertEquals("1.95", bookData.getString(DBDefinitions.KEY_PRICE_LISTED));
        assertEquals("GBP", bookData.getString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY));
        assertEquals("159", bookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals(bookType, bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("COLLECTION", bookData.getString(IsfdbBook.BookField.BOOK_TYPE));
        assertEquals(TocEntry.Authors.MULTIPLE_WORKS | TocEntry.Authors.MULTIPLE_AUTHORS,
                bookData.getLong(DBDefinitions.KEY_TOC_BITMASK));

        assertEquals(13665857, bookData.getLong(DBDefinitions.KEY_WORLDCAT_ID));

        assertEquals("Month from Locus1", bookData.getString(DBDefinitions.KEY_DESCRIPTION));

        ArrayList<Author> authors = bookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Russell", authors.get(0).getFamilyName());
        assertEquals("Eric Frank", authors.get(0).getGivenNames());
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