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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

public class EditStringLauncher
        extends DialogLauncher {

    private static final String TAG = "EditStringLauncher";
    static final String BKEY_DIALOG_TITLE = TAG + ":title";
    static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    static final String BKEY_EXTRAS = TAG + ":extras";

    static final String BKEY_CURRENT = TAG + ":current";
    private static final String BKEY_PREVIOUS = TAG + ":previous";

    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";

    @Nullable
    private ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey FragmentResultListener request key to use for our response.
     */
    public EditStringLauncher(@NonNull final String requestKey) {
        super(requestKey,
              EditStringDialogFragment::new,
              EditStringBottomSheet::new);
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment      the calling DialogFragment
     * @param requestKey    to use
     * @param previousValue the previous value
     * @param currentValue  the new value
     * @param extras        (optional) Bundle as provided to {@link #launch}
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final String previousValue,
                          @NonNull final String currentValue,
                          @Nullable final Bundle extras) {
        final Bundle result = new Bundle(3);
        result.putString(BKEY_PREVIOUS, previousValue);
        result.putString(BKEY_CURRENT, currentValue);
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
     * @param currentValue  (optional) the current value of the field
     * @param extras        (optional) Bundle which will be passed back to the result-listener.
     */
    public void launch(@NonNull final Context context,
                       @NonNull final String dialogTitle,
                       @Nullable final String dialogMessage,
                       @Nullable final String currentValue,
                       @Nullable final Bundle extras) {

        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Bundle args = new Bundle();
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);
        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            args.putString(BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        // be consistent: don't pass null, DO pass empty (i.e. PartialDate#NOT_SET)
        if (currentValue != null) {
            args.putString(BKEY_CURRENT, currentValue);
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
                Objects.requireNonNull(result.getString(BKEY_PREVIOUS), BKEY_PREVIOUS),
                Objects.requireNonNull(result.getString(BKEY_CURRENT), BKEY_CURRENT),
                result.getBundle(BKEY_EXTRAS));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's entry.
         *
         * @param previousValue the previous value
         * @param currentValue  the new value
         * @param extras        (optional) Bundle as provided to one of the
         *                      {@code Launcher#launch} methods
         */
        void onResult(@NonNull String previousValue,
                      @NonNull String currentValue,
                      @Nullable Bundle extras);
    }
}
