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
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MoneyParsingTest
        extends Base {

    private static final BigDecimal twelveDotThreeFour = BigDecimal.valueOf(12.34d);

    @Test
    void uk00() {
        setLocale(Locale.UK);
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("GBP&nbsp;12.34");
        assertNotNull(money);
        assertEquals(twelveDotThreeFour, money.getValue());
        assertEquals(MoneyParser.GBP, money.getCurrencyCode());
    }

    @Test
    void uk01() {
        setLocale(Locale.UK);
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("£ 12.34");
        assertNotNull(money);
        assertEquals(twelveDotThreeFour, money.getValue());
        assertEquals(MoneyParser.GBP, money.getCurrencyCode());
    }

    @Test
    void uk02() {
        setLocale(Locale.UK);
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("£12.34");
        assertNotNull(money);
        assertEquals(twelveDotThreeFour, money.getValue());
        assertEquals(MoneyParser.GBP, money.getCurrencyCode());
    }

    @Test
    void uk03() {
        setLocale(Locale.UK);
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("GBP12.34");
        assertNotNull(money);
        assertEquals(twelveDotThreeFour, money.getValue());
        assertEquals(MoneyParser.GBP, money.getCurrencyCode());
    }

    @Test
    void uk04() {
        setLocale(Locale.UK);
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("£12");
        assertNotNull(money);
        assertEquals(BigDecimal.valueOf(12.0d), money.getValue());
        assertEquals(MoneyParser.GBP, money.getCurrencyCode());
    }


    @Test
    void fr01() {
        setLocale(Locale.FRANCE);
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("12,34&nbsp;€");
        assertNotNull(money);
        assertEquals(twelveDotThreeFour, money.getValue());
        assertEquals(MoneyParser.EUR, money.getCurrencyCode());
    }

    @Test
    void fr02() {
        setLocale(Locale.FRANCE);
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("12,34 €");
        assertNotNull(money);
        assertEquals(twelveDotThreeFour, money.getValue());
        assertEquals(MoneyParser.EUR, money.getCurrencyCode());
    }

    @Test
    void fr03() {
        setLocale(Locale.FRANCE);
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("12,34€");
        assertNotNull(money);
        assertEquals(twelveDotThreeFour, money.getValue());
        assertEquals(MoneyParser.EUR, money.getCurrencyCode());
    }

    @Test
    void fr04() {
        setLocale(Locale.FRANCE);
        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("12,34 eur");
        assertNotNull(money);
        assertEquals(twelveDotThreeFour, money.getValue());
        assertEquals(MoneyParser.EUR, money.getCurrencyCode());
    }
}
