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

import java.text.NumberFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * This is not so much a test, but code written to decipher some anomalies seen in how
 * {@link NumberFormat#parse(String)} works with Locales.
 * {@link #parseFloat20()} and {@link #parseFloat21()}
 * have anomalies with {@link #TH_SEP_DEC_SEP_IS_COMMA}.
 *
 * https://en.wikipedia.org/wiki/Decimal_separator
 */
class ParseUtilsTest {

    /** US / computer standard. */
    private static final String DEC_SEP_IS_DOT = "1234.56";
    /** Comma. */
    private static final String DEC_SEP_IS_COMMA = "1234,56";

    /** Thousands separator the opposite of the decimal separator. */
    private static final String TH_SEP_DEC_SEP_IS_DOT = "1,234.56";
    private static final String TH_SEP_DEC_SEP_IS_COMMA = "1.234,56";

    @Test
    void parseFloat00() {
        assertEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_DOT));
        assertEquals(1234.56f, ParseUtils.parseFloat(TH_SEP_DEC_SEP_IS_DOT));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(DEC_SEP_IS_COMMA));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(TH_SEP_DEC_SEP_IS_COMMA));
    }

    @Test
    void parseFloat10() {
        Locale LOCALE = Locale.ENGLISH;
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_DOT));
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_DOT));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_COMMA));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_COMMA));
    }

    @Test
    void parseFloat11() {
        Locale LOCALE = Locale.US;
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_DOT));
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_DOT));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_COMMA));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_COMMA));
    }

    @Test
    void parseFloat12() {
        Locale LOCALE = Locale.UK;
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_DOT));
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_DOT));

        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_COMMA));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_COMMA));
    }

    @Test
    void parseFloat20() {
        Locale LOCALE = Locale.FRENCH;
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_DOT));

        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_COMMA));
        // ??
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_COMMA));

    }

    @Test
    void parseFloat21() {
        Locale LOCALE = Locale.FRANCE;
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_DOT));

        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_COMMA));
        // ??
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_COMMA));
    }

    @Test
    void parseFloat30() {
        Locale LOCALE = Locale.GERMAN;
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_DOT));

        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_COMMA));
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_COMMA));
    }

    @Test
    void parseFloat31() {
        Locale LOCALE = Locale.GERMANY;
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_DOT));

        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_COMMA));
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_COMMA));
    }

    @Test
    void parseFloat40() {
        Locale LOCALE = Locale.ITALIAN;
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_DOT));

        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_COMMA));
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_COMMA));
    }

    @Test
    void parseFloat41() {
        Locale LOCALE = Locale.ITALY;
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_DOT));

        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, DEC_SEP_IS_COMMA));
        assertEquals(1234.56f, ParseUtils.parseFloat(LOCALE, TH_SEP_DEC_SEP_IS_COMMA));
    }
}