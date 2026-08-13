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

package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltEditionsTest
        extends BaseDBTest {

    private static final String TAG = "AltEditionsTest";

    private OpenLibrarySearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        searchEngine = (OpenLibrarySearchEngine) EngineId.OpenLibrary.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
        searchEngine.getConfig().setLogHttpGetRequests(true);    }

    @Test
    void searchAlternativeEditionsTest()
            throws SearchException {
        final List<AltEditionOpenLibrary> editions = searchEngine
                .searchAlternativeEditions(context, ISBN.parse("9780141339092"));

        //Log.d(TAG, editions.toString());

        // 2024-05-01: returned 87 results; the first was "9783551357793"
        // but as we're fetching live data, this might change of course.
        assertTrue(editions.size() > 1, "size=" + editions.size());

        // The order however, can be DIFFERENT EACH TIME !
        // This is just a crude test, so we just look for the test result
        final Optional<AltEditionOpenLibrary> oe =
                editions.stream()
                        .filter(ed -> "9783551357793".equals(ed.getProductCode()))
                        .findAny();
        assertTrue(oe.isPresent());
        final AltEditionOpenLibrary edition = oe.get();

        assertEquals("OL49350279M", edition.getSid());
        assertEquals("9783551357793", edition.getProductCode());
        assertEquals("Carlsen", edition.getPublisher());
        assertEquals("ger", edition.getLangIso3());
    }
}
