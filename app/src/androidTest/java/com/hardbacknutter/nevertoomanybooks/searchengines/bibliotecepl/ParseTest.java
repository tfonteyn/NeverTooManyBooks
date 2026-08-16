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

package com.hardbacknutter.nevertoomanybooks.searchengines.bibliotecepl;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2025-11-06: image downloads may fail randomly.
 * Parsing has been verified manually, and the failing url's ARE CORRECT.
 */
class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";

    private BibliotecePlSearchEngine searchEngine;
    private RealNumberParser ratingNumberParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.BibliotecePl.getConfig().setLogHttpGetRequests(true);
        searchEngine = EngineId.BibliotecePl.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(EngineId.BibliotecePl.getPreferenceKey()
                                  + ".resolve.authors.wikidata", true)
                      .apply();

        ratingNumberParser = new RealNumberParser(List.of(searchEngine.getLocale(context)));
    }

    @Test
    void parseMultiResult()
            throws IOException {
        final String locationHeader = "https://w.bibliotece.pl/search/?q=Ziele%C5%84";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bibliotece_pl_multi_result;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        assertEquals("https://w.bibliotece.pl/446634/Ziele%C5%84+szmaragdu",
                     searchEngine.parseMultiResult(document));
    }

    /** Short test to verify ISBN 10/13 handling only. */
    @Test
    void parse9788321331966()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader =
                "https://w.bibliotece.pl/546206/Historia+sztuki";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bibliotece_pl_9788321331966;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        // emulate searchEngine#searchByIsbn behaviour
        book.setRawProductCode("9788321331966");
        searchEngine.parse(context, document, new boolean[]{false, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("8321331963", book.getString(DBKey.ISBN, null));
    }

    @Test
    void parse9788384252963()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader =
                "https://w.bibliotece.pl/6985469/Ziele%C5%84";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bibliotece_pl_9788384252963;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        // emulate searchEngine#searchByIsbn behaviour
        book.setRawProductCode("9788384252963");
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Zieleń - Kolory zła", book.getString(DBKey.TITLE, null));
        assertEquals("9788384252963", book.getString(DBKey.ISBN, null));

        assertEquals("2026", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("2025", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));
        assertEquals("pl", book.getString(DBKey.LANGUAGE, null));

        assertEquals("6985469", book.requireIdentifierValue(
                Identifier.SID_BIBLIOTECE_PL));

        final String description = book.getString(DBKey.DESCRIPTION, null);
        assertNotNull(description);
        assertTrue(description.startsWith("Nowy kolor bestsellerowej"));
        assertTrue(description.endsWith("metodami śledczymi. [Azymut]"));

        final List<Tag> bookTags = book.getTags();
        assertEquals(3, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("druk"));
        assertTrue(tags.contains("powieści"));
        assertTrue(tags.contains("proza"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(2, allPublishers.size());
        assertEquals("Grupa Wydawnicza Foksal", allPublishers.get(0).getName());
        assertEquals("Wydawnictwo WAB", allPublishers.get(1).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Optional<String> oIv;
        final Author author;
        author = authors.get(0);
        assertEquals("Sobczak", author.getFamilyName());
        assertEquals("Małgorzata Oliwia", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1982-04-20", author.getBirthDate().orElse(null));
        assertEquals(1, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q120435809", oIv.get());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BibliotecePl.getPreferenceKey()
                                          + "_9788384252963_0_.jpg"));
    }

    @Test
    void parse9788380837744()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader =
                "https://w.bibliotece.pl/3451326/Ona+i+dom+kt%C3%B3ry+ta%C5%84czy";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bibliotece_pl_9788380837744;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        // emulate searchEngine#searchByIsbn behaviour
        book.setRawProductCode("9788380837744");
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Ona i dom, który tańczy", book.getString(DBKey.TITLE, null));
        assertEquals("9788380837744", book.getString(DBKey.ISBN, null));
        assertEquals("2024", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("2017", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));

        assertEquals(1.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);

        assertEquals("pl", book.getString(DBKey.LANGUAGE, null));

        assertEquals("3451326", book.requireIdentifierValue(Identifier.SID_BIBLIOTECE_PL));

        final String description = book.getString(DBKey.DESCRIPTION, null);
        assertNotNull(description);
        assertTrue(description.startsWith("Trzy kobiety, których"));
        assertTrue(description.endsWith("trosk jego mieszkańców."));

        final List<Tag> bookTags = book.getTags();
        assertEquals(8, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("beletrystyka"));
        assertTrue(tags.contains("druk"));
        assertTrue(tags.contains("epika"));
        assertTrue(tags.contains("literatura"));
        assertTrue(tags.contains("literatura piękna"));
        assertTrue(tags.contains("powieści"));
        assertTrue(tags.contains("proza"));
        assertTrue(tags.contains("rodzina"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(5, allPublishers.size());
        assertEquals("Wydawnictwo W. A. B", allPublishers.get(0).getName());
        assertEquals("Wydawnictwo WAB", allPublishers.get(1).getName());
        assertEquals("Novae Res-Wydawnictwo Innowacyjne", allPublishers.get(2).getName());
        assertEquals("Legimi", allPublishers.get(3).getName());
        assertEquals("Grupa Wydawnicza Foksal", allPublishers.get(4).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Optional<String> oIv;
        final Author author;
        author = authors.get(0);
        assertEquals("Sobczak", author.getFamilyName());
        assertEquals("Małgorzata Oliwia", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1982-04-20", author.getBirthDate().orElse(null));
        assertEquals(1, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q120435809", oIv.get());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BibliotecePl.getPreferenceKey()
                                          + "_9788380837744_0_.jpg"));
    }

    @Test
    void parse9788368591095()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader =
                "https://w.bibliotece.pl/2261259/Studnia+wst%C4%85pienia";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bibliotece_pl_9788368591095;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        // emulate searchEngine#searchByIsbn behaviour
        book.setRawProductCode("9788367023290");
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Studnia wstąpienia - The well of ascension",
                     book.getString(DBKey.TITLE, null));
        assertEquals("Well of ascension", book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("9788367023290", book.getString(DBKey.ISBN, null));
        assertEquals("2023", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("2008", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));

        assertEquals(4.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);

        assertEquals("pl", book.getString(DBKey.LANGUAGE, null));

        assertEquals("2261259", book.requireIdentifierValue(Identifier.SID_BIBLIOTECE_PL));

        final String description = book.getString(DBKey.DESCRIPTION, null);
        assertNotNull(description);
        assertTrue(description.startsWith("Doskonała kontynuacja Z mgły"));
        assertTrue(description.endsWith("Orson Scott Card"));

        final List<Tag> bookTags = book.getTags();
        assertEquals(15, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("audiobooki"));
        assertTrue(tags.contains("CD"));
        assertTrue(tags.contains("druk"));
        assertTrue(tags.contains("MP3"));
        assertTrue(tags.contains("powieści"));
        assertTrue(tags.contains("proza"));
        assertTrue(tags.contains("beletrystyka"));
        assertTrue(tags.contains("dokumenty elektroniczne"));
        assertTrue(tags.contains("e-booki"));
        assertTrue(tags.contains("epika"));
        assertTrue(tags.contains("fantasy"));
        assertTrue(tags.contains("literatura"));
        assertTrue(tags.contains("literatura piękna"));
        assertTrue(tags.contains("nagrania"));
        assertTrue(tags.contains("zasoby elektroniczne"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(3, allPublishers.size());
        assertEquals("Wydawnictwo Mag Jacek Rodek", allPublishers.get(0).getName());
        assertEquals("we współpr. z Biblioteka Akustyczna", allPublishers.get(1).getName());
        assertEquals("Legimi", allPublishers.get(2).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(5, authors.size());

        Optional<String> oIv;
        Author author;
        author = authors.get(0);
        assertEquals("Sanderson", author.getFamilyName());
        assertEquals("Brandon", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1975-12-19", author.getBirthDate().orElse(null));
        assertEquals(18, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q457608", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ASIN);
        assertTrue(oIv.isPresent());
        assertEquals("B001IGFHW6", oIv.get());
        // There are 16 more... no need to check them all

        final File authorImageFile = author.getImage(context, 0).orElse(null);
        assertNotNull(authorImageFile);
        assertTrue(authorImageFile.getName().endsWith("_wikidata_Q457608_0_.jpg"));

        author = authors.get(1);
        assertEquals("Studniarek-Więch", author.getFamilyName());
        assertEquals("Anna", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR | AuthorRole.NARRATOR, author.getRole());

        author = authors.get(2);
        assertEquals("Popczyński", author.getFamilyName());
        assertEquals("Marcin", author.getGivenNames());
        assertEquals(AuthorRole.NARRATOR, author.getRole());
        assertEquals("1974-01-01", author.getBirthDate().orElse(null));
        assertEquals(3, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q111578216", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000113550691", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_VIAF);
        assertTrue(oIv.isPresent());
        assertEquals("165891730", oIv.get());

        author = authors.get(3);
        assertEquals("Studniarek", author.getFamilyName());
        assertEquals("Anna", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());

        author = authors.get(4);
        assertEquals("Hesko-Kołodzińska", author.getFamilyName());
        assertEquals("Małgorzata", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());


        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BibliotecePl.getPreferenceKey()
                                          + "_9788367023290_0_.jpg"));
    }

    @Test
    void parse9788328172241()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader =
                "https://w.bibliotece.pl/6976045/Ruchome+miasto";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bibliotece_pl_9788328172241;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        // emulate searchEngine#searchByIsbn behaviour
        book.setRawProductCode("9788328172241");
        searchEngine.parse(context, document, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Ruchome miasto", book.getString(DBKey.TITLE, null));
        assertEquals("Cité mouvante", book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("9788328172241", book.getString(DBKey.ISBN, null));
        assertEquals("2025", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("2025", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));

        assertEquals("pl", book.getString(DBKey.LANGUAGE, null));

        assertEquals("6976045", book.requireIdentifierValue(Identifier.SID_BIBLIOTECE_PL));

        final String description = book.getString(DBKey.DESCRIPTION, null);
        assertNotNull(description);
        assertTrue(description.startsWith("Komiks, piąty tom cyklu. Thorgal"));
        assertTrue(description.endsWith("tytuł \"Szron i ogień\"."));

        final List<Tag> bookTags = book.getTags();
        assertEquals(6, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("beletrystyka"));
        assertTrue(tags.contains("druk"));
        assertTrue(tags.contains("film i wideo"));
        assertTrue(tags.contains("ikonografia"));
        assertTrue(tags.contains("komiksy"));
        assertTrue(tags.contains("komiksy i książki obrazkowe"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Story House Egmont", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());
        assertEquals("Thorgal Saga", allSeries.get(0).getTitle());
        assertEquals("Klub Świata Komiksu", allSeries.get(1).getTitle());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(7, authors.size());

        Optional<String> oIv;
        Author author;
        File authorImageFile;
        author = authors.get(0);
        assertEquals("Tatti", author.getFamilyName());
        assertEquals("Bruno", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals("1969-01-01", author.getBirthDate().orElse(null));
        assertEquals(3, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q130420130", oIv.get());

        author = authors.get(1);
        assertEquals("Aouamri", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals(0, author.getIdentifiers().size());

        author = authors.get(2);
        assertEquals("Aouamri", author.getFamilyName());
        assertEquals("Mohamed", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals("1957-03-24", author.getBirthDate().orElse(null));
        assertEquals(7, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q2422857", oIv.get());

        author = authors.get(3);
        assertEquals("Ozanam", author.getFamilyName());
        assertEquals("Antoine", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1970-05-20", author.getBirthDate().orElse(null));
        assertEquals(9, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q27075287", oIv.get());

        author = authors.get(4);
        assertEquals("Birek", author.getFamilyName());
        assertEquals("Wojciech", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertEquals("1961-10-17", author.getBirthDate().orElse(null));
        assertEquals(6, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q9376359", oIv.get());

        authorImageFile = author.getImage(context, 0).orElse(null);
        assertNotNull(authorImageFile);
        assertTrue(authorImageFile.getName().endsWith("_wikidata_Q9376359_0_.jpg"));

        author = authors.get(5);
        assertEquals("Rosiński", author.getFamilyName());
        assertEquals("Grzegorz", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1941-08-03", author.getBirthDate().orElse(null));
        assertEquals(11, author.getIdentifiers().size());
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q744325", oIv.get());

        authorImageFile = author.getImage(context, 0).orElse(null);
        assertNotNull(authorImageFile);
        assertTrue(authorImageFile.getName().endsWith("_wikidata_Q744325_0_.jpg"));

        // 2025-12-14: wikidata seems to have removed some data for this author
        author = authors.get(6);
        assertEquals("Van Hamme", author.getFamilyName());
        assertEquals("Jean", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
//        assertEquals("1939-01-16", author.getBirthDate().orElse(null));
//        assertFalse(author.getIdentifiers().isEmpty());
//        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
//        assertTrue(oIv.isPresent());
//        assertEquals("Q428160", oIv.get());

//        authorImageFile = author.getImage(context, 0).orElse(null);
//        assertNotNull(authorImageFile);
//        assertTrue(authorImageFile.getName().endsWith("_wikidata_Q428160_0_.jpg"));

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BibliotecePl.getPreferenceKey()
                                          + "_9788328172241_0_.jpg"));
    }

    @Test
    void parse9788377052730()
            throws IOException, SearchException, CredentialsException, StorageException {
        final String locationHeader =
                "https://w.bibliotece.pl/3779215/Kr%C3%B3tkie+odpowiedzi+na+wielkie+pytania";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bibliotece_pl_9788377052730;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        // emulate searchEngine#searchByIsbn behaviour
        book.setRawProductCode("9788377052730");
        searchEngine.parse(context, document, new boolean[]{false, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Krótkie odpowiedzi na wielkie pytania - 22066",
                     book.getString(DBKey.TITLE, null));
        assertEquals("Brief answers to the big questions",
                     book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("eng", book.getString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, null));
        assertEquals("9788377052730", book.getString(DBKey.ISBN, null));
        assertEquals("2023", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("2007", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));

        assertEquals(4.0f, book.getFloat(DBKey.RATING, ratingNumberParser), 0.1f);

        assertEquals("pl", book.getString(DBKey.LANGUAGE, null));

        assertEquals("3779215", book.requireIdentifierValue(Identifier.SID_BIBLIOTECE_PL));

        final String description = book.getString(DBKey.DESCRIPTION, null);
        assertNotNull(description);
        assertTrue(description.startsWith("Krótkie odpowiedzi na wielkie"));
        assertTrue(description.endsWith("inne materiały archiwalne."));

        final List<Tag> bookTags = book.getTags();
        assertEquals(21, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("audiobooki"));
        assertTrue(tags.contains("dokumenty elektroniczne"));
        assertTrue(tags.contains("druk"));
        assertTrue(tags.contains("e-booki"));
        assertTrue(tags.contains("beletrystyka"));
        assertTrue(tags.contains("CD"));
        assertTrue(tags.contains("czytak"));
        assertTrue(tags.contains("DVD"));
        assertTrue(tags.contains("epika"));
        assertTrue(tags.contains("literatura"));
        assertTrue(tags.contains("literatura faktu"));
        assertTrue(tags.contains("literatura faktu, eseje, publicystyka"));
        assertTrue(tags.contains("literatura piękna"));
        assertTrue(tags.contains("literatura stosowana"));
        assertTrue(tags.contains("MP3"));
        assertTrue(tags.contains("muzyka"));
        assertTrue(tags.contains("nagrania"));
        assertTrue(tags.contains("nagrania muzyczne"));
        assertTrue(tags.contains("proza"));
        assertTrue(tags.contains("publikacje popularnonaukowe"));
        assertTrue(tags.contains("zasoby elektroniczne"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(8, allPublishers.size());
        int p = 0;
        assertEquals("Legimi", allPublishers.get(p++).getName());
        assertEquals("Zysk i Spółka Wydawnictwo", allPublishers.get(p++).getName());
        assertEquals("Storybox.pl", allPublishers.get(p++).getName());
        assertEquals("Stowarzyszenie Pomocy Osobom Niepełnosprawnym Larix",
                     allPublishers.get(p++).getName());
        assertEquals("NASBI", allPublishers.get(p++).getName());
        assertEquals("ebookpoint BIBLIO", allPublishers.get(p++).getName());
        assertEquals("Heraclon International", allPublishers.get(p++).getName());
        assertEquals("Stowarzyszenie Pomocy Osobom Niepełnosprawnym Larix im. Henryka Ruszczyca",
                     allPublishers.get(p++).getName());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());

        assertEquals("Czytak Larix", series.get(0).getTitle());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(8, authors.size());

        Optional<String> oIv;
        Author author;
        final File authorImageFile;
        p = 0;
        author = authors.get(p++);
        assertEquals("Hawking", author.getFamilyName());
        assertEquals("Stephen", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("1942-01-08", author.getBirthDate().orElse(null));
        assertEquals("2018-03-14", author.getDeathDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q17714", oIv.get());

        authorImageFile = author.getImage(context, 0).orElse(null);
        assertNotNull(authorImageFile);
        assertTrue(authorImageFile.getName().endsWith("_wikidata_Q17714_0_.jpg"));

        author = authors.get(p++);
        assertEquals("Krośniak", author.getFamilyName());
        assertEquals("Marek", author.getGivenNames());
        assertEquals(AuthorRole.WRITER | AuthorRole.TRANSLATOR, author.getRole());
        assertEquals("1955-01-01", author.getBirthDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q122943159", oIv.get());

        author = authors.get(p++);
        assertEquals("Krajewski", author.getFamilyName());
        assertEquals("Artur", author.getGivenNames());
        assertEquals(AuthorRole.NARRATOR, author.getRole());
        assertEquals("1968-08-04", author.getBirthDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q9160680", oIv.get());

        author = authors.get(p++);
        assertEquals("Plewako-Szczerbiński", author.getFamilyName());
        assertEquals("Krzysztof", author.getGivenNames());
        assertEquals(AuthorRole.NARRATOR, author.getRole());

        author = authors.get(p++);
        assertEquals("Szczerbiński", author.getFamilyName());
        assertEquals("Krzysztof", author.getGivenNames());
        assertEquals(AuthorRole.NARRATOR, author.getRole());
        assertEquals("1978-01-09", author.getBirthDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q11749718", oIv.get());

        author = authors.get(p++);
        assertEquals("Hawking", author.getFamilyName());
        assertEquals("Lucy", author.getGivenNames());
        assertEquals(AuthorRole.AFTERWORD, author.getRole());
        assertEquals("1969-11-02", author.getBirthDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q2209781", oIv.get());

        author = authors.get(p++);
        assertEquals("Thorne", author.getFamilyName());
        assertEquals("Kip S.", author.getGivenNames());
        assertEquals(AuthorRole.INTRODUCTION, author.getRole());
        assertEquals("1940-06-01", author.getBirthDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q323320", oIv.get());

        author = authors.get(p++);
        assertEquals("Redmayne", author.getFamilyName());
        assertEquals("Eddie", author.getGivenNames());
        assertEquals(AuthorRole.FOREWORD, author.getRole());
        assertEquals("1982-01-06", author.getBirthDate().orElse(null));
        oIv = author.getIdentifierValue(Identifier.SID_WIKIDATA);
        assertTrue(oIv.isPresent());
        assertEquals("Q28288", oIv.get());
    }
}
