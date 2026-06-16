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

package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsinTest {

    @Test
    void isbn10() {
        final ASIN asin = new ASIN("1529514371");
        assertEquals("1529514371", asin.asText());
        assertTrue(asin.isValid());
    }

    // Won't be seen in the wild, but paranoia...
    @Test
    void isbn13() {
        final ASIN asin = new ASIN("978-1529514377");
        assertEquals("1529514371", asin.asText());
        assertTrue(asin.isValid());
    }

    @Test
    void invalid() {
        final ASIN asin = new ASIN("1529514370");
        assertEquals("1529514370", asin.asText());
        assertFalse(asin.isValid());
    }

    @Test
    void alpha() {
        final ASIN asin = new ASIN("B0DGNTX32R");
        assertEquals("B0DGNTX32R", asin.asText());
        assertTrue(asin.isValid());
    }

    @Test
    void invalidAlpha() {
        final ASIN asin = new ASIN("B0DGN_X32R");
        assertEquals("B0DGN_X32R", asin.asText());
        assertFalse(asin.isValid());
    }
}
