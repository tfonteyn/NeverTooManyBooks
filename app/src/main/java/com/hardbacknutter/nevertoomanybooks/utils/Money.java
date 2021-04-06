/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.annotation.Nullable;

import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value class to represent a value + currency.
 * <p>
 * Normally {@link #doubleValue()} should be used for the value.
 *
 * <p>
 * Casting involves rounding to int/long by adding 0.5 to positive values.
 * 2.3 + 0.5 -> 2
 * 2.7 + 0.5 -> 3.
 * Negative numbers are always rounded down.
 * -2.3 -> -2
 * -2.7 -> -2
 * <p>
 * Casting to a float is with loss of precision.
 * <p>
 * ENHANCE: currency fields should not use float/double.
 * https://javamoney.github.io - a wonderful library, although it seems it
 * won't make it into the JDK for now. Might have issues on Android though.
 * https://www.joda.org/joda-money/ not tried, but looks small and neat.
 * Alternatively migrate to using BigDecimal as a short term solution.
 */
public class Money
        extends Number {

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

    /** A Map to translate currency symbols to their official ISO code. */
    private static final Map<String, String> CURRENCY_MAP = new HashMap<>();

    private static final long serialVersionUID = -2882175581162071769L;

    /** ISO code. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private String mCurrency;
    private double mValue;

    public Money() {
    }

    public Money(final double value,
                 @NonNull final String currency) {
        mValue = value;
        mCurrency = currency;
    }

    /**
     * Takes a combined price field, and returns the value/currency in the Bundle.
     *
     * <strong>Note:</strong>
     * The UK (GBP), pre-decimal 1971, had Shilling/Pence as subdivisions of the pound.
     * UK Shilling was written as "1/-", for example:
     * three shillings and six pence => 3/6
     * It's used on the ISFDB web site. We convert it to GBP.
     * <a href="https://en.wikipedia.org/wiki/Decimal_Day">Decimal_Day</a>
     *
     * <strong>If any conversion fails, the currency will be {@code null},
     * and the value will be 0.0d</strong>
     *
     * @param locale            <strong>Must</strong> be the Locale for the source data.
     *                          (and NOT simply the system/user).
     * @param priceWithCurrency price to decode, e.g. "Bf459", "$9.99", "66 EUR", ...
     */
    public Money(@NonNull final Locale locale,
                 @NonNull final CharSequence priceWithCurrency) {

        parse(locale, priceWithCurrency);
    }

    /**
     * Populate CURRENCY_MAP.
     * The key in the map must be <strong>LOWER-case</strong>.
     * The value in the map must be <strong>UPPER-case</strong>.
     *
     * <a href="https://en.wikipedia.org/wiki/List_of_territorial_entities_where_English_is_an_official_language>English</a>
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
        CURRENCY_MAP.put("dm", "DEM"); // German marks
        CURRENCY_MAP.put("ƒ", "NLG"); // Dutch Guilder
        CURRENCY_MAP.put("fr", "BEF"); // Belgian Franc
        CURRENCY_MAP.put("fr.", "BEF"); // Belgian Franc
        CURRENCY_MAP.put("f", "FRF"); // French Franc
        CURRENCY_MAP.put("ff", "FRF"); // French Franc
        CURRENCY_MAP.put("pta", "ESP"); // Spanish Peseta
        CURRENCY_MAP.put("L", "ITL"); // Italian Lira
        CURRENCY_MAP.put("Δρ", "GRD"); // Greek Drachma
        CURRENCY_MAP.put("₺", "TRY "); // Turkish Lira

        // some others as seen on ISFDB site
        CURRENCY_MAP.put("r$", "BRL"); // Brazilian Real
        CURRENCY_MAP.put("kr", "DKK"); // Denmark Krone
        CURRENCY_MAP.put("Ft", "HUF"); // Hungarian Forint
    }

    public boolean parse(@NonNull final Locale locale,
                         @NonNull final CharSequence priceWithCurrency) {

        // website html cleaning
        final String pc = NBSP_LITERAL.matcher(priceWithCurrency).replaceAll(" ");

        final String[] data = CURRENCY_AS_PREFIX_PATTERN.split(pc, 2);
        if (data.length > 1 && parse(locale, data[0], data[1])) {
            return true;
        }

        Matcher matcher;

        matcher = CURRENCY_AS_SUFFIX_PATTERN.matcher(pc);
        if (matcher.find() && parse(locale, matcher.group(2), matcher.group(1))) {
            return true;
        }

        // let's see if this was UK shillings/pence
        matcher = SHILLING_PENCE_PATTERN.matcher(pc);
        if (matcher.find()) {
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
                mValue = ((shillings * 12) + pence) / 240f;
                mCurrency = GBP;
                return true;

            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }

        return false;
    }

    public boolean parse(@NonNull final Locale locale,
                         @Nullable final String currencyStr,
                         @Nullable final String valueStr) {

        String currencyCode = null;
        if (currencyStr != null && !currencyStr.isEmpty()) {
            currencyCode = currencyStr.trim().toUpperCase(locale);
            // if we don't have a normalized ISO3 code, see if we can convert it to one.
            if (currencyCode.length() != 3) {
                currencyCode = currencyToISO(currencyStr);
            }
        }

        if (currencyCode != null && currencyCode.length() == 3) {
            if (valueStr != null && !valueStr.isEmpty()) {
                try {
                    // buffer just in case the getCurrencyCode() throws.
                    final double tmpValue = ParseUtils.parseDouble(valueStr, locale);
                    // re-get the code just in case it used a recognised but non-standard string
                    mCurrency = Currency.getInstance(currencyCode).getCurrencyCode();
                    mValue = tmpValue;
                    return true;

                } catch (@NonNull final IllegalArgumentException ignore) {
                    // covers NumberFormatException
                }
            }
        }

        return false;
    }

    /**
     * Convert the passed string with a (hopefully valid) currency unit, into the ISO code
     * for that currency.
     *
     * @param currency to convert
     *
     * @return ISO code.
     */
    @Nullable
    private String currencyToISO(@NonNull final String currency) {
        if (CURRENCY_MAP.isEmpty()) {
            createCurrencyMap();
        }
        final String key = currency.trim().toLowerCase(AppLocale.getInstance().getSystemLocale());
        return CURRENCY_MAP.get(key);
    }

    @Override
    public double doubleValue() {
        return mValue;
    }

    @Override
    public int intValue() {
        if (mValue >= 0) {
            return (int) (mValue + 0.5);
        } else {
            return (int) mValue;
        }
    }

    @Override
    public long longValue() {
        if (mValue >= 0) {
            return (int) (mValue + 0.5);
        } else {
            return (int) mValue;
        }
    }

    @Override
    public float floatValue() {
        return (float) mValue;
    }

    @Nullable
    public String getCurrency() {
        return mCurrency;
    }


    /**
     * NOT DEBUG!
     * This implements the same behaviour as
     * {@link Number#toString()} for a <strong>double</strong>.
     *
     * @return the value part as a string.
     */
    @Override
    @NonNull
    public String toString() {
        return Double.toString(mValue);
    }

    /**
     * Convert from a pre-euro currencies (List complete 2021-01-01)
     * <p>
     * Values in euro, or without currency are returned as-is in euro.
     * <p>
     * Non (pre)-euro currency values are returned as-is.
     * The caller should check on the return value <strong>actually being euro</strong>.
     *
     * @return EURO value as a new Money object.
     */
    public Money toEuro() {
        if (EUR.equals(mCurrency) || mCurrency == null || mCurrency.isEmpty()) {
            return new Money(mValue, EUR);
        }

        switch (mCurrency) {
            case "BEF":
            case "LUF":
                return new Money(mValue / 40.3399, EUR);

            case "NLD":
                return new Money(mValue / 2.20371, EUR);

            case "FRF":
            case "MCF":
                return new Money(mValue / 6.55957, EUR);

            case "DEM":
                return new Money(mValue / 1.95583, EUR);

            case "IEP":
                return new Money(mValue / 0.787564, EUR);

            case "ITL":
                return new Money(mValue / 1.93627, EUR);

            case "GRD":
                return new Money(mValue / 340.75, EUR);

            case "ESP":
                return new Money(mValue / 166.386, EUR);

            case "PTE":
                return new Money(mValue / 200.482, EUR);

            case "ATS":
                return new Money(mValue / 13.7603, EUR);

            case "CYP":
                return new Money(mValue / 0.585274, EUR);

            case "EEK":
                return new Money(mValue / 15.6466, EUR);

            case "FIM":
                return new Money(mValue / 5.94573, EUR);

            case "LVL":
                return new Money(mValue / 0.702804, EUR);

            case "LTL":
                return new Money(mValue / 3.45280, EUR);

            case "MTL":
                return new Money(mValue / 0.429300, EUR);

            case "SML":
                return new Money(mValue / 1936.27, EUR);

            case "SKK":
                return new Money(mValue / 30.1260, EUR);

            case "SIT":
                return new Money(mValue / 239.640, EUR);

            case "VAL":
                return new Money(mValue / 1936.27, EUR);

            default:
                return new Money(mValue, mCurrency);
        }
    }
}
