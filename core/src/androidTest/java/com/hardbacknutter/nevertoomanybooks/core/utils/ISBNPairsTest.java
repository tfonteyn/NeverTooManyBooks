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
package com.hardbacknutter.nevertoomanybooks.core.utils;

import androidx.annotation.Nullable;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ISBNPairsTest {

    /**
     * Valid ISBN 10,13 digits
     */
    private final String[][] valid_isbn = {
            {"2723481220", "9782723481229"},
            {"1-886778-17-5", "978-1-886778-17-7"},
            {"1886778175", "9781886778177"},
            {"0-684-18818-X", "978-0-684-18818-8"},
            {"068418818X", "9780684188188"},
            // duplicate so array length is equal
            {"068418818X", "9780684188188"},

            {"90-6334-994-7", "9789063349943"},
    };

    /**
     * same as above, but one digit changed so the checksum fails.
     * This array must have the same length as the one above.
     */
    private final String[][] invalid_isbn = {
            {"2733481220", "9782733481229"},
            {"2-886778-17-5", "978-2-886778-17-7"},
            {"2886778175", "9782886778177"},
            {"0-684-28818-X", "978-0-684-28818-8"},
            {"068418828X", "9780684188288"},
            // 'A' embedded
            {"06841A828X", "978068418A288"},

            {"91-6334-994-7", "9789163349943"},
    };

    /**
     * Check if two ISBN codes are matching.
     *
     * @param isbnStr1 first code
     * @param isbnStr2 second code
     *
     * @return {@code true} if the 2 ISBN codes match.
     */
    private static boolean matches(@Nullable final String isbnStr1,
                                   @Nullable final String isbnStr2) {
        if (isbnStr1 == null || isbnStr2 == null) {
            return false;
        }

        // If either one is not a valid ISBN, we consider them different
        final ISBN o1 = ISBN.parseISBN(isbnStr1);
        if (!o1.isIsbn()) {
            return false;
        }

        final ISBN o2 = ISBN.parseISBN(isbnStr2);
        if (!o2.isIsbn()) {
            return false;
        }

        return o1.equals(o2);
    }

    @Test
    void isbn_isValid() {
        for (final String[] isbnPair : valid_isbn) {
            final ISBN isbn0 = ISBN.parseISBN(isbnPair[0]);
            final ISBN isbn1 = ISBN.parseISBN(isbnPair[1]);

            assertTrue(isbn0.isIsbn());
            assertTrue(isbn1.isIsbn());
        }
    }

    @Test
    void isbn_isInvalid() {
        for (final String[] isbnPair : invalid_isbn) {
            final ISBN isbn0 = ISBN.parseISBN(isbnPair[0]);
            final ISBN isbn1 = ISBN.parseISBN(isbnPair[1]);

            assertFalse(isbn0.isIsbn());
            assertFalse(isbn1.isIsbn());
        }
    }

    @Test
    void isbn_is10() {
        for (final String[] isbnPair : valid_isbn) {
            final ISBN isbn0 = ISBN.parseISBN(isbnPair[0]);
            final ISBN isbn1 = ISBN.parseISBN(isbnPair[1]);

            assertSame(CodeType.Isbn10, isbn0.getCodeType());
            assertNotSame(CodeType.Isbn10, isbn1.getCodeType());
        }
        for (final String[] isbnPair : invalid_isbn) {
            final ISBN isbn0 = ISBN.parseISBN(isbnPair[0]);
            final ISBN isbn1 = ISBN.parseISBN(isbnPair[1]);

            assertNotSame(CodeType.Isbn10, isbn0.getCodeType());
            assertNotSame(CodeType.Isbn10, isbn1.getCodeType());
        }
    }

    @Test
    void isbn_is13() {
        for (final String[] isbnPair : valid_isbn) {
            final ISBN isbn0 = ISBN.parseISBN(isbnPair[0]);
            final ISBN isbn1 = ISBN.parseISBN(isbnPair[1]);

            assertNotSame(CodeType.Isbn13, isbn0.getCodeType());
            assertSame(CodeType.Isbn13, isbn1.getCodeType());
        }
        for (final String[] isbnPair : invalid_isbn) {
            final ISBN isbn0 = ISBN.parseISBN(isbnPair[0]);
            final ISBN isbn1 = ISBN.parseISBN(isbnPair[1]);

            assertNotSame(CodeType.Isbn13, isbn0.getCodeType());
            assertNotSame(CodeType.Isbn13, isbn1.getCodeType());
        }
    }

    @Test
    void isbn_swap1013() {
        for (final String[] isbnPair : valid_isbn) {
            final ISBN isbn10 = ISBN.parseISBN(isbnPair[0]);
            final ISBN isbn13 = ISBN.parseISBN(isbnPair[1]);

            assertEquals(isbn10.asText(CodeType.Isbn13), isbn13.asText(CodeType.Isbn13));
            assertEquals(isbn10.asText(CodeType.Isbn10), isbn13.asText(CodeType.Isbn10));
        }
    }

    @Test
    void isbn_matchesValidValid() {
        for (final String[] isbnPair : valid_isbn) {
            assertTrue(matches(isbnPair[0], isbnPair[1]),
                       "isbnPair=" + Arrays.toString(isbnPair));
        }
    }

    @Test
    void isbn_matchesInvalidInvalid() {
        for (final String[] isbnPair : invalid_isbn) {
            assertFalse(matches(isbnPair[0], isbnPair[1]),
                        () -> "isbnPair=" + Arrays.toString(isbnPair));
        }
    }

    @Test
    void isbn_matchesValidInvalid() {
        for (int i = 0; i < invalid_isbn.length; i++) {
            assertFalse(matches(valid_isbn[i][0], invalid_isbn[i][1]));
        }
    }

    @Test
    void isbn_equalsValidValid() {
        for (final String[] isbnPair : valid_isbn) {
            final ISBN isbn0 = ISBN.parseISBN(isbnPair[0]);
            final ISBN isbn1 = ISBN.parseISBN(isbnPair[1]);
            assertEquals(isbn0, isbn1);
        }
    }

    @Test
    void isbn_equalsInvalidInvalid() {
        for (final String[] isbnPair : invalid_isbn) {
            final ISBN isbn0 = ISBN.parseISBN(isbnPair[0]);
            final ISBN isbn1 = ISBN.parseISBN(isbnPair[1]);
            assertNotEquals(isbn0, isbn1);
        }
    }

    @Test
    void isbn_equalsValidInvalid() {
        for (int i = 0; i < invalid_isbn.length; i++) {
            final ISBN isbn0 = ISBN.parseISBN(valid_isbn[i][0]);
            final ISBN isbn1 = ISBN.parseISBN(invalid_isbn[i][1]);
            assertNotEquals(isbn0, isbn1);
        }
    }
}
