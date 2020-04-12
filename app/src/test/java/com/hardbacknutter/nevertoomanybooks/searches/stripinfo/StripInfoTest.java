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
package com.hardbacknutter.nevertoomanybooks.searches.stripinfo;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.CommonSetup;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class StripInfoTest
        extends CommonSetup {

    @Test
    void parse01() {
        setLocale(Locale.FRANCE);
        String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                + "/336348_Hauteville_House_14_De_37ste_parallel";
        String filename = "/stripinfo/336348_Hauteville_House_14_De_37ste_parallel.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, "UTF-8", locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        SearchEngine searchEngine = new StripInfoSearchEngine();
        StripInfoBookHandler stripInfoBookHandler =
                new StripInfoBookHandler(mContext, searchEngine, doc);
        // we've set the doc, so no internet download will be done.
        boolean[] fetchThumbnail = {false, false};
        Bundle bookData = stripInfoBookHandler.parseDoc(mRawData, fetchThumbnail);

        assertFalse(bookData.isEmpty());
        System.out.println(bookData);

        assertEquals("De 37ste parallel", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("9789463064385", bookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("2018", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("48", bookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Hardcover", bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("nld", bookData.getString(DBDefinitions.KEY_LANGUAGE));
        assertEquals("Kleur", bookData.getString(DBDefinitions.KEY_COLOR));

        ArrayList<Publisher> allPublishers = bookData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Silvester", allPublishers.get(0).getName());

        ArrayList<Series> allSeries = bookData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("Hauteville House", series.getTitle());
        assertEquals("14", series.getNumber());

        ArrayList<Author> allAuthors = bookData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
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
    void parse02() {
        setLocale(Locale.FRANCE);
        String locationHeader = "https://www.stripinfo.be/reeks/strip"
                                + "/2060_De_boom_van_de_twee_lentes_1_De_boom_van_de_twee_lentes";
        String filename = "/stripinfo/2060_De_boom_van_de_twee_lentes_1"
                          + "_De_boom_van_de_twee_lentes.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, null, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        SearchEngine searchEngine = new StripInfoSearchEngine();
        StripInfoBookHandler stripInfoBookHandler =
                new StripInfoBookHandler(mContext, searchEngine, doc);
        // we've set the doc, so no internet download will be done.
        boolean[] fetchThumbnail = {false, false};
        Bundle bookData = stripInfoBookHandler.parseDoc(mRawData, fetchThumbnail);

        assertFalse(bookData.isEmpty());
        System.out.println(bookData);

        assertEquals("De boom van de twee lentes", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("905581315X", bookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("2000", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("64", bookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Softcover", bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("nld", bookData.getString(DBDefinitions.KEY_LANGUAGE));
        assertEquals("Kleur", bookData.getString(DBDefinitions.KEY_COLOR));

        ArrayList<Publisher> allPublishers = bookData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        ArrayList<Series> allSeries = bookData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("De boom van de twee lentes", series.getTitle());
        assertEquals("1", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Getekend", series.getTitle());
        assertEquals("", series.getNumber());

        ArrayList<Author> allAuthors = bookData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
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

    @Test
    void parseIntegrale() {
        setLocale(Locale.FRANCE);
        String locationHeader = "https://www.stripinfo.be/reeks/strip/"
                                + "316016_Johan_en_Pirrewiet_INT_5_De_integrale_5";
        String filename = "/stripinfo/316016_Johan_en_Pirrewiet_INT_5_De_integrale_5.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, null, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        SearchEngine searchEngine = new StripInfoSearchEngine();
        StripInfoBookHandler stripInfoBookHandler =
                new StripInfoBookHandler(mContext, searchEngine, doc);
        // we've set the doc, so no internet download will be done.
        boolean[] fetchThumbnail = {false, false};
        Bundle bookData = stripInfoBookHandler.parseDoc(mRawData, fetchThumbnail);

        assertFalse(bookData.isEmpty());
        System.out.println(bookData);

        assertEquals("De integrale 5", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("9789055819485", bookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("2017", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("224", bookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Hardcover", bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("nld", bookData.getString(DBDefinitions.KEY_LANGUAGE));
        assertEquals("Kleur", bookData.getString(DBDefinitions.KEY_COLOR));

        ArrayList<Publisher> allPublishers = bookData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        ArrayList<TocEntry> tocs = bookData.getParcelableArrayList(Book.BKEY_TOC_ENTRY_ARRAY);
        assertNotNull(tocs);
        assertEquals(4, tocs.size());

        assertEquals("14 De horde van de raaf", tocs.get(0).getTitle());
        assertEquals("15 De troubadours van Steenbergen", tocs.get(1).getTitle());
        assertEquals("16 De nacht van de Magiërs", tocs.get(2).getTitle());
        assertEquals("17 De Woestijnroos", tocs.get(3).getTitle());

        assertEquals("Culliford", tocs.get(0).getAuthor().getFamilyName());

        ArrayList<Series> allSeries = bookData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("Johan en Pirrewiet", series.getTitle());
        assertEquals("INT 5", series.getNumber());

        ArrayList<Author> allAuthors = bookData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        assertNotNull(allAuthors);
        assertEquals(6, allAuthors.size());

        Author author = allAuthors.get(0);
        assertEquals("Culliford", author.getFamilyName());
        assertEquals("Thierry", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = allAuthors.get(3);
        assertEquals("Maury", author.getFamilyName());
        assertEquals("Alain", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
        // there are more...
    }

    @Test
    void parseIntegrale2() {
        setLocale(Locale.FRANCE);
        String locationHeader = "https://www.stripinfo.be/reeks/strip/"
                                + "17030_Comanche_1_Red_Dust";
        String filename = "/stripinfo/17030_Comanche_1_Red_Dust.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, null, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        SearchEngine searchEngine = new StripInfoSearchEngine();
        StripInfoBookHandler stripInfoBookHandler =
                new StripInfoBookHandler(mContext, searchEngine, doc);
        // we've set the doc, so no internet download will be done.
        boolean[] fetchThumbnail = {false, false};
        Bundle bookData = stripInfoBookHandler.parseDoc(mRawData, fetchThumbnail);

        assertFalse(bookData.isEmpty());
        System.out.println(bookData);

        assertEquals("Red Dust", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("1972", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("48", bookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Softcover", bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("nld", bookData.getString(DBDefinitions.KEY_LANGUAGE));
        assertEquals("Kleur", bookData.getString(DBDefinitions.KEY_COLOR));

        ArrayList<Publisher> allPublishers = bookData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        ArrayList<TocEntry> tocs = bookData.getParcelableArrayList(Book.BKEY_TOC_ENTRY_ARRAY);
        assertNotNull(tocs);
        assertEquals(3, tocs.size());

        assertEquals("1 De samenzwering", tocs.get(0).getTitle());
        assertEquals("2 De afrekening", tocs.get(1).getTitle());
        assertEquals("3 De ontmaskering", tocs.get(2).getTitle());

        assertEquals("Greg", tocs.get(0).getAuthor().getFamilyName());

        ArrayList<Series> allSeries = bookData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("Comanche", series.getTitle());
        assertEquals("1", series.getNumber());

        ArrayList<Author> allAuthors = bookData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        assertNotNull(allAuthors);
        assertEquals(2, allAuthors.size());

        Author author = allAuthors.get(0);
        assertEquals("Greg", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = allAuthors.get(1);
        assertEquals("Hermann", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
    }

    @Test
    void parseFavReeks2() {
        setLocale(Locale.FRANCE);
        String locationHeader = "https://www.stripinfo.be/reeks/strip/"
                                + "8155_De_avonturen_van_de_3L_7_Spoken_in_de_grot";
        String filename = "/stripinfo/8155_De_avonturen_van_de_3L_7_Spoken_in_de_grot.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, null, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        SearchEngine searchEngine = new StripInfoSearchEngine();
        StripInfoBookHandler stripInfoBookHandler =
                new StripInfoBookHandler(mContext, searchEngine, doc);
        // we've set the doc, so no internet download will be done.
        boolean[] fetchThumbnail = {false, false};
        Bundle bookData = stripInfoBookHandler.parseDoc(mRawData, fetchThumbnail);

        assertFalse(bookData.isEmpty());
        System.out.println(bookData);

        assertEquals("Spoken in de grot", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("1977", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("Softcover", bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("nld", bookData.getString(DBDefinitions.KEY_LANGUAGE));
        assertEquals("Kleur", bookData.getString(DBDefinitions.KEY_COLOR));

        ArrayList<Publisher> allPublishers = bookData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Le Lombard", allPublishers.get(0).getName());

        ArrayList<TocEntry> tocs = bookData.getParcelableArrayList(Book.BKEY_TOC_ENTRY_ARRAY);
        assertNull(tocs);

        ArrayList<Series> allSeries = bookData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("De avonturen van de 3L", series.getTitle());
        assertEquals("7", series.getNumber());
        series = allSeries.get(1);
        assertEquals("Favorietenreeks", series.getTitle());
        assertEquals("2.50", series.getNumber());

        ArrayList<Author> allAuthors = bookData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        assertNotNull(allAuthors);
        assertEquals(2, allAuthors.size());

        Author author = allAuthors.get(0);
        assertEquals("Duchateau", author.getFamilyName());
        assertEquals("André-Paul", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = allAuthors.get(1);
        assertEquals("Mittéï", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());
    }


    /** Network access! */
    @Test
    void parseMultiResult() {
        setLocale(Locale.FRANCE);
        String locationHeader = "https://stripinfo.be/zoek/zoek?zoekstring=pluvi";
        String filename = "/stripinfo/multi-result-pluvi.html";

        Document doc = null;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            doc = Jsoup.parse(is, null, locationHeader);
        } catch (@NonNull final IOException e) {
            fail(e);
        }
        assertNotNull(doc);
        assertTrue(doc.hasText());

        SearchEngine searchEngine = new StripInfoSearchEngine();
        StripInfoBookHandler stripInfoBookHandler =
                new StripInfoBookHandler(mContext, searchEngine, doc);
        // we've set the doc, but will redirect.. so an internet download WILL be done.
        Bundle bookData = null;
        try {
            boolean[] fetchThumbnail = {false, false};
            bookData = stripInfoBookHandler.parseMultiResult(mRawData, fetchThumbnail);
        } catch (@NonNull final SocketTimeoutException e) {
            fail(e);
        }

        assertFalse(bookData.isEmpty());
        System.out.println(bookData);

        assertEquals("9782756010830", bookData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("Le chant du pluvier", bookData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("2009", bookData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("172", bookData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Hardcover", bookData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("fra", bookData.getString(DBDefinitions.KEY_LANGUAGE));
        assertEquals("Kleur", bookData.getString(DBDefinitions.KEY_COLOR));

        ArrayList<Publisher> allPublishers = bookData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Delcourt", allPublishers.get(0).getName());

        ArrayList<TocEntry> tocs = bookData.getParcelableArrayList(Book.BKEY_TOC_ENTRY_ARRAY);
        assertNull(tocs);

        ArrayList<Series> allSeries = bookData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("Le chant du pluvier", series.getTitle());
        assertEquals("1", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Mirages", series.getTitle());
        assertEquals("", series.getNumber());

        ArrayList<Author> allAuthors = bookData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        assertNotNull(allAuthors);
        assertEquals(3, allAuthors.size());

        Author author = allAuthors.get(0);
        assertEquals("Béhé", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = allAuthors.get(1);
        assertEquals("Laprun", author.getFamilyName());
        assertEquals("Amandine", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = allAuthors.get(2);
        assertEquals("Surcouf", author.getFamilyName());
        assertEquals("Erwann", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST | Author.TYPE_COLORIST, author.getType());

    }
}
