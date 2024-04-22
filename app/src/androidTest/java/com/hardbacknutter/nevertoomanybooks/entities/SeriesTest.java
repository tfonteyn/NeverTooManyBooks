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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.os.Parcel;

import androidx.test.filters.MediumTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@MediumTest
@SuppressWarnings("MissingJavadoc")
public class SeriesTest
        extends BaseDBTest {

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    public void parcelling() {
        final Series series = Series.from("test");
        series.setNumber("5");

        final Parcel parcel = Parcel.obtain();
        series.writeToParcel(parcel, series.describeContents());
        parcel.setDataPosition(0);
        final Series pSeries = Series.CREATOR.createFromParcel(parcel);

        assertEquals(pSeries, series);

        assertEquals(pSeries.getId(), series.getId());
        assertEquals(pSeries.getTitle(), series.getTitle());
        assertEquals(pSeries.getNumber(), series.getNumber());
        assertEquals(pSeries.isComplete(), series.isComplete());
    }

    /**
     * Fairly generic and 'normally' sorted names and numbers.
     *
     * @throws DaoWriteException on conflicts
     */
    @Test
    public void pruneSeries01List()
            throws DaoWriteException {
        final Locale bookLocale = Locale.getDefault();
        final SeriesDao seriesDao = serviceLocator.getSeriesDao();

        final List<Series> list = new ArrayList<>();
        Series series;

        // keep, position 0
        series = Series.from("The series (5)");
        seriesDao.fixId(context, series, bookLocale);
        long id0 = series.getId();
        if (id0 == 0) {
            id0 = seriesDao.insert(context, series, bookLocale);
        }
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
        seriesDao.fixId(context, series, bookLocale);
        long id1 = series.getId();
        if (id1 == 0) {
            id1 = seriesDao.insert(context, series, bookLocale);
        }
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
        seriesDao.fixId(context, series, bookLocale);
        long id2 = series.getId();
        if (id2 == 0) {
            id2 = seriesDao.insert(context, series, bookLocale);
        }
        series.setId(100);
        list.add(series);

        final boolean modified = seriesDao.pruneList(context, list, item -> bookLocale);

        assertTrue(list.toString(), modified);
        assertEquals(list.toString(), 3, list.size());

        assertTrue(id0 > 0);
        assertTrue(id1 > 0);
        assertTrue(id2 > 0);

        series = list.get(0);
        assertEquals(id0, series.getId());
        assertEquals("The series", series.getTitle());
        assertEquals("5", series.getNumber());
        assertTrue(series.isComplete());

        series = list.get(1);
        assertEquals(id1, series.getId());
        assertEquals("De reeks", series.getTitle());
        assertEquals("1", series.getNumber());

        series = list.get(2);
        assertEquals(id2, series.getId());
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
    public void pruneReorderedDuplications() {
        final Locale bookLocale = Locale.getDefault();
        final SeriesDao seriesDao = serviceLocator.getSeriesDao();

        final List<Series> list = new ArrayList<>();

        final Series series1 = Series.from("The title");
        series1.setId(1);
        series1.setNumber("1");
        list.add(series1);

        final Series series2 = Series.from("title, The");
        // Set the SAME id, so the only diff is the title!
        series2.setId(1);
        series2.setNumber("1");
        list.add(series2);


        // Note we force normalization here - this is the test for it... duh...
        final boolean modified = seriesDao.pruneList(context, list, true,
                                                     item -> bookLocale);

        assertTrue("Failed to prune", modified);
        assertEquals(1, list.size());
    }

    @Test
    public void checkForSeriesNameInTitle01() {
        final Book book = new Book();
        book.putString(DBKey.TITLE, "Isle of the Dead");
        Series.checkForSeriesNameInTitle(book);
        assertEquals("Isle of the Dead", book.getTitle());
        final List<Series> allSeries = book.getSeries();
        assertTrue(allSeries.isEmpty());
    }

    @Test
    public void checkForSeriesNameInTitle02() {
        final Book book = new Book();
        book.putString(DBKey.TITLE, "The Last Colony (Old Man's War, #3)");
        Series.checkForSeriesNameInTitle(book);
        assertEquals("The Last Colony", book.getTitle());
        final List<Series> allSeries = book.getSeries();
        assertEquals(1, allSeries.size());
        final Series series = allSeries.get(0);
        assertEquals("Old Man's War", series.getTitle());
        assertEquals("3", series.getNumber());
    }

    @Test
    public void checkForSeriesNameInTitle03() {
        final Book book = new Book();
        book.putString(DBKey.TITLE, "Kip,Koek en Ei (Agent 212, #12)");
        Series.checkForSeriesNameInTitle(book);
        assertEquals("Kip,Koek en Ei", book.getTitle());
        final List<Series> allSeries = book.getSeries();
        assertEquals(1, allSeries.size());
        final Series series = allSeries.get(0);
        assertEquals("Agent 212", series.getTitle());
        assertEquals("12", series.getNumber());
    }

    @Test
    public void checkForSeriesNameInTitle04() {
        final Book book = new Book();
        book.putString(DBKey.TITLE, "Behind the Walls of Terra (World of Tiers 4)");
        Series.checkForSeriesNameInTitle(book);
        assertEquals("Behind the Walls of Terra", book.getTitle());
        final List<Series> allSeries = book.getSeries();
        assertEquals(1, allSeries.size());
        final Series series = allSeries.get(0);
        assertEquals("World of Tiers", series.getTitle());
        assertEquals("4", series.getNumber());
    }
}
