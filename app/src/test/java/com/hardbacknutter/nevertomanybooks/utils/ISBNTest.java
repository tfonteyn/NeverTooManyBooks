package com.hardbacknutter.nevertomanybooks.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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