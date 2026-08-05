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

package com.hardbacknutter.nevertoomanybooks.core.parsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.utils.Money;

/**
 * A parser for monetary values and their currency units.
 * <p>
 * Currency symbol parsing is best-effort.
 * There are overlaps, e.g. pre-euro "fr"/BEL and Swiss "fr"/CHF and some others.
 *
 * @see <a href="https://en.wikipedia.org/wiki/List_of_territorial_entities_where_English_is_an_official_language">
 *         English as an official language</a>
 */
public class MoneyParser {
    /** European Union Euro. */
    public static final String EUR = "EUR";
    /** British Pound. */
    public static final String GBP = "GBP";
    /** US Dollar. */
    public static final String USD = "USD";
    /** Chinese Yuan. */
    public static final String CNY = "CNY";

    /** For prefixed currencies, split on first digit, but leave it in the second part. */
    private static final Pattern CURRENCY_AS_PREFIX_PATTERN = Pattern.compile("(?=\\d)");
    /** Suffixed currencies, do a normal match/find. */
    private static final Pattern CURRENCY_AS_SUFFIX_PATTERN =
            Pattern.compile("(\\d*[.,]?\\d*)(.*)");
    /** Specific for pre-decimal UK money. */
    private static final Pattern SHILLING_PENCE_PATTERN = Pattern.compile("(\\d*|-?)/(\\d*|-?)");
    /** HTML cleaning. */
    private static final Pattern NBSP_LITERAL = Pattern.compile("&nbsp;", Pattern.LITERAL);
    /**
     * A Map to translate currency <strong>symbols</strong> to their official ISO code.
     * This is a fixed/static map with historical symbols and overrides.
     */
    private static final Map<String, String> HISTORIC_EXCEPTIONS = new HashMap<>();

    /**
     * Cache results of {@link #createDynamicCurrencyMap(Locale)} for each Locale.
     */
    private static final Map<Locale, Map<String, String>> DYNAMIC_MAP_CACHE
            = new ConcurrentHashMap<>();

    static {
        createCurrencyMap();
    }

    /**
     * A Map to translate currency <strong>symbols</strong> to their official ISO code.
     * This is dynamically build from all currencies, using the {@link #locale}.
     */
    private final Map<String, String> dynamicCurrencyMap;
    @NonNull
    private final Locale locale;
    @NonNull
    private final RealNumberParser realNumberParser;

    /**
     * Constructor.
     *
     * @param currencyLocale to use for parsing the currency
     * @param numberLocales  to use for parsing the number part
     */
    public MoneyParser(@NonNull final Locale currencyLocale,
                       @NonNull final List<Locale> numberLocales) {
        this.locale = currencyLocale;
        this.realNumberParser = RealNumberParser.money(numberLocales);

        dynamicCurrencyMap = createDynamicCurrencyMap(currencyLocale);
    }

