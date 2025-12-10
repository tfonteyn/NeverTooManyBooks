/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("MissingJavadoc")
public class AuthorTest
        extends BaseDBTest {

    @Before
    public void setup()
            throws StorageException {
        super.setup(AppLocale.SYSTEM_LANGUAGE);
    }

    /**
     * Reminder: The base test {@code assertEquals(pAuthor, author)}
     * is testing {@link Author#equals(Object)} only.
     */
    @Test
    public void parcelling() {
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

    @Test
    public void norm() {
        final Locale bookLocale = Locale.GERMANY;
        final String n1 = SqlEncode.normalize("Jan Groß").toLowerCase(bookLocale);
        final String n2 = SqlEncode.normalize("Jan Gross").toLowerCase(bookLocale);

        assertEquals(n1, n2);
    }
}
