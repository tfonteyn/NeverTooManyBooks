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
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

public class BaseDatePickerDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "BaseDatePickerDialog";

    /** a standard sql style date string, must be correct. */
    static final String BKEY_DATE = TAG + ":date";
    /** or the date split into components, which can be partial. */
    @SuppressWarnings("WeakerAccess")
    static final String BKEY_YEAR = TAG + ":year";
    /** range: 0: 'not set' or 1..12. */
    @SuppressWarnings("WeakerAccess")
    static final String BKEY_MONTH = TAG + ":month";
    /** range: 0: 'not set' or 1..31. */
    @SuppressWarnings("WeakerAccess")
    static final String BKEY_DAY = TAG + ":day";

    /** Currently displayed; {@code null} if empty/invalid. */
    @Nullable
    Integer mYear;

    /**
     * Currently displayed; {@code null} or {@code 0} if invalid/empty.
     * <strong>IMPORTANT:</strong> 1..12 based. (the jdk internals expect 0..11).
     */
    @Nullable
    Integer mMonth;

    /** Currently displayed; {@code null} if empty/invalid. */
    @Nullable
    Integer mDay;

    /** Where to send the result. */
    @Nullable
    private WeakReference<DatePickerResultsListener> mListener;

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
            mYear = args.getInt(BKEY_YEAR);
            mMonth = args.getInt(BKEY_MONTH);
            mDay = args.getInt(BKEY_DAY);
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mYear != null) {
            outState.putInt(BKEY_YEAR, mYear);
        }
        if (mMonth != null) {
            outState.putInt(BKEY_MONTH, mMonth);
        }
        if (mDay != null) {
            outState.putInt(BKEY_DAY, mDay);
        }
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
            mYear = null;
            mMonth = null;
            mDay = null;
            return;
        }

        Integer yyyy = null;
        Integer mm = null;
        Integer dd = null;
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

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final DatePickerResultsListener listener) {
        mListener = new WeakReference<>(listener);
    }

    /**
     * Send the date back to the listener.
     *
     * @param month 1..12 based (or null for no month)
     */
    void send(@Nullable final Integer year,
              @Nullable final Integer month,
              @Nullable final Integer day) {
        dismiss();

        if (mListener != null && mListener.get() != null) {
            mListener.get().onDateSet(year, month, day);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "send|" +
                           (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                              : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
    }
}