    /**
     * Populate the {@link #HISTORIC_EXCEPTIONS}.
     * This is used during parsing of price/currency strings.
     * <p>
     * The key in the map must be <strong>LOWER-case</strong>.
     * The value in the map must be <strong>UPPER-case</strong>.
     * <p>
     * This is a fixed/static map with historical symbols and overrides.
     */
    private static void createCurrencyMap() {
        // allow re-creating, used for tests only
        HISTORIC_EXCEPTIONS.clear();

        // =========================================================================
        // Legacy European Currencies; replaced by the Euro.
        // =========================================================================
        HISTORIC_EXCEPTIONS.put("dm", "DEM");   // German Mark
        HISTORIC_EXCEPTIONS.put("m", "DEM");    // German Mark / East German Mark shorthand
        HISTORIC_EXCEPTIONS.put("f", "FRF");    // French Franc
        HISTORIC_EXCEPTIONS.put("ff", "FRF");   // French Franc
        HISTORIC_EXCEPTIONS.put("bfr", "BEF");  // Belgian Franc
        HISTORIC_EXCEPTIONS.put("fr.", "BEF");  // Belgian Franc
        HISTORIC_EXCEPTIONS.put("fr", "BEF");   // Belgian Franc (overrides CHF 'Fr')
        HISTORIC_EXCEPTIONS.put("ir£", "IEP");  // Irish Punt
        HISTORIC_EXCEPTIONS.put("l", "ITL");    // Italian Lira
        HISTORIC_EXCEPTIONS.put("lit", "ITL");  // Italian Lira
        HISTORIC_EXCEPTIONS.put("pta", "ESP");  // Spanish Peseta
        HISTORIC_EXCEPTIONS.put("ƒ", "NLG");    // Dutch Guilder
        HISTORIC_EXCEPTIONS.put("Δρ", "GRD");   // Greek Drachma
        HISTORIC_EXCEPTIONS.put("kn", "HRK");   // Croatian Kuna
        HISTORIC_EXCEPTIONS.put("ls", "LVL");   // Latvian Lats
        HISTORIC_EXCEPTIONS.put("sk", "SKK");   // Slovak Koruna
        HISTORIC_EXCEPTIONS.put("ös", "ATS");   // Austrian Schilling
        HISTORIC_EXCEPTIONS.put("sch", "ATS");  // Austrian Schilling
        HISTORIC_EXCEPTIONS.put("esc", "PTE");  // Portuguese Escudo
        HISTORIC_EXCEPTIONS.put("mk", "FIM");   // Finnish Markka
        HISTORIC_EXCEPTIONS.put("sit", "SIT");  // Slovenian Tolar
        HISTORIC_EXCEPTIONS.put("lt", "LTL");   // Lithuanian Litas
        HISTORIC_EXCEPTIONS.put("lm", "MTL");   // Maltese Lira
        HISTORIC_EXCEPTIONS.put("lfr", "LUF");  // Luxembourgish Franc

        // =========================================================================
        // Multi-Character & Regional Disambiguations
        // =========================================================================
        HISTORIC_EXCEPTIONS.put("a$", "AUD");   // Australian Dollar
        HISTORIC_EXCEPTIONS.put("c$", "CAD");   // Canadian Dollar
        HISTORIC_EXCEPTIONS.put("nz$", "NZD");  // New Zealand Dollar
        HISTORIC_EXCEPTIONS.put("s$", "SGD");   // Singapore Dollar
        HISTORIC_EXCEPTIONS.put("nt$", "TWD");  // Taiwan Dollar
        HISTORIC_EXCEPTIONS.put("r$", "BRL");   // Brazilian Real
        HISTORIC_EXCEPTIONS.put("mx$", "MXN");  // Mexican Peso
        HISTORIC_EXCEPTIONS.put("r", "ZAR");    // South African Rand
        HISTORIC_EXCEPTIONS.put("rm", "MYR");   // Malaysian Ringgit

        // =========================================================================
        // Non-Standard Abbreviations / Native Text Suffixes
        // =========================================================================
        HISTORIC_EXCEPTIONS.put("br", "RUB");   // Custom representation for Russian Rouble
        HISTORIC_EXCEPTIONS.put("ft", "HUF");   // Hungarian Forint abbreviation
        HISTORIC_EXCEPTIONS.put("kc", "CZK");   // Czech Koruna abbreviation
        HISTORIC_EXCEPTIONS.put("kč", "CZK");   // Czech Koruna abbreviation
        HISTORIC_EXCEPTIONS.put("lei", "RON");  // Romanian Leu text representation
        HISTORIC_EXCEPTIONS.put("zł", "PLN");   // Polish Złoty symbol
        HISTORIC_EXCEPTIONS.put("din", "YUD");  // Yugoslav Dinar
        HISTORIC_EXCEPTIONS.put("lev", "BGN");  // Bulgarian Lev
        HISTORIC_EXCEPTIONS.put("hrn", "UAH");  // Ukrainian Hryvnia
        HISTORIC_EXCEPTIONS.put("tl", "TRY");   // Turkish Lira

        HISTORIC_EXCEPTIONS.put("dkr", "DKK");  // Danish Krone
        HISTORIC_EXCEPTIONS.put("nkr", "NOK");  // Norwegian Krone
        HISTORIC_EXCEPTIONS.put("skr", "SEK");  // Swedish Krona
        HISTORIC_EXCEPTIONS.put("ikr", "ISK");  // Icelandic Króna

        // =========================================================================
        // Nordic Currency Choice Override
        // =========================================================================
        // We had DKK for quite a while, so leaving this as the default.
        // We could pick one based on the user device locale and/or on
        // a specific SearchEngine Local.
        // This overrides:
        // SEK (Swedish)
        // NOK (Norway)
        // ISK(Iceland)
        // EEK (Estonia)
        HISTORIC_EXCEPTIONS.put("kr", "DKK");  // Danish Krone
    }

