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
package com.hardbacknutter.nevertoomanybooks.utils.dates;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class FullDateParserTest
        extends Base {

    // 1987-02-25; i.e. the day > possible month number (1..12)
    private final Optional<LocalDateTime> w_2017_01_12 =
            Optional.of(LocalDateTime.of(2017, 1, 12, 0, 0));

    // 1987-06-25; i.e. the day > possible month number (1..12)
    private final Optional<LocalDateTime> s_1987_06_25 =
            Optional.of(LocalDateTime.of(1987, 6, 25, 0, 0));

    // 1987-06; partial date
    private final Optional<LocalDateTime> s_1987_06_01 =
            Optional.of(LocalDateTime.of(1987, 6, 1, 0, 0));


    @Test
    void numeric() {
        setLocale(Locale.ENGLISH);
        final DateParser parser = new FullDateParser(locales.get(0), locales);

        // Matches due to MM-dd pattern being before dd-MM
        assertEquals(Optional.of(LocalDateTime.of(2017, 1, 12,
                                                  11, 57, 41)),
                     parser.parse("01-12-2017 11:57:41"));

        // Matches due to MM-dd pattern being before dd-MM
        assertEquals(Optional.of(LocalDateTime.of(2017, 1, 12,
                                                  11, 57)),
                     parser.parse("01-12-2017 11:57"));


        // No match due to MM-dd pattern being before dd-MM
        assertNotEquals(Optional.of(LocalDateTime.of(2017, 1, 12,
                                                     11, 57, 41)),
                        parser.parse("12-01-2017 11:57:41"));

        // No match due to MM-dd pattern being before dd-MM
        assertNotEquals(Optional.of(LocalDateTime.of(2017, 1, 12,
                                                     11, 57)),
                        parser.parse("12-01-2017 11:57"));


        assertEquals(Optional.of(LocalDateTime.of(2017, 6, 25,
                                                  11, 57, 41)),
                     parser.parse("06-25-2017 11:57:41"));

        assertEquals(Optional.of(LocalDateTime.of(2017, 6, 25,
                                                  11, 57)),
                     parser.parse("06-25-2017 11:57"));

        assertEquals(Optional.of(LocalDateTime.of(2017, 6, 25,
                                                  11, 57, 41)),
                     parser.parse("25-06-2017 11:57:41"));

        assertEquals(Optional.of(LocalDateTime.of(2017, 6, 25,
                                                  11, 57)),
                     parser.parse("25-06-2017 11:57"));


        assertEquals(Optional.of(LocalDateTime.of(1987, 6, 25,
                                                  0, 0, 0)),
                     parser.parse("25-06-1987"));
        assertEquals(Optional.of(LocalDateTime.of(1987, 6, 25,
                                                  0, 0, 0)),
                     parser.parse("06-25-1987"));
    }

    @Test
    void englishOnly() {
        setLocale(Locale.ENGLISH);
        final DateParser parser = new FullDateParser(locales.get(0), locales);

        assertEquals(s_1987_06_25, parser.parse("25-Jun-1987"));
        assertEquals(s_1987_06_25, parser.parse("25 Jun 1987"));
        assertEquals(s_1987_06_25, parser.parse("Jun 25, 1987"));
        assertEquals(s_1987_06_25, parser.parse("25 jun. 1987"));

        assertEquals(w_2017_01_12, parser.parse("12 january 2017"));
        // french without the france locale enabled
        assertTrue(parser.parse("12 janvier 2017").isEmpty());

        // day 01 is implied
        assertEquals(s_1987_06_01, parser.parse("Jun 1987"));
    }

    @Test
    void multiLocale() {
        setLocale(Locale.FRENCH, Locale.GERMAN);
        final DateParser parser = new FullDateParser(locales.get(0), locales);

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
}
