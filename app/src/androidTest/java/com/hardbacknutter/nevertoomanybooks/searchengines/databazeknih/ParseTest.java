/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ebook:
 * https://www.databazeknih.cz/prehled-knihy/vecer-na-bezdezu-krivoklad-krkonosska-pout-543519
 */
@SuppressWarnings("LongLine")
class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";

    private DatabazeKnihSearchEngine searchEngine;
    private RealNumberParser ratingNumberParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.DatabazeKnih.getConfig().setHttpLoggingEnabled(true);
        searchEngine = EngineId.DatabazeKnih.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(AuthorResolverHelper.getPreferenceKey(
                              EngineId.DatabazeKnih, EngineId.DatabazeKnih), true)
                      .apply();

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        ratingNumberParser = new RealNumberParser(allLocales);
    }

    @Test
    void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.databazeknih.cz/prehled-knihy/pripad-levoruke-damy-546691";

        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.databazeknih_9788025368626;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Případ levoruké dámy", book.getString(DBKey.TITLE, null));
        assertEquals("9788025368626", book.getString(DBKey.ISBN, null));
        assertEquals("546691", book.requireIdentifierValue(Identifier.SID_DATABAZE_KNIH));
        assertEquals("2024", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("ces", book.getString(DBKey.LANGUAGE, null));
        assertEquals("192", book.getString(DBKey.PAGES, null));
        assertEquals(4.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertEquals("pevná / vázaná", book.getString(DBKey.FORMAT, null));

        assertEquals("The Case of the Left-Handed Lady",
                     book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("2007", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));

        assertTrue(book.getString(DBKey.DESCRIPTION).startsWith("Enola se stále skrývá před svým bratrem"));

        final List<Tag> bookTags = book.getTags();

        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatura světová"));
        assertTrue(tags.contains("Detektivky, krimi"));
        assertTrue(tags.contains("Pro děti a mládež"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Fragment (CZ)", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        final Optional<String> oIv;

        author = authors.get(0);
        Log.d(TAG, author.toString());

        assertEquals("Springer", author.getFamilyName());
        assertEquals("Nancy", author.getGivenNames());
        assertEquals("1948", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        final String pic = author.getTmpPictureFileSpec().orElse(null);
        assertNotNull(pic);
        assertTrue(pic.endsWith("_databazeknih_9571_0_.jpg"));

        assertEquals(AuthorRole.WRITER, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        assertTrue(oIv.isPresent());
        assertEquals("9571", oIv.get());

        author = authors.get(1);
        assertEquals("Davidová", author.getFamilyName());
        assertEquals("Vendula", author.getGivenNames());
        assertNull(author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertFalse(author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH).isPresent());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());

        assertEquals("Enola Holmesová", series.get(0).getTitle());
        assertEquals("2", series.get(0).getNumber());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.DatabazeKnih.getPreferenceKey()
                                          + "_9788025368626_0_.jpg"));
    }

    @Test
    void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.databazeknih.cz/prehled-knihy/p-s-267961";

        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.databazeknih_9788024929613;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("P.S.", book.getString(DBKey.TITLE, null));
        assertEquals("9788024929613", book.getString(DBKey.ISBN, null));
        assertEquals("267961", book.requireIdentifierValue(Identifier.SID_DATABAZE_KNIH));
        assertEquals("2015", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("ces", book.getString(DBKey.LANGUAGE, null));
        assertEquals("216", book.getString(DBKey.PAGES, null));
        assertEquals(3.5f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertEquals("pevná / vázaná", book.getString(DBKey.FORMAT, null));
        assertEquals("2015", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));

        assertTrue(book.getString(DBKey.DESCRIPTION).startsWith("Soubor fejetonů, kterými herečka Aňa Geislerová"));

        final List<Tag> bookTags = book.getTags();

        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Fejetony, eseje"));
        assertTrue(tags.contains("Literatura česká"));
        assertTrue(tags.contains("Cestopisy a místopisy"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Ikar (CZ)", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Geislerová", author.getFamilyName());
        assertEquals("Aňa", author.getGivenNames());
        assertEquals("1976", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        assertEquals(AuthorRole.WRITER, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        assertTrue(oIv.isPresent());
        assertEquals("85414", oIv.get());

        author = author.getRealAuthor();
        assertNotNull(author);
        assertEquals("Geislerová", author.getFamilyName());
        assertEquals("Anna", author.getGivenNames());
        assertEquals("1976", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        assertTrue(oIv.isPresent());
        assertEquals("85414", oIv.get());

        author = authors.get(1);
        assertEquals("Geislerová", author.getFamilyName());
        assertEquals("Lela", author.getGivenNames());
        assertNull(author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        assertEquals(AuthorRole.COVER_ARTIST | AuthorRole.ARTIST, author.getRole());
        assertFalse(author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH).isPresent());
    }

    @Test
    void parse03()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.databazeknih.cz/prehled-knihy/sonety-milencin-narek-dvojjazycna-kniha-694";

        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.databazeknih_8072210041;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Sonety / Milenčin nářek (dvojjazyčná kniha)",
                     book.getString(DBKey.TITLE, null));
        assertEquals("8072210041", book.getString(DBKey.ISBN, null));
        assertEquals("694", book.requireIdentifierValue(Identifier.SID_DATABAZE_KNIH));
        assertEquals("1997", book.getString(DBKey.PUBLICATION_DATE, null));
        // language=jiný -> "other", not stored
        assertNull(book.getString(DBKey.LANGUAGE, null));
        assertEquals("352", book.getString(DBKey.PAGES, null));
        assertEquals(4.5f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertEquals("pevná / vázaná s přebalem", book.getString(DBKey.FORMAT, null));

        assertEquals("Sonnets / A lover's complaint",
                     book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));

        assertTrue(book.getString(DBKey.DESCRIPTION).startsWith("Dvojjazyčné vydání /česky a anglicky"));

        final List<Tag> bookTags = book.getTags();

        assertEquals(1, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Poezie"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Arca JiMfa", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(3, authors.size());

        Author author;
        final Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Shakespeare", author.getFamilyName());
        assertEquals("William", author.getGivenNames());
        assertEquals("1564", author.getBirthDate().orElse(null));
        assertEquals("1616", author.getDeathDate().orElse(null));

        assertEquals(AuthorRole.WRITER, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        assertTrue(oIv.isPresent());
        assertEquals("73", oIv.get());

        final String pic = author.getTmpPictureFileSpec().orElse(null);
        assertNotNull(pic);
        assertTrue(pic.endsWith("_databazeknih_73_0_.jpg"));

        author = authors.get(1);
        assertEquals("Urbánková", author.getFamilyName());
        assertEquals("Jarmila", author.getGivenNames());
        assertNull(author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertFalse(author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH).isPresent());

        author = authors.get(2);
        assertEquals("Lavický", author.getFamilyName());
        assertEquals("Vladimír", author.getGivenNames());
        assertNull(author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertFalse(author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH).isPresent());
    }


    @Test
    void multiResult()
            throws IOException {
        final String locationHeader = "https://www.databazeknih.cz/search?in=books&q=nadace+asim&hledat=";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.databazeknih_multi_foundation_asi;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final String url = searchEngine.parseMultiResult(document);
        assertNotNull(url);
        assertEquals("https://www.databazeknih.cz/prehled-knihy/nadace-nadacia-nadace-15829",url);
    }

    @Test
    void parseMulti01()
            throws IOException, SearchException, CredentialsException, StorageException {

        final String locationHeader = "https://www.databazeknih.cz/prehled-knihy/nadace-nadacia-nadace-15829";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.databazeknih_nadace;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false, false, false},
                                 book);
        Log.d(TAG, book.toString());

        assertEquals("Nadace", book.getString(DBKey.TITLE, null));
        assertEquals("9788025701331", book.getString(DBKey.ISBN, null));
        assertEquals("15829", book.requireIdentifierValue(Identifier.SID_DATABAZE_KNIH));
        assertEquals("2009", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("ces", book.getString(DBKey.LANGUAGE, null));
        assertEquals("204", book.getString(DBKey.PAGES, null));
        assertEquals(4.5f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);
        assertEquals("pevná / vázaná s přebalem", book.getString(DBKey.FORMAT, null));
        assertEquals("1951", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));

        assertTrue(book.getString(DBKey.DESCRIPTION).startsWith("Po dlouhých dvanácti tisíciletích existence spěje galaktická Říše"));
        assertEquals("Foundation",
                     book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));

        final List<Tag> bookTags = book.getTags();

        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatura světová"));
        assertTrue(tags.contains("Romány"));
        assertTrue(tags.contains("Sci-fi"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(2, allPublishers.size());

        assertEquals("Argo", allPublishers.get(0).getName());
        assertEquals("Triton", allPublishers.get(1).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(3, authors.size());

        Author author;
        final Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertEquals("1920", author.getBirthDate().orElse(null));
        assertEquals("1992", author.getDeathDate().orElse(null));
        assertTrue(author.getTmpPictureFileSpec().orElse("").endsWith("_databazeknih_79_0_.jpg"));
        assertEquals(AuthorRole.WRITER, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        assertTrue(oIv.isPresent());
        assertEquals("79", oIv.get());

        author = authors.get(1);
        assertEquals("Janiš", author.getFamilyName());
        assertEquals("Viktor", author.getGivenNames());
        assertNull(author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertFalse(author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH).isPresent());

        author = authors.get(2);
        assertEquals("Whelan", author.getFamilyName());
        assertEquals("Michael", author.getGivenNames());
        assertNull(author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));
        assertEquals(AuthorRole.COVER_ARTIST, author.getRole());
        assertFalse(author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH).isPresent());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());

        assertEquals("Nadace", series.get(0).getTitle());
        assertEquals("1", series.get(0).getNumber());
    }

    @Test
    void parseWithToc01()
            throws IOException {

        final String locationHeader = "https://www.databazeknih.cz/povidky-z-knihy/ja-robot-246";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.databazeknih_8023739611_povidky;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parseToc(context, document, book);
        Log.d(TAG, book.toString());

        final List<TocEntry> toc = book.getToc();
        assertEquals(9, toc.size());

        TocEntry te;
        int i = 0;

        te = toc.get(i++);
        assertEquals("Chyť toho králíka! / Najprv zabiť vlka", te.getTitle());
        assertEquals("1944", te.getFirstPublicationDate().getIsoString());
        te = toc.get(i++);
        assertEquals("Důkaz / Dôkaz", te.getTitle());
        assertEquals("1946", te.getFirstPublicationDate().getIsoString());
        te = toc.get(i++);
        assertEquals("Hra na honěnou / Kolotoč", te.getTitle());
        assertEquals("1942", te.getFirstPublicationDate().getIsoString());
        te = toc.get(i++);
        assertEquals("Konflikt nikoli nevyhnutelný / Odvrátiteľný konflikt", te.getTitle());
        assertEquals("1950", te.getFirstPublicationDate().getIsoString());
        te = toc.get(i++);
        assertEquals("Lhář! / Klamár!", te.getTitle());
        assertEquals("1941", te.getFirstPublicationDate().getIsoString());
        te = toc.get(i++);
        assertEquals("Malý ztracený robot / Stratený robot", te.getTitle());
        assertEquals("1947", te.getFirstPublicationDate().getIsoString());
        te = toc.get(i++);
        assertEquals("Robbie", te.getTitle());
        assertEquals("1940", te.getFirstPublicationDate().getIsoString());
        te = toc.get(i++);
        assertEquals("Rozum", te.getTitle());
        assertEquals("1941", te.getFirstPublicationDate().getIsoString());
        te = toc.get(i++);
        assertEquals("Únik", te.getTitle());
        assertEquals("1945", te.getFirstPublicationDate().getIsoString());
    }
}
