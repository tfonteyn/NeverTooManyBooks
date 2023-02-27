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
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <a href="https://en.wikipedia.org/wiki/Decimal_separator">wikipedia</a>
 */
class RealNumberParserTest
        extends Base {

    private static final String DEC_DOT_NO_GROUPING = "1234.56";
    private static final String DEC_COMMA_NO_GROUPING = "1234,56";
    private static final String DEC_DOT_GROUPING_COMMA = "1,234.56";
    private static final String DEC_COMMA_GROUPING_DOT = "1.234,56";


    private static final float FLOAT = 1234.56f;
    private static final double DOUBLE = 1234.56d;

    @NonNull
    static Stream<Arguments> decDotGrpComma() {
        return Stream.of(
                Arguments.of((Object) new Locale[]{Locale.ENGLISH}),
                Arguments.of((Object) new Locale[]{Locale.US}),
                Arguments.of((Object) new Locale[]{Locale.UK})
        );
    }

    @NonNull
    static Stream<Arguments> decCommaNoGrp() {
        return Stream.of(
                // Grouping separator is ' ' (0x202F) -> Narrow No-Break Space
                Arguments.of((Object) new Locale[]{new Locale("fr")}),
                Arguments.of((Object) new Locale[]{new Locale("fr", "FR")})
        );
    }

    @NonNull
    static Stream<Arguments> decCommaGrpDot() {
        return Stream.of(
                Arguments.of((Object) new Locale[]{new Locale("de")}),
                Arguments.of((Object) new Locale[]{new Locale("de", "DE")}),
                Arguments.of((Object) new Locale[]{new Locale("nl")}),
                Arguments.of((Object) new Locale[]{new Locale("nl", "NL")})
        );
    }

    @NonNull
    static Stream<Arguments> decDotGrpComma_decDotGrpComma() {
        return Stream.of(
                Arguments.of((Object) new Locale[]{new Locale("de"), Locale.US}),
                Arguments.of((Object) new Locale[]{new Locale("de", "DE"), Locale.US}),
                Arguments.of((Object) new Locale[]{new Locale("nl"), Locale.US}),
                Arguments.of((Object) new Locale[]{new Locale("nl", "NL"), Locale.US})
        );
    }

    @ParameterizedTest
    @MethodSource("decDotGrpComma")
    void parseFloat10(@NonNull final Locale[] testLocales) {
        setLocale(testLocales);
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(FLOAT, parser.parseFloat(DEC_DOT_NO_GROUPING));
        assertEquals(FLOAT, parser.parseFloat(DEC_DOT_GROUPING_COMMA));

        assertThrows(NumberFormatException.class, () -> parser.parseFloat(DEC_COMMA_NO_GROUPING));
        assertThrows(NumberFormatException.class, () -> parser.parseFloat(DEC_COMMA_GROUPING_DOT));

        assertEquals(DOUBLE, parser.parseDouble(DEC_DOT_NO_GROUPING));
        assertEquals(DOUBLE, parser.parseDouble(DEC_DOT_GROUPING_COMMA));

        assertThrows(NumberFormatException.class, () -> parser.parseDouble(DEC_COMMA_NO_GROUPING));
        assertThrows(NumberFormatException.class, () -> parser.parseDouble(DEC_COMMA_GROUPING_DOT));
    }


    @ParameterizedTest
    @MethodSource("decCommaNoGrp")
    void parseFloat20(@NonNull final Locale[] testLocales) {
        setLocale(testLocales);
        final RealNumberParser parser = new RealNumberParser(locales);

        assertThrows(NumberFormatException.class, () -> parser.parseFloat(DEC_DOT_NO_GROUPING));
        assertThrows(NumberFormatException.class, () -> parser.parseFloat(DEC_DOT_GROUPING_COMMA));

        assertEquals(FLOAT, parser.parseFloat(DEC_COMMA_NO_GROUPING));
        assertEquals(FLOAT, parser.parseFloat(DEC_COMMA_GROUPING_DOT));

        assertThrows(NumberFormatException.class, () -> parser.parseDouble(DEC_DOT_NO_GROUPING));
        assertThrows(NumberFormatException.class, () -> parser.parseDouble(DEC_DOT_GROUPING_COMMA));

        assertEquals(DOUBLE, parser.parseDouble(DEC_COMMA_NO_GROUPING));
        assertEquals(DOUBLE, parser.parseDouble(DEC_COMMA_GROUPING_DOT));
    }


    @ParameterizedTest
    @MethodSource("decCommaGrpDot")
    void parseFloat30(@NonNull final Locale[] testLocales) {
        setLocale(testLocales);
        final RealNumberParser parser = new RealNumberParser(locales);

        assertThrows(NumberFormatException.class, () -> parser.parseFloat(DEC_DOT_NO_GROUPING));
        assertThrows(NumberFormatException.class, () -> parser.parseFloat(DEC_DOT_GROUPING_COMMA));

        assertEquals(FLOAT, parser.parseFloat(DEC_COMMA_NO_GROUPING));
        assertEquals(FLOAT, parser.parseFloat(DEC_COMMA_GROUPING_DOT));

        assertThrows(NumberFormatException.class, () -> parser.parseDouble(DEC_DOT_NO_GROUPING));
        assertThrows(NumberFormatException.class, () -> parser.parseDouble(DEC_DOT_GROUPING_COMMA));

        assertEquals(DOUBLE, parser.parseDouble(DEC_COMMA_NO_GROUPING));
        assertEquals(DOUBLE, parser.parseDouble(DEC_COMMA_GROUPING_DOT));
    }


    @ParameterizedTest
    @MethodSource("decDotGrpComma_decDotGrpComma")
    void parseFloat30_10(@NonNull final Locale[] testLocales) {
        setLocale(testLocales);
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(FLOAT, parser.parseFloat(DEC_DOT_NO_GROUPING));
        assertEquals(FLOAT, parser.parseFloat(DEC_DOT_GROUPING_COMMA));

        assertEquals(FLOAT, parser.parseFloat(DEC_COMMA_NO_GROUPING));
        assertEquals(FLOAT, parser.parseFloat(DEC_COMMA_GROUPING_DOT));

        assertEquals(DOUBLE, parser.parseDouble(DEC_DOT_NO_GROUPING));
        assertEquals(DOUBLE, parser.parseDouble(DEC_DOT_GROUPING_COMMA));

        assertEquals(DOUBLE, parser.parseDouble(DEC_COMMA_NO_GROUPING));
        assertEquals(DOUBLE, parser.parseDouble(DEC_COMMA_GROUPING_DOT));
    }
}
