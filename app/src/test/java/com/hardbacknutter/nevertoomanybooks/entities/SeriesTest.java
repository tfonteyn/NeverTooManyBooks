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
package com.hardbacknutter.nevertoomanybooks.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SeriesTest {

    @Test
    void fromString00() {
        Series series = Series.fromString("This is the series title");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
    }

    @Test
    void fromString01() {
        Series series = Series.fromString("This is the series title(34)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString02() {
        Series series = Series.fromString("This is the series title (34)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString03() {
        Series series = Series.fromString("This is the series title ( 34)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }


    @Test
    void fromString11() {
        Series series = Series.fromString("This is the series title(iv)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("iv", series.getNumber());
    }

    @Test
    void fromString12() {
        Series series = Series.fromString("This is the series title (iv)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("iv", series.getNumber());
    }

    @Test
    void fromString13() {
        Series series = Series.fromString("This is the series title ( iv)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("iv", series.getNumber());
    }


    @Test
    void fromString21() {
        Series series = Series.fromString("This is the series title, subtitle(34)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString22() {
        Series series = Series.fromString("This is the series title, subtitle (34)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString23() {
        Series series = Series.fromString("This is the series title, subtitle ( 34)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }


    @Test
    void fromString31() {
        Series series = Series.fromString("This is the series title, subtitle(vii)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("vii", series.getNumber());
    }

    @Test
    void fromString32() {
        Series series = Series.fromString("This is the series title, subtitle (vii)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("vii", series.getNumber());
    }

    @Test
    void fromString33() {
        Series series = Series.fromString("This is the series title, subtitle ( vii)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("vii", series.getNumber());
    }


    @Test
    void fromString41() {
        Series series = Series.fromString("This is the series title, subtitle(part 1)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("1", series.getNumber());
    }

    @Test
    void fromString42() {
        Series series = Series.fromString("This is the series title, subtitle (deel 2)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("2", series.getNumber());
    }

    @Test
    void fromString43() {
        Series series = Series.fromString("This is the series title, subtitle ( vol. 3)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("3", series.getNumber());
    }


    @Test
    void fromString51() {
        Series series = Series.fromString("This is the series title, subtitle(part1)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("1", series.getNumber());
    }

    @Test
    void fromString52() {
        Series series = Series.fromString("This is the series title, subtitle (deel2)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("2", series.getNumber());
    }

    @Test
    void fromString53() {
        Series series = Series.fromString("This is the series title, subtitle ( vol3)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("3", series.getNumber());
    }


    @Test
    void fromString61() {
        Series series = Series.fromString("This is the series title, subtitle(34|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34|omnibus", series.getNumber());
    }

    @Test
    void fromString62() {
        Series series = Series.fromString("This is the series title, subtitle (34|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34|omnibus", series.getNumber());
    }

    @Test
    void fromString63() {
        Series series = Series.fromString("This is the series title, subtitle ( 34|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34|omnibus", series.getNumber());
    }


    @Test
    void fromString71() {
        Series series = Series.fromString("This is the series title, subtitle(iii|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("iii|omnibus", series.getNumber());
    }

    @Test
    void fromString72() {
        Series series = Series.fromString("This is the series title, subtitle (iii|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("iii|omnibus", series.getNumber());
    }

    @Test
    void fromString73() {
        Series series = Series.fromString("This is the series title, subtitle ( iii|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("iii|omnibus", series.getNumber());
    }


    @Test
    void fromString81() {
        Series series = Series.fromString("This is the series title #34");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString82() {
        Series series = Series.fromString("This is the series title, subtitle # 34");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString83() {
        Series series = Series.fromString("This is the series title, subtitle #34  ");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString84() {
        Series series = Series.fromString("This is the series title, #34  ");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString85() {
        Series series = Series.fromString("This is the series title,#34  ");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString86() {
        Series series = Series.fromString("This is the series title#34  ");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }


    @Test
    void fromString91() {
        Series series = Series.fromString("This is the series 34  ");
        assertNotNull(series);
        assertEquals("This is the series", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString92() {
        Series series = Series.fromString("This is the series, 34");
        assertNotNull(series);
        assertEquals("This is the series", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString93() {
        Series series = Series.fromString("This is the series, subtitle part 34");
        assertNotNull(series);
        assertEquals("This is the series, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString94() {
        Series series = Series.fromString("This is the series, subtitle, part 34");
        assertNotNull(series);
        assertEquals("This is the series, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }


    @Test
    void fromString1001() {
        Series series = Series.fromString("This is (the series) title, subtitle ( iii|omnibus)");
        assertNotNull(series);
        assertEquals("This is (the series) title, subtitle", series.getTitle());
        assertEquals("iii|omnibus", series.getNumber());
    }

    @Test
    void fromString1002() {
        Series series = Series.fromString("This is (the series) title, subtitle (34)");
        assertNotNull(series);
        assertEquals("This is (the series) title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString1003() {
        Series series = Series.fromString("This is #title, subtitle (4omnibus)");
        assertNotNull(series);
        assertEquals("This is #title, subtitle", series.getTitle());
        assertEquals("4omnibus", series.getNumber());
    }


    // FAILS
    @Test
    void fromString1004() {
        Series series = Series.fromString("This is #title, subtitle (omnibus)");
        assertNotNull(series);
        assertEquals("This is #title, subtitle", series.getTitle());
        assertEquals("omnibus", series.getNumber());
    }

    // FAILS
    @Test
    void fromString1005() {
        Series series = Series.fromString("This is #title, subtitle (omnibus)");
        assertNotNull(series);
        assertEquals("This is #title, subtitle", series.getTitle());
        assertEquals("omnibus", series.getNumber());
    }
}