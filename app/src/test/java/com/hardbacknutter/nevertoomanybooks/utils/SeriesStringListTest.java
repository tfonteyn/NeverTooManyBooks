/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvCoder;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesStringListTest {

    /**
     * String suited to be used with Series.fromString(),
     * as tested in com.hardbacknutter.nevertoomanybooks.entities.SeriesTest.
     */
    private static final String[] SERIES = {
            "Jerry Cornelius",
            "Dancers (5)",
            "Cornelius Chronicles, The (8|8 as includes The Alchemist's Question)",
            "Eternal Champion, The (984|Jerry Cornelius Calendar 4 as includes"
            + " The Alchemist's * Question)"
    };

    /**
     * The correct string result of encoding the above; as written to the export file.
     * Ergo, the correct string how an import file should contain this field for a book.
     */
    private static final String ENCODED =
            "Jerry Cornelius"
            + "|"
            + "Dancers (5) * {\"complete\":true}"
            + "|"
            + "Cornelius Chronicles, The (8\\|8 as includes The Alchemist's Question)"
            + " * {\"complete\":true}"
            + "|"
            + "Eternal Champion, The (984\\|Jerry Cornelius Calendar 4 as includes"
            + " The Alchemist's \\\\* Question)";

    private List<Series> mSeries;

    private StringList<Series> mCoder;

    @BeforeEach
    void setUp() {
        mSeries = new ArrayList<>();
        for (String s : SERIES) {
            mSeries.add(Series.fromString(s));
        }
        mSeries.get(1).setComplete(true);
        mSeries.get(2).setComplete(true);

        mCoder = CsvCoder.getSeriesCoder();
    }

    @Test
    void encode() {
        String encoded = mCoder.encodeList(mSeries);
        assertEquals(ENCODED, encoded);
    }

    @Test
    void decode() {
        List<Series> decoded = mCoder.decode(ENCODED);
        assertEquals(SERIES.length, decoded.size());

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
