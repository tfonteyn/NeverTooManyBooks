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

import androidx.annotation.NonNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ISBNTest {

    /**
     * Valid ISBN 10,13 digits
     */
    private final String[][] validFinals = {
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
    private final String[][] invalidFinals = {
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
    private final String[][] upcIsbnFinals = {
            {"0-70999-00225-5 30054", "0345300548"}
    };

    /**
     * Generic UPC_A codes
     */
    private final String[][] upcAFinals = {
            {"7 12345 67890 4", "712345678904"}
    };

    /**
     * Generic EAN-13 codes.
     */
    private final String[][] eanFinals = {
            {"5410983535003", "5410983535003"}
    };

    private String[][] createValidIsbnList() {
        return Arrays.stream(validFinals).map(String[]::clone).toArray(String[][]::new);
    }

    private String[][] createInvalidIsbnList() {
        return Arrays.stream(invalidFinals).map(String[]::clone).toArray(String[][]::new);
    }

    private String[][] createIsbnUpcList() {
        return Arrays.stream(upcIsbnFinals).map(String[]::clone).toArray(String[][]::new);
    }

    private String[][] createUpcAList() {
        return Arrays.stream(upcAFinals).map(String[]::clone).toArray(String[][]::new);
    }

    private String[][] createEanList() {
        return Arrays.stream(eanFinals).map(String[]::clone).toArray(String[][]::new);
    }


    @Test
    void isbn_isValid() {
        for (String[] isbnPair : createValidIsbnList()) {
            ISBN isbn0 = ISBN.createISBN(isbnPair[0]);
            ISBN isbn1 = ISBN.createISBN(isbnPair[1]);

            assertTrue(isbn0.isValid(true));
            assertTrue(isbn1.isValid(true));
        }
    }

    @Test
    void isbn_isInvalid() {
        for (String[] isbnPair : createInvalidIsbnList()) {
            ISBN isbn0 = ISBN.createISBN(isbnPair[0]);
            ISBN isbn1 = ISBN.createISBN(isbnPair[1]);

            assertFalse(isbn0.isValid(true));
            assertFalse(isbn1.isValid(true));
        }
    }

    @Test
    void isbn_is10() {
        for (String[] isbnPair : createValidIsbnList()) {
            ISBN isbn0 = ISBN.createISBN(isbnPair[0]);
            ISBN isbn1 = ISBN.createISBN(isbnPair[1]);

            assertTrue(isbn0.isType(ISBN.TYPE_ISBN10));
            assertFalse(isbn1.isType(ISBN.TYPE_ISBN10));
        }
        for (String[] isbnPair : createInvalidIsbnList()) {
            ISBN isbn0 = ISBN.createISBN(isbnPair[0]);
            ISBN isbn1 = ISBN.createISBN(isbnPair[1]);

            assertFalse(isbn0.isType(ISBN.TYPE_ISBN10));
            assertFalse(isbn1.isType(ISBN.TYPE_ISBN10));
        }
    }

    @Test
    void isbn_is13() {
        for (String[] isbnPair : createValidIsbnList()) {
            ISBN isbn0 = ISBN.createISBN(isbnPair[0]);
            ISBN isbn1 = ISBN.createISBN(isbnPair[1]);

            assertFalse(isbn0.isType(ISBN.TYPE_ISBN13));
            assertTrue(isbn1.isType(ISBN.TYPE_ISBN13));
        }
        for (String[] isbnPair : createInvalidIsbnList()) {
            ISBN isbn0 = ISBN.createISBN(isbnPair[0]);
            ISBN isbn1 = ISBN.createISBN(isbnPair[1]);

            assertFalse(isbn0.isType(ISBN.TYPE_ISBN13));
            assertFalse(isbn1.isType(ISBN.TYPE_ISBN13));
        }
    }

    @Test
    void isbn_swap1013() {
        for (String[] isbnPair : createValidIsbnList()) {
            ISBN isbn10 = ISBN.createISBN(isbnPair[0]);
            ISBN isbn13 = ISBN.createISBN(isbnPair[1]);

            try {
                assertEquals(isbn10.asText(ISBN.TYPE_ISBN13), isbn13.asText(ISBN.TYPE_ISBN13));
                assertEquals(isbn10.asText(ISBN.TYPE_ISBN10), isbn13.asText(ISBN.TYPE_ISBN10));
            } catch (@NonNull final NumberFormatException e) {
                fail(e);
            }
        }
    }

    @Test
    void isbn_matchesValidValid() {
        for (String[] isbnPair : createValidIsbnList()) {
            assertTrue(ISBN.matches(isbnPair[0], isbnPair[1], true),
                       "isbnPair=" + Arrays.toString(isbnPair));
        }
    }

    @Test
    void isbn_matchesInvalidInvalid() {
        for (String[] isbnPair : createInvalidIsbnList()) {
            assertFalse(ISBN.matches(isbnPair[0], isbnPair[1], true),
                        "isbnPair=" + Arrays.toString(isbnPair));
        }
    }

    @Test
    void isbn_matchesValidInvalid() {
        String[][] valid = createValidIsbnList();
        String[][] invalid = createInvalidIsbnList();
        for (int i = 0; i < invalid.length; i++) {
            assertFalse(ISBN.matches(valid[i][0], invalid[i][1], true));
        }
    }

    @Test
    void isbn_equalsValidValid() {
        String[][] valid = Arrays.stream(validFinals).map(String[]::clone).toArray(String[][]::new);
        for (String[] isbnPair : valid) {
            ISBN isbn0 = ISBN.createISBN(isbnPair[0]);
            ISBN isbn1 = ISBN.createISBN(isbnPair[1]);
            assertEquals(isbn0, isbn1);
        }
    }

    @Test
    void isbn_equalsInvalidInvalid() {
        for (String[] isbnPair : createInvalidIsbnList()) {
            ISBN isbn0 = ISBN.createISBN(isbnPair[0]);
            ISBN isbn1 = ISBN.createISBN(isbnPair[1]);
            assertNotEquals(isbn0, isbn1);
        }
    }

    @Test
    void isbn_equalsValidInvalid() {
        String[][] valid = createValidIsbnList();
        String[][] invalid = createInvalidIsbnList();

        for (int i = 0; i < invalid.length; i++) {
            ISBN isbn0 = ISBN.createISBN(valid[i][0]);
            ISBN isbn1 = ISBN.createISBN(invalid[i][1]);
            assertNotEquals(isbn0, isbn1);
        }
    }


    @Test
    void upc2isbn() {
        for (String[] upcPair : createIsbnUpcList()) {
            ISBN isbn = ISBN.create(upcPair[0]);
            assertNotNull(isbn);
            assertTrue(isbn.isType(ISBN.TYPE_ISBN10));
            assertTrue(isbn.isValid(false));
            assertEquals(upcPair[1], isbn.asText());
        }
    }

    @Test
    void upcA_isValid() {
        for (String[] upcPair : createUpcAList()) {
            ISBN upc = ISBN.create(upcPair[0]);
            assertNotNull(upc);
            assertTrue(upc.isType(ISBN.TYPE_UPC_A));
            assertTrue(upc.isValid(false));
            assertEquals(upcPair[1], upc.asText());
        }
    }

    @Test
    void ean13_isValid() {
        for (String[] eanPair : createEanList()) {
            ISBN ean = ISBN.create(eanPair[0]);
            assertNotNull(ean);
            assertTrue(ean.isType(ISBN.TYPE_EAN13));
            assertTrue(ean.isValid(false));
            assertEquals(eanPair[1], ean.asText());
        }
    }
}
