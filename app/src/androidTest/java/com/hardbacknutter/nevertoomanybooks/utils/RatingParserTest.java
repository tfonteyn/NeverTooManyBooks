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

package com.hardbacknutter.nevertoomanybooks.utils;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("MissingJavadoc")
public class RatingParserTest {

    @Test
    public void v5() {
        final RatingParser equalizer = new RatingParser(5);
        Optional<Float> convert;

        // Anything less than 0.251 becomes 0, which we reject
        convert = equalizer.parse("0.1");
        assertFalse(convert.isPresent());

        convert = equalizer.parse("0.4");
        assertTrue(convert.isPresent());
        assertEquals(0.5, convert.get(), 0.1);

        convert = equalizer.parse("3");
        assertTrue(convert.isPresent());
        assertEquals(3, convert.get(), 0.1);

        convert = equalizer.parse("3.1");
        assertTrue(convert.isPresent());
        assertEquals(3, convert.get(), 0.1);

        convert = equalizer.parse("3.4");
        assertTrue(convert.isPresent());
        assertEquals(3.5, convert.get(), 0.1);

        convert = equalizer.parse("3.5");
        assertTrue(convert.isPresent());
        assertEquals(3.5, convert.get(), 0.1);

        convert = equalizer.parse("3.6");
        assertTrue(convert.isPresent());
        assertEquals(3.5, convert.get(), 0.1);

        convert = equalizer.parse("3.9");
        assertTrue(convert.isPresent());
        assertEquals(4, convert.get(), 0.1);

        convert = equalizer.parse("5.0");
        assertTrue(convert.isPresent());
        assertEquals(5, convert.get(), 0.1);
    }

    @Test
    public void v10() {
        final RatingParser equalizer = new RatingParser(10);
        Optional<Float> convert;

        // Anything less than 0.5 becomes 0, which we reject
        convert = equalizer.parse("0.1");
        assertFalse(convert.isPresent());

        convert = equalizer.parse("0.6");
        assertTrue(convert.isPresent());
        assertEquals(0.5, convert.get(), 0.1);

        convert = equalizer.parse("6");
        assertTrue(convert.isPresent());
        assertEquals(3, convert.get(), 0.1);

        convert = equalizer.parse("6.1");
        assertTrue(convert.isPresent());
        assertEquals(3, convert.get(), 0.1);

        convert = equalizer.parse("6.4");
        assertTrue(convert.isPresent());
        assertEquals(3.0, convert.get(), 0.1);

        convert = equalizer.parse("6.5");
        assertTrue(convert.isPresent());
        assertEquals(3.5, convert.get(), 0.1);

        convert = equalizer.parse("6.6");
        assertTrue(convert.isPresent());
        assertEquals(3.5, convert.get(), 0.1);

        convert = equalizer.parse("6.9");
        assertTrue(convert.isPresent());
        assertEquals(3.5, convert.get(), 0.1);

        convert = equalizer.parse("10.0");
        assertTrue(convert.isPresent());
        assertEquals(5, convert.get(), 0.1);
    }
}