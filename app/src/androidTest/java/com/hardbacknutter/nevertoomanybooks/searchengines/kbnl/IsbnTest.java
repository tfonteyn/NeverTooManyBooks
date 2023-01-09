/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.content.Context;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.BookData;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

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

    private Context context;

    private SearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup();

        context = serviceLocator.getLocalizedAppContext();

        searchEngine = Site.Type.Data.getSite(EngineId.KbNl).getSearchEngine();
        searchEngine.setCaller(new MockCancellable());
    }

    @Test
    public void MultiResult()
            throws SearchException, CredentialsException, StorageException {

        // this will first hit a multi-result page, take the first book, and fetch that.
        // Run in debug for a full verification if in doubt
        final BookData bookData = ((SearchEngine.ByIsbn) searchEngine)
                .searchByIsbn(context, "9020612476", new boolean[]{false, false});

        Logger.d(TAG, "", bookData.toString());
        assertNotNull(bookData);
        assertFalse(bookData.isEmpty());
        assertEquals("De Discus valt aan", bookData.getString(DBKey.TITLE, null));
        assertEquals("1973", bookData.getString(DBKey.BOOK_PUBLICATION__DATE, null));
        // this is good enough... the local junit tests do the full parse test
    }

    private static class MockCancellable
            implements Cancellable {
        @Override
        public void cancel() {

        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
