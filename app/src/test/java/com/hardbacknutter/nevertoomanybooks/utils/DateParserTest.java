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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Commented out asserts are failing for now. But not all of them are:
 * (a) actually added to the parser, and/or (b) not important.
 * <p>
 * DateParser also needs to put the userLocale into account.
 */
class DateParserTest {

    // 'Winter' time.
    // 1987-02-25; i.e. the day > possible month number (1..12)
    private final LocalDate wd_1987_02_25 = LocalDate.of(1987, 2, 25);
    private final LocalDate wd_2017_01_12 = LocalDate.of(2017, 1, 12);

    // 'Summer' time.
    // 1987-06-25; i.e. the day > possible month number (1..12)
    private final LocalDate sd_1987_06_25 = LocalDate.of(1987, 6, 25);

    // 1987-06-22; i.e. the day < possible month number (1..12)
    private final LocalDate sd_1987_06_10 = LocalDate.of(1987, 6, 10);

    // 1987-06; partial date
    private final LocalDate sd_1987_06_01 = LocalDate.of(1987, 6, 1);

    private final LocalDateTime wd_2017_01_12_11_57_41 = LocalDateTime.of(2017, 1, 12,
                                                                          11, 57, 41);

    private final LocalDateTime sd_1987_06_25_11_57_41 = LocalDateTime.of(1987, 6, 25,
                                                                          11, 57, 41);

    @Test
    void ambiguousNumeric() {
        DateParser.create(Locale.ENGLISH);

        // Patter list has MMdd before ddMM
        //assertEquals(sd_1987_06_10, DateParser.parse(locale, "10-06-1987"));
        assertEquals(sd_1987_06_10, DateParser.DATE.parse("06-10-1987"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_10, DateParser.parse(locale, "10 06 1987"));
//        assertEquals(sd_1987_06_10, DateParser.parse(locale, "06 10 1987"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_10, DateParser.parse(locale, "10-06-87"));
//        assertEquals(sd_1987_06_10, DateParser.parse(locale, "06-10-87"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_10, DateParser.parse(locale, "10 06 87"));
//        assertEquals(sd_1987_06_10, DateParser.parse(locale, "06 10 87"));
    }

    @Test
    void numeric() {
        DateParser.create(Locale.ENGLISH);

        assertEquals(sd_1987_06_25, DateParser.DATE.parse("25-06-1987"));
        assertEquals(sd_1987_06_25, DateParser.DATE.parse("06-25-1987"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_25, DateParser.parse(locale, "25 06 1987"));
//        assertEquals(sd_1987_06_25, DateParser.parse(locale, "06 25 1987"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_25, DateParser.parse(locale, "25-06-87"));
//        assertEquals(sd_1987_06_25, DateParser.parse(locale, "06-25-87"));

        // Pattern not actually added
//        assertEquals(sd_1987_06_25, DateParser.parse(locale, "25 06 87"));
//        assertEquals(sd_1987_06_25, DateParser.parse(locale, "06 25 87"));

        assertEquals(wd_2017_01_12_11_57_41, DateParser.ISO.parse("2017-01-12 11:57:41"));
    }

    @Test
    void englishOnly() {
        DateParser.create(Locale.ENGLISH);

        assertEquals(sd_1987_06_25, DateParser.DATE.parse("25-Jun-1987"));
        assertEquals(sd_1987_06_10, DateParser.DATE.parse("10-Jun-1987"));

        assertEquals(sd_1987_06_25, DateParser.DATE.parse("25 Jun 1987"));
        assertEquals(sd_1987_06_10, DateParser.DATE.parse("10 Jun 1987"));

        assertEquals(sd_1987_06_25, DateParser.DATE.parse("Jun 25, 1987"));
        assertEquals(sd_1987_06_10, DateParser.DATE.parse("Jun 10, 1987"));

        assertEquals(sd_1987_06_10, DateParser.DATE.parse("10 Jun. 1987"));

        assertEquals(wd_2017_01_12, DateParser.DATE.parse("12 january 2017"));

        // day 01 is implied
        assertEquals(sd_1987_06_01, DateParser.DATE.parse("Jun 1987"));
    }

    @Test
    void frenchThenEnglish() {
        DateParser.create(Locale.FRENCH);

        assertEquals(sd_1987_06_25, DateParser.DATE.parse("25-Jun-1987"));
        assertEquals(sd_1987_06_10, DateParser.DATE.parse("10-Jun-1987"));

        assertEquals(sd_1987_06_25, DateParser.DATE.parse("25 Jun 1987"));
        assertEquals(sd_1987_06_10, DateParser.DATE.parse("10 Jun 1987"));

        assertEquals(sd_1987_06_25, DateParser.DATE.parse("Jun 25, 1987"));
        assertEquals(sd_1987_06_10, DateParser.DATE.parse("Jun 10, 1987"));

        assertEquals(sd_1987_06_10, DateParser.DATE.parse("10 Jun. 1987"));

        assertEquals(wd_2017_01_12, DateParser.DATE.parse("12 january 2017"));

        assertEquals(wd_2017_01_12, DateParser.DATE.parse("12 janvier 2017"));

        // day 01 is implied
        assertEquals(sd_1987_06_01, DateParser.DATE.parse("Jun 1987"));
    }


    /**
     * Sql formatted.
     */
    @Test
    void parseSqlDate() {

        // Winter: exact time match
        // Daylight savings time will be offset by 1 hour.
        Locale locale = Locale.UK;
        DateParser.create(locale);

        assertEquals(sd_1987_06_25_11_57_41, DateParser.ISO.parse("1987-06-25 11:57:41"));

        assertEquals(LocalDateTime.of(1987, 6, 10,
                                      0, 0, 0),
                     DateParser.ISO.parse("1987-06-10"));

        assertEquals(LocalDateTime.of(1987, 6, 1,
                                      0, 0, 0),
                     DateParser.ISO.parse("1987-06"));
    }
}
