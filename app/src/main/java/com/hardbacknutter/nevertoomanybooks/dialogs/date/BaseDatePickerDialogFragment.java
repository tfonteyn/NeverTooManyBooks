/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.dialogs.date;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class BaseDatePickerDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "BaseDatePickerDialog";

    /** a standard sql style date string, must be correct. */
    static final String BKEY_DATE = TAG + ":date";

    /** or the date split into components, which can be partial. */
    private static final String SIS_YEAR = TAG + ":year";
    /** range: 0: 'not set' or 1..12. */
    private static final String SIS_MONTH = TAG + ":month";
    /** range: 0: 'not set' or 1..31. */
    private  static final String SIS_BKEY_DAY = TAG + ":day";

    /** Currently displayed; {@code 0} if empty/invalid. */
    int mYear;

    /**
     * Currently displayed; {@code 0} if invalid/empty.
     * <strong>IMPORTANT:</strong> 1..12 based. (the jdk internals expect 0..11).
     */
    int mMonth;

    /** Currently displayed; {@code 0} if empty/invalid. */
    int mDay;

    /**
     * Common setup for the pickers.
     *
     * @param savedInstanceState from #onCreateDialog
     */
    void setupDate(@Nullable final Bundle savedInstanceState) {

        final Bundle args = savedInstanceState != null ? savedInstanceState : requireArguments();
        if (args.containsKey(BKEY_DATE)) {
            // BKEY_DATE is only present in the original args
            parseDate(args.getString(BKEY_DATE));
        } else {
            // These are only present in the savedInstanceState
            mYear = args.getInt(SIS_YEAR);
            mMonth = args.getInt(SIS_MONTH);
            mDay = args.getInt(SIS_BKEY_DAY);
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
            outState.putInt(SIS_YEAR, mYear);
            outState.putInt(SIS_MONTH, mMonth);
            outState.putInt(SIS_BKEY_DAY, mDay);
    }

    /**
     * Parse the input ISO date string into the individual components.
     * <p>
     * Note we don't use {@link com.hardbacknutter.nevertoomanybooks.utils.DateParser}
     * as we the current implementation always returns full dates.
     * Here, we explicitly need to support partial dates.
     *
     * <ul>Allowed formats:
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
            mYear = 0;
            mMonth = 0;
            mDay = 0;
            return;
        }

        int yyyy = 0;
        int mm = 0;
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

        mYear = yyyy;
        mMonth = mm;
        mDay = dd;
    }
}
