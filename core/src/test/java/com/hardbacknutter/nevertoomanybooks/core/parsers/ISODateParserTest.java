/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.parsers;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ISODateParserTest {

    private static final Locale LOCALE = Locale.US;

    /** "yyyy" */
    @Test
    void isoYear() {
        final DateParser parser = new ISODateParser(LOCALE);

        assertEquals(Optional.of(LocalDateTime.of(1987, 1, 1,
                                                  0, 0, 0)),
                     parser.parse("1987"));
    }

    /** "yyyy-MM" */
    @Test
    void isoYearMonth() {
        final DateParser parser = new ISODateParser(LOCALE);

        assertEquals(Optional.of(LocalDateTime.of(1987, 6, 1,
                                                  0, 0, 0)),
                     parser.parse("1987-06"));

        assertEquals(Optional.of(LocalDateTime.of(1987, 11, 1,
                                                  0, 0, 0)),
                     parser.parse("1987-11"));
    }

    /** "yyyy-MM-dd" */
    @Test
    void isoYearMonthDay() {
        final DateParser parser = new ISODateParser(LOCALE);

        assertEquals(Optional.of(LocalDateTime.of(1987, 6, 10,
                                                  0, 0, 0)),
                     parser.parse("1987-06-10"));

        assertEquals(Optional.of(LocalDateTime.of(1987, 11, 10,
                                                  0, 0, 0)),
                     parser.parse("1987-11-10"));
    }

    /**
     * "yyyy-MM-dd HH:mm:ss",
     * "yyyy-MM-dd HH:mm",
     */
    @Test
    void isoDateTime() {
        final DateParser parser = new ISODateParser(LOCALE);

        assertEquals(Optional.of(LocalDateTime.of(2020, 9, 1,
                                                  14, 20, 21, 542_000_000)),
                     parser.parse("2020-09-01 14:20:21.542000+00:00"));

        assertEquals(Optional.of(LocalDateTime.of(1987, 6, 25, 11, 57, 41)),
                     parser.parse("1987-06-25 11:57:41"));

        assertEquals(Optional.of(LocalDateTime.of(1987, 6, 25, 11, 57)),
                     parser.parse("1987-06-25 11:57"));

        assertEquals(Optional.of(LocalDateTime.of(1987, 11, 25, 11, 57, 41)),
                     parser.parse("1987-11-25 11:57:41"));

        assertEquals(Optional.of(LocalDateTime.of(1987, 11, 25, 11, 57)),
                     parser.parse("1987-11-25 11:57"));

        assertTrue(parser.parse("1987-25-11 11:57:41").isEmpty());
        assertTrue(parser.parse("1987-11-11 4:57:41").isEmpty());
        assertTrue(parser.parse("1987-11-32 04:57:41").isEmpty());
    }

    /** JDK 'T' variations */
    @Test
    void isoTeaTime() {
        final DateParser parser = new ISODateParser(LOCALE);

        assertEquals(Optional.of(LocalDateTime.of(2020, 8, 12,
                                                  14, 29, 9, 414_000_000)),
                     parser.parse("2020-08-12T14:29:09.414"));

        assertEquals(Optional.of(LocalDateTime.of(2020, 8, 12,
                                                  14, 29, 9)),
                     parser.parse("2020-08-12T14:29:09"));

        assertEquals(Optional.of(LocalDateTime.of(2020, 8, 12,
                                                  14, 29)),
                     parser.parse("2020-08-12T14:29"));

    }
}
