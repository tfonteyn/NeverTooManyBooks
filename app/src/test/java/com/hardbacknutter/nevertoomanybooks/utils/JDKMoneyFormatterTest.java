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

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.MoneyFormatter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test the variations of currency location (before/after), currency symbol/code,
 * decimal separator and thousands separator.
 */
class JDKMoneyFormatterTest
        extends Base {

    private static final double VALUE = 1234.50d;

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
    void formatUS(@NonNull final Locale locale,
                  @NonNull final String currencyCode,
                  final double input,
                  @NonNull final String expected) {

        final FieldFormatter<Money> f = new MoneyFormatter(locale);
        final Money money = new Money(BigDecimal.valueOf(input),
                                      Currency.getInstance(currencyCode));
        assertNotNull(money);
        assertEquals(expected, f.format(context, money));
    }
}
