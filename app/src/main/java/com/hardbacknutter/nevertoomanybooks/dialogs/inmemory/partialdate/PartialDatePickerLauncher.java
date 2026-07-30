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
import androidx.fragment.app.Fragment;

import java.util.Objects;

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
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment   the calling DialogFragment
     * @param requestKey to use
     * @param output     result
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final Output output) {
        fragment.getParentFragmentManager().setFragmentResult(requestKey, output.toBundle());
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
        final Output output = Output.fromBundle(result);
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

    static class Output {
        private static final String TAG = "Output";
        private static final String BKEY_ORIGINAL = TAG + ":original";
        private static final String BKEY_EDIT = TAG + ":edit";
        private static final String BKEY_EXTRAS = TAG + ":extras";

        @NonNull
        private final PartialDate original;
        @NonNull
        private final PartialDate edited;
        @Nullable
        private final Bundle extras;

        /**
         * Constructor.
         *
         * @param original the previous value
         * @param edited   the new value
         * @param extras   (optional) Bundle provided as input
         */
        Output(@NonNull final PartialDate original,
               @NonNull final PartialDate edited,
               @Nullable final Bundle extras) {
            this.original = original;
            this.edited = edited;
            this.extras = extras;
        }

        @SuppressWarnings("deprecation")
        @NonNull
        static Output fromBundle(@NonNull final Bundle result) {
            final PartialDate previousValue = Objects.requireNonNull(
                    result.getParcelable(BKEY_ORIGINAL), BKEY_ORIGINAL);
            final PartialDate currentValue = Objects.requireNonNull(
                    result.getParcelable(BKEY_EDIT), BKEY_EDIT);
            final Bundle extras = result.getBundle(BKEY_EXTRAS);

            return new Output(previousValue, currentValue, extras);
        }

        @NonNull
        Bundle toBundle() {
            final Bundle result = new Bundle(3);
            result.putParcelable(BKEY_ORIGINAL, original);
            result.putParcelable(BKEY_EDIT, edited);
            if (extras != null && !extras.isEmpty()) {
                result.putBundle(BKEY_EXTRAS, extras);
            }

            return result;
        }

        @NonNull
        PartialDate getOriginal() {
            return original;
        }

        @NonNull
        PartialDate getEdited() {
            return edited;
        }

        @Nullable
        Bundle getExtras() {
            return extras;
        }
    }
}
