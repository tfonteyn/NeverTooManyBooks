/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;

import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

/**
 * IMPORTANT: The <strong>input</strong> current-value/selection is a {@code String}.
 * The <strong>output</strong> for the same is a {@link PartialDate}.
 */
public class PartialDatePickerLauncher
        extends DialogLauncher {

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener to use
     */
    public PartialDatePickerLauncher(@NonNull final String requestKey,
                                     @NonNull final ResultListener resultListener) {
        super(requestKey,
              PartialDatePickerDialogFragment::new,
              PartialDatePickerBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Launch the dialog.
     *
     * @param context       preferably the {@code Activity}
     *                      but another UI {@code Context} will also do.
     * @param dialogTitle   the dialog title
     * @param dialogMessage (optional) message to display at the top of the dialog
     * @param selectedDate  (optional) the value to edit
     * @param extras        (optional) Bundle which will be passed back to the result-listener.
     */
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final String dialogTitle,
                       @Nullable final String dialogMessage,
                       @Nullable final String selectedDate,
                       @Nullable final Bundle extras) {

        final PartialDatePickerInput input = new PartialDatePickerInput(
                getRequestKey(), dialogTitle, dialogMessage, selectedDate, extras);
        showDialog(context, input.toBundle());
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final PartialDatePickerOutput output = PartialDatePickerOutput.fromBundle(result);
        resultListener.onResult(output.getOriginal(), output.getEdited(), output.getExtras());
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's selection.
         * <p>
         * Either values can be {@link PartialDate#NOT_SET}.
         *
         * @param previousValue the previous value
         * @param currentValue  the new value
         * @param extras        (optional) Bundle as provided to one of the
         *                      {@code Launcher#launch} methods
         */
        void onResult(@NonNull PartialDate previousValue,
                      @NonNull PartialDate currentValue,
                      @Nullable Bundle extras);
    }
}
