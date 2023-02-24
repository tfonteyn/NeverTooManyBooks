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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;

/**
 * Value class to represent a value + currency.
 * <p>
 * Normally {@link #getValue()} should be used for displaying the value
 * with {@link #getCurrency()} or {@link #getCurrencyCode()}.
 * In practice we're often forced to use {@link #doubleValue()} due to limitations
 * in Android classes and/or the need to convert to/from external source.
 * <p>
 * Casting involves rounding to int/long by adding 0.5 to positive values.
 * 2.3 + 0.5 -> 2
 * 2.7 + 0.5 -> 3.
 * Negative numbers are always rounded down.
 * -2.3 -> -2
 * -2.7 -> -2
 * <p>
 * ENHANCE: currency fields should not use double.
 * <a href="https://javamoney.github.io">JavaMoney</a>
 * - a wonderful library, might have issues on Android though.
 * <a href="https://www.joda.org/joda-money/">Joda Money</a> not tried, but looks small and neat.
 *
 * <p>
 * <strong>Supports UK (GBP), pre-decimal 1971:</strong>
 * Shilling/Pence as subdivisions of the pound.
 * UK Shilling was written as "1/-", for example:
 * three shillings and six pence => 3/6
 * It's used on the ISFDB web site. We convert it to GBP.
 * <a href="https://en.wikipedia.org/wiki/Decimal_Day">Decimal_Day</a>
 * <p>
 */
