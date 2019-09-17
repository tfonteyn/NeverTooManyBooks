/*
 * @Copyright 2019 HardBackNutter
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

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvCoder;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringListTest {

    private static final String SERIES =
            "Jerry Cornelius"
            + "|"
            + "Dancers (5)"
            + "|"
            + "Cornelius Chronicles, The (8\\|8 as includes The Alchemist's Question)"
            + "|"
            + "Eternal Champion, The (984\\|Jerry Cornelius Calendar 4 as includes The Alchemist's Question)";


    /**
     * First decode, test individual strings, then encode and test we got the original back.
     */
    @Test
    void coder() {
        StringList<Series> coder = CsvCoder.getSeriesCoder();
        ArrayList<Series> series = coder.decode(SERIES);
        int i = 0;
        assertEquals("Jerry Cornelius", series.get(i).getTitle());
        assertEquals("", series.get(i).getNumber());
        i++;
        assertEquals("Dancers", series.get(i).getTitle());
        assertEquals("5", series.get(i).getNumber());
        i++;
        assertEquals("Cornelius Chronicles, The", series.get(i).getTitle());
        assertEquals("8|8 as includes The Alchemist's Question", series.get(i).getNumber());
        i++;
        assertEquals("Eternal Champion, The", series.get(i).getTitle());
        assertEquals("984|Jerry Cornelius Calendar 4 as includes The Alchemist's Question",
                     series.get(i).getNumber());

        String encoded = CsvCoder.getSeriesCoder().encodeList(series);
        assertEquals(SERIES, encoded);
    }
}