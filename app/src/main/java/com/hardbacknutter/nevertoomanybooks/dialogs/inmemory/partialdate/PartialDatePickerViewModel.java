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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import java.time.LocalDate;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;

/**
 * We're handling the current value as separate year/month/day components instead
 * of the {@link PartialDate} they represent.
 * The latter is immutable; we don't want to keep recreating a new object all the time.
 */
@SuppressWarnings("WeakerAccess")
public class PartialDatePickerViewModel
        extends ViewModel {

    /** Currently displayed. */
    @Nullable
    private Integer year;
    /**
     * Currently displayed.
     * <strong>IMPORTANT:</strong> 1..12 based. (the jdk internals expect 0..11).
     */
    @Nullable
    private Integer month;
    /** Currently displayed. */
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

            final PartialDateParser partialDateParser = new PartialDateParser();
            previousSelection = partialDateParser.parse(dateString).orElse(PartialDate.NOT_SET);

            year = previousSelection.getYear().orElse(LocalDate.now().getYear());
            month = previousSelection.getMonth().orElse(null);
            day = previousSelection.getDay().orElse(null);

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

    @NonNull
    Optional<Integer> getMonth() {
        return month != null ? Optional.of(month) : Optional.empty();
    }

    void setMonth(@Nullable final Integer month) {
        this.month = month;
    }

    @NonNull
    Optional<Integer> getDay() {
        return day != null ? Optional.of(day) : Optional.empty();
    }

    void setDay(@Nullable final Integer day) {
        this.day = day;
    }

    @NonNull
    Optional<Integer> getYMD(@NonNull final YMD ymd) {
        switch (ymd) {
            case Year:
                return getYear();
            case Month:
                return getMonth();
            case Day:
                return getDay();
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
