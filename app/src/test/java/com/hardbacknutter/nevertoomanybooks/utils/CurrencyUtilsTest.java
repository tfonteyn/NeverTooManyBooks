/*
 * @Copyright 2018-2021 HardBackNutter
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

import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.Base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CurrencyUtilsTest
        extends Base {

    /** Country with ',' as thousands, and '.' as decimal separator. */
    @Test
    void splitPrice10() {

        final Locale locale = Locale.UK;
        Money money;

        money = new Money(locale, "$10.50");
        assertEquals(Money.USD, money.getCurrency());
        assertEquals(10.50d, money.doubleValue());

        money = new Money(locale, "£10.50");
        assertEquals(Money.GBP, money.getCurrency());
        assertEquals(10.50d, money.doubleValue());

        money = new Money(locale, "EUR 10.50");
        assertEquals(Money.EUR, money.getCurrency());
        assertEquals(10.50d, money.doubleValue());
    }

    @Test
    void splitPrice100() {

        final Locale locale = Locale.UK;
        final Money money;

        money = new Money(locale, "10.50");
        assertNull(money.getCurrency());
        assertEquals(0.0d, money.doubleValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice20() {
        final Locale locale = new Locale("nl", "BE");
        Money money;

        money = new Money(locale, "fr10,50");
        assertEquals("BEF", money.getCurrency());
        assertEquals(10.50d, money.doubleValue());

        money = new Money(locale, "$10.50");
        assertEquals(Money.USD, money.getCurrency());
        assertEquals(10.50d, money.doubleValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice21() {
        final Locale locale = new Locale("nl", "NL");
        final Money money;

        money = new Money(locale, "£10.50");
        assertEquals(Money.GBP, money.getCurrency());
        assertEquals(10.50d, money.doubleValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice22() {
        final Locale locale = new Locale("nl", "NL");
        final Money money;

        money = new Money(locale, "EUR 10.50");
        assertEquals(Money.EUR, money.getCurrency());
        assertEquals(10.50d, money.doubleValue());
    }
}
