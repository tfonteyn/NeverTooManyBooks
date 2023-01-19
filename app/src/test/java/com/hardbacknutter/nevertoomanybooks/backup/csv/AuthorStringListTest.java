/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.AuthorCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.StringList;
import com.hardbacknutter.nevertoomanybooks.debug.TestFlags;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthorStringListTest {

    /**
     * The correct string result of encoding the above; as written to the export file.
     * Ergo, the correct string how an import file should contain this field for a book.
     */
    private static final String ENCODED =
            "Winchester, Charles\\ Emerson"
            + "|"
            + "Winchester, Charles\\ Emerson"
            + "|"
            + "Winchester, Charles\\ Emerson"
            + "|"
            + "Wade, \\(ps\\ Jack\\ Vance\\)"
            + "|"
            + "Giroud, Frank"
            + "|"
            + "Meyer, Ralph * {\"author_type\":36864}"
            + "|"
            + "Van\\ der\\ Heide\\ Produkties, Zandvoort * {\"author_type\":16}"
            + "|"
            + "\\(\\\\*3\\), Don";

    private StringList<Author> coder;

    @BeforeAll
    static void startUp() {
        TestFlags.isJUnit = true;
    }

    @BeforeEach
    void setUp() {
        coder = new StringList<>(new AuthorCoder());
    }

    @Test
    void decode() {
        final List<Author> decoded = coder.decodeList(ENCODED);
        assertEquals(8, decoded.size());
        Author author;

        author = decoded.get(0);
        assertEquals("Winchester", author.getFamilyName());
        assertEquals("Charles Emerson", author.getGivenNames());
        assertFalse(author.isComplete());
        author = decoded.get(1);
        assertEquals("Winchester", author.getFamilyName());
        assertEquals("Charles Emerson", author.getGivenNames());
        assertFalse(author.isComplete());
        author = decoded.get(2);
        assertEquals("Winchester", author.getFamilyName());
        assertEquals("Charles Emerson", author.getGivenNames());
        assertFalse(author.isComplete());

        author = decoded.get(3);
        assertEquals("Wade (ps Jack Vance)", author.getFamilyName());
        assertEquals("", author.getGivenNames());
        assertFalse(author.isComplete());

        author = decoded.get(4);
        assertEquals("Giroud", author.getFamilyName());
        assertEquals("Frank", author.getGivenNames());
        assertFalse(author.isComplete());

        author = decoded.get(5);
        assertEquals("Meyer", author.getFamilyName());
        assertEquals("Ralph", author.getGivenNames());
        assertFalse(author.isComplete());

        author = decoded.get(6);
        assertEquals("Van der Heide Produkties", author.getFamilyName());
        assertEquals("Zandvoort", author.getGivenNames());
        assertFalse(author.isComplete());

        author = decoded.get(7);
        assertEquals("(*3)", author.getFamilyName());
        assertEquals("Don", author.getGivenNames());
        assertFalse(author.isComplete());
    }

    @Test
    void decode01() {
        final List<Author> author = new StringList<>(new AuthorCoder())
                .decodeElement("Winchester, Charles\\ Emerson");

        assertEquals("Winchester", author.get(0).getFamilyName());
        assertEquals("Charles Emerson", author.get(0).getGivenNames());
    }

    @Test
    void decode01b() {
        final List<Author> author = new StringList<>(new AuthorCoder())
                .decodeElement("Winchester, Charles Emerson");

        assertEquals("Winchester", author.get(0).getFamilyName());
        assertEquals("Charles Emerson", author.get(0).getGivenNames());
    }
}
