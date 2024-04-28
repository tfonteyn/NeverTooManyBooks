/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModel;

import java.time.LocalDate;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;

public class PartialDatePickerViewModel
        extends ViewModel {

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    @StringRes
    private int dialogTitleId;

    @IdRes
    private int fieldId;

    /** Currently displayed; {@code 0} if empty/invalid. */
    private int year;
    /**
     * Currently displayed; {@code 0} if invalid/empty.
     * <strong>IMPORTANT:</strong> 1..12 based. (the jdk internals expect 0..11).
     */
    private int month;
    /** Currently displayed; {@code 0} if empty/invalid. */
    private int day;


    public void init(@NonNull final Bundle args) {
        if (requestKey == null) {
            requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                                DialogLauncher.BKEY_REQUEST_KEY);
            dialogTitleId = args.getInt(PartialDatePickerLauncher.BKEY_DIALOG_TITLE_ID,
                                        R.string.action_edit);
            fieldId = args.getInt(PartialDatePickerLauncher.BKEY_FIELD_ID);
            parseDate(args.getString(PartialDatePickerLauncher.BKEY_DATE));
        }

        // can't have a 0 year. (but month/day can be 0)
        // The user can/should use the "clear" button if they want no date at all.
        if (year == 0) {
            year = LocalDate.now().getYear();
        }
    }

    @NonNull
    public String getRequestKey() {
        return requestKey;
    }

    public int getFieldId() {
        return fieldId;
    }

    public int getDialogTitleId() {
        return dialogTitleId;
    }

    public int getYear() {
        return year;
    }

    public void setYear(final int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(final int month) {
        this.month = month;
    }

    public int getDay() {
        return day;
    }

    public void setDay(final int day) {
        this.day = day;
    }

    /**
     * Parse the input ISO date string into the individual components.
     * <p>
     * Note we don't use {@link FullDateParser}
     * as we the current implementation always returns full dates.
     * Here, we explicitly need to support partial dates.
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
            year = 0;
            month = 0;
            day = 0;
            return;
        }

        int yyyy = 0;
        int mm = 0;
        //noinspection QuestionableName
        int dd = 0;
        try {
            final String[] dateAndTime = dateString.split(" ");
            final String[] date = dateAndTime[0].split("-");
            yyyy = Integer.parseInt(date[0]);
            if (date.length > 1) {
                mm = Integer.parseInt(date[1]);
            }
            if (date.length > 2) {
                dd = Integer.parseInt(date[2]);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore. Any values we did get, are used.
        }

        year = yyyy;
        month = mm;
        day = dd;
    }
}
