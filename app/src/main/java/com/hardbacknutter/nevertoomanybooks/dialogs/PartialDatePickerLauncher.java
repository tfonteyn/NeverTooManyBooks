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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;

public class PartialDatePickerLauncher
        extends DialogLauncher {

    private static final String TAG = "PartialDatePickerLaunch";
    private static final String RK_DATE_PICKER_PARTIAL = TAG + ":rk:pd";

    /** a standard sql style date string, must be correct. */
    static final String BKEY_DATE = TAG + ":date";
    static final String BKEY_FIELD_ID = TAG + ":fieldId";
    static final String BKEY_DIALOG_TITLE_ID = TAG + ":titleId";

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param resultListener listener
     */
    public PartialDatePickerLauncher(@NonNull final ResultListener resultListener) {
        super(RK_DATE_PICKER_PARTIAL,
              PartialDatePickerDialogFragment::new,
              PartialDatePickerBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param fieldId    this destination field id
     * @param date       the picked date
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @IdRes final int fieldId,
                          @NonNull final PartialDate date) {
        final Bundle result = new Bundle(4);
        result.putInt(BKEY_FIELD_ID, fieldId);
        result.putParcelable(BKEY_DATE, date);

        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     * @param context          preferably the {@code Activity}
     *                         but another UI {@code Context} will also do.
     * @param dialogTitleId resource id for the dialog title
     * @param fieldId       this dialog operates on
     *                      (one launcher can serve multiple fields)
     * @param currentValue  the current value of the field
     * @param todayIfNone   {@code true} if we should use 'today' if the field was empty.
     */
    public void launch(@NonNull final Context context,
                       @StringRes final int dialogTitleId,
                       @IdRes final int fieldId,
                       @Nullable final String currentValue,
                       final boolean todayIfNone) {
        final String dateStr;
        if (todayIfNone && (currentValue == null || currentValue.isEmpty())) {
            dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } else {
            dateStr = Objects.requireNonNullElse(currentValue, "");
        }

        final Bundle args = new Bundle(4);
        args.putInt(BKEY_DIALOG_TITLE_ID, dialogTitleId);
        args.putInt(BKEY_FIELD_ID, fieldId);
        args.putString(BKEY_DATE, dateStr);

        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {

        resultListener.onResult(result.getInt(BKEY_FIELD_ID),
                                Objects.requireNonNull(
                                        result.getParcelable(BKEY_DATE),
                                        BKEY_DATE));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's selection.
         *
         * @param fieldId this destination field id
         * @param date    the picked date
         */
        void onResult(@IdRes int fieldId,
                      @NonNull PartialDate date);
    }
}
