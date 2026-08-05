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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MoneyFormatterTest {

    private static final String VALUE = "1234.50";

    @NonNull
    static Stream<Arguments> readArgs() {
        return Stream.of(
                Arguments.of(Locale.US, MoneyParser.USD, VALUE, "$1,234.50"),
                Arguments.of(Locale.US, MoneyParser.GBP, VALUE, "£1,234.50"),
                Arguments.of(Locale.US, MoneyParser.EUR, VALUE, "€1,234.50"),

                Arguments.of(Locale.UK, MoneyParser.USD, VALUE, "US$1,234.50"),
                Arguments.of(Locale.UK, MoneyParser.GBP, VALUE, "£1,234.50"),
                Arguments.of(Locale.UK, MoneyParser.EUR, VALUE, "€1,234.50"),

                Arguments.of(Locale.GERMANY, MoneyParser.USD, VALUE, "1.234,50 $"),
                Arguments.of(Locale.GERMANY, MoneyParser.GBP, VALUE, "1.234,50 £"),
                Arguments.of(Locale.GERMANY, MoneyParser.EUR, VALUE, "1.234,50 €")
        );
    }

    @ParameterizedTest
    @MethodSource("readArgs")
    void format(@NonNull final Locale fLocale,
                @NonNull final String fCurrencyCode,
                @NonNull final String fInput,
                @NonNull final String fExpected) {
        final FieldFormatter<Money> f = new MoneyFormatter(fLocale);
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        final Money money = MoneyParser.parse(new BigDecimal(fInput), fCurrencyCode);
        assertNotNull(money);
        assertEquals(fExpected, f.format(context, money));
    }
}
