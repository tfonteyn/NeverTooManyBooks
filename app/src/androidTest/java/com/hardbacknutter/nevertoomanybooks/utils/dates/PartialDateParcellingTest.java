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
package com.hardbacknutter.nevertoomanybooks.utils.dates;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SmallTest
public class PartialDateParcellingTest {

    /**
     * Reminder: The base test {@code assertEquals(pDate, date)}
     * is testing {@link PartialDate#equals(Object)} only.
     */
    @Test
    public void parcelling() {
        final PartialDate date = new PartialDate(2020, 9, 15);

        final Parcel parcel = Parcel.obtain();
        date.writeToParcel(parcel, date.describeContents());
        parcel.setDataPosition(0);
        final PartialDate pDate = PartialDate.CREATOR.createFromParcel(parcel);

        assertEquals(pDate, date);
    }

}
