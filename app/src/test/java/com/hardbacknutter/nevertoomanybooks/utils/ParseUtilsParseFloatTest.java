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

import java.text.DecimalFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * This is not so much a test, but code written to decipher some anomalies seen in how
 * {@link DecimalFormat#parse(String)} works with Locales.
 * {@link #parseFloat20()} and {@link #parseFloat21()}
 * i.e. against expectations, France does not use a comma as a thousands separator.
 * <p>
 * https://en.wikipedia.org/wiki/Decimal_separator
 */
class ParseUtilsParseFloatTest {

    /** US / computer standard. */
    private static final String DEC_SEP_IS_DOT = "1234.56";
    /** Comma. */
    private static final String DEC_SEP_IS_COMMA = "1234,56";

    /** Thousands separator the opposite of the decimal separator. */
    private static final String DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA = "1,234.56";
    private static final String DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT = "1.234,56";

    @Test
    void parseFloat10() {
        Locale LOCALE = Locale.ENGLISH;
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT, LOCALE));
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA, LOCALE));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT, LOCALE));
    }

    @Test
    void parseFloat11() {
        Locale LOCALE = Locale.US;
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT, LOCALE));
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA, LOCALE));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT, LOCALE));
    }

    @Test
    void parseFloat12() {
        Locale LOCALE = Locale.UK;
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT, LOCALE));
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA, LOCALE));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT, LOCALE));
    }

    @Test
    void parseFloat20() {
        Locale LOCALE = new Locale("fr");
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT, LOCALE));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA, LOCALE));
    }

    @Test
    void parseFloat21() {
        Locale LOCALE = new Locale("fr", "FR");
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT, LOCALE));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA, LOCALE));
    }

    @Test
    void parseFloat30() {
        Locale LOCALE = new Locale("de");
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA, LOCALE));
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT, LOCALE));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA, LOCALE));
    }

    @Test
    void parseFloat31() {
        Locale LOCALE = new Locale("de", "DE");
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA, LOCALE));
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT, LOCALE));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA, LOCALE));
    }

    @Test
    void parseFloat40() {
        Locale LOCALE = new Locale("nl");
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA, LOCALE));
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT, LOCALE));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA, LOCALE));
    }

    @Test
    void parseFloat41() {
        Locale LOCALE = new Locale("nl", "NL");
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA, LOCALE));
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT, LOCALE));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT, LOCALE));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA, LOCALE));
    }
}
