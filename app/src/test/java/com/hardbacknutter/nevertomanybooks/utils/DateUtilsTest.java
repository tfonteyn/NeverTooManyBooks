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

import java.util.Date;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertomanybooks.App;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Commented out asserts are failing for now. But not all of them are:
 * (a) actually added to the parser, and/or (b) not important.
 *
 * DateUtils also needs to put the userLocale into account.
 */
class DateUtilsTest {

    // 1987-06-25; i.e. the day > possible month number (0..11)
    @SuppressWarnings("deprecation")
    private Date d_1987_06_25 = new Date(87, 5, 25);

    // 1987-06-22; i.e. the day < possible month number (0..11)
    @SuppressWarnings("deprecation")
    private Date d_1987_06_10 = new Date(87, 5, 10);

    // 1987-06; partial date
    @SuppressWarnings("deprecation")
    private Date d_1987_06_01 = new Date(87, 5, 1);


    /**
     *
     */
    @Test
    void parseAmbiguousMMDD() {
        // Patter list has MMdd before ddMM
        //assertEquals(d_1987_06_10, DateUtils.parseDate("10-06-1987"));
        assertEquals(d_1987_06_10, DateUtils.parseDate("06-10-1987"));

        // Pattern not actually added
//        assertEquals(d_1987_06_10, DateUtils.parseDate("10 06 1987"));
//        assertEquals(d_1987_06_10, DateUtils.parseDate("06 10 1987"));

        // Pattern not actually added
//        assertEquals(d_1987_06_10, DateUtils.parseDate("10-06-87"));
//        assertEquals(d_1987_06_10, DateUtils.parseDate("06-10-87"));

        // Pattern not actually added
//        assertEquals(d_1987_06_10, DateUtils.parseDate("10 06 87"));
//        assertEquals(d_1987_06_10, DateUtils.parseDate("06 10 87"));
    }

    @Test
    void parseDDMM() {
        assertEquals(d_1987_06_25, DateUtils.parseDate("25-06-1987"));
        assertEquals(d_1987_06_25, DateUtils.parseDate("06-25-1987"));

        // Pattern not actually added
//        assertEquals(d_1987_06_25, DateUtils.parseDate("25 06 1987"));
//        assertEquals(d_1987_06_25, DateUtils.parseDate("06 25 1987"));

        // Pattern not actually added
//        assertEquals(d_1987_06_25, DateUtils.parseDate("25-06-87"));
//        assertEquals(d_1987_06_25, DateUtils.parseDate("06-25-87"));

        // Pattern not actually added
//        assertEquals(d_1987_06_25, DateUtils.parseDate("25 06 87"));
//        assertEquals(d_1987_06_25, DateUtils.parseDate("06 25 87"));
    }

    /**
     * Sql formatted.
     */
    @Test
    void parseSqlDate() {
        assertEquals(d_1987_06_25, DateUtils.parseDate("1987-06-25"));
        assertEquals(d_1987_06_10, DateUtils.parseDate("1987-06-10"));
        assertEquals(d_1987_06_01, DateUtils.parseDate("1987-06"));
    }

    /**
     * Native English.
     */
    @Test
    void parseNativeEnglish() {
        DateUtils.createParseDateFormats(Locale.ENGLISH, false);
        assertEquals(d_1987_06_25, DateUtils.parseDate("25-Jun-1987"));
        assertEquals(d_1987_06_10, DateUtils.parseDate("10-Jun-1987"));

        assertEquals(d_1987_06_25, DateUtils.parseDate("25 Jun 1987"));
        assertEquals(d_1987_06_10, DateUtils.parseDate("10 Jun 1987"));

        assertEquals(d_1987_06_25, DateUtils.parseDate("Jun 25, 1987"));
        assertEquals(d_1987_06_10, DateUtils.parseDate("Jun 10, 1987"));

        assertEquals(d_1987_06_01, DateUtils.parseDate("Jun 1987"));
    }
}
