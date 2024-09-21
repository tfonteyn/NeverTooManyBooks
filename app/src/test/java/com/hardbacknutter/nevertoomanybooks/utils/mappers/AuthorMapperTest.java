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

package com.hardbacknutter.nevertoomanybooks.utils.mappers;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.entities.Author;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorMapperTest {

    @Test
    void authors() {
        final AuthorTypeMapper mapper = new AuthorTypeMapper();

        assertEquals(Author.TYPE_ARTIST | Author.TYPE_WRITER,
                     mapper.map(Locale.ENGLISH, "Author, Illustrator"));

        assertEquals(Author.TYPE_NARRATOR,
                     mapper.map(Locale.ENGLISH, "Narrator"));
    }
}
