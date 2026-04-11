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

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorTest
        extends BaseDBTest {

    @BeforeEach
    void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    /**
     * Reminder: The base test {@code assertEquals(pAuthor, author)}
     * is testing {@link Author#equals(Object)} only.
     */
    @Test
    void parcelling() {
        final Author author = Author.from("Paul French");
        author.setRealAuthor(Author.from("Isaac Asimov"));

        final Parcel parcel = Parcel.obtain();
        author.writeToParcel(parcel, author.describeContents());
        parcel.setDataPosition(0);
        final Author pAuthor = Author.CREATOR.createFromParcel(parcel);

        assertEquals(pAuthor, author);

        assertEquals(pAuthor.getId(), author.getId());
        assertEquals(pAuthor.getFamilyName(), author.getFamilyName());
        assertEquals(pAuthor.getGivenNames(), author.getGivenNames());
        assertEquals(pAuthor.isComplete(), author.isComplete());
        assertEquals(pAuthor.getRole(), author.getRole());
        assertEquals(pAuthor.getRealAuthor(), author.getRealAuthor());
    }
}
