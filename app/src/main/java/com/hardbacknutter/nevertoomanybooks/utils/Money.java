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
import java.util.Map;
import java.util.Objects;

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
//        extends Number
        implements Parcelable {

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

    //    private static final long serialVersionUID = -2882175581162071769L;
    public static final Currency EURO = Currency.getInstance(MoneyParser.EUR);

    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private final Currency currency;
    @NonNull
    private final BigDecimal value;

    /**
     * Constructor.
     *
     * @param value    to set
     * @param currency to set
     */
    public Money(@NonNull final BigDecimal value,
                 @Nullable final Currency currency) {
        this.value = value;
        this.currency = currency;
    }

    protected Money(@NonNull final Parcel in) {
        value = (BigDecimal) in.readSerializable();
        final boolean hasCurrency = in.readByte() != 0;
        if (hasCurrency) {
            currency = Currency.getInstance(in.readString());
        } else {
            currency = null;
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeSerializable(value);
        if (currency != null) {
            dest.writeByte((byte) 1);
            dest.writeString(currency.getCurrencyCode());
        } else {
            dest.writeByte((byte) 0);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
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
        return currency == null ? null : currency.getCurrencyCode();
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
//    @Override
    public double doubleValue() {
        return Objects.requireNonNull(value).doubleValue();
    }

    /** <strong>DO NOT USE</strong>. */
//    @Override
    public int intValue() {
        return Objects.requireNonNull(value).round(MathContext.UNLIMITED).intValue();
    }

    /** <strong>DO NOT USE</strong>. */
//    @Override
    public long longValue() {
        return Objects.requireNonNull(value).round(MathContext.UNLIMITED).longValue();
    }

    /** <strong>DO NOT USE</strong>. */
//    @Override
    public float floatValue() {
        return Objects.requireNonNull(value).floatValue();
    }

    /**
     * NOT DEBUG!
     * This return the {@link BigDecimal#toString()} of the value.
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
            return new Money(value, EURO);
        }

        if (MoneyParser.EUR.equals(currency.getCurrencyCode())) {
            // The Euro itself
            return new Money(value, currency);
        }

        final Double rate = EUROS.get(currency.getCurrencyCode());
        if (rate == null) {
            // Not a Euro currency, return as is.
            return new Money(value, currency);
        }

        return new Money(value.divide(BigDecimal.valueOf(rate), RoundingMode.HALF_UP),
                         EURO);
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
