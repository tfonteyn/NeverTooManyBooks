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
package com.hardbacknutter.nevertoomanybooks.core.parsers;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.core.utils.Money;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MoneyParserTest {

    private static final BigDecimal twelveDotThreeFour = BigDecimal.valueOf(12.34d);

    @NonNull
    static Stream<Arguments> readArgs() {
        return Stream.of(
                // Variations of GBP and Locale.UK
                Arguments.of(List.of(Locale.UK),
                             "GBP&nbsp;12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of(List.of(Locale.UK),
                             "GBP12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of(List.of(Locale.UK),
                             "£ 12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of(List.of(Locale.UK),
                             "£12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of(List.of(Locale.UK),
                             "£12", BigDecimal.valueOf(12.0d), MoneyParser.GBP),

                Arguments.of(List.of(Locale.UK),
                             "12/6", BigDecimal.valueOf(0.625d), MoneyParser.GBP),
                Arguments.of(List.of(Locale.UK),
                             "10/-", BigDecimal.valueOf(0.5d), MoneyParser.GBP),

                // Variations of EUR and Locale.{eu}
                Arguments.of(List.of(new Locale("de", "DE")),
                             "12,34&nbsp;€", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of(List.of(new Locale("fr", "FR")),
                             "12,34 €", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of(List.of(new Locale("nl", "NL")),
                             "12,34€", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of(List.of(new Locale("es", "ES")),
                             "12,34 eur", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of(List.of(new Locale("nl", "BE")),
                             "EUR 12,34", twelveDotThreeFour, MoneyParser.EUR),

                // Multiple Locales
                Arguments.of(List.of(Locale.CANADA_FRENCH, Locale.UK),
                             "GBP&nbsp;12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of(List.of(Locale.CANADA_FRENCH, Locale.UK),
                             "GBP12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of(List.of(Locale.CANADA_FRENCH, Locale.UK),
                             "£ 12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of(List.of(Locale.CANADA_FRENCH, Locale.UK),
                             "£12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of(List.of(Locale.CANADA_FRENCH, Locale.UK),
                             "£12", BigDecimal.valueOf(12.0d), MoneyParser.GBP),

                Arguments.of(List.of(new Locale("de", "DE"), Locale.UK),
                             "12,34&nbsp;€", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of(List.of(new Locale("fr", "FR"), Locale.UK),
                             "12,34 €", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of(List.of(new Locale("nl", "NL"), Locale.UK),
                             "12,34€", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of(List.of(new Locale("es", "ES"), Locale.UK),
                             "12,34 eur", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of(List.of(new Locale("nl", "BE"), Locale.UK),
                             "EUR 12,34", twelveDotThreeFour, MoneyParser.EUR)
        );
    }

    @ParameterizedTest
    @MethodSource("readArgs")
    void simple(@NonNull final List<Locale> testLocales,
                @NonNull final CharSequence source,
                @NonNull final BigDecimal value,
                @NonNull final String code) {
        final RealNumberParser realNumberParser = new RealNumberParser(testLocales);
        final MoneyParser moneyParser = new MoneyParser(testLocales.get(0), realNumberParser);
        final Money money = moneyParser.parse(source);
        assertNotNull(money);
        assertEquals(value, money.getValue());
        assertEquals(code, money.getCurrencyCode());
    }
}
