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

package com.hardbacknutter.nevertoomanybooks.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ISNITest {

    @Test
    void simple() {
        final ISNI i1 = new ISNI("0000 0001 2146 438X");
        assertTrue(i1.isValid());
        assertEquals("000000012146438X", i1.asText());
    }

    @Test
    void simple2() {
        final ISNI i1 = new ISNI("1 2146 438X");
        assertTrue(i1.isValid());
        assertEquals("000000012146438X", i1.asText());
    }

    @Test
    void invalidChecksum() {
        final ISNI i1 = new ISNI("2 2146 438X");
        assertFalse(i1.isValid());
        // The original string (stripped)
        assertEquals("22146438X", i1.asText());
    }
}
