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
package com.hardbacknutter.nevertoomanybooks.dialogs.picker;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;

public class BaseDatePickerDialogFragment
        extends DialogFragment {

    public static final String TAG = "BaseDatePickerDialog";

    /** a standard sql style date string, must be correct. */
    static final String BKEY_DATE = TAG + ":date";
    /** or the date split into components, which can be partial. */
    @SuppressWarnings("WeakerAccess")
    static final String BKEY_YEAR = TAG + ":year";
    /** range: 1..12. */
    @SuppressWarnings("WeakerAccess")
    static final String BKEY_MONTH = TAG + ":month";
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

    /** Listener for the result. */
    private WeakReference<DatePickerResultsListener> mListener;

    /** identifier of the field this dialog is bound to. */
    @IdRes
    private int mDestinationFieldId;

    /**
     * Common setup for the pickers.
     *
     * @param savedInstanceState from #onCreateDialog
     */
    void baseSetup(@Nullable final Bundle savedInstanceState) {

        Bundle args = requireArguments();
        mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);

        args = savedInstanceState != null ? savedInstanceState : args;
        if (args.containsKey(BKEY_DATE)) {
            // BKEY_DATE is only present in the original args
            setDate(args.getString(BKEY_DATE));
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
     * Private helper, NOT a public accessor.
     * <ul>Allows partial dates:
     * <li>yyyy-mm-dd time</li>
     * <li>yyyy-mm-dd</li>
     * <li>yyyy-mm</li>
     * <li>yyyy</li>
     * </ul>
     *
     * @param dateString SQL formatted (partial) date, can be {@code null}.
     */
    private void setDate(@Nullable final String dateString) {
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
            String[] dateAndTime = dateString.split(" ");
            String[] date = dateAndTime[0].split("-");
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

        if (mListener.get() != null) {
            String date = DateUtils.buildPartialDate(year, month, day);
            mListener.get().onDateSet(mDestinationFieldId, date);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Log.d(TAG, "send|" + ErrorMsg.WEAK_REFERENCE);
            }
        }
    }
}
