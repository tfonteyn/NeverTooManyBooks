/*
 * @Copyright 2018-2025 HardBackNutter
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
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ebook:
 * https://www.databazeknih.cz/prehled-knihy/vecer-na-bezdezu-krivoklad-krkonosska-pout-543519
 */
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private static final String UTF_8 = "UTF-8";

    private DatabazeKnihSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (DatabazeKnihSearchEngine) EngineId.DatabazeKnih.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    public void parse01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.databazeknih.cz/prehled-knihy/pripad-levoruke-damy-546691";

        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.databazeknih_9788025368626;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("Případ levoruké dámy", book.getString(DBKey.TITLE, null));
        assertEquals("9788025368626", book.getString(DBKey.ISBN, null));
        assertEquals("546691", book.requireIdentifierValue(Identifier.SID_DATABAZE_KNIH));
        assertEquals("2024", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("český", book.getString(DBKey.LANGUAGE, null));
        assertEquals("192", book.getString(DBKey.PAGES, null));
        assertEquals(4.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);
        assertEquals("pevná / vázaná", book.getString(DBKey.FORMAT, null));

        assertEquals("The Case of the Left-Handed Lady",
                     book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE, null));
        assertEquals("2007", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));

        assertEquals(
                "Enola se stále skrývá před svým bratrem – slavným detektivem Sherlockem Holmesem. Když ale objeví tajnou skrýš plnou brilantních kreseb, vyrazí po stopě jejich autorky, mladé lady Cecily, která beze stopy zmizela ze své ložnice. Enola se vydá do nočních ulic Londýna, kterými se potulují vrazi, aby rozluštila záhadu a zachránila talentovanou aristokratku před mocným padouchem. Riskuje však, že odhalí víc, než by měla...",
                book.getString(DBKey.DESCRIPTION, null));

        assertEquals("klasická kniha",
                     book.getString(DatabazeKnihSearchEngine.SiteField.FORMA, null));

        final List<Tag> bookTags = book.getTags();

        assertEquals(13, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        // the 3 "genre" tags
        assertTrue(tags.contains("Literatura světová"));
        assertTrue(tags.contains("Detektivky, krimi"));
        assertTrue(tags.contains("Pro děti a mládež"));
        // the 10 "Štítky" tags
        assertTrue(tags.contains("Londýn"));
        assertTrue(tags.contains("19. století"));
        assertTrue(tags.contains("americká literatura"));
        assertTrue(tags.contains("Velká Británie"));
        assertTrue(tags.contains("hypnóza"));
        assertTrue(tags.contains("historické detektivky"));
        assertTrue(tags.contains("mysteriózní, mystéria"));
        assertTrue(tags.contains("únosy"));
        assertTrue(tags.contains("pro dospívající mládež (young adult)"));
        assertTrue(tags.contains("fikce"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Fragment (CZ)", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author;
        Optional<String> oIv;

        author = authors.get(0);
        assertEquals("Springer", author.getFamilyName());
        assertEquals("Nancy", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());
        oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        assertTrue(oIv.isPresent());
        assertEquals("9571", oIv.get());

        author = authors.get(1);
        assertEquals("Davidová", author.getFamilyName());
        assertEquals("Vendula", author.getGivenNames());
        assertEquals(Author.TYPE_TRANSLATOR, author.getType());
        oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        assertTrue(oIv.isPresent());
        assertEquals("24256", oIv.get());

        final List<Series> series = book.getSeries();
        assertNotNull(series);
        assertEquals(1, series.size());

        assertEquals("Enola Holmesová", series.get(0).getTitle());
        assertEquals("2. díl", series.get(0).getNumber());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.DatabazeKnih.getPreferenceKey()
                                          + "_9788025368626_0_.jpg"));
    }


    @Test
    public void parse02()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader = "https://www.databazeknih.cz/prehled-knihy/p-s-267961";

        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.databazeknih_9788024929613;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parse(context, document, new boolean[]{false, false}, book);
        Log.d(TAG, book.toString());

        assertEquals("P.S.", book.getString(DBKey.TITLE, null));
        assertEquals("9788024929613", book.getString(DBKey.ISBN, null));
        assertEquals("267961", book.requireIdentifierValue(Identifier.SID_DATABAZE_KNIH));
        assertEquals("2015", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("český", book.getString(DBKey.LANGUAGE, null));
        assertEquals("216", book.getString(DBKey.PAGES, null));
        assertEquals(4.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);
        assertEquals("pevná / vázaná", book.getString(DBKey.FORMAT, null));
        assertEquals("2015", book.getString(DBKey.FIRST_PUBLICATION_DATE, null));

        assertEquals(
                "Soubor fejetonů, kterými herečka Aňa Geislerová přispívala do magazínu ELLE, nyní vychází knižně. V souhrnném vydání vyvstává příběh, který mohl dříve čtenářům snadno uniknout; příběh části života, ve které jako by se odehrálo úplně všechno. Příchod nových členů rodiny, odcházení těch starých, lásky, pády, úspěchy, problémy. Pět podivuhodných let, k nimž autorka přidala ještě několik dříve nepublikovaných textů.",
                book.getString(DBKey.DESCRIPTION, null));

        assertEquals("klasická kniha",
                     book.getString(DatabazeKnihSearchEngine.SiteField.FORMA, null));

        final List<Tag> bookTags = book.getTags();

        assertEquals(8, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Fejetony, eseje"));
        assertTrue(tags.contains("Literatura česká"));
        assertTrue(tags.contains("Cestopisy a místopisy"));
        assertTrue(tags.contains("prvotina"));
        assertTrue(tags.contains("česká literatura"));
        assertTrue(tags.contains("ze života"));
        assertTrue(tags.contains("deníkové záznamy"));
        assertTrue(tags.contains("Magnesia Litera"));


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
        assertEquals(Author.TYPE_WRITER, author.getType());
        oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        assertTrue(oIv.isPresent());
        assertEquals("85414", oIv.get());

        author = author.getRealAuthor();
        assertNotNull(author);
        assertEquals("Geislerová", author.getFamilyName());
        assertEquals("Anna", author.getGivenNames());


        author = authors.get(1);
        assertEquals("Geislerová", author.getFamilyName());
        assertEquals("Lela", author.getGivenNames());
        assertEquals(Author.TYPE_COVER_ARTIST | Author.TYPE_ARTIST, author.getType());
        oIv = author.getIdentifierValue(Identifier.SID_DATABAZE_KNIH);
        assertTrue(oIv.isPresent());
        assertEquals("70610", oIv.get());
    }

}
