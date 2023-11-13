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
package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import java.util.Arrays;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("MissingJavadoc")
@RunWith(Parameterized.class)
public class FormatMapperTest
        extends BaseDBTest {

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"en", "pb", "Paperback"},
                {"en", "Dimensions 5x4", "Dimensions 5x4"},
                {"en", "some-string", "some-string"},

                {"fr", "pb", "Livre de poche"},

        });
    }

    @Parameterized.Parameter(0)
    public String fLocaleCode;

    @Parameterized.Parameter(1)
    public String fInput;

    @Parameterized.Parameter(2)
    public String fExpected;

    private Book book;

    private FormatMapper mapper;
    private String key;

    @Before
    public void setup()
            throws DaoWriteException, StorageException {
        super.setup(fLocaleCode);

        book = new Book();
        mapper = new FormatMapper();
        key = mapper.getKey();
    }

    @Test
    public void basic() {
        book.putString(key, fInput);
        mapper.map(context, book);
        assertEquals(fExpected, book.getString(key, null));
    }
}
