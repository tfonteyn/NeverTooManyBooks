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

package com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("MissingJavadoc")
public class NormalizeSeriesTitlesTest
        extends BaseDBTest {

    private static final String TAG = "normalizeSeriesTitlesTe";

    private LastDodoSearchEngine searchEngine;
    private Book book;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        book = new Book();
        searchEngine = (LastDodoSearchEngine) EngineId.LastDodoNl.createSearchEngine(context);
        searchEngine.setCaller(new TestProgressListener(TAG));
    }

    /** Dutch test data using site locale Dutch. */
    @Test
    public void normalize() {
        book.add(Series.from("titel, De"));

        searchEngine.normalizeSeriesTitles(context, book);

        final List<Series> series = book.getSeries();
        assertEquals(1, series.size());
        assertEquals("De titel", series.get(0).getTitle());
    }

    /** Dutch test data using site locale Dutch. */
    @Test
    public void normalize1() {
        book.add(Series.from("Dames van de Pillar To Post, De"));

        searchEngine.normalizeSeriesTitles(context, book);

        final List<Series> series = book.getSeries();
        assertEquals(1, series.size());
        assertEquals("De Dames van de Pillar To Post", series.get(0).getTitle());
    }

    /** Dutch test data using site locale Dutch. */
    @Test
    public void normalize2() {
        book.add(Series.from("titel, De"));
        book.add(Series.from("De titel"));

        searchEngine.normalizeSeriesTitles(context, book);

        final List<Series> series = book.getSeries();
        assertEquals(1, series.size());
        assertEquals("De titel", series.get(0).getTitle());
    }
}
