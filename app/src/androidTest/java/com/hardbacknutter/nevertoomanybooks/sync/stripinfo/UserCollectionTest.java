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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserCollectionTest
        extends BaseDBTest {

    private static final String TAG = "UserCollectionTest";

    private static final String UTF_8 = "UTF-8";
    /**
     * This user does not exist on the site, it was manually replaced in the sample file
     * username: NTMBUser
     */
    private static final String userId = "666";
    private final ProgressListener logger =
            new TestProgressListener("UserCollectionTest");
    private final Bookshelf ownedBookshelf = new Bookshelf(
            "owned", BuiltinStyle.HARD_DEFAULT_UUID);
    private final Bookshelf wishlistBookshelf = new Bookshelf(
            "wishlist", BuiltinStyle.HARD_DEFAULT_UUID);
    private BookshelfMapper bookshelfMapper;
    private StripInfoSearchEngine searchEngine;
    private MoneyParser moneyParser;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (StripInfoSearchEngine) EngineId.StripInfoBe.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        final Locale siteLocale = searchEngine.getLocale(context);
        final List<Locale> allLocales = List.of(siteLocale);
        moneyParser = new MoneyParser(siteLocale, allLocales);

        bookshelfMapper = new BookshelfMapper();
    }

    @Test
    void parseCollectionPage()
            throws IOException, SearchException {

        final String locationHeader = "https://www.stripinfo.be/userCollection/index/666/0/0/0000";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripinfo_collection;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        final UserCollection uc = new UserCollection(context, searchEngine, userId,
                                                     bookshelfMapper);

        final List<Book> collection = uc.parseDocument(document, 1, logger);
        assertFalse(collection.isEmpty());

        assertEquals(3, uc.getMaxPages());
        assertNotNull(collection);

        assertEquals(25, collection.size());

        assertEquals(25, collection
                .stream()
                .map(Book::getBookshelves)
                .count());

        final Book b0 = collection.get(0);
        assertNotNull(b0);

        final StripInfoCollectionData collectionData =
                b0.getParcelable(StripInfoCollectionData.BKEY);
        assertNotNull(collectionData);

        assertEquals("5435", b0.requireIdentifierValue(Identifier.SID_STRIP_INFO));
        assertEquals(5408, collectionData.getCollectionId());
        assertEquals(1, collectionData.getAmount());
        assertTrue(collectionData.isOwned());
        assertTrue(collectionData.isWanted());

        assertEquals(45f, b0.getDouble(DBKey.PRICE_PAID,
                                       moneyParser.getRealNumberParser()), 0);
        assertEquals(MoneyParser.EUR, b0.getString(DBKey.PRICE_PAID_CURRENCY, null));
        assertEquals("2021-03-10", b0.getString(DBKey.DATE_ACQUIRED, null));
    }

    @Test
    void parseCollectionLastPage()
            throws IOException, SearchException {

        // The "3" near the end of the url == the 3rd page from the collection.
        final String locationHeader = "https://www.stripinfo.be/userCollection/index/666/0/3/0000";
        final int resId = com.hardbacknutter.nevertoomanybooks.test
                .R.raw.stripinfo_collection_last_page;

        final Document document = loadDocument(resId, UTF_8, locationHeader);

        final UserCollection uc = new UserCollection(context, searchEngine, userId,
                                                     bookshelfMapper);

        final List<Book> collection = uc.parseDocument(document, 3, logger);
        assertFalse(collection.isEmpty());

        assertNotNull(collection);
        assertEquals(1, collection.size());
    }
}
