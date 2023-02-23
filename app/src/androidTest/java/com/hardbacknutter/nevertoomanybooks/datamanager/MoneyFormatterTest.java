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
package com.hardbacknutter.nevertoomanybooks.datamanager;

import android.content.Context;

import java.math.BigDecimal;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MoneyFormatterTest {

    @Test
    public void formatUS() {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        final FieldFormatter<Money> f = new MoneyFormatter(Locale.US);
        assertEquals("$1,234.50", f.format(context, new Money(BigDecimal.valueOf(1234.50d),
                                                              Money.USD)));
        assertEquals("£1,234.50", f.format(context, new Money(BigDecimal.valueOf(1234.50d),
                                                              Money.GBP)));
        assertEquals("€1,234.50", f.format(context, new Money(BigDecimal.valueOf(1234.50d),
                                                              Money.EUR)));
    }

    @Test
    public void formatUK() {
        final FieldFormatter<Money> f = new MoneyFormatter(Locale.UK);
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        assertEquals("US$1,234.50", f.format(context, new Money(BigDecimal.valueOf(1234.50d),
                                                                Money.USD)));
        assertEquals("£1,234.50", f.format(context, new Money(BigDecimal.valueOf(1234.50d),
                                                              Money.GBP)));
        assertEquals("€1,234.50", f.format(context, new Money(BigDecimal.valueOf(1234.50d),
                                                              Money.EUR)));
    }

    @Test
    public void formatGERMANY() {
        final FieldFormatter<Money> f = new MoneyFormatter(Locale.GERMANY);
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        assertEquals("1.234,50 $", f.format(context, new Money(BigDecimal.valueOf(1234.50d),
                                                               Money.USD)));
        assertEquals("1.234,50 £", f.format(context, new Money(BigDecimal.valueOf(1234.50d),
                                                               Money.GBP)));
        assertEquals("1.234,50 €", f.format(context, new Money(BigDecimal.valueOf(1234.50d),
                                                               Money.EUR)));
    }
}
