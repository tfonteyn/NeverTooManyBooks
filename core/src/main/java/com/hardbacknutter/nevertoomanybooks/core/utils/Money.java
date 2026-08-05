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
package com.hardbacknutter.nevertoomanybooks.core.utils;

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
 * Casting involves rounding to int/long by adding 0.5 to positive values.
 * <ul>
 *     <li>2.3 + 0.5 -> 2</li>
 *     <li>2.7 + 0.5 -> 3</li>
 * </ul>
 * Negative numbers are always rounded down.
 * <ul>
 *     <li>-2.3 -> -2</li>
 *     <li>-2.7 -> -2</li>
 * </ul>
 * <p>
 * ENHANCE: currency fields should not use double:
 * <a href="https://javamoney.github.io">JavaMoney</a>
 * - a wonderful library, might have issues on Android though.
 * <a href="https://www.joda.org/joda-money/">Joda Money</a> not tried, but looks small and neat.
 *
 * <p>
 * <strong>Supports UK (GBP), pre-decimal 1971:</strong>
 * Shilling/Pence as subdivisions of the pound.
 * UK Shilling was written as "1/-", for example:
 * three shillings and six pence => 3/6
 * It's used on the ISFDB website. We convert it to GBP. See
 * <a href="https://en.wikipedia.org/wiki/Decimal_Day">Decimal_Day</a>
 * <p>
 * Dev. note: this class <strong>MUST</strong> extend {@link Number} as
 * it will be cast to one when editing.
 */
public class Money
        extends Number
        implements Parcelable {

    /** {@link Parcelable}. */
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

    private static final String EUR_STR = "EUR";

    /** Cached EURO currency. */
    public static final Currency EURO = Currency.getInstance(EUR_STR);
    @SuppressWarnings("CheckStyle")
    private static final Map<String, BigDecimal> EUROS = Map.ofEntries(
            // Andorra
            Map.entry("ADP", new BigDecimal("166.386")),
            // Austria
            Map.entry("ATS", new BigDecimal("13.7603")),
            // Belgium
            Map.entry("BEF", new BigDecimal("40.3399")),
            // Bulgaria
            Map.entry("BGN", new BigDecimal("1.95583")),
            // Croatia
            Map.entry("HRK", new BigDecimal("7.53450")),
            // Cyprus
            Map.entry("CYP", new BigDecimal("0.585274")),
            // Estonia
            Map.entry("EEK", new BigDecimal("15.6466")),
            // Finland
            Map.entry("FIM", new BigDecimal("5.94573")),
            // France
            Map.entry("FRF", new BigDecimal("6.55957")),
            // Germany
            Map.entry("DEM", new BigDecimal("1.95583")),
            // Greece
            Map.entry("GRD", new BigDecimal("340.75")),
            // Ireland
            Map.entry("IEP", new BigDecimal("0.787564")),
            // Italy
            Map.entry("ITL", new BigDecimal("1936.27")),
            // Latvia
            Map.entry("LVL", new BigDecimal("0.702804")),
            // Lithuania
            Map.entry("LTL", new BigDecimal("3.45280")),
            // Luxembourg
            Map.entry("LUF", new BigDecimal("40.3399")),
            // Malta
            Map.entry("MTL", new BigDecimal("0.429300")),
            // Monaco
            Map.entry("MCF", new BigDecimal("6.55957")),
            // Netherlands
            Map.entry("NLG", new BigDecimal("2.20371")),
            // Portugal
            Map.entry("PTE", new BigDecimal("200.482")),
            // San Marino
            Map.entry("SML", new BigDecimal("1936.27")),
            // Slovakia
            Map.entry("SKK", new BigDecimal("30.1260")),
            // Slovenia
            Map.entry("SIT", new BigDecimal("239.640")),
            // Spain
            Map.entry("ESP", new BigDecimal("166.386")),
            // Vatican City
            Map.entry("VAL", new BigDecimal("1936.27"))
    );

    private static final long serialVersionUID = -8273127556226893529L;

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

    private Money(@NonNull final Parcel in) {
        value = new BigDecimal(in.readString());
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
        //noinspection CallToNumericToString
        dest.writeString(value.toString());

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

    /**
     * Get the value part.
     *
     * @return monetary value
     */
    @NonNull
    public BigDecimal getValue() {
        return value;
    }

    /**
     * Get the currency part.
     *
     * @return monetary currency
     *
     * @see <a href="https://en.wikipedia.org/wiki/ISO_4217">ISO 4217</a>
     */
    @Nullable
    public Currency getCurrency() {
        return currency;
    }

    /**
     * Convenience method to check if the value-part is zero.
     *
     * @return {@code true} if it is
     */
    public boolean isZero() {
        return value.compareTo(BigDecimal.ZERO) == 0;
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
     * This return the {@link BigDecimal#toString()} of the value.
     *
     * @return the value part as a string.
     */
    @Override
    @NonNull
    public String toString() {
        //noinspection CallToNumericToString
        return value.toString();
    }

    /**
     * DEBUG.
     *
     * @return traditional toString() formatting
     */
    @SuppressWarnings("unused")
    @NonNull
    public String toDbgString() {
        return "Money{"
               + "currency=" + currency
               + ", value=" + value
               + '}';
    }

    /**
     * Convert from a pre-euro currencies (List complete 2023-01-01)
     * <a href="https://en.wikipedia.org/wiki/Eurozone">Euro zone</a>
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

        if (EUR_STR.equals(currency.getCurrencyCode())) {
            // The Euro itself
            return new Money(value, currency);
        }

        final BigDecimal rate = EUROS.get(currency.getCurrencyCode());
        if (rate == null) {
            // Not a Euro currency, return as is.
            return new Money(value, currency);
        }

        return new Money(value.divide(rate, RoundingMode.HALF_UP), EURO);
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
               && value.compareTo(money.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, value);
    }
}
