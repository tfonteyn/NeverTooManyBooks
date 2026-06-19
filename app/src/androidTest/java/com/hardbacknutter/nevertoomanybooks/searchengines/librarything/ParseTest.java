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

package com.hardbacknutter.nevertoomanybooks.searchengines.librarything;

import android.util.Log;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";
    /** THIS TOKEN NEEDS TO BE TEMPORARILY FILLED IN - BUT NEVER COMMIT IT TO GIT! */
    private static final String TOKEN = "";

    private LibraryThingSearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        if (TOKEN.isEmpty()) {
            throw new IllegalArgumentException("TOKEN NOT SET");
        }

        searchEngine = (LibraryThingSearchEngine) EngineId.LibraryThing.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(true);

        ServiceLocator.getInstance().getSharedPreferences()
                      .edit()
                      .putString(LibraryThingSearchEngine.PK_API_TOKEN, TOKEN)
                      .apply();
    }

    @Test
    void p1()
            throws SearchException, CredentialsException {

        final List<AltEditionProductCode> isbns = searchEngine
                .searchAlternativeEditions(context, ISBN.parse("0441172717"));

        Log.d(TAG, isbns.toString());

        assertTrue(isbns.size() > 200, "Did you add the api token?");
    }
}
