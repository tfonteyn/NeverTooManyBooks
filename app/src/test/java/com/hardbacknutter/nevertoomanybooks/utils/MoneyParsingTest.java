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

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.Base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyParsingTest
        extends Base {

    private static final BigDecimal twelveDotThreeFour = BigDecimal.valueOf(12.34d);
    private static final List<Locale> UK = List.of(Locale.UK);
    private static final List<Locale> FRANCE = List.of(Locale.FRANCE);

    @Test
    void uk00() {
        final Money m = new Money(UK, "GBP&nbsp;12.34");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }

    @Test
    void uk01() {
        final Money m = new Money(UK, "£ 12.34");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }

    @Test
    void uk02() {
        final Money m = new Money(UK, "£12.34");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }

    @Test
    void uk03() {
        final Money m = new Money(UK, "GBP12.34");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }

    @Test
    void uk04() {
        final Money m = new Money(UK, "£12");
        assertEquals(BigDecimal.valueOf(12.0d), m.getValue());
        assertEquals(Money.GBP, m.getCurrencyCode());
    }


    @Test
    void fr01() {
        final Money m = new Money(FRANCE, "12,34&nbsp;€");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.EUR, m.getCurrencyCode());
    }

    @Test
    void fr02() {
        final Money m = new Money(FRANCE, "12,34 €");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.EUR, m.getCurrencyCode());
    }

    @Test
    void fr03() {
        final Money m = new Money(FRANCE, "12,34€");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.EUR, m.getCurrencyCode());
    }

    @Test
    void fr04() {
        final Money m = new Money(FRANCE, "12,34 eur");
        assertEquals(twelveDotThreeFour, m.getValue());
        assertEquals(Money.EUR, m.getCurrencyCode());
    }
}
