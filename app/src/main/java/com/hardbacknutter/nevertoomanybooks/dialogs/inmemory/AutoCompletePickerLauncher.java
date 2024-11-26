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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

public class AutoCompletePickerLauncher
        extends DialogLauncher {

    private static final String TAG = "ACPickerLauncher";
    static final String BKEY_DIALOG_TITLE = TAG + ":title";
    static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    static final String BKEY_PREVIOUS_SELECTION = TAG + ":previous";
    static final String BKEY_CURRENT_SELECTION = TAG + ":selected";
    static final String BKEY_EXTRAS = TAG + ":extras";

    static final String BKEY_ITEM_LIST_TEXT = TAG + ":items-text";

    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";

    @Nullable
    private ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey FragmentResultListener request key to use for our response.
     */
    public AutoCompletePickerLauncher(@NonNull final String requestKey) {
        super(requestKey,
              AutoCompletePickerDialogFragment::new,
              AutoCompletePickerBottomSheet::new);
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment          the calling DialogFragment
     * @param requestKey        to use
     * @param previousSelection the selection as it was before the user (potentially)
     *                          made changes
     * @param selectedItem      item currently selected
     * @param extras            the optional Bundle as provided to
     *                          {@link #launch(Context, String, String, List, String, Bundle)}
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final String previousSelection,
                          @NonNull final String selectedItem,
                          @Nullable final Bundle extras) {
        final Bundle result = new Bundle(3);
        result.putString(BKEY_PREVIOUS_SELECTION, previousSelection);
        result.putString(BKEY_CURRENT_SELECTION, selectedItem);
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
     * @param dialogMessage optional message to display at the top of the dialog
     * @param allItems      list of all possible items
     * @param selectedItem  item currently selected
     * @param extras        optional Bundle which will be passed back to the result-listener.
     */
    public void launch(@NonNull final Context context,
                       @NonNull final String dialogTitle,
                       @Nullable final String dialogMessage,
                       @NonNull final List<String> allItems,
                       @NonNull final String selectedItem,
                       @Nullable final Bundle extras) {

        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Bundle args = new Bundle();
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);

        if (dialogMessage != null) {
            args.putString(BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        args.putStringArray(BKEY_ITEM_LIST_TEXT, allItems.toArray(String[]::new));
        args.putString(BKEY_CURRENT_SELECTION, selectedItem);

        if (extras != null && !extras.isEmpty()) {
            args.putBundle(BKEY_EXTRAS, extras);
        }
        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {

        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        resultListener.onResult(result.getString(BKEY_PREVIOUS_SELECTION, null),
                                result.getString(BKEY_CURRENT_SELECTION, null),
                                result.getBundle(BKEY_EXTRAS));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's selection.
         *
         * @param previousSelection the selection as it was before the user (potentially)
         *                          made changes
         * @param currentSelection  the selected/entered text
         * @param extras            the optional Bundle as provided to
         *                          {@link #launch(Context, String, String, List, String, Bundle)}
         */
        void onResult(@NonNull String previousSelection,
                      @NonNull String currentSelection,
                      @Nullable Bundle extras);
    }
}
