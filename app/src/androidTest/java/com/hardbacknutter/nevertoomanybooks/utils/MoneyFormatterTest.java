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

import android.content.Context;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("MissingJavadoc")
@RunWith(Parameterized.class)
public class MoneyFormatterTest {

    private static final double VALUE = 1234.50d;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {Locale.US, MoneyParser.USD, VALUE, "$1,234.50"},
                {Locale.US, MoneyParser.GBP, VALUE, "£1,234.50"},
                {Locale.US, MoneyParser.EUR, VALUE, "€1,234.50"},

                {Locale.UK, MoneyParser.USD, VALUE, "US$1,234.50"},
                {Locale.UK, MoneyParser.GBP, VALUE, "£1,234.50"},
                {Locale.UK, MoneyParser.EUR, VALUE, "€1,234.50"},

                {Locale.GERMANY, MoneyParser.USD, VALUE, "1.234,50 $"},
                {Locale.GERMANY, MoneyParser.GBP, VALUE, "1.234,50 £"},
                {Locale.GERMANY, MoneyParser.EUR, VALUE, "1.234,50 €"},
        });
    }

    @Parameterized.Parameter(0)
    public Locale fLocale;

    @Parameterized.Parameter(1)
    public String fCurrencyCode;

    @Parameterized.Parameter(2)
    public Double fInput;

    @Parameterized.Parameter(3)
    public String fExpected;

    @Test
    public void format() {
        final FieldFormatter<Money> f = new MoneyFormatter(fLocale);
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        final Money money = MoneyParser.parse(BigDecimal.valueOf(fInput), fCurrencyCode);
        assertNotNull(money);
        assertEquals(fExpected, f.format(context, money));
    }
}
