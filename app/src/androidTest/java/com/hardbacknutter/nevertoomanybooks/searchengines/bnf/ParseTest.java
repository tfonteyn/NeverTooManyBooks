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

package com.hardbacknutter.nevertoomanybooks.searchengines.bnf;

import android.util.Log;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.PublicationFrequency;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";

    private BnfSearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (BnfSearchEngine) EngineId.Bnf.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        // DON'T call wikipedia
        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putBoolean(AuthorResolverHelper.getPreferenceKey(
                              EngineId.Bnf, EngineId.Wikidata), false)
                      .apply();
    }

    @Test
    void issn()
            throws IOException, CredentialsException, StorageException {
        // uses the "anywhere" but finds an issn.
        final String locationHeader =
                "https://catalogue.bnf.fr/api/SRU?version=1.2&operation=searchRetrieve&query=bib.anywhere+all+%22lettre%20de%20mon%20moulin%22&recordSchema=unimarcXchange&maximumRecords=1";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bnf_lettre_de_mon_moulin;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());
        final Book book = new Book();
        searchEngine.parse(context, document, null,new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("La Lettre de mon moulin", book.getString(DBKey.TITLE, null));
        assertEquals("17683920", book.getString(DBKey.ISBN, null));
        assertEquals("39187957", book.getIdentifierValue(Identifier.SID_BNF).orElse(null));

        assertEquals("Periodical", book.getString(DBKey.FORMAT, null));
        assertEquals("fre", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2002", book.getString(DBKey.PUBLICATION_DATE, null));

         final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Green & white", allPublishers.get(0).getName());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());
        Series series;

        series = allSeries.get(0);
        assertEquals("La Lettre de mon moulin", series.getTitle());
        final PublicationFrequency frequency = series.getPublicationFrequency();
        assertNotNull(frequency);
        assertEquals(PublicationFrequency.Type.Monthly, frequency.getType());
        assertEquals(3, frequency.getCadence());
        assertFalse(frequency.isOrdinal());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        Author author;

        author = authors.get(0);
        assertEquals("Minoteries Viron", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("14548183", author.getIdentifierValue(Identifier.SID_BNF).orElse(null));
        assertNull(author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));
    }

    @Test
    void parse47592158()
            throws IOException, CredentialsException, StorageException {

        final String locationHeader = "https://catalogue.bnf.fr/api/SRU?version=1.2&operation=searchRetrieve&query=bib.persistentid+all+%22ark:/12148/cb475921587%22&recordSchema=unimarcXchange&maximumRecords=1";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bnf_47592158;

        final Document document = loadDocument(resId, UTF_8, locationHeader, Parser.xmlParser());

        final Book book = new Book();
        searchEngine.parse(context, document, null,new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Les étymologies", book.getString(DBKey.TITLE, null));
        assertEquals("9782266341417", book.getString(DBKey.ISBN, null));
        assertEquals("47592158", book.getIdentifierValue(Identifier.SID_BNF).orElse(null));

        assertEquals("Paperback", book.getString(DBKey.FORMAT, null));
        assertEquals("fre", book.getString(DBKey.LANGUAGE, null));

        assertEquals("The etymologies", book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("eng", book.getString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, null));

        assertEquals("2024", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("152", book.getString(DBKey.PAGES, null));

        assertEquals("7.3", book.getString(DBKey.PRICE_LISTED, null));
        assertEquals("EUR", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertTrue(book.getString(DBKey.DESCRIPTION).startsWith("Un dictionnaire pour les nommer tous !"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Pocket", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(3, authors.size());

        Author author;

        author = authors.get(0);
        assertEquals("Tolkien", author.getFamilyName());
        assertEquals("John Ronald Reuel", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("11926763", author.getIdentifierValue(Identifier.SID_BNF).orElse(null));
        assertEquals("0000000121441970",author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));
        assertEquals("1892", author.getBirthDate().orElse(null));
        assertEquals("1973", author.getDeathDate().orElse(null));

        author = authors.get(1);
        assertEquals("Tolkien", author.getFamilyName());
        assertEquals("Christopher", author.getGivenNames());
        assertEquals(AuthorRole.EDITOR, author.getRole());
        assertEquals("11926762", author.getIdentifierValue(Identifier.SID_BNF).orElse(null));
        assertEquals("0000000071393949", author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));
        assertEquals("1924", author.getBirthDate().orElse(null));
        assertEquals("2020", author.getDeathDate().orElse(null));

        author = authors.get(2);
        assertEquals("Lauzon", author.getFamilyName());
        assertEquals("Daniel", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        assertEquals("15069993", author.getIdentifierValue(Identifier.SID_BNF).orElse(null));
        assertEquals("0000000072518950", author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));
        assertEquals("1979", author.getBirthDate().orElse(null));
        assertNull(author.getDeathDate().orElse(null));

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series;
        series = allSeries.get(0);
        assertEquals("Presses pocket (Paris)", series.getTitle());
        assertEquals("7375", series.getNumber());
        assertEquals("02446405", series.getIdentifierValue(Identifier.SID_ISSN).orElse(null));
        assertEquals("34228117", series.getIdentifierValue(Identifier.SID_BNF).orElse(null));

        series = allSeries.get(1);
        assertEquals("Imaginaire (Paris)", series.getTitle());
        assertEquals("", series.getNumber());
        assertEquals("24977284", series.getIdentifierValue(Identifier.SID_ISSN).orElse(null));
        assertEquals("45156268", series.getIdentifierValue(Identifier.SID_BNF).orElse(null));

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        final List<String> covers;
        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9782266341417_0_.jpg"));
    }

    @Test
    void parse45842142()
            throws IOException, CredentialsException, StorageException {

        final String locationHeader = "https://catalogue.bnf.fr/api/SRU?version=1.2&operation=searchRetrieve&query=bib.persistentid+all+%22ark:/12148/cb458421422%22&recordSchema=unimarcXchange&maximumRecords=1";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bnf_45842142;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, null, new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Mise à jour", book.getString(DBKey.TITLE, null));
        assertEquals("9782756078311", book.getString(DBKey.ISBN, null));
        assertEquals("45842142", book.getIdentifierValue(Identifier.SID_BNF).orElse(null));

        assertEquals("fre", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2019", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("46", book.getString(DBKey.PAGES, null));
        assertEquals("Colored", book.getString(DBKey.COLOR, null));
        assertEquals("Hardcover", book.getString(DBKey.FORMAT, null));

        assertEquals("14.5", book.getString(DBKey.PRICE_LISTED, null));
        assertEquals("EUR", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertTrue(book.getString(DBKey.DESCRIPTION).startsWith("Alors que Nävis est sur la piste d'un de ces mystérieux métamorphes"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Delcourt", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;

        author = authors.get(0);
        assertEquals("Morvan", author.getFamilyName());
        assertEquals("Jean-David", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        assertEquals("12950081", author.getIdentifierValue(Identifier.SID_BNF).orElse(null));
        assertEquals("0000000121449219", author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));
        assertEquals("1969", author.getBirthDate().orElse(null));

        author = authors.get(1);
        assertEquals("Buchet", author.getFamilyName());
        assertEquals("Philippe", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        assertEquals("13205924", author.getIdentifierValue(Identifier.SID_BNF).orElse(null));
        assertEquals("0000000121225055",author.getIdentifierValue(Identifier.SID_ISNI).orElse(null));
        assertEquals("1962", author.getBirthDate().orElse(null));

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series;
        series = allSeries.get(0);
        assertEquals("Sillage", series.getTitle());
        assertEquals("20", series.getNumber());
        assertEquals("36133793", series.getIdentifierValue(Identifier.SID_BNF).orElse(null));

        series = allSeries.get(1);
        assertEquals("Neopolis (Paris)", series.getTitle());
        assertEquals("", series.getNumber());
        assertEquals("12485152", series.getIdentifierValue(Identifier.SID_ISSN).orElse(null));
        assertEquals("34280292", series.getIdentifierValue(Identifier.SID_BNF).orElse(null));

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        final List<String> covers;
        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9782756078311_0_.jpg"));
    }
}
