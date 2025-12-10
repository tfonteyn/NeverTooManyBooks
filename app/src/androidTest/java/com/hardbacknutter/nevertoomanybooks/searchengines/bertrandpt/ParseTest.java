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

package com.hardbacknutter.nevertoomanybooks.searchengines.bertrandpt;

import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    private static final String UTF_8 = "UTF-8";

    private BertrandPtSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (BertrandPtSearchEngine) EngineId.BertrandPt.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);
    }

    @Test
    public void parseMultiResult01()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader =
                "https://www.bertrand.pt/pesquisa/9789895812899";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bertrandpt_multi_9789895812899;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parseMultiResult(context, document, new boolean[]{true, false, false, false},
                                      book);
        Log.d(TAG, book.toString());

        assertEquals("A Livraria Cinnamon Bun", book.getString(DBKey.TITLE, null));
        assertEquals("9789895812899", book.getString(DBKey.ISBN, null));

        assertEquals("2024-11", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("320", book.getString(DBKey.PAGES, null));
        assertEquals("Capa mole", book.getString(DBKey.FORMAT, null));
        assertEquals("Português", book.getString(DBKey.LANGUAGE, null));

        // test is a dynamic download, can fail / needs updating
//        assertEquals(4.0f, book.getFloat(DBKey.RATING, realNumberParser));
//        assertEquals(new Money(BigDecimal.valueOf(15.60d), Money.EURO),
//                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

        final List<Tag> bookTags = book.getTags();
        assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatura"));
        assertTrue(tags.contains("Romance"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Quinta Essência", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Author author = authors.get(0);
        assertEquals("Gilmore", author.getFamilyName());
        assertEquals("Laurie", author.getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BertrandPt.getPreferenceKey()
                                          + "_9789895812899_0_.jpg"));
    }

    @Test
    public void parseMultiResult02()
            throws SearchException, IOException, CredentialsException, StorageException {

        final String locationHeader =
                "https://www.bertrand.pt/pesquisa/9789897734939";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.bertrandpt_multi_9789897734939;

        final RealNumberParser realNumberParser =
                new RealNumberParser(List.of(searchEngine.getLocale(context)));

        final Document document = loadDocument(resId, UTF_8, locationHeader);
        final Book book = new Book();
        searchEngine.parseMultiResult(context, document, new boolean[]{true, false, false, false},
                                      book);
        Log.d(TAG, book.toString());

        assertEquals("Fundação e Terra", book.getString(DBKey.TITLE, null));
        assertEquals("9789897734939", book.getString(DBKey.ISBN, null));

        assertEquals("2023-01", book.getString(DBKey.PUBLICATION_DATE, null));
        assertEquals("416", book.getString(DBKey.PAGES, null));
        assertEquals("Capa mole", book.getString(DBKey.FORMAT, null));
        assertEquals("Português", book.getString(DBKey.LANGUAGE, null));

        // test is a dynamic download, can fail / needs updating
        assertEquals(5.0f, book.getFloat(DBKey.RATING, realNumberParser), 0.1f);
//        assertEquals(new Money(BigDecimal.valueOf(17.91d), Money.EURO),
//                     book.getMoney(DBKey.PRICE_LISTED, realNumberParser));

        final List<Tag> bookTags = book.getTags();
        assertEquals(2, bookTags.size());
        final List<String> tags = bookTags.stream().map(Tag::getName).collect(Collectors.toList());
        assertTrue(tags.contains("Literatura"));
        assertTrue(tags.contains("Ficção Científica"));

        final List<Publisher> allPublishers = book.getPublishers();
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Saída de Emergência", allPublishers.get(0).getName());

        final List<Author> authors = book.getAuthors();
        assertNotNull(authors);
        assertEquals(1, authors.size());

        final Author author = authors.get(0);
        assertEquals("Asimov", author.getFamilyName());
        assertEquals("Isaac", author.getGivenNames());
        assertEquals(AuthorRole.UNKNOWN, author.getRole());

        final List<String> covers = CoverFileSpecArray.getList(book, 0);
        assertNotNull(covers);
        assertEquals(1, covers.size());
        assertTrue(covers.get(0).endsWith(EngineId.BertrandPt.getPreferenceKey()
                                          + "_9789897734939_0_.jpg"));
    }
}
