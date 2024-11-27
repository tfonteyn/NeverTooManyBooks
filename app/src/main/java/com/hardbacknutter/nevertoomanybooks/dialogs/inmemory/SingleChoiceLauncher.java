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
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

public class SingleChoiceLauncher<T extends Parcelable & Entity>
        extends DialogLauncher {

    private static final String TAG = "SingleChoiceLauncher";
    static final String BKEY_DIALOG_TITLE = TAG + ":title";
    static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    static final String BKEY_PREVIOUS_SELECTION = TAG + ":previous";
    static final String BKEY_CURRENT_SELECTION = TAG + ":selected";
    static final String BKEY_EXTRAS = TAG + ":extras";

    static final String BKEY_ITEM_LIST_ID = TAG + ":items-id";
    static final String BKEY_ITEM_LIST_TEXT = TAG + ":items-text";

    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";

    @Nullable
    private ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey FragmentResultListener request key to use for our response.
     */
    public SingleChoiceLauncher(@NonNull final String requestKey) {
        super(requestKey,
              SingleChoiceDialogFragment::new,
              SingleChoiceBottomSheet::new);
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment          the calling DialogFragment
     * @param requestKey        to use
     * @param previousSelection the selection as it was before the user (potentially)
     *                          made changes
     *                          can be {@code null} for none selected.
     * @param currentSelection  the <strong>checked</strong> item,
     *                          can be {@code null} for none selected.
     * @param extras            the optional Bundle as provided to
     *                          {@link #launch(Context, String, String, List, Parcelable, Bundle)}
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @Nullable final Long previousSelection,
                          @Nullable final Long currentSelection,
                          @Nullable final Bundle extras) {
        final Bundle result = new Bundle(3);
        if (previousSelection != null) {
            result.putLong(BKEY_PREVIOUS_SELECTION, previousSelection);
        }
        if (currentSelection != null) {
            result.putLong(BKEY_CURRENT_SELECTION, currentSelection);
        }
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
     * @param context          preferably the {@code Activity}
     *                         but another UI {@code Context} will also do.
     * @param dialogTitle      the dialog title
     * @param dialogMessage    optional message to display at the top of the dialog
     * @param allItems         list of all possible items
     * @param currentSelection item which is currently selected; can be {@code null} for none
     * @param extras           optional Bundle which will be passed back to the result-listener.
     */
    public void launch(@NonNull final Context context,
                       @NonNull final String dialogTitle,
                       @Nullable final String dialogMessage,
                       @NonNull final List<T> allItems,
                       @Nullable final T currentSelection,
                       @Nullable final Bundle extras) {

        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Bundle args = new Bundle();
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);

        if (dialogMessage != null) {
            args.putString(BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        args.putLongArray(BKEY_ITEM_LIST_ID, allItems
                .stream().mapToLong(Entity::getId).toArray());
        args.putStringArray(BKEY_ITEM_LIST_TEXT, allItems
                .stream().map(item -> item.getLabel(context)).toArray(String[]::new));

        if (currentSelection != null) {
            args.putLong(BKEY_CURRENT_SELECTION, currentSelection.getId());
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

        final long previousSelection = result.getLong(BKEY_PREVIOUS_SELECTION, -1);
        final long currentSelection = result.getLong(BKEY_CURRENT_SELECTION, -1);

        resultListener.onResult(
                previousSelection == -1 ? null : previousSelection,
                currentSelection == -1 ? null : currentSelection,
                result.getBundle(BKEY_EXTRAS));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's selection.
         *
         * @param previousSelection the selection as it was before the user (potentially)
         *                          made changes
         *                          can be {@code null} for none.
         * @param currentSelection  the <strong>checked</strong> item,
         *                          can be {@code null} for none.
         * @param extras            the optional Bundle as provided to
         *                          {@link #launch(Context, String, String, List, Parcelable, Bundle)}
         */
        void onResult(@Nullable Long previousSelection,
                      @Nullable Long currentSelection,
                      @Nullable Bundle extras);
    }
}
