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

package com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormaliseSeriesTitlesTest
        extends BaseDBTest {

    private static final String TAG = "normaliseSeriesTitlesTe";

    private LastDodoSearchEngine searchEngine;
    private Book book;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        EngineId.LastDodoNl.getConfig().setLogHttpGetRequests(true);
        searchEngine = EngineId.LastDodoNl.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));

        book = new Book();
    }

    /** Dutch test data using site locale Dutch. */
    @Test
    void normalise() {
        book.add(Series.from("titel, De"));

        searchEngine.normaliseSeriesTitles(context, book);

        final List<Series> series = book.getSeries();
        assertEquals(1, series.size());
        assertEquals("De titel", series.get(0).getTitle());
    }

    /** Dutch test data using site locale Dutch. */
    @Test
    void normalise1() {
        book.add(Series.from("Dames van de Pillar To Post, De"));

        searchEngine.normaliseSeriesTitles(context, book);

        final List<Series> series = book.getSeries();
        assertEquals(1, series.size());
        assertEquals("De Dames van de Pillar To Post", series.get(0).getTitle());
    }

    /** Dutch test data using site locale Dutch. */
    @Test
    void normalise2() {
        book.add(Series.from("titel, De"));
        book.add(Series.from("De titel"));

        searchEngine.normaliseSeriesTitles(context, book);

        final List<Series> series = book.getSeries();
        assertEquals(1, series.size());
        assertEquals("De titel", series.get(0).getTitle());
    }
}
