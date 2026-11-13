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

package com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo;

import android.content.Context;
import android.util.Log;

import java.net.CookieManager;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SingleLookupTest
        extends BaseDBTest {

    private static final String TAG = "SingleLookupTest";

    private StripInfoSearchEngine searchEngine;
    private Context context;
    private CookieManager cookieManager;

    @Override
    @BeforeEach
    protected void setup()
            throws Exception {
        super.setup();
        cookieManager = ServiceLocator.getInstance().getCookieManager();
        context = createContext();

        EngineId.StripInfoBe.getConfig().setHttpLoggingEnabled(true);
        searchEngine = EngineId.StripInfoBe.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void yoko()
            throws SearchException, CredentialsException, StorageException {
        final String isbn = "9789031442287";
        final BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setRawProductCode(isbn);

        final Book book = searchEngine.searchByIsbn(context, criteria);
        Log.d(TAG, book.toString());
    }
}
