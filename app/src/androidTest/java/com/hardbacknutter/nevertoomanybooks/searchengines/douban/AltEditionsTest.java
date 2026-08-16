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

package com.hardbacknutter.nevertoomanybooks.searchengines.douban;

import android.util.Log;

import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
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

    private DoubanSearchEngine searchEngine;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.Douban.getConfig().setLogHttpGetRequests(true);
        searchEngine = EngineId.Douban.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    @Test
    void searchAlternativeEditionsTest()
            throws SearchException, CredentialsException {
        final List<AltEditionDouban> editions = searchEngine
                .searchAlternativeEditions(context, ISBN.parse("9787536692930"));

        Log.d(TAG, editions.toString());

        // 2024-07-1: returned 3 results
        // but as we're fetching live data, this might change of course.
        assertTrue(editions.size() > 1, "size=" + editions.size());

        // The order however, can be DIFFERENT EACH TIME !
        // This is just a crude test, so we just look for the test result
        final Optional<AltEditionDouban> oe = editions.stream()
                                                      .filter(ed -> ed.getSid() == 36892731)
                                                      .findAny();
        assertTrue(oe.isPresent());
        final AltEditionDouban edition = oe.get();
        assertEquals(36892731, edition.getSid());
        assertEquals("https://book.douban.com/subject/36892731/", edition.getBookUrl());
        assertEquals("https://img3.doubanio.com/view/subject/m/public/s34863232.jpg",
                     edition.getCoverUrl());
    }
}
