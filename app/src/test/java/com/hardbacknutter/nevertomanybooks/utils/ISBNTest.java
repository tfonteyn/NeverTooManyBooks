/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.utils;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ISBNTest {

    private final String[][] validFinals = {
            {"1-886778-17-5", "978-1-886778-17-7"},
            {"1886778175", "9781886778177"},
            {"0-684-18818-X", "978-0-684-18818-8"},
            {"068418818X", "9780684188188"},
            // duplicate so array length is equal
            {"068418818X", "9780684188188"},
            };

    // same as above, but one digit changed so the checksum fails.
    private final String[][] invalidFinals = {
            {"2-886778-17-5", "978-2-886778-17-7"},
            {"2886778175", "9782886778177"},
            {"0-684-28818-X", "978-0-684-28818-8"},
            {"068418828X", "9780684188288"},
            // 'A' embedded
            {"06841A828X", "978068418A288"},
            };

    private String[][] valid;
    private String[][] invalid;

    @BeforeEach
    void setUp() {
        valid = Arrays.stream(validFinals).map(String[]::clone).toArray(String[][]::new);
        invalid = Arrays.stream(invalidFinals).map(String[]::clone).toArray(String[][]::new);
    }

    @Test
    void isValid() {
        for (String[] isbnPair : valid) {
            assertTrue(ISBN.isValid(isbnPair[0]));
            assertTrue(ISBN.isValid(isbnPair[1]));
        }
        for (String[] isbnPair : invalid) {
            assertFalse(ISBN.isValid(isbnPair[0]));
            assertFalse(ISBN.isValid(isbnPair[1]));
        }
    }

    @Test
    void is10() {
        for (String[] isbnPair : valid) {
            ISBN isbn0 = new ISBN(isbnPair[0]);
            ISBN isbn1 = new ISBN(isbnPair[1]);
            assertTrue(isbn0.is10());
            assertFalse(isbn1.is10());
        }
        for (String[] isbnPair : invalid) {
            ISBN isbn0 = new ISBN(isbnPair[0]);
            ISBN isbn1 = new ISBN(isbnPair[1]);
            assertFalse(isbn0.is10());
            assertFalse(isbn1.is10());
        }
    }

    @Test
    void is13() {
        for (String[] isbnPair : valid) {
            ISBN isbn0 = new ISBN(isbnPair[0]);
            ISBN isbn1 = new ISBN(isbnPair[1]);
            assertFalse(isbn0.is13());
            assertTrue(isbn1.is13());
        }
        for (String[] isbnPair : invalid) {
            ISBN isbn0 = new ISBN(isbnPair[0]);
            ISBN isbn1 = new ISBN(isbnPair[1]);
            assertFalse(isbn0.is13());
            assertFalse(isbn1.is13());
        }
    }

    @Test
    void matches() {
        // valid == valid ?
        for (String[] isbnPair : valid) {
            assertTrue(ISBN.matches(isbnPair[0], isbnPair[1]));
        }
        // invalid == invalid ?
        for (String[] isbnPair : invalid) {
            assertFalse(ISBN.matches(isbnPair[0], isbnPair[1]));
        }
        // valid == invalid ?
        for (int i = 0; i < invalid.length; i++) {
            assertFalse(ISBN.matches(valid[i][0], invalid[i][1]));
        }
    }

    @Test
    void equals() {
        // valid == valid ?
        for (String[] isbnPair : valid) {
            ISBN isbn0 = new ISBN(isbnPair[0]);
            ISBN isbn1 = new ISBN(isbnPair[1]);
            assertEquals(isbn0, isbn1);
        }
        // invalid == invalid ?
        for (String[] isbnPair : invalid) {
            ISBN isbn0 = new ISBN(isbnPair[0]);
            ISBN isbn1 = new ISBN(isbnPair[1]);
            assertNotEquals(isbn0, isbn1);
        }
        // valid == invalid ?
        for (int i = 0; i < invalid.length; i++) {
            ISBN isbn0 = new ISBN(valid[i][0]);
            ISBN isbn1 = new ISBN(invalid[i][1]);
            assertNotEquals(isbn0, isbn1);
        }
    }
}