/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Provides storage for an partial/incomplete date.
 * <strong>Immutable</strong>.
 * A partial date can consist of just a year, a year+month, or year+month+day value.
 */
public class PartialDate
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<PartialDate> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public PartialDate createFromParcel(@NonNull final Parcel in) {
            return new PartialDate(in);
        }

        @Override
        @NonNull
        public PartialDate[] newArray(final int size) {
            return new PartialDate[size];
        }
    };

    /** An immutable 'empty' date. */
    public static final PartialDate NOT_SET = new PartialDate(null, null, null);

    /** NonNull - the partial date; using '1' for not-set day,month,year fields. */
    private LocalDate localDate;
    private boolean yearSet;
    private boolean monthSet;
    private boolean daySet;

    /**
     * Constructor.
     *
     * @param localDate to set; all fields are set/used
     */
    public PartialDate(@NonNull final LocalDate localDate) {
        this.localDate = localDate;
        yearSet = true;
        monthSet = true;
        daySet = true;
    }

    /**
     * Constructor.
     *
     * @param year  -9_999..9_999 based (i.e 4 digits), or {@code null} for none
     * @param month 1..12 based, or {@code null} or {@code 0} for none
     * @param day   1..31 based, or {@code null} or {@code 0} for none
     */
    public PartialDate(@Nullable @IntRange(from = -9_999, to = 9_999) final Integer year,
                       @Nullable @IntRange(from = 0, to = 12) final Integer month,
                       @Nullable @IntRange(from = 0, to = 31) final Integer day) {

        if (year == null) {
            unset();
        } else {
            try {
                if (month == null || month < 1) {
                    localDate = LocalDate.of(year, 1, 1);
                    yearSet = true;
                    monthSet = false;
                    daySet = false;
                } else if (day == null || day < 1) {
                    localDate = LocalDate.of(year, month, 1);
                    yearSet = true;
                    monthSet = true;
                    daySet = false;
                } else {
                    localDate = LocalDate.of(year, month, day);
                    yearSet = true;
                    monthSet = true;
                    daySet = true;
                }
            } catch (@NonNull final DateTimeException e) {
                unset();
            }
        }
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private PartialDate(@NonNull final Parcel in) {
        yearSet = in.readByte() != 0;
        final int year = in.readInt();

        monthSet = in.readByte() != 0;
        final int month = in.readInt();

        daySet = in.readByte() != 0;
        final int dayOfMonth = in.readInt();

        try {
            localDate = LocalDate.of(year, month, dayOfMonth);
        } catch (@NonNull final DateTimeException e) {
            // we should never get here... flw
            unset();
        }
    }

    /**
     * Format the year as either "YYYY" or "-YYYY" for negative years.
     *
     * @param year to format
     *
     * @return formatted string
     */
    @SuppressLint("DefaultLocale")
    @NonNull
    private static String formatYear(final int year) {
        if (year >= 0) {
            return String.format("%04d", year);
        } else {
            // allow for leading '-'
            return String.format("%05d", year);
        }
    }

    /**
     * Internal 'reset' to recover after parsing issues.
     */
    private void unset() {
        // 0001/01/01 is not actually used, as we set the booleans all to false,
        // but this way we don't have to take localDate == null into account
        localDate = LocalDate.of(1, 1, 1);
        yearSet = false;
        monthSet = false;
        daySet = false;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeByte((byte) (yearSet ? 1 : 0));
        dest.writeInt(localDate.getYear());

        dest.writeByte((byte) (monthSet ? 1 : 0));
        dest.writeInt(localDate.getMonthValue());

        dest.writeByte((byte) (daySet ? 1 : 0));
        dest.writeInt(localDate.getDayOfMonth());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Does the date have any fields set?
     * A PartialDate is considered to be present if at least the year is set.
     *
     * @return {@code true} if the date is present.
     */
    public boolean isPresent() {
        return yearSet;
    }

    /**
     * Format the date as {@code YYYY-MM-DD}, {@code YYYY-MM}, {@code YYYY} or an empty string
     * depending on the fields set.
     *
     * @return (partial) ISO string representation of the date.
     */
    @NonNull
    public String getIsoString() {
        return getIsoStringWithDelim("-");
    }

    /**
     * Using the given delimiter (instead of the iso '-' delimiter),
     * format the date as {@code YYYY-MM-DD}, {@code YYYY-MM}, {@code YYYY} or an empty string
     * depending on the fields set.
     *
     * @param delimiter to use
     *
     * @return (partial) string representation of the date.
     */
    @SuppressLint("DefaultLocale")
    @NonNull
    public String getIsoStringWithDelim(@NonNull final String delimiter) {
        final StringJoiner sj = new StringJoiner(delimiter);
        if (yearSet) {
            sj.add(formatYear(localDate.getYear()));
            if (monthSet) {
                sj.add(String.format("%02d", localDate.getMonthValue()));
                if (daySet) {
                    sj.add(String.format("%02d", localDate.getDayOfMonth()));
                }
            }
        }
        return sj.toString();
    }

    /**
     * Pretty format the date.
     * <ul>
     *     <li>Full date as per OS/User settings</li>
     *     <li>MMM yyyy / MMM -yyyy</li>
     *     <li>yyyy / -yyyy</li>
     * </ul>
     *
     * @param locale   to use
     * @param defValue default string to return if the date is not-set.
     *                 If {@code null} an empty string will be used.
     *
     * @return human readable date string.
     */
    @NonNull
    public String toDisplay(@NonNull final Locale locale,
                            @Nullable final String defValue) {
        if (yearSet && monthSet && daySet) {
            return localDate.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale));

        } else if (yearSet && monthSet) {
            return localDate.getMonth().getDisplayName(TextStyle.SHORT, locale)
                   + ' ' + formatYear(localDate.getYear());

        } else if (yearSet) {
            return formatYear(localDate.getYear());

        } else {
            return defValue != null ? defValue : "";
        }
    }

    /**
     * Get the year field.
     *
     * @return year value
     */
    @NonNull
    public Optional<Integer> getYear() {
        if (yearSet) {
            return Optional.of(localDate.getYear());
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    public Optional<Integer> getMonth() {
        if (monthSet) {
            return Optional.of(localDate.getMonthValue());
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    public Optional<Integer> getDay() {
        if (daySet) {
            return Optional.of(localDate.getDayOfMonth());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PartialDate that = (PartialDate) o;
        return yearSet == that.yearSet
               && monthSet == that.monthSet
               && daySet == that.daySet
               && localDate.equals(that.localDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localDate, yearSet, monthSet, daySet);
    }

    @Override
    @NonNull
    public String toString() {
        return "PartialDate{"
               + "localDate=" + localDate
               + ", yearSet=" + yearSet
               + ", monthSet=" + monthSet
               + ", daySet=" + daySet
               + '}';
    }
}
