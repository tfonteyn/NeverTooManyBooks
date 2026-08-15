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

package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;


import android.util.Log;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Does live lookups to the website !
 */
@SuppressWarnings("MissingJavadoc")
class SearchByIsbnTest
        extends BaseDBTest {

    private static final String TAG = "SearchByIsbnTest";


    private SearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Amazon.getConfig().setLogHttpGetRequests(true);
        searchEngine = EngineId.Amazon.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void Isbn0702315516()
            throws SearchException, CredentialsException, StorageException {
        final BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setRawProductCode("0702315516");
        final Book book = ((SearchEngine.ByIsbn) searchEngine).searchByIsbn(context, criteria);
        assertNotNull(book);
        assertFalse(book.isEmpty());
        Log.d(TAG, book.toString());

        // At the time of this test comment, we were using amazon.es:
        // 2023-06-25T13:58:43.156|SearchByIsbnTest|WARN|DataManager{rawData=Bundle[{
        // language=Inglés,
        // format=Tapa dura,
        // date_published=28 abril 2022,
        // asin=0702315516,
        // isbn=978-0702315510,
        // pages=96,
        // title=The Imagination Chamber: Philip Pullman's breathtaking return to the world
        //       of His Dark Materials: cosmic rays from Lyra's universe,
        // list_price=12.46,
        // author_list=[Author{id=0, familyName=`Pullman`, givenNames=`Philip`, complete=false,
        //                     role=0b1: Role{TYPE_WRITER}, realAuthor=null}],
        // publisher_list=[Publisher{id=0, name=`Scholastic`}], list_price_currency=EUR}]}|.

        // this is good enough... the local junit tests do the full parse test
    }
}

