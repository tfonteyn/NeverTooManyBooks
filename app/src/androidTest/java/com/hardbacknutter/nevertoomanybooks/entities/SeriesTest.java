/*
 * @Copyright 2020 HardBackNutter
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

import androidx.test.filters.SmallTest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SmallTest
public class SeriesTest {

    @Test
    public void parcelling() {
        final Series series = Series.from("test");
        series.setNumber("5");

        final Parcel parcel = Parcel.obtain();
        series.writeToParcel(parcel, series.describeContents());
        parcel.setDataPosition(0);
        Series pSeries = Series.CREATOR.createFromParcel(parcel);

        assertEquals(pSeries, series);

        assertEquals(pSeries.getId(), series.getId());
        assertEquals(pSeries.getTitle(), series.getTitle());
        assertEquals(pSeries.getNumber(), series.getNumber());
        assertEquals(pSeries.isComplete(), series.isComplete());
    }
}
