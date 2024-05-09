/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

/**
 * Does live lookups to the website !
 */
@SuppressWarnings("MissingJavadoc")
public class SearchByIsbnTest
        extends BaseDBTest {

    private static final String TAG = "SearchByIsbnTest";

    private SearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = EngineId.KbNl.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    public void MultiResult()
            throws SearchException, CredentialsException, StorageException {

        // this will first hit a multi-result page, take the first book, and fetch that.
        final Book book = ((SearchEngine.ByIsbn) searchEngine)
                .searchByIsbn(context, "9020612476", new boolean[]{false, false});

        ParseTest.verify9020612476(book);
    }
}
