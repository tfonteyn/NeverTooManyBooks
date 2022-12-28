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
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.Nullable;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.Base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ISBNTest
        extends Base {

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
     * UPC codes which can be translated to ISBN-10.
     */
    private final String[][] valid_upc_isbn = {
            {"0-70999-00225-5 30054", "0345300548"}
    };

    /**
     * Generic UPC_A codes
     */
    private final String[][] valid_upc = {
            {"7 12345 67890 4", "712345678904"}
    };

    /**
     * Generic EAN-13 codes.
     */
    private final String[][] valid_ean = {
            {"5410983535003", "5410983535003"}
    };

    /**
     * pure 8 digit ISSN codes.
     */
    private final String[][] valid_issn8 = {
            {"0378-5955", "03785955"},
            {"0024-984X", "0024984X"}
    };

    /**
     * pure 13 digit ISSN codes.
     */
    private final String[][] valid_issn13 = {
            {"977 0378-595 12 5", "9770378595125"},
            {"9770024984648", "9770024984648"}
    };

    /**
     * mixed 13 digit input; 8 digit expected ISSN codes.
     */
    private final String[][] valid_issn138 = {
            {"977 0378-595 12 5", "03785955"},
            {"9770024984648", "0024984X"}
    };

    /**
     * Check if two codes are matching.
     *
     * @param isbnStr1 first code
     * @param isbnStr2 second code
     *
     * @return {@code true} if the 2 codes match.
     */
    private static boolean matches(@Nullable final String isbnStr1,
                                   @Nullable final String isbnStr2) {
        if (isbnStr1 == null || isbnStr2 == null) {
            return false;
        }

        // If either one is invalid, we consider them different
        final ISBN o1 = new ISBN(isbnStr1, true);
        if (!o1.isValid(true)) {
            return false;
        }

        final ISBN o2 = new ISBN(isbnStr2, true);
        if (!o2.isValid(true)) {
            return false;
        }

        return o1.equals(o2);
    }

    @Test
    void isbn_isValid() {
        for (final String[] isbnPair : valid_isbn) {
            final ISBN isbn0 = new ISBN(isbnPair[0], true);
            final ISBN isbn1 = new ISBN(isbnPair[1], true);

            assertTrue(isbn0.isValid(true));
            assertTrue(isbn1.isValid(true));
        }
    }

    @Test
    void isbn_isInvalid() {
        for (final String[] isbnPair : invalid_isbn) {
            final ISBN isbn0 = new ISBN(isbnPair[0], true);
            final ISBN isbn1 = new ISBN(isbnPair[1], true);

            assertFalse(isbn0.isValid(true));
            assertFalse(isbn1.isValid(true));
        }
    }

    @Test
    void isbn_is10() {
        for (final String[] isbnPair : valid_isbn) {
            final ISBN isbn0 = new ISBN(isbnPair[0], true);
            final ISBN isbn1 = new ISBN(isbnPair[1], true);

            assertTrue(isbn0.isType(ISBN.Type.Isbn10));
            assertFalse(isbn1.isType(ISBN.Type.Isbn10));
        }
        for (final String[] isbnPair : invalid_isbn) {
            final ISBN isbn0 = new ISBN(isbnPair[0], true);
            final ISBN isbn1 = new ISBN(isbnPair[1], true);

            assertFalse(isbn0.isType(ISBN.Type.Isbn10));
            assertFalse(isbn1.isType(ISBN.Type.Isbn10));
        }
    }

    @Test
    void isbn_is13() {
        for (final String[] isbnPair : valid_isbn) {
            final ISBN isbn0 = new ISBN(isbnPair[0], true);
            final ISBN isbn1 = new ISBN(isbnPair[1], true);

            assertFalse(isbn0.isType(ISBN.Type.Isbn13));
            assertTrue(isbn1.isType(ISBN.Type.Isbn13));
        }
        for (final String[] isbnPair : invalid_isbn) {
            final ISBN isbn0 = new ISBN(isbnPair[0], true);
            final ISBN isbn1 = new ISBN(isbnPair[1], true);

            assertFalse(isbn0.isType(ISBN.Type.Isbn13));
            assertFalse(isbn1.isType(ISBN.Type.Isbn13));
        }
    }

    @Test
    void isbn_swap1013() {
        for (final String[] isbnPair : valid_isbn) {
            final ISBN isbn10 = new ISBN(isbnPair[0], true);
            final ISBN isbn13 = new ISBN(isbnPair[1], true);

            assertEquals(isbn10.asText(ISBN.Type.Isbn13), isbn13.asText(ISBN.Type.Isbn13));
            assertEquals(isbn10.asText(ISBN.Type.Isbn10), isbn13.asText(ISBN.Type.Isbn10));
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
                        "isbnPair=" + Arrays.toString(isbnPair));
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
            final ISBN isbn0 = new ISBN(isbnPair[0], true);
            final ISBN isbn1 = new ISBN(isbnPair[1], true);
            assertEquals(isbn0, isbn1);
        }
    }

    @Test
    void isbn_equalsInvalidInvalid() {
        for (final String[] isbnPair : invalid_isbn) {
            final ISBN isbn0 = new ISBN(isbnPair[0], true);
            final ISBN isbn1 = new ISBN(isbnPair[1], true);
            assertNotEquals(isbn0, isbn1);
        }
    }

    @Test
    void isbn_equalsValidInvalid() {
        for (int i = 0; i < invalid_isbn.length; i++) {
            final ISBN isbn0 = new ISBN(valid_isbn[i][0], true);
            final ISBN isbn1 = new ISBN(invalid_isbn[i][1], true);
            assertNotEquals(isbn0, isbn1);
        }
    }


    @Test
    void upc2isbn() {
        for (final String[] upcPair : valid_upc_isbn) {
            final ISBN isbn = new ISBN(upcPair[0], false);
            assertTrue(isbn.isType(ISBN.Type.Isbn10));
            assertTrue(isbn.isValid(false));
            assertEquals(upcPair[1], isbn.asText());
        }
    }

    @Test
    void upcA_isValid() {
        for (final String[] upcPair : valid_upc) {
            final ISBN upc = new ISBN(upcPair[0], false);
            assertTrue(upc.isType(ISBN.Type.UpcA));
            assertTrue(upc.isValid(false));
            assertEquals(upcPair[1], upc.asText());
        }
    }

    @Test
    void ean13_isValid() {
        for (final String[] eanPair : valid_ean) {
            final ISBN ean = new ISBN(eanPair[0], false);
            assertTrue(ean.isType(ISBN.Type.Ean13));
            assertTrue(ean.isValid(false));
            assertEquals(eanPair[1], ean.asText());
        }
    }

    @Test
    void issn8_isValid() {
        for (final String[] issnPair : valid_issn8) {
            final ISBN issn = new ISBN(issnPair[0], false);
            assertTrue(issn.isValid(false));
            assertTrue(issn.isType(ISBN.Type.Issn8));
            assertEquals(issnPair[1], issn.asText(ISBN.Type.Issn8));
        }
    }

    @Test
    void issn13_isValid() {
        for (final String[] issnPair : valid_issn13) {
            final ISBN issn = new ISBN(issnPair[0], false);
            assertTrue(issn.isValid(false));
            assertTrue(issn.isType(ISBN.Type.Issn13));
            assertEquals(issnPair[1], issn.asText(ISBN.Type.Issn13));
        }
    }

    @Test
    void issn138_isValid() {
        for (final String[] issnPair : valid_issn138) {
            final ISBN issn = new ISBN(issnPair[0], false);
            assertTrue(issn.isValid(false));
            assertTrue(issn.isType(ISBN.Type.Issn13));
            assertEquals(issnPair[1], issn.asText(ISBN.Type.Issn8));
        }
    }
}