    /**
     * Constructor parsing the (optional) currency from a string.
     * <p>
     * If the currency is in any form invalid, a Money object is <strong>STILL</strong> returned,
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
        if (currencyStr != null && !currencyStr.isBlank()) {
            try {
                final Currency currency = Currency.getInstance(currencyStr);
                return new Money(value, currency);
            } catch (@NonNull final IllegalArgumentException e) {
                // covers NumberFormatException
            }
        }
        return new Money(value, null);
    }

    @NonNull
    private static Optional<Money> parseBritishPreDecimal(@NonNull final MatchResult matcher) {
        try {
            int shillings = 0;
            int pence = 0;
            String tmp;

            tmp = matcher.group(1);
            if (tmp != null && !tmp.isBlank() && !"-".equals(tmp)) {
                shillings = Integer.parseInt(tmp);
            }
            tmp = matcher.group(2);
            if (tmp != null && !tmp.isBlank() && !"-".equals(tmp)) {
                pence = Integer.parseInt(tmp);
            }

            // the British pound was made up of 20 shillings, each of which was
            // made up of 12 pence, a total of 240 pence. Madness...
            final BigDecimal value = BigDecimal
                    .valueOf(((long) shillings * 12) + pence)
                    .divide(BigDecimal.valueOf(240), 2, RoundingMode.HALF_UP);
            return Optional.of(new Money(value, Currency.getInstance(GBP)));

        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
        return Optional.empty();
    }

    @NonNull
    private Map<String, String> createDynamicCurrencyMap(final Locale locale) {
        return DYNAMIC_MAP_CACHE.computeIfAbsent(locale, loc -> {
            final Map<String, String> dynamicMap = new HashMap<>();

            for (final Currency currency : Currency.getAvailableCurrencies()) {
                final String code = currency.getCurrencyCode();
                final String symbol = currency.getSymbol(loc).toLowerCase(loc);

                // Some symbols are used by multiple countries (e.g., '$')
                // Give preference to the native currency of the website's locale.
                if (currency.equals(Currency.getInstance(code))) {
                    dynamicMap.put(symbol, code);
                } else {
                    dynamicMap.putIfAbsent(symbol, code);
                }

                // Also register the raw lowercase ISO code as a safe fallback match
                dynamicMap.put(code.toLowerCase(loc), code);
            }

            return Collections.unmodifiableMap(dynamicMap);
        });
    }


    /**
     * Parse a string containing a combined value/currency, e.g. "Bf459", "$9.99", "66 EUR".
     *
     * @param valueWithCurrency to parse
     *
     * @return a Money object with or without currency
     *         or {@code null} if parsing failed.
     */
    @NonNull
    public Optional<Money> parse(@NonNull final CharSequence valueWithCurrency) {
        try {
            // website HTML cleaning: replace any "&nbsp;" by " "
            // and strip the whole thing
            final String vwc = NBSP_LITERAL.matcher(valueWithCurrency)
                                           .replaceAll(" ")
                                           .strip();
            if (vwc.isBlank()) {
                return Optional.empty();
            }

            // If the string does not start with a digit,
            // we likely have a currency string as a prefix.
            if (!Character.isDigit(vwc.charAt(0))) {
                final String[] data = CURRENCY_AS_PREFIX_PATTERN.split(vwc, 2);
                if (data.length > 1) {
                    final Optional<Money> parse = parse(data[1], data[0]);
                    if (parse.isPresent()) {
                        return parse;
                    }
                }
            }

            Matcher matcher;

            // First check if this was UK shillings/pence
            matcher = SHILLING_PENCE_PATTERN.matcher(vwc);
            if (matcher.find()) {
                return parseBritishPreDecimal(matcher);
            }

            // We should either have a value without a currency at all,
            // or a value with a currency as suffix.
            matcher = CURRENCY_AS_SUFFIX_PATTERN.matcher(vwc);
            if (matcher.find()) {
                return parse(matcher.group(1), matcher.group(2));
            }

        } catch (@NonNull final IllegalArgumentException e) {
            // covers NumberFormatException
        }
        return Optional.empty();
    }

    /**
     * Parsing the value and the currency from 2 strings.
     *
     * @param valueStr    to set
     * @param currencyStr to parse
     *
     * @return a Money object with or without currency
     */
    @NonNull
    public Optional<Money> parse(@Nullable final String valueStr,
                                 @Nullable final String currencyStr) {

        Currency currency = null;
        if (currencyStr != null && !currencyStr.isBlank()) {
            try {
                // We MUST use the users Locale here as currencies can use local characters.
                final String currencyCode = currencyStr.strip().toUpperCase(locale);
                // If we have a normalised ISO3 code, use it.
                // Otherwise try to convert it to one.
                if (currencyCode.length() == 3) {
                    try {
                        currency = Currency.getInstance(currencyCode);
                    } catch (final IllegalArgumentException e) {
                        currency = fromSymbol(currencyStr);
                    }
                } else {
                    currency = fromSymbol(currencyStr);
                }
            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }

        if (valueStr != null && !valueStr.isBlank()) {
            //noinspection OverlyBroadCatchBlock
            try {
                final BigDecimal value = realNumberParser.parseBigDecimal(valueStr);
                return Optional.of(new Money(value, currency));

            } catch (@NonNull final IllegalArgumentException ignore) {
                // covers NumberFormatException
            }
        }
        return Optional.empty();
    }

    /**
     * Get the value parser for special use.
     * <p>
     * Dev. note: this is mainly used for tests,
     * but also for some SearchEngines where the website
     * uses a single/hardcoded currency.
     *
     * @return parser
     */
    @NonNull
    public RealNumberParser getRealNumberParser() {
        return realNumberParser;
    }

    /**
     * Convert the passed string with a (hopefully valid) currency unit/symbol,
     * into a Currency.
     *
     * @param symbol to convert
     *
     * @return Currency, or {@code null} if not found
     */
    @Nullable
    private Currency fromSymbol(@NonNull final String symbol) {
        if (symbol.isBlank()) {
            return null;
        }

        final String key = symbol.strip().toLowerCase(locale);

        // Check static historical/overrides
        if (HISTORIC_EXCEPTIONS.containsKey(key)) {
            return Currency.getInstance(HISTORIC_EXCEPTIONS.get(key));
        }

        // otherwise the standard table
        if (dynamicCurrencyMap.containsKey(key)) {
            return Currency.getInstance(dynamicCurrencyMap.get(key));
        }

        return null;
    }
}
