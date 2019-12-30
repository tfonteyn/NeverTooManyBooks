/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.entities.Author;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvCoderTest {

    @Test
    void encode01() {
        Author author = Author.fromString("Charles Emerson Winchester");
        String authorStr = CsvCoder.getAuthorCoder().encodeElement(author);

        assertEquals("Winchester, Charles\\ Emerson", authorStr);
    }

    @Test
    void decode01() {
        List<Author> author = CsvCoder.getAuthorCoder().decode("Winchester, Charles\\ Emerson");

        assertEquals("Winchester", author.get(0).getFamilyName());
        assertEquals("Charles Emerson", author.get(0).getGivenNames());
    }

    @Test
    void decode01b() {
        List<Author> author = CsvCoder.getAuthorCoder().decode("Winchester, Charles Emerson");

        assertEquals("Winchester", author.get(0).getFamilyName());
        assertEquals("Charles Emerson", author.get(0).getGivenNames());
    }

    @Test
    void encode02() {
        // yes, there REALLY is a book with an author named like this...
        Author author = Author.fromString("Don (*3)");
        String authorStr = CsvCoder.getAuthorCoder().encodeElement(author);

        assertEquals("\\(\\\\*3\\), Don", authorStr);
    }

    @Test
    void decode02() {
        // yes, there REALLY is a book with an author named like this...
        List<Author> author = CsvCoder.getAuthorCoder().decode("\\(\\\\*3\\), Don");

        assertEquals("(*3)", author.get(0).getFamilyName());
        assertEquals("Don", author.get(0).getGivenNames());
    }
}
