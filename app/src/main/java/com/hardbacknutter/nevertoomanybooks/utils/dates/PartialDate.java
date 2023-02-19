/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils.dates;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;

/**
 * TEST: <a href="https://issuetracker.google.com/issues/158417777">DateTimeParseException</a>
 * seems to be fixed, but the bug was never closed?
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
    /** Log tag. */
    private static final String TAG = "PartialDate";
    /** NonNull - the partial date; using '1' for not-set day,month,year fields. */
    private LocalDate localDate;
    private boolean yearSet;
    private boolean monthSet;
    private boolean daySet;

    /**
     * Constructor.
     * <p>
     * Passing in an invalid ISO string will not throw any error, but the date will simply
     * be marked invalid / not-set.
     *
     * @param dateStr a valid ISO string (full or partial date), or {@code null}, or {@code ""}
     */
    public PartialDate(@Nullable final String dateStr) {
        parse(dateStr);
    }

    /**
     * Constructor.
     *
     * @param year  1..999_999_999 based, or {@code 0} for none (dev: inlined from Year.MAX_VALUE)
     * @param month 1..12 based, or {@code 0} for none
     * @param day   1..31 based, or {@code 0} for none
     */
    public PartialDate(@IntRange(from = 0, to = 999_999_999) final int year,
                       @IntRange(from = 0, to = 12) final int month,
                       @IntRange(from = 0, to = 31) final int day) {
        if (year < 1) {
            unset();
        } else {
            try {
                if (month < 1) {
                    localDate = LocalDate.of(year, 1, 1);
                    yearSet = true;
                    monthSet = false;
                    daySet = false;
                } else if (day < 1) {
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

    private void unset() {
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

    private void parse(@Nullable final String dateStr) {
        if (dateStr != null) {
            final int len = dateStr.length();
            try {
                if (len == 4) {
                    // yyyy
                    localDate = Year.parse(dateStr).atDay(1);
                    yearSet = true;
                    monthSet = false;
                    daySet = false;
                    return;
                } else if (len == 7) {
                    // yyyy-MM
                    localDate = YearMonth.parse(dateStr).atDay(1);
                    yearSet = true;
                    monthSet = true;
                    daySet = false;
                    return;
                } else if (len >= 10) {
                    // yyyy-MM-dd[...]
                    localDate = LocalDate.parse(dateStr.substring(0, 10));
                    yearSet = true;
                    monthSet = true;
                    daySet = true;
                    return;
                }
            } catch (@NonNull final DateTimeParseException e) {
                if (BuildConfig.DEBUG /* always */) {
                    ServiceLocator.getInstance().getLogger().d(TAG, e, "dateStr=" + dateStr);
                }
            }
        }

        unset();
    }

    @NonNull
    public LocalDate getDate() {
        return localDate;
    }

    public void setDate(@Nullable final String dateStr) {
        parse(dateStr);
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
     * If a value is present, invoke the specified consumer with the value,
     * otherwise do nothing.
     *
     * @param consumer block to be executed if a value is present
     */
    public void ifPresent(@NonNull final Consumer<PartialDate> consumer) {
        if (yearSet) {
            consumer.accept(this);
        }
    }

    /**
     * Format the date as 'YYYY-MM-DD', or 'YYYY-MM' or 'YYYY' or ''
     * depending on the fields set.
     *
     * @return partial ISO string representation of the date.
     */
    @SuppressLint("DefaultLocale")
    @NonNull
    public String getIsoString() {
        final StringJoiner sj = new StringJoiner("-");
        if (yearSet) {
            sj.add(String.format("%04d", localDate.getYear()));
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
     *
     * @param locale   to use
     * @param defValue default string to return if the date is not-set.
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
                   + ' ' + String.format(locale, "%04d", localDate.getYear());

        } else if (yearSet) {
            return String.format(locale, "%04d", localDate.getYear());

        } else {
            return defValue != null ? defValue : "";
        }
    }

    /**
     * Get the year field. Will be {@code 1} if the field was not set.
     * A call to {@link #isPresent()} ()} should be made before.
     *
     * @return year value
     */
    public int getYearValue() {
        return localDate.getYear();
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
