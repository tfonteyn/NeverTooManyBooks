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

class CurrencyUtilsTest
        extends Base {

    private static final BigDecimal tenDotFive = BigDecimal.valueOf(10.50d);

    /** Country with ',' as thousands, and '.' as decimal separator. */
    @Test
    void splitPrice10() {
        setLocale(Locale.UK);

        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        Money money;
        money = moneyParser.parse("$10.50");
        assertNotNull(money);
        assertEquals(MoneyParser.USD, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());

        money = moneyParser.parse("£10.50");
        assertNotNull(money);
        assertEquals(MoneyParser.GBP, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());

        money = moneyParser.parse("EUR 10.50");
        assertNotNull(money);
        assertEquals(MoneyParser.EUR, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice20() {
        setLocale(new Locale("nl", "BE"));

        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        Money money;
        money = moneyParser.parse("fr10,50");
        assertNotNull(money);
        assertEquals("BEF", money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());

        money = moneyParser.parse("$10.50");
        assertNotNull(money);
        assertEquals(MoneyParser.USD, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice21() {
        setLocale(new Locale("nl", "NL"));

        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("£10.50");
        assertNotNull(money);
        assertEquals(MoneyParser.GBP, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());
    }

    /** Country with '.' as thousands, and ',' as decimal separator. */
    @Test
    void splitPrice22() {
        setLocale(new Locale("nl", "NL"));

        final RealNumberParser realNumberParser = new RealNumberParser(context);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse("EUR 10.50");
        assertNotNull(money);
        assertEquals(MoneyParser.EUR, money.getCurrencyCode());
        assertEquals(tenDotFive, money.getValue());
    }
}
