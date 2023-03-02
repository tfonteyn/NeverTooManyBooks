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
import java.util.Currency;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test the variations of currency location (before/after), currency symbol/code,
 * decimal separator and thousands separator.
 */
class JDKMoneyFormatterTest
        extends Base {

    @Test
    void formatUS() {
        setLocale(Locale.US);
        final FieldFormatter<Money> f = new MoneyFormatter(locales.get(0));
        Money money;
        money = new Money(BigDecimal.valueOf(1234.50d), Currency.getInstance(MoneyParser.USD));
        assertNotNull(money);
        assertEquals("$1,234.50", f.format(context, money));
        money = new Money(BigDecimal.valueOf(1234.50d), Currency.getInstance(MoneyParser.GBP));
        assertNotNull(money);
        assertEquals("£1,234.50", f.format(context, money));
        money = new Money(BigDecimal.valueOf(1234.50d), Currency.getInstance(MoneyParser.EUR));
        assertNotNull(money);
        assertEquals("€1,234.50", f.format(context, money));
    }

    @Test
    void formatUK() {
        setLocale(Locale.UK);
        final FieldFormatter<Money> f = new MoneyFormatter(locales.get(0));
        Money money;
        money = new Money(BigDecimal.valueOf(1234.50d), Currency.getInstance(MoneyParser.USD));
        assertNotNull(money);
        assertEquals("US$1,234.50", f.format(context, money));
        money = new Money(BigDecimal.valueOf(1234.50d), Currency.getInstance(MoneyParser.GBP));
        assertNotNull(money);
        assertEquals("£1,234.50", f.format(context, money));
        money = new Money(BigDecimal.valueOf(1234.50d), Currency.getInstance(MoneyParser.EUR));
        assertNotNull(money);
        assertEquals("€1,234.50", f.format(context, money));
    }

    @Test
    void formatGERMANY() {
        setLocale(Locale.GERMANY);
        final FieldFormatter<Money> f = new MoneyFormatter(locales.get(0));
        Money money;
        money = new Money(BigDecimal.valueOf(1234.50d), Currency.getInstance(MoneyParser.USD));
        assertNotNull(money);
        assertEquals("1.234,50 $", f.format(context, money));
        money = new Money(BigDecimal.valueOf(1234.50d), Currency.getInstance(MoneyParser.GBP));
        assertNotNull(money);
        assertEquals("1.234,50 £", f.format(context, money));
        money = new Money(BigDecimal.valueOf(1234.50d), Currency.getInstance(MoneyParser.EUR));
        assertNotNull(money);
        assertEquals("1.234,50 €", f.format(context, money));
    }
}
