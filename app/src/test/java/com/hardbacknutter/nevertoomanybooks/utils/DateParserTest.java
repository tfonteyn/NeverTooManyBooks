/*
 * @Copyright 2020 HardBackNutter
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

import java.time.LocalDateTime;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.Base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Commented out asserts are failing for now. But not all of them are:
 * (a) actually added to the parser, and/or (b) not important.
 * <p>
 * DateParser also needs to put the userLocale into account.
 */
class DateParserTest
        extends Base {

    // 1987-02-25; i.e. the day > possible month number (1..12)
    private final LocalDateTime w_2017_01_12 = LocalDateTime.of(2017, 1, 12, 0, 0);

    // 1987-06-25; i.e. the day > possible month number (1..12)
    private final LocalDateTime s_1987_06_25 = LocalDateTime.of(1987, 6, 25, 0, 0);

    // 1987-06; partial date
    private final LocalDateTime s_1987_06_01 = LocalDateTime.of(1987, 6, 1, 0, 0);

    @Test
    void numeric() {
        final Locale[] locales = new Locale[]{Locale.ENGLISH};
        final DateParser parser = DateParser.createForTesting(locales);

        // Matches due to MM-dd pattern being before dd-MM
        assertEquals(LocalDateTime.of(2017, 1, 12,
                                      11, 57, 41),
                     parser.parse("01-12-2017 11:57:41"));

        // Matches due to MM-dd pattern being before dd-MM
        assertEquals(LocalDateTime.of(2017, 1, 12,
                                      11, 57),
                     parser.parse("01-12-2017 11:57"));


        // No match due to MM-dd pattern being before dd-MM
        assertNotEquals(LocalDateTime.of(2017, 1, 12,
                                         11, 57, 41),
                        parser.parse("12-01-2017 11:57:41"));

        // No match due to MM-dd pattern being before dd-MM
        assertNotEquals(LocalDateTime.of(2017, 1, 12,
                                         11, 57),
                        parser.parse("12-01-2017 11:57"));


        assertEquals(LocalDateTime.of(2017, 6, 25,
                                      11, 57, 41),
                     parser.parse("06-25-2017 11:57:41"));

        assertEquals(LocalDateTime.of(2017, 6, 25,
                                      11, 57),
                     parser.parse("06-25-2017 11:57"));

        assertEquals(LocalDateTime.of(2017, 6, 25,
                                      11, 57, 41),
                     parser.parse("25-06-2017 11:57:41"));

        assertEquals(LocalDateTime.of(2017, 6, 25,
                                      11, 57),
                     parser.parse("25-06-2017 11:57"));


        assertEquals(LocalDateTime.of(1987, 6, 25,
                                      0, 0, 0),
                     parser.parse("25-06-1987"));
        assertEquals(LocalDateTime.of(1987, 6, 25,
                                      0, 0, 0),
                     parser.parse("06-25-1987"));
    }

    @Test
    void englishOnly() {
        final Locale[] locales = new Locale[]{Locale.ENGLISH};
        final DateParser parser = DateParser.createForTesting(locales);

        assertEquals(s_1987_06_25, parser.parse("25-Jun-1987"));
        assertEquals(s_1987_06_25, parser.parse("25 Jun 1987"));
        assertEquals(s_1987_06_25, parser.parse("Jun 25, 1987"));
        assertEquals(s_1987_06_25, parser.parse("25 jun. 1987"));

        assertEquals(w_2017_01_12, parser.parse("12 january 2017"));
        // french without the france locale enabled
        assertNull(parser.parse("12 janvier 2017"));

        // day 01 is implied
        assertEquals(s_1987_06_01, parser.parse("Jun 1987"));
    }

    @Test
    void multiLocale() {
        final Locale[] locales = new Locale[]{Locale.FRENCH, Locale.GERMAN};
        final DateParser parser = DateParser.createForTesting(locales);

        // English is always added (at the end of the parser list)
        assertEquals(s_1987_06_25, parser.parse("25-Jun-1987"));
        assertEquals(s_1987_06_25, parser.parse("25 Jun 1987"));
        assertEquals(s_1987_06_25, parser.parse("Jun 25, 1987"));
        assertEquals(s_1987_06_25, parser.parse("25 jun. 1987"));

        assertEquals(s_1987_06_25, parser.parse("25 juin 1987"));
        assertEquals(w_2017_01_12, parser.parse("12 january 2017"));
        assertEquals(w_2017_01_12, parser.parse("12 janvier 2017"));
        assertEquals(s_1987_06_25, parser.parse("25 Juni 1987"));


        // day 01 is implied
        assertEquals(s_1987_06_01, parser.parse("Jun 1987"));
        assertEquals(s_1987_06_01, parser.parse("Juni 1987"));
    }

    /**
     * {@link DateParser#parseISO}.
     * <p>
     * "yyyy"
     */
    @Test
    void isoYear() {
        final Locale[] locales = new Locale[]{Locale.US};
        final DateParser parser = DateParser.createForTesting(locales);

        assertEquals(LocalDateTime.of(1987, 1, 1,
                                      0, 0, 0),
                     parser.parseISO("1987"));
    }

    /**
     * {@link DateParser#parseISO}.
     * <p>
     * "yyyy-MM"
     */
    @Test
    void isoYearMonth() {
        final Locale[] locales = new Locale[]{Locale.US};
        final DateParser parser = DateParser.createForTesting(locales);

        assertEquals(LocalDateTime.of(1987, 6, 1,
                                      0, 0, 0),
                     parser.parseISO("1987-06"));

        assertEquals(LocalDateTime.of(1987, 11, 1,
                                      0, 0, 0),
                     parser.parseISO("1987-11"));
    }

    /**
     * {@link DateParser#parseISO}.
     * <p>
     * "yyyy-MM-dd"
     */
    @Test
    void isoYearMonthDay() {
        final Locale[] locales = new Locale[]{Locale.US};
        final DateParser parser = DateParser.createForTesting(locales);

        assertEquals(LocalDateTime.of(1987, 6, 10,
                                      0, 0, 0),
                     parser.parseISO("1987-06-10"));

        assertEquals(LocalDateTime.of(1987, 11, 10,
                                      0, 0, 0),
                     parser.parseISO("1987-11-10"));
    }

    /**
     * {@link DateParser#parseISO}.
     * <p>
     * "yyyy-MM-dd HH:mm:ss",
     * "yyyy-MM-dd HH:mm",
     */
    @Test
    void isoDateTime() {
        final Locale[] locales = new Locale[]{Locale.US};
        final DateParser parser = DateParser.createForTesting(locales);

        assertEquals(LocalDateTime.of(2020, 9, 1,
                                      14, 20, 21, 542_000_000),
                     parser.parseISO("2020-09-01 14:20:21.542000+00:00"));

        assertEquals(LocalDateTime.of(1987, 6, 25,
                                      11, 57, 41),
                     parser.parseISO("1987-06-25 11:57:41"));

        assertEquals(LocalDateTime.of(1987, 6, 25,
                                      11, 57),
                     parser.parseISO("1987-06-25 11:57"));

        assertEquals(LocalDateTime.of(1987, 11, 25,
                                      11, 57, 41),
                     parser.parseISO("1987-11-25 11:57:41"));

        assertEquals(LocalDateTime.of(1987, 11, 25,
                                      11, 57),
                     parser.parseISO("1987-11-25 11:57"));

        assertNull(parser.parseISO("1987-25-11 11:57:41"));
        assertNull(parser.parseISO("1987-11-11 4:57:41"));
        assertNull(parser.parseISO("1987-11-32 04:57:41"));
    }

    /**
     * {@link DateParser#parseISO}.
     * <p>
     * JDK 'T' variations
     */
    @Test
    void isoDateTeaTime() {
        final Locale[] locales = new Locale[]{Locale.US};
        final DateParser parser = DateParser.createForTesting(locales);

        assertEquals(LocalDateTime.of(2020, 8, 12,
                                      14, 29, 9, 414_000_000),
                     parser.parseISO("2020-08-12T14:29:09.414"));

        assertEquals(LocalDateTime.of(2020, 8, 12,
                                      14, 29, 9),
                     parser.parseISO("2020-08-12T14:29:09"));

        assertEquals(LocalDateTime.of(2020, 8, 12,
                                      14, 29),
                     parser.parseISO("2020-08-12T14:29"));

    }
}
