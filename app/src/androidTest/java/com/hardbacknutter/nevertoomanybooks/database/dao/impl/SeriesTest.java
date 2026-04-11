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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesTest
        extends BaseDBTest {

    private static final String THE_TITLE = "The title";
    private static final String TITLE_THE = "title, The";

    private SeriesDao seriesDao;

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);

        seriesDao = serviceLocator.getSeriesDao();
    }

    /**
     * Fairly generic and 'normally' sorted names and numbers.
     */
    @Test
    void pruneSeries01List() {
        final Locale bookLocale = Locale.getDefault();

        final List<Series> list = new ArrayList<>();
        Series series;

        // keep, position 0
        series = Series.from("The series (5)");
        series.setId(100);
        series.setComplete(true);
        list.add(series);

        // discard in favour of position 0 which has a number set
        series = Series.from("The series");
        series.setId(100);
        list.add(series);

        // discard in favour of position 1 (added two below here) which has a number set
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);

        // discard in favour of position 1 (added one below here) which has a number set
        series = Series.from("De reeks");
        series.setId(200);
        list.add(series);

        // keep, position 1
        series = Series.from("De reeks (1)");
        series.setId(200);
        list.add(series);

        // discard in favour of position 0 where we already had the number "5".
        // Note the difference in 'isComplete' is disregarded (first occurrence 'wins')
        series = Series.from("The series (5)");
        series.setId(100);
        series.setComplete(false);
        list.add(series);

        // keep, position 2. Note duplicate id, but different nr as compared to position 0
        series = Series.from("The series (6)");
        series.setId(100);
        list.add(series);

        // Explicit: NO normalisation
        final boolean modified = seriesDao.pruneList(context, list, false,
                                                     item -> bookLocale,
                                                     (p, l) -> {
                                                     });

        assertTrue(modified, list.toString());
        assertEquals(3, list.size(), list.toString());

        series = list.get(0);
        assertEquals(100, series.getId());
        assertEquals("The series", series.getTitle());
        assertEquals("5", series.getNumber());
        assertTrue(series.isComplete());

        series = list.get(1);
        assertEquals(200, series.getId());
        assertEquals("De reeks", series.getTitle());
        assertEquals("1", series.getNumber());

        series = list.get(2);
        assertEquals(100, series.getId());
        assertEquals("The series", series.getTitle());
        assertEquals("6", series.getNumber());
    }

    /**
     * Prune a list which contains both the non-reordered AND the reordered name (of a series).
     * i.e.:  "The title" and "title, The" (with same number)
     * <p>
     * Original issue coming from isbn 9789463941914 on lastdodo.nl
     */
    @Test
    void pruneReorderedDuplications() {
        final Locale bookLocale = Locale.getDefault();

        final List<Series> list = new ArrayList<>();

        final Series series1 = Series.from(THE_TITLE);
        series1.setId(100);
        series1.setNumber("1");
        list.add(series1);

        final Series series2 = Series.from(TITLE_THE);
        // Set the SAME id, so the only diff is the title!
        series2.setId(100);
        series2.setNumber("1");
        list.add(series2);

        // FORCE normalisation - this is the test for it... duh...
        final boolean modified = seriesDao.pruneList(context, list, true,
                                                     item -> bookLocale,
                                                     (p, l) -> {
                                                     });

        assertTrue(modified, list.toString());
        assertEquals(1, list.size());

        Series series;

        series = list.get(0);
        assertEquals(100, series.getId());
        assertEquals(THE_TITLE, series.getTitle());
        assertEquals("1", series.getNumber());
    }
}
