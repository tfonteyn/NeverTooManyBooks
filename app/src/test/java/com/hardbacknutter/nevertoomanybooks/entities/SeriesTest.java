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
package com.hardbacknutter.nevertoomanybooks.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test the regular expressions used by {@link DataHolderUtils#requireSeries}.
 */
class SeriesTest {

    @Test
    void fromString00() {
        final Series series = Series.from("This is the series title");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
    }

    @Test
    void fromString01() {
        final Series series = Series.from("This is the series title(34)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString02() {
        final Series series = Series.from("This is the series title (34)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString03() {
        final Series series = Series.from("This is the series title ( 34)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString04() {
        final Series series = Series.from("Series Title");
        assertNotNull(series);
        assertEquals("Series Title", series.getTitle());
        assertEquals("", series.getNumber());
    }

    /**
     * <strong>single</strong> word with a roman numeral embedded
     */
    @Test
    void fromString05() {
        final Series series = Series.from("bill");
        assertNotNull(series);
        assertEquals("bill", series.getTitle());
        assertEquals("", series.getNumber());
    }

    @Test
    void fromString05a() {
        final Series series = Series.from("bill", "");
        assertNotNull(series);
        assertEquals("bill", series.getTitle());
        assertEquals("", series.getNumber());
    }

    /**
     * <strong>single</strong> word starting with a roman numeral.
     */
    @Test
    void fromString06() {
        final Series series = Series.from("illegal");
        assertNotNull(series);
        assertEquals("illegal", series.getTitle());
        assertEquals("", series.getNumber());
    }

    @Test
    void fromString06a() {
        final Series series = Series.from("illegal", "");
        assertNotNull(series);
        assertEquals("illegal", series.getTitle());
        assertEquals("", series.getNumber());
    }

    @Test
    void fromString07() {
        final Series series = Series.from("illegal 5");
        assertNotNull(series);
        assertEquals("illegal", series.getTitle());
        assertEquals("5", series.getNumber());
    }

    @Test
    void fromString11() {
        final Series series = Series.from("This is the series title(iv)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("iv", series.getNumber());
    }

    @Test
    void fromString12() {
        final Series series = Series.from("This is the series title (iv)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("iv", series.getNumber());
    }

    @Test
    void fromString13() {
        final Series series = Series.from("This is the series title ( iv)");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("iv", series.getNumber());
    }


    @Test
    void fromString21() {
        final Series series = Series.from("This is the series title, subtitle(34)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString22() {
        final Series series = Series.from("This is the series title, subtitle (34)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString23() {
        final Series series = Series.from("This is the series title, subtitle ( 34)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }


    @Test
    void fromString31() {
        final Series series = Series.from("This is the series title, subtitle(vii)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("vii", series.getNumber());
    }

    @Test
    void fromString32() {
        final Series series = Series.from("This is the series title, subtitle (vii)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("vii", series.getNumber());
    }

    @Test
    void fromString33() {
        final Series series = Series.from("This is the series title, subtitle ( vii)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("vii", series.getNumber());
    }


    @Test
    void fromString41() {
        final Series series = Series.from("This is the series title, subtitle(part 1)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("1", series.getNumber());
    }

    @Test
    void fromString42() {
        final Series series = Series.from("This is the series title, subtitle (deel 2)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("2", series.getNumber());
    }

    @Test
    void fromString43() {
        final Series series = Series.from("This is the series title, subtitle ( vol. 3)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("3", series.getNumber());
    }


    @Test
    void fromString51() {
        final Series series = Series.from("This is the series title, subtitle(part1)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("1", series.getNumber());
    }

    @Test
    void fromString52() {
        final Series series = Series.from("This is the series title, subtitle (deel2)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("2", series.getNumber());
    }

    @Test
    void fromString53() {
        final Series series = Series.from("This is the series title, subtitle ( vol3)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("3", series.getNumber());
    }


    @Test
    void fromString61() {
        final Series series = Series.from("This is the series title, subtitle(34|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34|omnibus", series.getNumber());
    }

    @Test
    void fromString62() {
        final Series series = Series.from("This is the series title, subtitle (34|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34|omnibus", series.getNumber());
    }

    @Test
    void fromString62b() {
        final Series series = Series.from("This is the series title, subtitle 34|omnibus");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34|omnibus", series.getNumber());
    }
    @Test
    void fromString63() {
        final Series series = Series.from("This is the series title, subtitle ( 34|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34|omnibus", series.getNumber());
    }


    @Test
    void fromString71() {
        final Series series = Series.from("This is the series title, subtitle(iii|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("iii|omnibus", series.getNumber());
    }

    @Test
    void fromString72() {
        final Series series = Series.from("This is the series title, subtitle (iii|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("iii|omnibus", series.getNumber());
    }

    @Test
    void fromString73() {
        final Series series = Series.from("This is the series title, subtitle ( iii|omnibus)");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("iii|omnibus", series.getNumber());
    }


    @Test
    void fromString81() {
        final Series series = Series.from("This is the series title #34");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString82() {
        final Series series = Series.from("This is the series title, subtitle # 34");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString83() {
        final Series series = Series.from("This is the series title, subtitle #34  ");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString84() {
        final Series series = Series.from("This is the series title, #34  ");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString85() {
        final Series series = Series.from("This is the series title,#34  ");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString86() {
        final Series series = Series.from("This is the series title#34  ");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }


    @Test
    void fromString91() {
        final Series series = Series.from("This is the series 34  ");
        assertNotNull(series);
        assertEquals("This is the series", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString92() {
        final Series series = Series.from("This is the series, 34");
        assertNotNull(series);
        assertEquals("This is the series", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString93() {
        final Series series = Series.from("This is the series, subtitle part 34");
        assertNotNull(series);
        assertEquals("This is the series, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString94() {
        final Series series = Series.from("This is the series, subtitle, part 34");
        assertNotNull(series);
        assertEquals("This is the series, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString95() {
        // Testing the variant
        final Series series = Series.from3("Favorietenreeks (II) nr. 24");
        assertNotNull(series);
        assertEquals("Favorietenreeks", series.getTitle());
        assertEquals("2.24", series.getNumber());
    }

    @Test
    void fromString96() {
        final Series series = Series.from("De avonturen van de 3L");
        assertNotNull(series);
        assertEquals("De avonturen van de 3L", series.getTitle());
        assertEquals("", series.getNumber());
    }

    /**
     * See {@link Series#from(String)} where we have a horrible hack in place to
     * make this series name work.
     */
    @Test
    void fromString100() {
        final Series series = Series.from("Blake's 7");
        assertNotNull(series);
        assertEquals("Blake's 7", series.getTitle());
        assertEquals("", series.getNumber());
        System.out.println("Blake's 7 has a hardcoded hack in Series#from(String)");
    }

    @Test
    void fromString101() {
        final Series series = Series.from("Stephen Baxter: Non-Fiction");
        assertNotNull(series);
        assertEquals("Stephen Baxter: Non-Fiction", series.getTitle());
        assertEquals("", series.getNumber());
    }


    /**
     * Use a roman numeral 'C' as the start of the last part.
     */
    @Test
    void fromString201() {
        final Series series = Series.from("Jerry Cornelius");
        assertNotNull(series);
        assertEquals("Jerry Cornelius", series.getTitle());
        assertEquals("", series.getNumber());
    }

    @Test
    void fromString202() {
        final Series series = Series.from("Jerry Cornelius 2");
        assertNotNull(series);
        assertEquals("Jerry Cornelius", series.getTitle());
        assertEquals("2", series.getNumber());
    }

    @Test
    void fromString203() {
        final Series series = Series.from("Jerry Cornelius xii");
        assertNotNull(series);
        assertEquals("Jerry Cornelius", series.getTitle());
        assertEquals("xii", series.getNumber());
    }

    /**
     * 2019-09-23: FAILS: can't deal with alphanumeric suffix.
     */
    @Test
    void fromString204() {
        final Series series = Series.from("Jerry Cornelius xii|bla");
        assertNotNull(series);
        assertEquals("Jerry Cornelius", series.getTitle());
        assertEquals("xii|bla", series.getNumber());
    }

    @Test
    void fromString205() {
        final Series series = Series.from(
                "Cornelius Chronicles, The (8|8 as includes The Alchemist's Question)");
        assertNotNull(series);
        assertEquals("Cornelius Chronicles, The", series.getTitle());
        assertEquals("8|8 as includes The Alchemist's Question", series.getNumber());
    }

    @Test
    void fromString206() {
        final Series series = Series.from(
                "Eternal Champion, The (984|Jerry Cornelius Calendar 4 as includes"
                + " The Alchemist's Question)");
        assertNotNull(series);
        assertEquals("Eternal Champion, The", series.getTitle());
        assertEquals("984|Jerry Cornelius Calendar 4 as includes The Alchemist's Question",
                     series.getNumber());
    }

    @Test
    void fromString1001() {
        final Series series = Series.from("This is (the series) title, subtitle ( iii|omnibus)");
        assertNotNull(series);
        assertEquals("This is (the series) title, subtitle", series.getTitle());
        assertEquals("iii|omnibus", series.getNumber());
    }

    @Test
    void fromString1002() {
        final Series series = Series.from("This is (the series) title, subtitle (34)");
        assertNotNull(series);
        assertEquals("This is (the series) title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void fromString1003() {
        final Series series = Series.from("This is #title, subtitle (4omnibus)");
        assertNotNull(series);
        assertEquals("This is #title, subtitle", series.getTitle());
        assertEquals("4omnibus", series.getNumber());
    }

    @Test
    void fromString1004() {
        final Series series = Series.from("This is #title, subtitle (omnibus)");
        assertNotNull(series);
        assertEquals("This is #title, subtitle", series.getTitle());
        assertEquals("omnibus", series.getNumber());
    }

    @Test
    void fromString1005() {
        final Series series = Series.from("This is #title, subtitle (omnibus)");
        assertNotNull(series);
        assertEquals("This is #title, subtitle", series.getTitle());
        assertEquals("omnibus", series.getNumber());
    }


    @Test
    void from2String00() {
        final Series series = Series.from("This is the series title", "");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("", series.getNumber());
    }

    @Test
    void from2String01() {
        final Series series = Series.from("This is the series title", "34");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }


    @Test
    void from2String03() {
        final Series series = Series.from("This is the series title", " 34");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void from2String11() {
        final Series series = Series.from("This is the series title", "iv");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("iv", series.getNumber());
    }


    @Test
    void from2String13() {
        final Series series = Series.from("This is the series title", " iv");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("iv", series.getNumber());
    }

    @Test
    void from2String41() {
        final Series series = Series.from("This is the series title, subtitle", "part 1");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("1", series.getNumber());
    }

    @Test
    void from2String42() {
        final Series series = Series.from("This is the series title, subtitle ", " deel  2");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("2", series.getNumber());
    }

    @Test
    void from2String43() {
        final Series series = Series.from("This is the series title, subtitle ", " vol. 3");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("3", series.getNumber());
    }


    @Test
    void from2String51() {
        final Series series = Series.from("This is the series title, subtitle", "part1");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("1", series.getNumber());
    }

    @Test
    void from2String61() {
        final Series series = Series.from("This is the series title, subtitle", "34|omnibus");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34|omnibus", series.getNumber());
    }

    @Test
    void from2String71() {
        final Series series = Series.from("This is the series title, subtitle", "iii|omnibus");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("iii|omnibus", series.getNumber());
    }

    @Test
    void from2String81() {
        final Series series = Series.from("This is the series title", "#34");
        assertNotNull(series);
        assertEquals("This is the series title", series.getTitle());
        assertEquals("34", series.getNumber());
    }

    @Test
    void from2String82() {
        final Series series = Series.from("This is the series title, subtitle", " # 34 ");
        assertNotNull(series);
        assertEquals("This is the series title, subtitle", series.getTitle());
        assertEquals("34", series.getNumber());
    }
}
