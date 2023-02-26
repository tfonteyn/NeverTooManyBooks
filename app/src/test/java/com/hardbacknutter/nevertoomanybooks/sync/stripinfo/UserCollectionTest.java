/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.xml.parsers.ParserConfigurationException;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class UserCollectionTest
        extends JSoupBase {

    private static final String UTF_8 = "UTF-8";

    /**
     * This user does not exist on the site, it was manually replaced in the sample file
     * username: NTMBUser
     */
    private static final String userId = "666";

    private final ProgressListener logger =
            new TestProgressListener("UserCollectionTest");

    private final Bookshelf ownedBookshelf = new Bookshelf(
            "owned", BuiltinStyle.DEFAULT_UUID);
    private final Bookshelf wishlistBookshelf = new Bookshelf(
            "wishlist", BuiltinStyle.UUID_FOR_TESTING_ONLY);
    @Mock
    BookshelfMapper bookshelfMapper;

    @Override
    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();

        when(bookshelfMapper.getOwnedBooksBookshelf(any()))
                .thenReturn(Optional.of(ownedBookshelf));
        when(bookshelfMapper.getWishListBookshelf(any()))
                .thenReturn(Optional.of(wishlistBookshelf));
    }

    @Test
    void parseCollectionPage()
            throws IOException, SearchException {
        setLocale(Locale.FRANCE);
        final RealNumberParser realNumberParser = new RealNumberParser(context);

        final String locationHeader = "https://www.stripinfo.be/userCollection/index/666/0/0/0000";
        final String filename = "/stripinfo/collection.html";

        final StripInfoSearchEngine searchEngine = (StripInfoSearchEngine)
                EngineId.StripInfoBe.createSearchEngine(context);

        final UserCollection uc = new UserCollection(context, searchEngine, userId,
                                                     bookshelfMapper);

        final Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, UTF_8, locationHeader);

            assertNotNull(document);
            assertTrue(document.hasText());

            final Optional<List<Book>> oCollection =
                    uc.parseDocument(context, document, 1, logger);
            assertTrue(oCollection.isPresent());

            final List<Book> collection = oCollection.get();
            assertEquals(3, uc.getMaxPages());
            assertNotNull(collection);

            assertEquals(25, collection.size());

            assertEquals(25, collection
                    .stream()
                    .map(Book::getBookshelves)
                    .count());

            final Book b0 = collection.get(0);
            assertEquals(5435, b0.getLong(DBKey.SID_STRIP_INFO));
            assertEquals(5408, b0.getLong(DBKey.STRIP_INFO_COLL_ID));

            assertEquals(45f, b0.getDouble(DBKey.PRICE_PAID, realNumberParser));
            assertEquals("EUR", b0.getString(DBKey.PRICE_PAID_CURRENCY, null));
            assertEquals("2021-03-10", b0.getString(DBKey.DATE_ACQUIRED, null));

            assertEquals(1, b0.getInt(DBKey.STRIP_INFO_AMOUNT));
            assertTrue(b0.getBoolean(DBKey.STRIP_INFO_OWNED));
            assertTrue(b0.getBoolean(DBKey.STRIP_INFO_WANTED));

        }
    }

    @Test
    void parseCollectionLastPage()
            throws IOException, SearchException {
        setLocale(Locale.FRANCE);

        // The "3" == the 3rd page from the collection.
        final String locationHeader = "https://www.stripinfo.be/userCollection/index/666/0/3/0000";
        final String filename = "/stripinfo/collection-last-page.html";

        final StripInfoSearchEngine searchEngine = (StripInfoSearchEngine)
                EngineId.StripInfoBe.createSearchEngine(context);

        final UserCollection uc = new UserCollection(context, searchEngine, userId,
                                                     bookshelfMapper);

        final Document document;
        try (InputStream is = this.getClass().getResourceAsStream(filename)) {
            assertNotNull(is);
            document = Jsoup.parse(is, UTF_8, locationHeader);

            assertNotNull(document);
            assertTrue(document.hasText());

            final Optional<List<Book>> oCollection =
                    uc.parseDocument(context, document, 3, logger);
            assertTrue(oCollection.isPresent());

            final List<Book> collection = oCollection.get();
            assertNotNull(collection);
            assertEquals(1, collection.size());
        }
    }
}
