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
public class PublisherTest {

    @Test
    public void parcelling() {
        final Publisher publisher = Publisher.from("Random House");

        final Parcel parcel = Parcel.obtain();
        publisher.writeToParcel(parcel, publisher.describeContents());
        parcel.setDataPosition(0);
        Publisher pPublisher = Publisher.CREATOR.createFromParcel(parcel);

        assertEquals(pPublisher, publisher);

        assertEquals(pPublisher.getId(), publisher.getId());
        assertEquals(pPublisher.getTitle(), publisher.getTitle());
    }
}
