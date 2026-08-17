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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Does live lookups to the website !
 */
class SearchByIsbnTest
        extends BaseDBTest {

    private static final String TAG = "SearchByIsbnTest";


    private SearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Bedetheque.getConfig().setHttpLoggingEnabled(true);
        searchEngine = EngineId.Bedetheque.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void Isbn9781849182089()
            throws SearchException, CredentialsException, StorageException {
        // {series_list=[Series{id=0, title=`Lucky Luke`, complete=false, number=`148`},
        // language=anglais, format=Couverture souple,
        // date_published=2014-08, isbn=9781849182089,
        // pages=48, title=Dick Digger's Gold Mine,
        // author_list=[Author{id=0, familyName=`Morris`, givenNames=``, complete=false,
        //                     role=0b1001000000000001: Role{WRITER,ARTIST,COLORIST},
        // realAuthor=Author{id=0, familyName=`De Bevere`, givenNames=`Maurice`, complete=false,
        // role=0b0: Role{}, realAuthor=null}}],
        // publisher_list=[Publisher{id=0, name=`Cinebook`}]}]
        final BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setRawProductCode("9781849182089");
        final Book book = ((SearchEngine.ByIsbn) searchEngine).searchByIsbn(context, criteria);
        assertNotNull(book);
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("anglais", book.getString(DBKey.LANGUAGE, null));
        // this is good enough... the local junit tests do the full parse test
    }
}
