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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.autocomplete;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

public class AutoCompletePickerLauncher
        extends DialogLauncher {

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener to use
     */
    public AutoCompletePickerLauncher(@NonNull final String requestKey,
                                      @NonNull final ResultListener resultListener) {
        super(requestKey,
              AutoCompletePickerDialogFragment::new,
              AutoCompletePickerBottomSheet::new);
        this.resultListener = resultListener;
    }

    /**
     * Launch the dialog.
     *
     * @param context          preferably the {@code Activity}
     *                         but another UI {@code Context} will also do.
     * @param dialogTitle      the dialog title
     * @param dialogMessage    (optional) message to display at the top of the dialog
     * @param allItems         list of all possible items
     * @param currentSelection (optional) the current value of the field
     * @param extras           (optional) Bundle which will be passed back to the result-listener.
     */
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final String dialogTitle,
                       @Nullable final String dialogMessage,
                       @NonNull final List<String> allItems,
                       @Nullable final String currentSelection,
                       @Nullable final Bundle extras) {

        final AutoCompletePickerInput input = new AutoCompletePickerInput(
                getRequestKey(), dialogTitle, dialogMessage, allItems, currentSelection, extras);
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
         *
         * @param previousSelection the previous selection/value
         * @param currentSelection  the new selection/value
         * @param extras            (optional) Bundle as provided to one of the
         *                          {@code Launcher#launch} methods
         */
        void onResult(@Nullable String previousSelection,
                      @NonNull String currentSelection,
                      @Nullable Bundle extras);
    }

    static class Output
            implements LauncherOutput {

        private static final String TAG = "Output";
        private static final String BKEY_ORIGINAL = TAG + ":original";
        private static final String BKEY_EDIT = TAG + ":edit";
        private static final String BKEY_EXTRAS = TAG + ":extras";

        @Nullable
        private final String original;
        @NonNull
        private final String edited;
        @Nullable
        private final Bundle extras;

        /**
         * Constructor.
         *
         * @param original the previous value
         * @param edited   the new value
         * @param extras   (optional) Bundle provided as input
         */
        Output(@Nullable final String original,
               @NonNull final String edited,
               @Nullable final Bundle extras) {
            this.original = original;
            this.edited = edited;
            this.extras = extras;
        }

        @NonNull
        static Output fromBundle(@NonNull final Bundle args) {
            final String previousValue = args.getString(BKEY_ORIGINAL);
            final String currentValue = Objects.requireNonNull(
                    args.getString(BKEY_EDIT), BKEY_EDIT);
            final Bundle extras = args.getBundle(BKEY_EXTRAS);

            return new Output(previousValue, currentValue, extras);
        }

        @NonNull
        public Bundle toBundle() {
            final Bundle result = new Bundle(3);
            if (original != null && !original.isBlank()) {
                result.putString(BKEY_ORIGINAL, original);
            }
            result.putString(BKEY_EDIT, edited);
            if (extras != null && !extras.isEmpty()) {
                result.putBundle(BKEY_EXTRAS, extras);
            }

            return result;
        }

        @Nullable
        String getOriginal() {
            return original;
        }

        @NonNull
        String getEdited() {
            return edited;
        }

        @Nullable
        Bundle getExtras() {
            return extras;
        }
    }
}
