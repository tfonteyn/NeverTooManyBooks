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
package com.hardbacknutter.nevertoomanybooks.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ParsedBookTitleTest {

    @Test
    void parse01Test() {
        ParsedBookTitle pbt = ParsedBookTitle.parse(
                "This is the book title (Series title, 1)");
        assertNotNull(pbt);
        assertEquals("This is the book title", pbt.getBookTitle());
        assertEquals("Series title", pbt.getSeriesTitle());
        assertEquals("1", pbt.getSeriesNumber());
    }

    @Test
    void parse02Test() {
        ParsedBookTitle pbt = ParsedBookTitle.parse(
                "This is the book title (Series title nr 1)");
        assertNotNull(pbt);
        assertEquals("This is the book title", pbt.getBookTitle());
        assertEquals("Series title", pbt.getSeriesTitle());
        assertEquals("1", pbt.getSeriesNumber());
    }

    @Test
    void parse03Test() {
        ParsedBookTitle pbt = ParsedBookTitle.parse(
                "This is the book title (Series title Tome 1)");
        assertNotNull(pbt);
        assertEquals("This is the book title", pbt.getBookTitle());
        assertEquals("Series title", pbt.getSeriesTitle());
        assertEquals("1", pbt.getSeriesNumber());
    }

    @Test
    void parse04Test() {
        // roman numeral
        ParsedBookTitle pbt = ParsedBookTitle.parse(
                "This is the book title (Series title Tome IV)");
        assertNotNull(pbt);
        assertEquals("This is the book title", pbt.getBookTitle());
        assertEquals("Series title", pbt.getSeriesTitle());
        assertEquals("IV", pbt.getSeriesNumber());
    }
}