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
import java.util.Locale;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.Base;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MoneyParsingTest
        extends Base {

    private static final BigDecimal twelveDotThreeFour = BigDecimal.valueOf(12.34d);
    private static final BigDecimal tenDotFive = BigDecimal.valueOf(10.50d);

    @NonNull
    static Stream<Arguments> readArgs() {
        return Stream.of(
                // Variations of GBP and Locale.UK
                Arguments.of((Object) new Locale[]{Locale.UK},
                             "GBP&nbsp;12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of((Object) new Locale[]{Locale.UK},
                             "GBP12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of((Object) new Locale[]{Locale.UK},
                             "£ 12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of((Object) new Locale[]{Locale.UK},
                             "£12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of((Object) new Locale[]{Locale.UK},
                             "£12", BigDecimal.valueOf(12.0d), MoneyParser.GBP),

                // Variations of EUR and Locale.{eu}
                Arguments.of((Object) new Locale[]{new Locale("de", "DE")},
                             "12,34&nbsp;€", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of((Object) new Locale[]{new Locale("fr", "FR")},
                             "12,34 €", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of((Object) new Locale[]{new Locale("nl", "NL")},
                             "12,34€", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of((Object) new Locale[]{new Locale("es", "ES")},
                             "12,34 eur", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of((Object) new Locale[]{new Locale("nl", "BE")},
                             "EUR 12,34", twelveDotThreeFour, MoneyParser.EUR),

                // Multiple Locales
                Arguments.of((Object) new Locale[]{Locale.CANADA_FRENCH, Locale.UK},
                             "GBP&nbsp;12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of((Object) new Locale[]{Locale.CANADA_FRENCH, Locale.UK},
                             "GBP12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of((Object) new Locale[]{Locale.CANADA_FRENCH, Locale.UK},
                             "£ 12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of((Object) new Locale[]{Locale.CANADA_FRENCH, Locale.UK},
                             "£12.34", twelveDotThreeFour, MoneyParser.GBP),
                Arguments.of((Object) new Locale[]{Locale.CANADA_FRENCH, Locale.UK},
                             "£12", BigDecimal.valueOf(12.0d), MoneyParser.GBP),

                Arguments.of((Object) new Locale[]{new Locale("de", "DE"), Locale.UK},
                             "12,34&nbsp;€", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of((Object) new Locale[]{new Locale("fr", "FR"), Locale.UK},
                             "12,34 €", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of((Object) new Locale[]{new Locale("nl", "NL"), Locale.UK},
                             "12,34€", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of((Object) new Locale[]{new Locale("es", "ES"), Locale.UK},
                             "12,34 eur", twelveDotThreeFour, MoneyParser.EUR),
                Arguments.of((Object) new Locale[]{new Locale("nl", "BE"), Locale.UK},
                             "EUR 12,34", twelveDotThreeFour, MoneyParser.EUR)
        );
    }

    @ParameterizedTest
    @MethodSource("readArgs")
    void simple(@NonNull final Locale[] testLocales,
                @NonNull final CharSequence source,
                @NonNull final BigDecimal value,
                @NonNull final String code) {
        setLocale(testLocales);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);
        final MoneyParser moneyParser = new MoneyParser(context, realNumberParser);
        final Money money = moneyParser.parse(source);
        assertNotNull(money);
        assertEquals(value, money.getValue());
        assertEquals(code, money.getCurrencyCode());
    }
}
