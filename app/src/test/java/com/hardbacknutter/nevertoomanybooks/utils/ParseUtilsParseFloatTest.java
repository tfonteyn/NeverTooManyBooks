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

import java.text.DecimalFormat;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * This is not so much a test, but code written to decipher some anomalies seen in how
 * {@link DecimalFormat#parse(String)} works with Locales.
 * {@link #parseFloat20()} and {@link #parseFloat21()}
 * i.e. against expectations, France does not use a comma as a thousands separator.
 * <p>
 * <a href="https://en.wikipedia.org/wiki/Decimal_separator">wikipedia</a>
 */
class ParseUtilsParseFloatTest
        extends Base {

    /** US / computer standard. */
    private static final String DEC_SEP_IS_DOT = "1234.56";
    /** Comma. */
    private static final String DEC_SEP_IS_COMMA = "1234,56";

    /** Thousands separator the opposite of the decimal separator. */
    private static final String DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA = "1,234.56";
    private static final String DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT = "1.234,56";

    @Test
    void parseFloat10() {
        setLocale(Locale.ENGLISH);
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT));
        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA));

        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA));
        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT));
    }

    @Test
    void parseFloat11() {
        setLocale(Locale.US);
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT));
        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA));

        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT));
    }

    @Test
    void parseFloat12() {
        setLocale(Locale.UK);
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT));
        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA));

        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT));
    }

    @Test
    void parseFloat20() {
        setLocale(new Locale("fr"));
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT));

        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA));
    }

    @Test
    void parseFloat21() {
        setLocale(new Locale("fr", "FR"));
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT));

        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA));
    }

    @Test
    void parseFloat30() {
        setLocale(new Locale("de"));
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA));
        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT));

        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA));
    }

    @Test
    void parseFloat31() {
        setLocale(new Locale("de", "DE"));
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA));
        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT));

        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA));
    }

    @Test
    void parseFloat40() {
        setLocale(new Locale("nl"));
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA));
        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT));

        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA));
    }

    @Test
    void parseFloat41() {
        setLocale(new Locale("nl", "NL"));
        final RealNumberParser parser = new RealNumberParser(locales);

        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA));
        assertEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_COMMA_THOUSANDS_IS_DOT));

        assertNotEquals(1234.56f, parser.parseFloat(DEC_SEP_IS_DOT));
        assertNotEquals(1234.56f,
                        parser.parseFloat(DEC_SEP_IS_DOT_THOUSANDS_IS_COMMA));
    }
}
