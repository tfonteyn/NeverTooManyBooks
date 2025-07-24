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

package com.hardbacknutter.nevertoomanybooks.searchengines.librarything;

import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionIsbn;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

@SuppressWarnings({"MissingJavadoc"})
public class ParseTest
        extends BaseDBTest {

    private static final String TAG = "ParseTest";

    private LibraryThingSearchEngine searchEngine;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (LibraryThingSearchEngine) EngineId.LibraryThing.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        //noinspection DataFlowIssue
        searchEngine.getEngineId().getConfig().setLogHttpGetRequests(context, true);

        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .putString(LibraryThingSearchEngine.PK_API_TOKEN,
                                    "")
                         .apply();
    }

    @Test
    public void p1()
            throws SearchException, CredentialsException {

        final List<AltEditionIsbn> isbns = searchEngine
                .searchAlternativeEditions(context, "0441172717");

        Log.d(TAG, isbns.toString());

        assertTrue("did you add the api token?", isbns.size() > 200);
    }
}
