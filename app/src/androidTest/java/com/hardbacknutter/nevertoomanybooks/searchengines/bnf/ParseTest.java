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
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";

    private BnfSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (BnfSearchEngine) EngineId.Bnf.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);
    }

    @Test
    public void parse9782266341417()
            throws SearchException, IOException, CredentialsException, CoverStorageException {

        final String locationHeader = "https://catalogue.bnf.fr/ark:/12148/cb475921587";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bnf_9782266341417;
        final String locationHeaderUM = "https://catalogue.bnf.fr/ark:/12148/cb475921587.unimarc";
        final int resIdUM = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bnf_9782266341417_unimarc;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Document unimarcDocument = loadDocument(resIdUM, UTF_8, locationHeaderUM);

        final Book book = new Book();
        searchEngine.parse(context, document, unimarcDocument,
                           new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Les étymologies", book.getString(DBKey.TITLE, null));
        assertEquals("9782266341417", book.getString(DBKey.ISBN, null));
        assertEquals("cb475921587", book.requireIdentifierValue(Identifier.SID_BNF));

        assertEquals("fre", book.getString(DBKey.LANGUAGE, null));

        assertEquals("The etymologies", book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("eng", book.getString(DBKey.TRANSLATION_ORIGINAL_LANGUAGE, null));

        assertEquals("2024", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("152", book.getString(DBKey.PAGES, null));

        assertEquals("7.3", book.getString(DBKey.PRICE_LISTED, null));
        assertEquals("EUR", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertEquals(
                "Un dictionnaire pour les nommer tous ! (A l'origine des noms dans"
                + " l'œuvre de Tolkien) Les lecteurs trouveront dans ce \" dictionnaire"
                + " étymologique \" elfique l'explication de nombreux noms et le point de départ"
                + " de bien des récits imaginés par J.R.R. Tolkien. Les Étymologies permettent de"
                + " suivre l'évolution d'une langue \" vivante \", de saisir son importance."
                + " Leur création a accompagné, si ce n'est guidé, la naissance du Seigneur des"
                + " Anneaux et de La Terre du Milieu.",
                book.getString(DBKey.DESCRIPTION, null));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Pocket", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(3, authors.size());

        Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Tolkien", author.getFamilyName());
        assertEquals("John Ronald Reuel", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_BNF);
        assertTrue(oIv.isPresent());
        assertEquals("cb11926763j", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000121441970", oIv.get());

        author = authors.get(1);
        assertEquals("Tolkien", author.getFamilyName());
        assertEquals("Christopher", author.getGivenNames());
        assertEquals(AuthorRole.EDITOR, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_BNF);
        assertTrue(oIv.isPresent());
        assertEquals("cb119267626", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000071393949", oIv.get());

        author = authors.get(2);
        assertEquals("Lauzon", author.getFamilyName());
        assertEquals("Daniel", author.getGivenNames());
        assertEquals(AuthorRole.TRANSLATOR, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_BNF);
        assertTrue(oIv.isPresent());
        assertEquals("cb15069993k", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000072518950", oIv.get());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series;
        series = allSeries.get(0);
        assertEquals("Imaginaire", series.getTitle());
        assertEquals("", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Pocket", series.getTitle());
        assertEquals("7375", series.getNumber());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;
        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9782266341417_0_.jpg"));
    }

    @Test
    public void parse9782756078311()
            throws SearchException, IOException, CredentialsException, CoverStorageException {

        final String locationHeader = "https://catalogue.bnf.fr/ark:/12148/cb458421422";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bnf_9782756078311;
        final String locationHeaderUM = "https://catalogue.bnf.fr/ark:/12148/cb458421422.unimarc";
        final int resIdUM = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bnf_9782756078311_unimarc;

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Document unimarcDocument = loadDocument(resIdUM, UTF_8, locationHeaderUM);

        final Book book = new Book();
        searchEngine.parse(context, document, unimarcDocument,
                           new boolean[]{true, false, false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Mise à jour", book.getString(DBKey.TITLE, null));
        assertEquals("9782756078311", book.getString(DBKey.ISBN, null));
        assertEquals("cb458421422", book.requireIdentifierValue(Identifier.SID_BNF));

        assertEquals("fre", book.getString(DBKey.LANGUAGE, null));
        assertEquals("2019", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("46", book.getString(DBKey.PAGES, null));

        assertEquals("14.5", book.getString(DBKey.PRICE_LISTED, null));
        assertEquals("EUR", book.getString(DBKey.PRICE_LISTED_CURRENCY, null));

        assertEquals(
                "Alors que Nävis est sur la piste d'un de ces mystérieux métamorphes,"
                + " celle-ci met fortuitement la main sur un dossier la concernant. Intriguée,"
                + " elle décide de le dérober et de le cracker avec l'aide de Juliette. Ce dossier"
                + " est un document d'archives récupéré sur le « Juniville 08 », vaisseau où"
                + " vivait notre héroïne avant d'être enrôlée par Sillage. Qui est-elle vraiment"
                + " et d'où vient elle ? Cet épisode nous le révèlera.",
                book.getString(DBKey.DESCRIPTION, null));


        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Delcourt", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Morvan", author.getFamilyName());
        assertEquals("Jean-David", author.getGivenNames());
        assertEquals(AuthorRole.WRITER, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_BNF);
        assertTrue(oIv.isPresent());
        assertEquals("cb12950081v", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000121449219", oIv.get());

        author = authors.get(1);
        assertEquals("Buchet", author.getFamilyName());
        assertEquals("Philippe", author.getGivenNames());
        assertEquals(AuthorRole.ARTIST, author.getRole());
        oIv = author.getIdentifierValue(Identifier.SID_BNF);
        assertTrue(oIv.isPresent());
        assertEquals("cb13205924m", oIv.get());
        oIv = author.getIdentifierValue(Identifier.SID_ISNI);
        assertTrue(oIv.isPresent());
        assertEquals("0000000121225055", oIv.get());

        final List<Series> allSeries = book.getSeries();
        assertNotNull(allSeries);
        assertEquals(2, allSeries.size());

        Series series;
        series = allSeries.get(0);
        assertEquals("Sillage", series.getTitle());
        assertEquals("20", series.getNumber());

        series = allSeries.get(1);
        assertEquals("Néopolis", series.getTitle());
        assertEquals("", series.getNumber());

        final String preferenceKey = searchEngine.getEngineId().getPreferenceKey();
        List<String> covers;
        covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(preferenceKey + "_9782756078311_0_.jpg"));
    }
}
