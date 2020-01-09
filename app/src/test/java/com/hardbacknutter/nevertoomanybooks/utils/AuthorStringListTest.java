/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvCoder;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthorStringListTest {

    /**
     * String suited to be used with Author.fromString(),
     * as tested in com.hardbacknutter.nevertoomanybooks.entities.AuthorTest.
     */
    private static final String[] AUTHORS = {
            "Charles Emerson Winchester",
            "Winchester, Charles Emerson",
            "Winchester, Charles\\ Emerson",
            "Wade, (ps Jack Vance)",
            "Giroud, Frank",
            "Ralph Meyer",
            "Van der Heide Produkties, Zandvoort",
            // yes, there REALLY is a book with an author named like this...
            "Don (*3)"
    };

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
            + "Meyer, Ralph * {\"type\":36864}"
            + "|"
            + "Van\\ der\\ Heide\\ Produkties, Zandvoort * {\"type\":16}"
            + "|"
            + "\\(\\\\*3\\), Don";

    private List<Author> mAuthor;

    private StringList<Author> mCoder;

    @BeforeEach
    void setUp() {
        mAuthor = new ArrayList<>();
        for (String s : AUTHORS) {
            mAuthor.add(Author.fromString(s));
        }
        mAuthor.get(5).setType(Author.TYPE_ARTIST | Author.TYPE_COLORIST);
        mAuthor.get(6).setType(Author.TYPE_TRANSLATOR);

        mCoder = CsvCoder.getAuthorCoder();
    }

    @Test
    void encode() {
        String encoded = mCoder.encodeList(mAuthor);
        assertEquals(ENCODED, encoded);
    }

    @Test
    void decode() {
        List<Author> decoded = mCoder.decode(ENCODED);
        assertEquals(AUTHORS.length, decoded.size());
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
        assertEquals("Wade", author.getFamilyName());
        assertEquals("(ps Jack Vance)", author.getGivenNames());
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
    void encode01() {
        Author author = Author.fromString("Charles Emerson Winchester");
        String authorStr = CsvCoder.getAuthorCoder().encodeElement(author);

        assertEquals("Winchester, Charles\\ Emerson", authorStr);
    }

    @Test
    void decode01() {
        List<Author> author = CsvCoder.getAuthorCoder()
                                      .decodeElement("Winchester, Charles\\ Emerson");

        assertEquals("Winchester", author.get(0).getFamilyName());
        assertEquals("Charles Emerson", author.get(0).getGivenNames());
    }

    @Test
    void decode01b() {
        List<Author> author = CsvCoder.getAuthorCoder()
                                      .decodeElement("Winchester, Charles Emerson");

        assertEquals("Winchester", author.get(0).getFamilyName());
        assertEquals("Charles Emerson", author.get(0).getGivenNames());
    }
}
