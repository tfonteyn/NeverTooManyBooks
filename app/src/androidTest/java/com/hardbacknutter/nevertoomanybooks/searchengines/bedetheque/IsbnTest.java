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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Does live lookups to the website !
 */
public class IsbnTest
        extends BaseDBTest {

    private static final String TAG = "IsbnTest";


    private SearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup();

        searchEngine = EngineId.Bedetheque.createSearchEngine();
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    public void Isbn9781849182089()
            throws SearchException, CredentialsException, StorageException {
        // {series_list=[Series{id=0, title=`Lucky Luke`, complete=false, number=`148`},
        // language=anglais, format=Couverture souple,
        // date_published=2014-08, isbn=9781849182089,
        // pages=48, title=Dick Digger's Gold Mine,
        // author_list=[Author{id=0, familyName=`Morris`, givenNames=``, complete=false,
        //                     type=0b1001000000000001:
        //                     Type{TYPE_WRITER,TYPE_ARTIST,TYPE_COLORIST},
        // realAuthor=Author{id=0, familyName=`De Bevere`, givenNames=`Maurice`, complete=false,
        // type=0b0: Type{}, realAuthor=null}}],
        // publisher_list=[Publisher{id=0, name=`Cinebook`}]}]
        final Book book = ((SearchEngine.ByIsbn) searchEngine)
                .searchByIsbn(context, "9781849182089", new boolean[]{false, false});
        assertNotNull(book);
        assertFalse(book.isEmpty());
        assertEquals("Softcover", book.getString(DBKey.FORMAT, null));
        assertEquals("anglais", book.getString(DBKey.LANGUAGE, null));
        // this is good enough... the local junit tests do the full parse test
    }
}
