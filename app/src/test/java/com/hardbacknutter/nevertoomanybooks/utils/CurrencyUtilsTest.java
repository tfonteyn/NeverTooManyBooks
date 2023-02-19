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
package com.hardbacknutter.nevertoomanybooks.utils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.Base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class CurrencyUtilsTest
        extends Base {

    private static final BigDecimal tenDotFive = BigDecimal.valueOf(10.50d);

    /** Country with ',' as thousands, and '.' as decimal separator. */
    @Test
    void splitPrice10() {

        final List<Locale> locale = List.of(Locale.UK);
        Money money;

        money = new Money(locale, "$10.50");
        assertEquals(Money.USD, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());

        money = new Money(locale, "£10.50");
        assertEquals(Money.GBP, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());

        money = new Money(locale, "EUR 10.50");
        assertEquals(Money.EUR, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());
    }

    @Test
    void parsingMustFail() {
        final List<Locale> locale = List.of(Locale.UK);
        final Money money;

        // The Money constructor EXPECTS a price WITH currency;
        money = new Money(locale, "10.50");
        assertFalse(money.isValid());
        assertNull(money.getCurrency());
        assertNull(money.getValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice20() {
        final List<Locale> locale = List.of(new Locale("nl", "BE"));
        Money money;

        money = new Money(locale, "fr10,50");
        assertEquals("BEF", money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());

        money = new Money(locale, "$10.50");
        assertEquals(Money.USD, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice21() {
        final List<Locale> locale = List.of(new Locale("nl", "NL"));
        final Money money;

        money = new Money(locale, "£10.50");
        assertEquals(Money.GBP, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice22() {
        final List<Locale> locale = List.of(new Locale("nl", "NL"));
        final Money money;

        money = new Money(locale, "EUR 10.50");
        assertEquals(Money.EUR, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());
    }
}
