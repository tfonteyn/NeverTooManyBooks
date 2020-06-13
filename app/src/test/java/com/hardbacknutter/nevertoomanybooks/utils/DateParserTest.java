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

import java.time.LocalDateTime;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Commented out asserts are failing for now. But not all of them are:
 * (a) actually added to the parser, and/or (b) not important.
 * <p>
 * DateParser also needs to put the userLocale into account.
 */
class DateParserTest {

    // 1987-02-25; i.e. the day > possible month number (1..12)
    private final LocalDateTime w_2017_01_12 = LocalDateTime.of(2017, 1, 12, 0, 0);

    // 1987-06-25; i.e. the day > possible month number (1..12)
    private final LocalDateTime s_1987_06_25 = LocalDateTime.of(1987, 6, 25, 0, 0);

    // 1987-06-22; i.e. the day < possible month number (1..12)
    private final LocalDateTime s_1987_06_10 = LocalDateTime.of(1987, 6, 10, 0, 0);

    // 1987-06; partial date
    private final LocalDateTime s_1987_06_01 = LocalDateTime.of(1987, 6, 1, 0, 0);

    /**
     * // US format first
     * "MM-dd-yyyy HH:mm:ss",
     * "MM-dd-yyyy HH:mm",
     * <p>
     * // International next
     * "dd-MM-yyyy HH:mm:ss",
     * "dd-MM-yyyy HH:mm",
     * <p>
     * // same without time
     * "MM-dd-yyyy",
     * "dd-MM-yyyy",
     */
    @Test
    void numeric() {
        final Locale[] locales = new Locale[]{Locale.ENGLISH};
        DateParser.create(locales);

        // Matches due to MM-dd pattern being before dd-MM
        assertEquals(LocalDateTime.of(2017, 1, 12,
                                      11, 57, 41),
                     DateParser.parse("01-12-2017 11:57:41"));

        // Matches due to MM-dd pattern being before dd-MM
        assertEquals(LocalDateTime.of(2017, 1, 12,
                                      11, 57),
                     DateParser.parse("01-12-2017 11:57"));


        // No match due to MM-dd pattern being before dd-MM
        assertNotEquals(LocalDateTime.of(2017, 1, 12,
                                         11, 57, 41),
                        DateParser.parse("12-01-2017 11:57:41"));

        // No match due to MM-dd pattern being before dd-MM
        assertNotEquals(LocalDateTime.of(2017, 1, 12,
                                         11, 57),
                        DateParser.parse("12-01-2017 11:57"));


        assertEquals(LocalDateTime.of(2017, 6, 25,
                                      11, 57, 41),
                     DateParser.parse("06-25-2017 11:57:41"));

        assertEquals(LocalDateTime.of(2017, 6, 25,
                                      11, 57),
                     DateParser.parse("06-25-2017 11:57"));

        assertEquals(LocalDateTime.of(2017, 6, 25,
                                      11, 57, 41),
                     DateParser.parse("25-06-2017 11:57:41"));

        assertEquals(LocalDateTime.of(2017, 6, 25,
                                      11, 57),
                     DateParser.parse("25-06-2017 11:57"));


        assertEquals(LocalDateTime.of(1987, 6, 25,
                                      0, 0, 0),
                     DateParser.parse("25-06-1987"));
        assertEquals(LocalDateTime.of(1987, 6, 25,
                                      0, 0, 0),
                     DateParser.parse("06-25-1987"));
    }

    @Test
    void englishOnly() {
        final Locale[] locales = new Locale[]{Locale.ENGLISH};
        DateParser.create(locales);

        assertEquals(s_1987_06_25, DateParser.parse("25-Jun-1987"));
        assertEquals(s_1987_06_25, DateParser.parse("25 Jun 1987"));
        assertEquals(s_1987_06_25, DateParser.parse("Jun 25, 1987"));
        assertEquals(s_1987_06_25, DateParser.parse("25 jun. 1987"));

        assertEquals(w_2017_01_12, DateParser.parse("12 january 2017"));
        // french without the france locale enabled
        assertNull(DateParser.parse("12 janvier 2017"));

        // day 01 is implied
        assertEquals(s_1987_06_01, DateParser.parse("Jun 1987"));
    }

    @Test
    void multiLocale() {
        final Locale[] locales = new Locale[]{Locale.FRENCH, Locale.GERMAN};
        DateParser.create(locales);

        // English is always added (at the end of the parser list)
        assertEquals(s_1987_06_25, DateParser.parse("25-Jun-1987"));
        assertEquals(s_1987_06_25, DateParser.parse("25 Jun 1987"));
        assertEquals(s_1987_06_25, DateParser.parse("Jun 25, 1987"));
        assertEquals(s_1987_06_25, DateParser.parse("25 jun. 1987"));

        assertEquals(s_1987_06_25, DateParser.parse("25 juin 1987"));
        assertEquals(w_2017_01_12, DateParser.parse("12 january 2017"));
        assertEquals(w_2017_01_12, DateParser.parse("12 janvier 2017"));
        assertEquals(s_1987_06_25, DateParser.parse("25 Juni 1987"));


        // day 01 is implied
        assertEquals(s_1987_06_01, DateParser.parse("Jun 1987"));
        assertEquals(s_1987_06_01, DateParser.parse("Juni 1987"));
    }


    /**
     * ISO formatted.
     * <p>
     * "yyyy-MM-dd HH:mm:ss",
     * "yyyy-MM-dd HH:mm",
     * "yyyy-MM-dd",
     * "yyyy-MM",
     */
    @Test
    void iso() {
        final Locale[] locales = new Locale[]{LocaleUtils.getSystemLocale()};
        DateParser.create(locales);

        assertEquals(LocalDateTime.of(1987, 6, 25,
                                      11, 57, 41),
                     DateParser.parseISO("1987-06-25 11:57:41"));

        assertEquals(LocalDateTime.of(1987, 6, 25,
                                      11, 57),
                     DateParser.parseISO("1987-06-25 11:57"));

        assertEquals(LocalDateTime.of(1987, 6, 10,
                                      0, 0, 0),
                     DateParser.parseISO("1987-06-10"));

        assertEquals(LocalDateTime.of(1987, 6, 1,
                                      0, 0, 0),
                     DateParser.parseISO("1987-06"));

        assertEquals(LocalDateTime.of(1987, 1, 1,
                                      0, 0, 0),
                     DateParser.parseISO("1987"));


        assertEquals(LocalDateTime.of(1987, 11, 25,
                                      11, 57, 41),
                     DateParser.parseISO("1987-11-25 11:57:41"));

        assertEquals(LocalDateTime.of(1987, 11, 25,
                                      11, 57),
                     DateParser.parseISO("1987-11-25 11:57"));

        assertEquals(LocalDateTime.of(1987, 11, 10,
                                      0, 0, 0),
                     DateParser.parseISO("1987-11-10"));

        assertEquals(LocalDateTime.of(1987, 11, 1,
                                      0, 0, 0),
                     DateParser.parseISO("1987-11"));

        assertNull(DateParser.parseISO("1987-25-11 11:57:41"));
        assertNull(DateParser.parseISO("1987-11-11 4:57:41"));
        assertNull(DateParser.parseISO("1987-11-32 04:57:41"));
    }
}
