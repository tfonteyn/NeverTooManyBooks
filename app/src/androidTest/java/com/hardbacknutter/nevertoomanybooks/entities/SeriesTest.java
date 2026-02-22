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

package com.hardbacknutter.nevertoomanybooks.entities;

import android.os.Parcel;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesTest
        extends BaseDBTest {

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    @Test
    void parcelling() {
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

    @Test
    void checkForSeriesNameInTitle01() {
        final Book book = new Book();
        book.setTitle("Isle of the Dead");
        Series.checkForSeriesNameInTitle(book);
        assertEquals("Isle of the Dead", book.getString(DBKey.TITLE, null));
        final List<Series> allSeries = book.getSeries();
        assertTrue(allSeries.isEmpty());
    }

    @Test
    void checkForSeriesNameInTitle02() {
        final Book book = new Book();
        book.setTitle("The Last Colony (Old Man's War, #3)");
        Series.checkForSeriesNameInTitle(book);
        assertEquals("The Last Colony", book.getString(DBKey.TITLE, null));
        final List<Series> allSeries = book.getSeries();
        assertEquals(1, allSeries.size());
        final Series series = allSeries.get(0);
        assertEquals("Old Man's War", series.getTitle());
        assertEquals("3", series.getNumber());
    }

    @Test
    void checkForSeriesNameInTitle03() {
        final Book book = new Book();
        book.setTitle("Kip,Koek en Ei (Agent 212, #12)");
        Series.checkForSeriesNameInTitle(book);
        assertEquals("Kip,Koek en Ei", book.getString(DBKey.TITLE, null));
        final List<Series> allSeries = book.getSeries();
        assertEquals(1, allSeries.size());
        final Series series = allSeries.get(0);
        assertEquals("Agent 212", series.getTitle());
        assertEquals("12", series.getNumber());
    }

    @Test
    void checkForSeriesNameInTitle04() {
        final Book book = new Book();
        book.setTitle("Behind the Walls of Terra (World of Tiers 4)");
        Series.checkForSeriesNameInTitle(book);
        assertEquals("Behind the Walls of Terra", book.getString(DBKey.TITLE, null));
        final List<Series> allSeries = book.getSeries();
        assertEquals(1, allSeries.size());
        final Series series = allSeries.get(0);
        assertEquals("World of Tiers", series.getTitle());
        assertEquals("4", series.getNumber());
    }
}
