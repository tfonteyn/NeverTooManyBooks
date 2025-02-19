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

    private static final String TAG = "PDatePickerLauncher";
    static final String BKEY_DIALOG_TITLE = TAG + ":title";
    static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    static final String BKEY_EXTRAS = TAG + ":extras";

    /** A standard sql style (partial) date string, must/will be valid. */
    static final String BKEY_EDIT = TAG + ":edit";
    private static final String BKEY_ORIGINAL = TAG + ":original";

    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";

    @Nullable
    private ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey FragmentResultListener request key to use for our response.
     */
    public PartialDatePickerLauncher(@NonNull final String requestKey) {
        super(requestKey,
              PartialDatePickerDialogFragment::new,
              PartialDatePickerBottomSheet::new);
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment          the calling DialogFragment
     * @param requestKey        to use
     * @param previousSelection the previous selection/value
     * @param currentSelection  the new selection/value
     * @param extras            (optional) Bundle as provided to {@link #launch}
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final PartialDate previousSelection,
                          @NonNull final PartialDate currentSelection,
                          @Nullable final Bundle extras) {
        final Bundle result = new Bundle(3);
        result.putParcelable(BKEY_ORIGINAL, previousSelection);
        result.putParcelable(BKEY_EDIT, currentSelection);
        if (extras != null && !extras.isEmpty()) {
            result.putBundle(BKEY_EXTRAS, extras);
        }
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Set the results listener.
     *
     * @param resultListener to use
     */
    public void setResultListener(@NonNull final ResultListener resultListener) {
        this.resultListener = resultListener;
    }

    /**
     * Launch the dialog.
     *
     * @param context       preferably the {@code Activity}
     *                      but another UI {@code Context} will also do.
     * @param dialogTitle   the dialog title
     * @param dialogMessage (optional) message to display at the top of the dialog
     * @param edit          (optional) the value to edit
     * @param extras        (optional) Bundle which will be passed back to the result-listener.
     */
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final String dialogTitle,
                       @Nullable final String dialogMessage,
                       @Nullable final String edit,
                       @Nullable final Bundle extras) {

        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Bundle args = new Bundle();
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);
        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            args.putString(BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        if (edit != null) {
            args.putString(BKEY_EDIT, edit);
        }

        if (extras != null && !extras.isEmpty()) {
            args.putBundle(BKEY_EXTRAS, extras);
        }
        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        resultListener.onResult(
                Objects.requireNonNull(result.getParcelable(BKEY_ORIGINAL), BKEY_ORIGINAL),
                Objects.requireNonNull(result.getParcelable(BKEY_EDIT), BKEY_EDIT),
                result.getBundle(BKEY_EXTRAS));
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
