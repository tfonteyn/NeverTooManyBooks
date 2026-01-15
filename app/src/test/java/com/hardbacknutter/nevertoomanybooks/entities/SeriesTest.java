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
package com.hardbacknutter.nevertoomanybooks.entities;

import androidx.annotation.NonNull;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test the regular expressions used by {@link DataHolderUtils#requireSeries}.
 */
class SeriesTest {

    /**
     * The input is a single string.
     */
    @NonNull
    static Stream<Arguments> read1Args() {
        return Stream.of(
                Arguments.of("This is the series title", "This is the series title", ""),
                Arguments.of("Series Title", "Series Title", ""),

                // roman numeral embedded: "i", "l"
                Arguments.of("bill", "bill", ""),
                Arguments.of("bill the illegal", "bill the illegal", ""),
                Arguments.of("illegal", "illegal", ""),
                Arguments.of("illegal 5", "illegal", "5"),

                Arguments.of("This is the series title #34", "This is the series title", "34"),

                Arguments.of("This is the series title, subtitle # 34",
                             "This is the series title, subtitle", "34"),
                Arguments.of("This is the series title, subtitle #34  ",
                             "This is the series title, subtitle", "34"),

                Arguments.of("This is the series title, #34  ", "This is the series title", "34"),
                Arguments.of("This is the series title,#34  ", "This is the series title", "34"),
                Arguments.of("This is the series title#34  ", "This is the series title", "34"),
                Arguments.of("This is the series 34  ", "This is the series", "34"),

                Arguments.of("This is the series, 34", "This is the series", "34"),
                Arguments.of("This is the series, subtitle part 34",
                             "This is the series, subtitle", "34"),
                Arguments.of("This is the series, subtitle, part 34",
                             "This is the series, subtitle", "34"),

                Arguments.of("The adventures of the 3L", "The adventures of the 3L", ""),
                Arguments.of("Stephen Baxter: Non-Fiction", "Stephen Baxter: Non-Fiction", ""),

                Arguments.of("Euro-5", "Euro-5", ""),
                Arguments.of("Euro-5 6", "Euro-5", "6"),
                Arguments.of("Euro-5-7 6", "Euro-5-7", "6"),
                Arguments.of("Euro-5-7-test 6", "Euro-5-7-test", "6"),

                Arguments.of("Blake's 7 13", "Blake's 7", "13"),

                // Use a roman numeral 'C' as the start of the last part.
                Arguments.of("Jerry Cornelius", "Jerry Cornelius", ""),

                Arguments.of("Jerry Cornelius 2", "Jerry Cornelius", "2"),
                Arguments.of("Jerry Cornelius xii", "Jerry Cornelius", "xii"),

                Arguments.of("Jerry Cornelius MCVIII", "Jerry Cornelius", "MCVIII"),

                Arguments.of("Series-Title", "Series-Title", ""),
                Arguments.of("Series-Title 777", "Series-Title", "777"),

                Arguments.of("Donjon morning -100", "Donjon morning", "-100")
                , Arguments.of("Donjon evening +100", "Donjon evening", "+100")
        );
    }

    /**
     * The input is one string: "title (nr)".
     */
    @NonNull
    static Stream<Arguments> read1ArgsWithBrackets() {
        return Stream.of(

                Arguments.of("This is the series title(34)", "This is the series title", "34"),
                Arguments.of("This is the series title (34)", "This is the series title", "34"),
                Arguments.of("This is the series title ( 34)", "This is the series title", "34"),

                Arguments.of("This is the series title(iv)", "This is the series title", "iv"),
                Arguments.of("This is the series title (iv)", "This is the series title", "iv"),
                Arguments.of("This is the series title ( iv)", "This is the series title", "iv"),

                Arguments.of("This is the series title, subtitle(34)",
                             "This is the series title, subtitle", "34"),
                Arguments.of("This is the series title, subtitle (34)",
                             "This is the series title, subtitle", "34"),
                Arguments.of("This is the series title, subtitle ( 34)",
                             "This is the series title, subtitle", "34"),

                Arguments.of("This is the series title, subtitle(vii)",
                             "This is the series title, subtitle", "vii"),
                Arguments.of("This is the series title, subtitle (vii)",
                             "This is the series title, subtitle", "vii"),
                Arguments.of("This is the series title, subtitle ( vii)",
                             "This is the series title, subtitle", "vii"),

                // with prefixes to-be-stripped
                Arguments.of("This is the series title, subtitle(part 1)",
                             "This is the series title, subtitle", "1"),
                Arguments.of("This is the series title, subtitle (deel 2)",
                             "This is the series title, subtitle", "2"),
                Arguments.of("This is the series title, subtitle ( vol. 3)",
                             "This is the series title, subtitle", "3"),

                Arguments.of("This is the series title, subtitle(part1)",
                             "This is the series title, subtitle", "1"),
                Arguments.of("This is the series title, subtitle (deel2)",
                             "This is the series title, subtitle", "2"),
                Arguments.of("This is the series title, subtitle ( vol3)",
                             "This is the series title, subtitle", "3"),

                Arguments.of("This is the series title, subtitle(34|omnibus)",
                             "This is the series title, subtitle", "34|omnibus"),
                Arguments.of("This is the series title, subtitle (34|omnibus)",
                             "This is the series title, subtitle", "34|omnibus"),
                Arguments.of("This is the series title, subtitle 34|omnibus",
                             "This is the series title, subtitle", "34|omnibus"),
                Arguments.of("This is the series title, subtitle ( 34|omnibus)",
                             "This is the series title, subtitle", "34|omnibus"),

                Arguments.of("This is the series title, subtitle(iii|omnibus)",
                             "This is the series title, subtitle", "iii|omnibus"),
                Arguments.of("This is the series title, subtitle (iii|omnibus)",
                             "This is the series title, subtitle", "iii|omnibus"),
                Arguments.of("This is the series title, subtitle ( iii|omnibus)",
                             "This is the series title, subtitle", "iii|omnibus"),

                Arguments.of("Cornelius Chronicles, The (8|8 as includes The Alchemist's Question)",
                             "Cornelius Chronicles, The",
                             "8|8 as includes The Alchemist's Question"),
                Arguments.of("Eternal Champion, The (984|Jerry Cornelius Calendar 4 as includes"
                             + " The Alchemist's Question)",
                             "Eternal Champion, The",
                             "984|Jerry Cornelius Calendar 4 as includes The Alchemist's Question"),
                Arguments.of("This is (the series) title, subtitle ( iii|omnibus)",
                             "This is (the series) title, subtitle",
                             "iii|omnibus"),
                Arguments.of("This is (the series) title, subtitle (34)",
                             "This is (the series) title, subtitle", "34"),
                Arguments.of("This is #title, subtitle (4omnibus)",
                             "This is #title, subtitle", "4omnibus"),
                Arguments.of("This is #title, subtitle (omnibus)",
                             "This is #title, subtitle", "omnibus"),
                Arguments.of("This is #title, subtitle (omnibus)",
                             "This is #title, subtitle", "omnibus")
        );
    }

    /**
     * The input is two strings: title + nr.
     */
    @NonNull
    static Stream<Arguments> read2Args() {
        return Stream.of(
                Arguments.of("This is the series title", "",
                             "This is the series title", ""),
                Arguments.of("This is the series title", "34",
                             "This is the series title", "34"),

                Arguments.of("This is the series title", "t. 34",
                             "This is the series title", "34"),

                Arguments.of("This is the series title", "iv",
                             "This is the series title", "iv"),

                Arguments.of("This is the series title, subtitle", "part 1",
                             "This is the series title, subtitle", "1"),
                Arguments.of("This is the series title, subtitle ", " deel  2",
                             "This is the series title, subtitle", "2"),
                Arguments.of("This is the series title, subtitle ", " vol. 3",
                             "This is the series title, subtitle", "3"),

                Arguments.of("This is the series title, subtitle", "part1",
                             "This is the series title, subtitle", "1"),
                Arguments.of("This is the series title, subtitle", "34|omnibus",
                             "This is the series title, subtitle", "34|omnibus"),
                Arguments.of("This is the series title, subtitle", "iii|omnibus",
                             "This is the series title, subtitle", "iii|omnibus"),
                Arguments.of("This is the series title", "#34",
                             "This is the series title", "34"),
                Arguments.of("This is the series title, subtitle", " # 34 ",
                             "This is the series title, subtitle", "34")
        );
    }

    /**
     * Using {@link Series#from3(String)}.
     */
    @NonNull
    static Stream<Arguments> usingSeriesFrom3() {
        return Stream.of(
                Arguments.of("Favorietenreeks (II) nr. 24", "Favorietenreeks", "2.24"));
    }

    @ParameterizedTest
    @MethodSource("read1Args")
    void from1String(@NonNull final String input,
                     @NonNull final String title,
                     @NonNull final String nr) {
        final Series series = Series.from(input);
        assertNotNull(series);
        assertEquals(title, series.getTitle());
        assertEquals(nr, series.getNumber());
    }

    @ParameterizedTest
    @MethodSource("read1ArgsWithBrackets")
    void from1StringWithBrackets(@NonNull final String input,
                                 @NonNull final String title,
                                 @NonNull final String nr) {
        final Series series = Series.from(input);
        assertNotNull(series);
        assertEquals(title, series.getTitle());
        assertEquals(nr, series.getNumber());
    }

    @ParameterizedTest
    @MethodSource("read2Args")
    void from2Strings(@NonNull final String input,
                      @NonNull final String inputNr,
                      @NonNull final String title,
                      @NonNull final String nr) {
        final Series series = Series.from(input, inputNr);
        assertNotNull(series);
        assertEquals(title, series.getTitle());
        assertEquals(nr, series.getNumber());
    }

    @ParameterizedTest
    @MethodSource("usingSeriesFrom3")
    void seriesFrom3(@NonNull final String input,
                     @NonNull final String title,
                     @NonNull final String nr) {
        final Series series = Series.from3(input);
        assertNotNull(series);
        assertEquals(title, series.getTitle());
        assertEquals(nr, series.getNumber());
    }

    /**
     * FIXME: Some day we're going to make these titles work...
     */
    @Test
    void expectedToFail() {
        // 2019-09-23: FAILS: can't deal with alphanumeric suffix.
        final Series series = Series.from("Jerry Cornelius xii|bla");
        assertNotNull(series);
        assertEquals("Jerry Cornelius", series.getTitle());
        assertEquals("xii|bla", series.getNumber());
    }

    /**
     * FIXME: Some day we're going to make these titles work without the hack
     * See {@link Series#from(String)} where we have a horrible hack in place to
     * make this series name work.
     */
    @Test
    void horribleHack() {
        final Series series = Series.from("Blake's 7");
        assertNotNull(series);
        assertEquals("Blake's 7", series.getTitle());
        assertEquals("", series.getNumber());
    }
}
