/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.SeriesCoder;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.StringList;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesStringListTest {

    /**
     * The correct string result of encoding the above; as written to the export file.
     * Ergo, the correct string how an import file should contain this field for a book.
     */
    private static final String ENCODED =
            "Jerry Cornelius"
            + "|"
            + "Dancers (5) * {\"series_complete\":true}"
            + "|"
            + "Cornelius Chronicles, The (8\\|8 as includes The Alchemist's Question)"
            + " * {\"series_complete\":true}"
            + "|"
            + "Eternal Champion, The (984\\|Jerry Cornelius Calendar 4 as includes"
            + " The Alchemist's \\\\* Question)";

    private StringList<Series> coder;

    @BeforeEach
    void setUp() {
        coder = new StringList<>(new SeriesCoder());
    }

    @Test
    void decode() {
        final List<Series> decoded = coder.decodeList(ENCODED);
        assertEquals(4, decoded.size());

        Series series;

        series = decoded.get(0);
        assertEquals("Jerry Cornelius", series.getTitle());
        assertEquals("", series.getNumber());
        assertFalse(series.isComplete());

        series = decoded.get(1);
        assertEquals("Dancers", series.getTitle());
        assertEquals("5", series.getNumber());
        assertTrue(series.isComplete());

        series = decoded.get(2);
        assertEquals("Cornelius Chronicles, The", series.getTitle());
        assertEquals("8|8 as includes The Alchemist's Question", series.getNumber());
        assertTrue(series.isComplete());

        series = decoded.get(3);
        assertEquals("Eternal Champion, The", series.getTitle());
        assertEquals("984|Jerry Cornelius Calendar 4 as includes The Alchemist's * Question",
                     series.getNumber());
        assertFalse(series.isComplete());
    }
}
