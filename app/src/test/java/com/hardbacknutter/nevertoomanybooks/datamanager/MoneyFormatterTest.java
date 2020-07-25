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
package com.hardbacknutter.nevertoomanybooks.datamanager;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.CommonMocks;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test the variations of currency location (before/after), currency symbol/code,
 * decimal separator and thousands separator.
 */
class MoneyFormatterTest
        extends CommonMocks {

    @Test
    void format00() {
        setLocale(Locale.US);
        //noinspection ConstantConditions
        FieldFormatter<Money> f = new MoneyFormatter(mLocale0);
        assertEquals("$1,234.50", f.format(mContext, new Money(1234.50d, "USD")));
    }

    @Test
    void format01() {
        setLocale(Locale.UK);
        //noinspection ConstantConditions
        FieldFormatter<Money> f = new MoneyFormatter(mLocale0);
        assertEquals("USD1,234.50", f.format(mContext, new Money(1234.50d, "USD")));
    }

    @Test
    void format02() {
        setLocale(Locale.GERMANY);
        //noinspection ConstantConditions
        FieldFormatter<Money> f = new MoneyFormatter(mLocale0);
        assertEquals("1.234,50 USD", f.format(mContext, new Money(1234.50d, "USD")));
    }

    @Test
    void format10() {
        setLocale(Locale.US);
        //noinspection ConstantConditions
        FieldFormatter<Money> f = new MoneyFormatter(mLocale0);
        assertEquals("GBP1,234.50", f.format(mContext, new Money(1234.50d, "GBP")));
    }

    @Test
    void format11() {
        setLocale(Locale.UK);
        //noinspection ConstantConditions
        FieldFormatter<Money> f = new MoneyFormatter(mLocale0);
        assertEquals("£1,234.50", f.format(mContext, new Money(1234.50d, "GBP")));
    }

    @Test
    void format12() {
        setLocale(Locale.GERMANY);
        //noinspection ConstantConditions
        FieldFormatter<Money> f = new MoneyFormatter(mLocale0);
        assertEquals("1.234,50 GBP", f.format(mContext, new Money(1234.50d, "GBP")));
    }

    @Test
    void format20() {
        setLocale(Locale.US);
        //noinspection ConstantConditions
        FieldFormatter<Money> f = new MoneyFormatter(mLocale0);
        assertEquals("EUR1,234.50", f.format(mContext, new Money(1234.50d, "EUR")));
    }

    @Test
    void format21() {
        setLocale(Locale.UK);
        //noinspection ConstantConditions
        FieldFormatter<Money> f = new MoneyFormatter(mLocale0);
        assertEquals("€1,234.50", f.format(mContext, new Money(1234.50d, "EUR")));
    }

    @Test
    void format22() {
        setLocale(Locale.GERMANY);
        //noinspection ConstantConditions
        FieldFormatter<Money> f = new MoneyFormatter(mLocale0);
        assertEquals("1.234,50 €", f.format(mContext, new Money(1234.50d, "EUR")));
    }
}
