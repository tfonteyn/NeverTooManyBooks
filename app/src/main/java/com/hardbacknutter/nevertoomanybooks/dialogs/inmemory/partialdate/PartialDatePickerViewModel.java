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

import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;

/**
 * We're handling the current value as separate year/month/day components instead
 * of the {@link com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate} they represent.
 * The latter is immutable; we don't want to keep recreating a new object all the time.
 */
@SuppressWarnings("WeakerAccess")
public class PartialDatePickerViewModel
        extends ViewModel {

    /** Currently displayed; {@code 0} if empty/invalid. */
    private int year;
    /**
     * Currently displayed; {@code 0} if invalid/empty.
     * <strong>IMPORTANT:</strong> 1..12 based. (the jdk internals expect 0..11).
     */
    private int month;
    /** Currently displayed; {@code 0} if empty/invalid. */
    private int day;

    @NonNull
    private PartialDate previousSelection = PartialDate.NOT_SET;

    @Nullable
    private Bundle extras;

    private boolean initDone;

    void init(@NonNull final Bundle args) {
        if (!initDone) {
            initDone = true;
            // parsing sets both previousSelection and currentSelection.
            // The latter as individual year/mont/day components
            parseDate(args.getString(PartialDatePickerLauncher.BKEY_EDIT, null));

            extras = args.getBundle(PartialDatePickerLauncher.BKEY_EXTRAS);
        }
    }

    int getYear() {
        return year;
    }

    void setYear(final int year) {
        this.year = year;
    }

    int getMonth() {
        return month;
    }

    void setMonth(final int month) {
        this.month = month;
    }

    int getDay() {
        return day;
    }

    void setDay(final int day) {
        this.day = day;
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

    /**
     * Parse the input ISO date string into the individual components.
     * If the parsed year is {@code 0} it will be substituted with the current year as we
     * can't have a 0 year. (but month/day can be 0)
     * The user can/should use the "clear" button if they want no date at all.
     * <p>
     * TODO: Note we don't use {@link PartialDateParser}... maybe we should...
     * <p>
     * Allowed formats:
     * <ul>
     *      <li>yyyy-mm-dd time</li>
     *      <li>yyyy-mm-dd</li>
     *      <li>yyyy-mm</li>
     *      <li>yyyy</li>
     * </ul>
     *
     * @param dateString SQL formatted (partial) date, can be {@code null}.
     */
    private void parseDate(@Nullable final String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            // currentSelection
            year = LocalDate.now().getYear();
            month = 0;
            day = 0;

            previousSelection = PartialDate.NOT_SET;
            return;
        }

        int tmpYear = 0;
        int tmpMonth = 0;
        int tmpDay = 0;
        try {
            final String[] dateAndTime = dateString.split(" ");
            final String[] date = dateAndTime[0].split("-");

            tmpYear = Integer.parseInt(date[0]);

            if (date.length > 1) {
                tmpMonth = Integer.parseInt(date[1]);
            }
            if (date.length > 2) {
                tmpDay = Integer.parseInt(date[2]);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore. Any values we did get, are used.
        }

        // currentSelection as components
        if (tmpYear == 0) {
            year = LocalDate.now().getYear();
        } else {
            year = tmpYear;
        }
        month = tmpMonth;
        day = tmpDay;

        previousSelection = new PartialDate(year, month, day);
    }
}
