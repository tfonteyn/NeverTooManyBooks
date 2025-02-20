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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.partialdate;

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;

/**
 * We're handling the current value as separate year/month/day components instead
 * of the {@link PartialDate} they represent.
 * The latter is immutable; we don't want to keep recreating a new object all the time.
 * TODO: we don't want to keep recreating a new object all the time. Maybe we should...
 * <p>
 * All values for day/month start at {@code 1}.
 * A {@code value < 1} or {@code null} is seen as "not set".
 */
@SuppressWarnings("WeakerAccess")
public class PartialDatePickerViewModel
        extends ViewModel {

    /** Currently selected. */
    @Nullable
    private Integer year;
    /** Currently selected. */
    @IntRange(from = 1, to = YMD.MAX_MONTHS)
    @Nullable
    private Integer month;
    /** Currently selected. */
    @IntRange(from = 1, to = YMD.MAX_DAYS)
    @Nullable
    private Integer day;

    @NonNull
    private PartialDate previousSelection = PartialDate.NOT_SET;

    @Nullable
    private Bundle extras;

    private boolean initDone;

    void init(@NonNull final Bundle args) {
        if (!initDone) {
            initDone = true;

            final String dateString = args.getString(PartialDatePickerLauncher.BKEY_EDIT, null);

            final DateParser<PartialDate> partialDateParser = new PartialDateParser();
            previousSelection = partialDateParser.parse(dateString).orElse(PartialDate.NOT_SET);

            year = previousSelection.getYear().orElse(LocalDate.now().getYear());
            month = previousSelection.getMonthValue().orElse(null);
            day = previousSelection.getDayOfMonth().orElse(null);

            extras = args.getBundle(PartialDatePickerLauncher.BKEY_EXTRAS);
        }
    }

    @NonNull
    Optional<Integer> getYear() {
        return year != null ? Optional.of(year) : Optional.empty();
    }

    void setYear(@Nullable final Integer year) {
        this.year = year;
    }

    @IntRange(from = 1, to = YMD.MAX_MONTHS)
    @NonNull
    Optional<Integer> getMonthValue() {
        return month != null ? Optional.of(month) : Optional.empty();
    }

    void setMonthValue(@IntRange(from = 1, to = YMD.MAX_MONTHS) @Nullable final Integer month) {
        this.month = month;
    }

    @IntRange(from = 1, to = YMD.MAX_DAYS)
    @NonNull
    Optional<Integer> getDayOfMonth() {
        return day != null ? Optional.of(day) : Optional.empty();
    }

    void setDayOfMonth(@IntRange(from = 1, to = YMD.MAX_DAYS) @Nullable final Integer day) {
        this.day = day;
    }

    @NonNull
    Optional<Integer> getYMD(@NonNull final YMD ymd) {
        switch (ymd) {
            case Year:
                return getYear();
            case Month:
                return getMonthValue();
            case Day:
                return getDayOfMonth();
        }
        throw new IllegalArgumentException(ymd.toString());
    }

    void setYMD(@NonNull final YMD ymd,
                @Nullable final Integer value) {
        switch (ymd) {
            case Year:
                year = value;
                break;
            case Month:
                month = value;
                break;
            case Day:
                day = value;
                break;
        }
    }

    /**
     * Determine the total days if we have a valid month/year,
     * or {@link YMD#MAX_DAYS}.
     *
     * @return number of days
     */
    @IntRange(from = YMD.MIN_DAYS, to = YMD.MAX_DAYS)
    int getDaysInMonth() {
        int totalDays;
        if (getYear().isPresent() && getMonthValue().isPresent()) {
            try {
                // Should never throw here, but paranoia...
                totalDays = LocalDate.of(getYear().get(), getMonthValue().get(), 1)
                                     .lengthOfMonth();
            } catch (@NonNull final DateTimeException e) {
                totalDays = YMD.MAX_DAYS;
            }
        } else {
            // allow the user to start inputting with day first.
            totalDays = YMD.MAX_DAYS;
        }
        return totalDays;
    }

    @NonNull
    PartialDate getPreviousSelection() {
        return previousSelection;
    }

    /**
     * Create and get the output.
     * <p>
     * It will never be {@code null} but can be {@link PartialDate#NOT_SET}.
     *
     * @return current value
     */
    @NonNull
    PartialDate getCurrentSelection() {
        return new PartialDate(year, month, day);
    }

    @Nullable
    Bundle getExtras() {
        return extras;
    }
}
