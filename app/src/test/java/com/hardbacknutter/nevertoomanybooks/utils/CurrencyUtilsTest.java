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

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CurrencyUtilsTest {

    /** Country with ',' as thousands, and '.' as decimal separator. */
    @Test
    void splitPrice10() {

        Locale locale = Locale.UK;
        Money money;

        money = new Money(locale, "$10.50");
        assertEquals("USD", money.getCurrency());
        assertEquals(10.50d, money.doubleValue());

        money = new Money(locale, "£10.50");
        assertEquals("GBP", money.getCurrency());
        assertEquals(10.50d, money.doubleValue());

        money = new Money(locale, "EUR 10.50");
        assertEquals("EUR", money.getCurrency());
        assertEquals(10.50d, money.doubleValue());
    }

    @Test
    void splitPrice100() {

        Locale locale = Locale.UK;
        Money money;

        money = new Money(locale, "10.50");
        assertNull(money.getCurrency());
        assertEquals(0.0d, money.doubleValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice20() {
        Locale locale = new Locale("nl", "BE");
        Money money;

        money = new Money(locale, "fr10,50");
        assertEquals("BEF", money.getCurrency());
        assertEquals(10.50d, money.doubleValue());

        money = new Money(locale, "$10.50");
        assertEquals("USD", money.getCurrency());
        assertEquals(10.50d, money.doubleValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice21() {
        Locale locale = new Locale("nl", "NL");
        Money money;

        money = new Money(locale, "£10.50");
        assertEquals("GBP", money.getCurrency());
        assertEquals(10.50d, money.doubleValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice22() {
        Locale locale = new Locale("nl", "NL");
        Money money;

        money = new Money(locale, "EUR 10.50");
        assertEquals("EUR", money.getCurrency());
        assertEquals(10.50d, money.doubleValue());
    }
}