public class Money
        extends Number
        implements Parcelable {

    public static final String EUR = "EUR";
    public static final String GBP = "GBP";
    public static final String USD = "USD";

    public static final Creator<Money> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public Money createFromParcel(@NonNull final Parcel in) {
            return new Money(in);
        }

        @Override
        @NonNull
        public Money[] newArray(final int size) {
            return new Money[size];
        }
    };

    private static final Map<String, Double> EUROS = Map.ofEntries(
            // Austria
            Map.entry("ATS", 13.7603d),
            // Belgium
            Map.entry("BEF", 40.3399d),
            // Luxembourg
            Map.entry("LUF", 40.3399d),
            // Croatia
            Map.entry("HRK", 7.53450d),
            // Cyprus
            Map.entry("CYP", 0.585274d),
            // Estonia
            Map.entry("EEK", 15.6466d),
            // Finland
            Map.entry("FIM", 5.94573d),
            // France
            Map.entry("FRF", 6.55957d),
            // Monaco
            Map.entry("MCF", 6.55957d),
            // Germany
            Map.entry("DEM", 1.95583d),
            // Greece
            Map.entry("GRD", 340.75d),
            // Ireland
            Map.entry("IEP", 0.787564d),
            // Italy
            Map.entry("ITL", 1936.27d),
            // San Marino
            Map.entry("SML", 1936.27d),
            // Vatican City
            Map.entry("VAL", 1936.27d),
            // Latvia
            Map.entry("LVL", 0.702804d),
            // Lithuania
            Map.entry("LTL", 3.45280d),
            // Malta
            Map.entry("MTL", 0.429300d),
            // Netherlands
            Map.entry("NLD", 2.20371d),
            // Portugal
            Map.entry("PTE", 200.482d),
            // Slovakia
            Map.entry("SKK", 30.1260d),
            // Slovenia
            Map.entry("SIT", 239.640d),
            // Spain
            Map.entry("ESP", 166.386d)
    );

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

    private static final long serialVersionUID = -2882175581162071769L;

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private Currency currency;
    @Nullable
    private BigDecimal value;

    /**
     * Constructor.
     *
     * @param value    to set
     * @param currency to set
     */
    public Money(@NonNull final BigDecimal value,
                 @NonNull final Currency currency) {
        this.value = value;
        this.currency = currency;
    }

    public Money(final double value,
                 @NonNull final String currency) {
        this(BigDecimal.valueOf(value), currency);
    }

    public Money(@NonNull final BigDecimal value,
                 @NonNull final String currency) {
        this.value = value;
        try {
            this.currency = Currency.getInstance(currency);
        } catch (@NonNull final IllegalArgumentException e) {
            // ignore
        }
    }

    /**
     * Constructor taking a combined price field, e.g. "Bf459", "$9.99", "66 EUR", etc.
     * <strong>If parsing fails, {@link #isValid()} will return {@code false}</strong>
     *
     * @param locales           to use for parsing
     * @param priceWithCurrency price to decode
     */
    public Money(@NonNull final List<Locale> locales,
                 @NonNull final CharSequence priceWithCurrency) {

        if (!parse(locales, priceWithCurrency)) {
            this.value = null;
            this.currency = null;
        }
    }

    protected Money(@NonNull final Parcel in) {
        boolean present;
        present = in.readByte() != 0;
        if (present) {
            currency = Currency.getInstance(in.readString());
        }
        present = in.readByte() != 0;
        if (present) {
            value = (BigDecimal) in.readSerializable();
        }
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
     * Convert the passed string with a (hopefully valid) currency unit/symbol,
     * into the ISO code for that currency.
     *
     * @param symbol to convert
     *
     * @return ISO code.
     */
    @Nullable
    private static String fromSymbol(@NonNull final String symbol,
                                     @NonNull final Locale locale) {
        if (CURRENCY_MAP.isEmpty()) {
            createCurrencyMap();
        }
        final String key = symbol.trim().toLowerCase(locale);
        return CURRENCY_MAP.get(key);
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        if (currency != null) {
            dest.writeByte((byte) 1);
            dest.writeString(currency.getCurrencyCode());
        } else {
            dest.writeByte((byte) 0);
        }

        if (value != null) {
            dest.writeByte((byte) 1);
            dest.writeSerializable(value);
        } else {
            dest.writeByte((byte) 0);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Parse a string containing e.g. "Bf459", "$9.99", "66 EUR".
     *
     * @param locales           to use for parsing
     * @param valueWithCurrency to parse
     *
     * @return {@code true} if both value and currency were parsed successfully
     */
    public boolean parse(@NonNull final List<Locale> locales,
                         @NonNull final CharSequence valueWithCurrency) {

        // website html cleaning
        final String vwc = NBSP_LITERAL.matcher(valueWithCurrency).replaceAll(" ");

        final String[] data = CURRENCY_AS_PREFIX_PATTERN.split(vwc, 2);
        if (data.length > 1 && parse(locales, data[0], data[1])) {
            return true;
        }

        Matcher matcher;

        matcher = CURRENCY_AS_SUFFIX_PATTERN.matcher(vwc);
        if (matcher.find() && parse(locales, matcher.group(2), matcher.group(1))) {
            return true;
        }

        // let's see if this was UK shillings/pence
        matcher = SHILLING_PENCE_PATTERN.matcher(vwc);
        return matcher.find() && parseBritishPreDecimal(matcher);
    }


    private boolean parse(@NonNull final List<Locale> localeList,
                          @Nullable final String currencyStr,
                          @Nullable final String valueStr) {

        String currencyCode = null;
        if (currencyStr != null && !currencyStr.isEmpty()) {
            currencyCode = currencyStr.trim().toUpperCase(localeList.get(0));
            // if we don't have a normalized ISO3 code, see if we can convert it to one.
            if (currencyCode.length() != 3) {
                currencyCode = fromSymbol(currencyStr, localeList.get(0));
            }
        }

        if (currencyCode != null && currencyCode.length() == 3) {
            if (valueStr != null && !valueStr.isEmpty()) {
                try {
                    // buffer in case the Currency.getInstance() throws.
                    final double tmpValue = NumberParser.parseDouble(localeList, valueStr);
                    // re-get the code in case it used a recognised but non-standard string
                    currency = Currency.getInstance(currencyCode);
                    value = BigDecimal.valueOf(tmpValue);
                    return true;

                } catch (@NonNull final IllegalArgumentException ignore) {
                    // covers NumberFormatException
                }
            }
        }

        return false;
    }

    private boolean parseBritishPreDecimal(@NonNull final MatchResult matcher) {
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
            value = BigDecimal.valueOf(((shillings * 12) + pence) / 240d);
            currency = Currency.getInstance(GBP);
            return true;

        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
        return false;
    }

    @Nullable
    public BigDecimal getValue() {
        return value;
    }

    @Nullable
    public Currency getCurrency() {
        return currency;
    }

    /**
     * Convenience method. Get the ISO currency code.
     *
     * @return ISO 4217 code or {@code null} if the currency is not set
     *
     * @see <a href="https://en.wikipedia.org/wiki/ISO_4217">ISO 4217</a>
     */
    @Nullable
    public String getCurrencyCode() {
        return currency != null ? currency.getCurrencyCode() : null;
    }

    /**
     * Check if this object contains valid data.
     * <p>
     * This should be called using the constructor which parses a "price with currency".
     *
     * @return {@code true} if it is
     */
    public boolean isValid() {
        return currency != null && value != null;
    }

    /**
     * Convenience method to check if the value-part is zero.
     *
     * @return {@code true} if it is
     */
    public boolean isZero() {
        return Objects.requireNonNull(value).compareTo(BigDecimal.ZERO) == 0;
    }

    /** Use {@link #getValue()} when possible. */
    @Override
    public double doubleValue() {
        return Objects.requireNonNull(value).doubleValue();
    }

    /** <strong>DO NOT USE</strong>. */
    @Override
    public int intValue() {
        return Objects.requireNonNull(value).round(MathContext.UNLIMITED).intValue();
    }

    /** <strong>DO NOT USE</strong>. */
    @Override
    public long longValue() {
        return Objects.requireNonNull(value).round(MathContext.UNLIMITED).longValue();
    }

    /** <strong>DO NOT USE</strong>. */
    @Override
    public float floatValue() {
        return Objects.requireNonNull(value).floatValue();
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
        //noinspection CallToNumericToString
        return Objects.requireNonNull(value).toString();
    }

    /**
     * DEBUG!
     */
    @NonNull
    public String toDbgString() {
        return "Money{"
               + "currency=" + currency
               + ", value=" + value
               + '}';
    }

    /**
     * Convert from a pre-euro currencies (List complete 2023-01-01)
     * <a href="https://en.wikipedia.org/wiki/Eurozone">Eurozone</a>
     * <p>
     * Values in euro, or without currency are returned as-is in euro.
     * <p>
     * Non (pre)-euro currency values are returned as-is.
     * The caller should check on the return value <strong>actually being euro</strong>.
     *
     * @return EURO value as a new Money object.
     */
    @NonNull
    public Money toEuro() {
        Objects.requireNonNull(value);

        if (currency == null) {
            return new Money(value, EUR);
        }

        if (EUR.equals(currency.getCurrencyCode())) {
            return new Money(value, EUR);
        }

        final Double rate = EUROS.get(currency.getCurrencyCode());
        if (rate == null) {
            return new Money(value, currency);
        }

        return new Money(value.divide(BigDecimal.valueOf(rate),
                                      RoundingMode.HALF_UP), EUR);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Money money = (Money) o;
        return Objects.equals(currency, money.currency)
               && Objects.equals(value, money.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, value);
    }
}
