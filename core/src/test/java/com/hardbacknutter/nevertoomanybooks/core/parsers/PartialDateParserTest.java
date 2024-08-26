/*
 * @Copyright 2018-2024 HardBackNutter
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

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartialDateParserTest {

    private PartialDateParser parser;

    @BeforeEach
    void setup() {
        parser = new PartialDateParser();
    }

    @Test
    void parse01() {
        Optional<PartialDate> od;

        od = parser.parse("2020-09-17");
        assertTrue(od.isPresent());
        assertEquals("2020-09-17", od.get().getIsoString());

        od = parser.parse("2020-09");
        assertTrue(od.isPresent());
        assertEquals("2020-09", od.get().getIsoString());

        od = parser.parse("2020");
        assertTrue(od.isPresent());
        assertEquals("2020", od.get().getIsoString());
    }

    @Test
    void parse02() {
        Optional<PartialDate> od;

        od = parser.parse("2020/09/17");
        assertTrue(od.isPresent());
        assertEquals("2020-09-17", od.get().getIsoString());

        od = parser.parse("2020/09");
        assertTrue(od.isPresent());
        assertEquals("2020-09", od.get().getIsoString());
    }

    @Test
    void parse10() {
        Optional<PartialDate> od;

        od = parser.parse("2020-9-17");
        assertTrue(od.isPresent());
        assertEquals("2020-09-17", od.get().getIsoString());

        od = parser.parse("2020-9");
        assertTrue(od.isPresent());
        assertEquals("2020-09", od.get().getIsoString());

        od = parser.parse("2020-9-7");
        assertTrue(od.isPresent());
        assertEquals("2020-09-07", od.get().getIsoString());
    }

    @Test
    void parse100() {
        Optional<PartialDate> od;

        od = parser.parse("Jun 2020", Locale.UK);
        assertTrue(od.isPresent());
        assertEquals("2020-06", od.get().getIsoString());

        od = parser.parse("June 2020", Locale.UK);
        assertTrue(od.isPresent());
        assertEquals("2020-06", od.get().getIsoString());

        od = parser.parse("Juni 2020", Locale.GERMAN);
        assertTrue(od.isPresent());
        assertEquals("2020-06", od.get().getIsoString());

        od = parser.parse("Juin 2020", Locale.FRENCH);
        assertTrue(od.isPresent());
        assertEquals("2020-06", od.get().getIsoString());

        od = parser.parse("juin 2020", Locale.FRENCH);
        assertTrue(od.isPresent());
        assertEquals("2020-06", od.get().getIsoString());
    }
}
