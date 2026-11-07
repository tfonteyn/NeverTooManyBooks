/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BigDecimalFormatterTest
        extends BaseDBTest {

    @Test
    void german() {
        final Context context = createContext("de_DE");

        final List<Locale> locales = LocaleListUtils.asList(
                context.getResources().getConfiguration().getLocales());
        final RealNumberParser moneyParser = RealNumberParser.money(locales);
        final BigDecimalFormatter formatter = new BigDecimalFormatter(moneyParser);

        assertEquals("10", formatter.format(context, new BigDecimal("10")));
        assertEquals("10", formatter.format(context, new BigDecimal("10.0000")));
        assertEquals(new BigDecimal("10"), formatter.extract(context, "10"));

        assertEquals("10,2", formatter.format(context, new BigDecimal("10.2")));
        assertEquals(new BigDecimal("10.2"), formatter.extract(context, "10,2"));

        // trailing zeros are removed
        assertEquals("0,2", formatter.format(context, new BigDecimal("0.20")));
        // but not re-added (obviously)
        assertEquals(new BigDecimal("0.2"), formatter.extract(context, "0,2"));
        assertEquals(new BigDecimal("0.2"), formatter.extract(context, "0,200"));

        assertEquals("10,2", formatter.format(context, new BigDecimal("10.20")));
        assertEquals(new BigDecimal("10.2"), formatter.extract(context, "10,2"));

        assertEquals("5,205", formatter.format(context, new BigDecimal("5.205")));
        assertEquals(new BigDecimal("5.205"), formatter.extract(context, "5,205"));
    }

    @Test
    void uk() {
        final Context context = createContext("en_GB");

        final List<Locale> locales = LocaleListUtils.asList(
                context.getResources().getConfiguration().getLocales());
        final RealNumberParser moneyParser = RealNumberParser.money(locales);
        final BigDecimalFormatter formatter = new BigDecimalFormatter(moneyParser);

        assertEquals("10", formatter.format(context, new BigDecimal("10")));
        assertEquals("10", formatter.format(context, new BigDecimal("10.0000")));
        assertEquals(new BigDecimal("10"), formatter.extract(context, "10"));

        assertEquals("10.2", formatter.format(context, new BigDecimal("10.2")));
        assertEquals(new BigDecimal("10.2"), formatter.extract(context, "10.2"));

        assertEquals("0.2", formatter.format(context, new BigDecimal("0.20")));
        assertEquals(new BigDecimal("0.2"), formatter.extract(context, "0.2"));
        assertEquals(new BigDecimal("0.2"), formatter.extract(context, "0.200"));

        assertEquals("10.2", formatter.format(context, new BigDecimal("10.20")));
        assertEquals(new BigDecimal("10.2"), formatter.extract(context, "10.2"));

        assertEquals("5.205", formatter.format(context, new BigDecimal("5.205")));
        assertEquals(new BigDecimal("5.205"), formatter.extract(context, "5.205"));
    }

    @Test
    void enBE() {
        final Context context = createContext("en_BE");

        final List<Locale> locales = LocaleListUtils.asList(
                context.getResources().getConfiguration().getLocales());
        final RealNumberParser moneyParser = RealNumberParser.money(locales);
        final BigDecimalFormatter formatter = new BigDecimalFormatter(moneyParser);

        assertEquals("10", formatter.format(context, new BigDecimal("10")));
        assertEquals("10", formatter.format(context, new BigDecimal("10.0000")));
        assertEquals(new BigDecimal("10"), formatter.extract(context, "10"));

        assertEquals("10,2", formatter.format(context, new BigDecimal("10.2")));
        assertEquals(new BigDecimal("10.2"), formatter.extract(context, "10,2"));

        assertEquals("0,2", formatter.format(context, new BigDecimal("0.20")));
        assertEquals(new BigDecimal("0.2"), formatter.extract(context, "0,2"));
        assertEquals(new BigDecimal("0.2"), formatter.extract(context, "0,200"));

        assertEquals("10,2", formatter.format(context, new BigDecimal("10.20")));
        assertEquals(new BigDecimal("10.2"), formatter.extract(context, "10,2"));

        assertEquals("5,205", formatter.format(context, new BigDecimal("5.205")));
        assertEquals(new BigDecimal("5.205"), formatter.extract(context, "5,205"));
    }
}
