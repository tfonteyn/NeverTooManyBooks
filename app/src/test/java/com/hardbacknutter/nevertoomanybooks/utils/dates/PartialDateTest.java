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

import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartialDateTest {

    @Test
    void t01() {
        PartialDate date;

        date = new PartialDate(2020, 9, 17);
        assertEquals("2020-09-17", date.getIsoString());

        date = new PartialDate("2020-09-17");
        assertEquals("2020-09-17", date.getIsoString());
        date = new PartialDate("2020-09");
        assertEquals("2020-09", date.getIsoString());
        date = new PartialDate("2020");
        assertEquals("2020", date.getIsoString());

        // No support for single digit date parts
        date = new PartialDate("2020-9-17");
        assertEquals("", date.getIsoString());
        date = new PartialDate("2020-9");
        assertEquals("", date.getIsoString());

        date = new PartialDate("1941");
        assertEquals("1941", date.getIsoString());
    }
}
