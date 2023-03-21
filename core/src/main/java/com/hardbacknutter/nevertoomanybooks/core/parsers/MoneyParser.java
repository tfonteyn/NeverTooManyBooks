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
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.utils.Money;

public class MoneyParser {
    public static final String EUR = "EUR";
    public static final String GBP = "GBP";
    public static final String USD = "USD";

    /** For prefixed currencies, split on first digit, but leave it in the second part. */
    private static final Pattern CURRENCY_AS_PREFIX_PATTERN = Pattern.compile("(?=\\d)");
    /** Suffixed currencies, do a normal match/find. */
    private static final Pattern CURRENCY_AS_SUFFIX_PATTERN =
            Pattern.compile("(\\d*[.,]?\\d*)(.*)");
    /** Specific for pre-decimal UK money. */
    private static final Pattern SHILLING_PENCE_PATTERN = Pattern.compile("(\\d*|-?)/(\\d*|-?)");
    /** HTML cleaning. */
    private static final Pattern NBSP_LITERAL = Pattern.compile("&nbsp;", Pattern.LITERAL);
    /** A Map to translate currency <strong>symbols</strong> to their official ISO code. */
    private static final Map<String, String> CURRENCY_MAP = new HashMap<>();
    @NonNull
    private final Locale locale;
    @NonNull
    private final RealNumberParser realNumberParser;

    /**
     * Constructor.
     *
     * @param locale           to use for parsing the currency
     * @param realNumberParser to use for parsing the number part
     */
    public MoneyParser(@NonNull final Locale locale,
                       @NonNull final RealNumberParser realNumberParser) {
        this.locale = locale;
        this.realNumberParser = realNumberParser;
    }

    /**
     * Constructor parsing the (optional) currency from a string.
     * <p>
     * If the currency is in any form invalid, a Money object is STILL returned,
     * but with its currency set to {@code null}.
     *
     * @param value       to set
     * @param currencyStr (optional) to parse
     *
     * @return a Money object with or without currency; never {@code null}.
     */
    @NonNull
    public static Money parse(@NonNull final BigDecimal value,
                              @Nullable final String currencyStr) {
        if (currencyStr != null && !currencyStr.isEmpty()) {
            try {
                final Currency currency = Currency.getInstance(currencyStr);
                return new Money(value, currency);
            } catch (@NonNull final IllegalArgumentException e) {
                // covers NumberFormatException
            }
        }
        return new Money(value, null);
    }

