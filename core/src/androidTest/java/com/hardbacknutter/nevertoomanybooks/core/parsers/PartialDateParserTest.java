/*
 * @Copyright 2018-2026 HardBackNutter
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

import java.time.Month;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartialDateParserTest {

    private final PartialDateParser parser = new PartialDateParser();

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

        od = parser.parse("13 Jun 2020", Locale.UK);
        assertTrue(od.isPresent());
        assertEquals("2020-06-13", od.get().getIsoString());

        od = parser.parse("June 2020", Locale.UK);
        assertTrue(od.isPresent());
        assertEquals("2020-06", od.get().getIsoString());

        od = parser.parse("9 June 2020", Locale.UK);
        assertTrue(od.isPresent());
        assertEquals("2020-06-09", od.get().getIsoString());

        od = parser.parse("Juni 2020", Locale.GERMAN);
        assertTrue(od.isPresent());
        assertEquals("2020-06", od.get().getIsoString());

        od = parser.parse("Juin 2020", Locale.FRENCH);
        assertTrue(od.isPresent());
        assertEquals("2020-06", od.get().getIsoString());

        od = parser.parse("juin 2020", Locale.FRENCH);
        assertTrue(od.isPresent());
        assertEquals("2020-06", od.get().getIsoString());

        od = parser.parse("13 juin 2020", Locale.FRENCH);
        assertTrue(od.isPresent());
        assertEquals("2020-06-13", od.get().getIsoString());
    }

    @Test
    void parseBC01() {
        final Optional<PartialDate> od;
        final PartialDate partialDate;

        od = parser.parse("-0019-08-13", Locale.UK);
        assertTrue(od.isPresent());
        partialDate = od.get();

        final Optional<Integer> oYear = partialDate.getYear();
        assertTrue(oYear.isPresent());
        assertEquals(-19, oYear.get());

        final Optional<Month> oMonth = partialDate.getMonth();
        assertTrue(oMonth.isPresent());
        assertEquals(Month.AUGUST, oMonth.get());

        final Optional<Integer> oDayOfMonth = partialDate.getDayOfMonth();
        assertTrue(oDayOfMonth.isPresent());
        assertEquals(13, oDayOfMonth.get());

        assertEquals("-0019-08-13", partialDate.getIsoString());
    }

    @Test
    void parseBC02() {
        final Optional<PartialDate> od;
        final PartialDate partialDate;

        od = parser.parse("-19-08-13", Locale.UK);
        assertTrue(od.isPresent());
        partialDate = od.get();

        final Optional<Integer> oYear = partialDate.getYear();
        assertTrue(oYear.isPresent());
        assertEquals(-19, oYear.get());

        final Optional<Month> oMonth = partialDate.getMonth();
        assertTrue(oMonth.isPresent());
        assertEquals(Month.AUGUST, oMonth.get());

        final Optional<Integer> oDayOfMonth = partialDate.getDayOfMonth();
        assertTrue(oDayOfMonth.isPresent());
        assertEquals(13, oDayOfMonth.get());

        assertEquals("-0019-08-13", partialDate.getIsoString());
    }

    @Test
    void parsePlus() {
        Optional<PartialDate> od;
        final PartialDate partialDate;

        od = parser.parse("+0019-08-13", Locale.UK);
        assertTrue(od.isPresent());
        partialDate = od.get();

        final Optional<Integer> oYear = partialDate.getYear();
        assertTrue(oYear.isPresent());
        assertEquals(19, oYear.get());

        final Optional<Month> oMonth = partialDate.getMonth();
        assertTrue(oMonth.isPresent());
        assertEquals(Month.AUGUST, oMonth.get());

        final Optional<Integer> oDayOfMonth = partialDate.getDayOfMonth();
        assertTrue(oDayOfMonth.isPresent());
        assertEquals(13, oDayOfMonth.get());

        assertEquals("0019-08-13", partialDate.getIsoString());

        od = parser.parse("+2020/09/17");
        assertTrue(od.isPresent());
        assertEquals("2020-09-17", od.get().getIsoString());
    }

    @Test
    void parseWikidataZeros() {
        Optional<PartialDate> od;
        final PartialDate partialDate;

        od = parser.parse("+1964-02-00T00:00:00Z", Locale.UK);
        assertTrue(od.isPresent());
        partialDate = od.get();

        final Optional<Integer> oYear = partialDate.getYear();
        assertTrue(oYear.isPresent());
        assertEquals(1964, oYear.get());

        final Optional<Month> oMonth = partialDate.getMonth();
        assertTrue(oMonth.isPresent());
        assertEquals(Month.FEBRUARY, oMonth.get());

        final Optional<Integer> oDayOfMonth = partialDate.getDayOfMonth();
        assertFalse(oDayOfMonth.isPresent());

        assertEquals("1964-02", partialDate.getIsoString());
    }

    @Test
    void parseWikidataZerosAndUtc() {
        Optional<PartialDate> od;
        final PartialDate partialDate;

        // Request utc with zero values, should not crash
        od = parser.parse("+1964-02-00T00:00:00Z", Locale.UK, true);
        assertTrue(od.isPresent());
        partialDate = od.get();

        final Optional<Integer> oYear = partialDate.getYear();
        assertTrue(oYear.isPresent());
        assertEquals(1964, oYear.get());

        final Optional<Month> oMonth = partialDate.getMonth();
        assertTrue(oMonth.isPresent());
        assertEquals(Month.FEBRUARY, oMonth.get());

        final Optional<Integer> oDayOfMonth = partialDate.getDayOfMonth();
        assertFalse(oDayOfMonth.isPresent());

        assertEquals("1964-02", partialDate.getIsoString());
    }
}