    @Nullable
    private static Money parseBritishPreDecimal(@NonNull final MatchResult matcher) {
        try {
            int shillings = 0;
            int pence = 0;
            String tmp;

            tmp = matcher.group(1);
            if (tmp != null && !tmp.isEmpty() && !"-".equals(tmp)) {
                shillings = Integer.parseInt(tmp);
            }
            tmp = matcher.group(2);
            if (tmp != null && !tmp.isEmpty() && !"-".equals(tmp)) {
                pence = Integer.parseInt(tmp);
            }

            // the British pound was made up of 20 shillings, each of which was
            // made up of 12 pence, a total of 240 pence. Madness...
            final double value = ((shillings * 12) + pence) / 240d;
            final Currency currency = Currency.getInstance(GBP);
            return new Money(BigDecimal.valueOf(value), currency);

        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
        return null;
    }

    /**
     * Populate CURRENCY_MAP. This is used during parsing of price/currency strings.
     * <p>
     * The key in the map must be <strong>LOWER-case</strong>.
     * The value in the map must be <strong>UPPER-case</strong>.
     * <p>
     * We can't add all possible values, so we add the ones from english speaking countries,
     * the Euro countries (and their legacy currencies) and the ones we specifically
     * support by having a locale (translation) for.
     * <p>
     * Others can/will be added as needed.
     *
     * @see <a href="https://en.wikipedia.org/wiki/List_of_territorial_entities_where_English_is_an_official_language">
     *         English as an official language</a>
     */
    private static void createCurrencyMap() {
        // allow re-creating
        CURRENCY_MAP.clear();

        CURRENCY_MAP.put("", "");
        CURRENCY_MAP.put("€", EUR);

        // English
        CURRENCY_MAP.put("a$", "AUD"); // Australian Dollar
        CURRENCY_MAP.put("nz$", "NZD"); // New Zealand Dollar
        CURRENCY_MAP.put("£", GBP); // British Pound
        CURRENCY_MAP.put("$", USD); // Trump Disney's

        CURRENCY_MAP.put("c$", "CAD"); // Canadian Dollar
        CURRENCY_MAP.put("ir£", "IEP"); // Irish Punt
        CURRENCY_MAP.put("s$", "SGD"); // Singapore dollar

        // supported locales (including pre-euro)
        CURRENCY_MAP.put("br", "RUB"); // Russian Rouble
        CURRENCY_MAP.put("zł", "PLN"); // Polish Zloty
        CURRENCY_MAP.put("kč", "CZK "); // Czech Koruna
        CURRENCY_MAP.put("kc", "CZK "); // Czech Koruna
        CURRENCY_MAP.put("dm", "DEM"); // German Marks
        CURRENCY_MAP.put("ƒ", "NLG"); // Dutch Guilder
        CURRENCY_MAP.put("fr", "BEF"); // Belgian Franc
        CURRENCY_MAP.put("fr.", "BEF"); // Belgian Franc
        CURRENCY_MAP.put("f", "FRF"); // French Franc
        CURRENCY_MAP.put("ff", "FRF"); // French Franc
        CURRENCY_MAP.put("pta", "ESP"); // Spanish Peseta
        CURRENCY_MAP.put("l", "ITL"); // Italian Lira
        CURRENCY_MAP.put("lit", "ITL"); // Italian Lira
        CURRENCY_MAP.put("Δρ", "GRD"); // Greek Drachma
        CURRENCY_MAP.put("₺", "TRY "); // Turkish Lira

        // some others
        CURRENCY_MAP.put("r$", "BRL"); // Brazilian Real
        CURRENCY_MAP.put("kr", "DKK"); // Denmark Krone
        CURRENCY_MAP.put("ft", "HUF"); // Hungarian Forint
        CURRENCY_MAP.put("lei", "RON"); // Romanian Leu (Lei)
        CURRENCY_MAP.put("kn", "HRK");  // Croatian Kuna
    }


    /**
     * Parse a string containing a combined value/currency, e.g. "Bf459", "$9.99", "66 EUR".
     *
     * @param valueWithCurrency to parse
     *
     * @return a Money object with or without currency
     *         or {@code null} if parsing failed.
     */
    @Nullable
    public Money parse(@NonNull final CharSequence valueWithCurrency) {
        try {
            // website html cleaning: replace any "&nbsp;" by " "
            // and trim the whole thing so charAt(0) below is reliable
            final String vwc = NBSP_LITERAL.matcher(valueWithCurrency)
                                           .replaceAll(" ")
                                           .trim();
            if (vwc.isEmpty()) {
                return null;
            }

            if (!Character.isDigit(vwc.charAt(0))) {
                final String[] data = CURRENCY_AS_PREFIX_PATTERN.split(vwc, 2);
                if (data.length > 1) {
                    final Money parse = parse(data[1], data[0]);
                    if (parse != null) {
                        return parse;
                    }
                }
            }

            Matcher matcher;

            matcher = CURRENCY_AS_SUFFIX_PATTERN.matcher(vwc);
            if (matcher.find()) {
                return parse(matcher.group(1), matcher.group(2));
            }

            // let's see if this was UK shillings/pence
            matcher = SHILLING_PENCE_PATTERN.matcher(vwc);
            if (matcher.find()) {
                return parseBritishPreDecimal(matcher);
            }

        } catch (@NonNull final IllegalArgumentException e) {
            // covers NumberFormatException
        }
        return null;
    }

    /**
     * Parsing the value and the currency from 2 strings.
     *
     * @param valueStr    to set
     * @param currencyStr to parse
     *
     * @return a Money object with or without currency
     *         or {@code null} if parsing failed.
     */
    @Nullable
    public Money parse(@Nullable final String valueStr,
                       @Nullable final String currencyStr) {

        Currency currency = null;
        if (currencyStr != null && !currencyStr.isEmpty()) {
            try {
                // We MUST use the users Locale here as currencies can use local characters.
                String currencyCode = currencyStr.trim().toUpperCase(locale);
                // if we don't have a normalized ISO3 code, see if we can convert it to one.
                if (currencyCode.length() != 3) {
                    currencyCode = fromSymbol(currencyStr);
                }
                if (currencyCode != null && !currencyCode.isEmpty()) {
                    // re-get the code in case it used a recognised but non-standard string
                    currency = Currency.getInstance(currencyCode);
                }
            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }

        if (valueStr != null && !valueStr.isEmpty()) {
            try {
                final double value = realNumberParser.parseDouble(valueStr);
                return new Money(BigDecimal.valueOf(value), currency);

            } catch (@NonNull final IllegalArgumentException ignore) {
                // covers NumberFormatException
            }
        }
        return null;
    }

    /**
     * Convert the passed string with a (hopefully valid) currency unit/symbol,
     * into the ISO code for that currency.
     *
     * @param symbol to convert
     *
     * @return ISO code.
     */
    @Nullable
    private String fromSymbol(@NonNull final String symbol) {
        if (CURRENCY_MAP.isEmpty()) {
            createCurrencyMap();
        }
        final String key = symbol.trim().toLowerCase(locale);
        return CURRENCY_MAP.get(key);
    }
}
