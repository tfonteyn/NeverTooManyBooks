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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

public final class MultiChoiceLauncher<T extends Parcelable & Entity>
        extends DialogLauncher {

    private static final String TAG = "MultiChoiceLauncher";
    static final String BKEY_DIALOG_TITLE = TAG + ":title";
    static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    static final String BKEY_EXTRAS = TAG + ":extras";

    /** The list of strings to display in the dropdown. */
    static final String BKEY_ITEM_LIST_TEXT = TAG + ":items-text";
    /** The ids for the list of strings to display in the dropdown. */
    static final String BKEY_ITEM_LIST_ID = TAG + ":items-id";

    static final String BKEY_CURRENT_SELECTION = TAG + ":current";
    private static final String BKEY_PREVIOUS_SELECTION = TAG + ":previous";

    private static final String ERROR_NULL_ON_EDIT_LISTENER = "onEditListener";

    @Nullable
    private ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey FragmentResultListener request key to use for our response.
     */
    public MultiChoiceLauncher(@NonNull final String requestKey) {
        super(requestKey,
              MultiChoiceDialogFragment::new,
              MultiChoiceBottomSheet::new);
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
                          @NonNull final Set<Long> previousSelection,
                          @NonNull final Set<Long> currentSelection,
                          @Nullable final Bundle extras) {
        final Bundle result = new Bundle(3);
        result.putLongArray(BKEY_PREVIOUS_SELECTION,
                            previousSelection.stream().mapToLong(o -> o).toArray());
        result.putLongArray(BKEY_CURRENT_SELECTION,
                            currentSelection.stream().mapToLong(o -> o).toArray());
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
     * @param dialogMessage    (optional) message to display at the top of the dialog
     * @param allItems         list of all possible items
     * @param currentSelection (optional) list of items which are currently selected
     * @param extras           (optional) Bundle which will be passed back to the result-listener.
     */
    public void launch(@NonNull final Context context,
                       @NonNull final String dialogTitle,
                       @Nullable final String dialogMessage,
                       @NonNull final List<T> allItems,
                       @NonNull final List<T> currentSelection,
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

        args.putLongArray(BKEY_CURRENT_SELECTION, currentSelection
                .stream().mapToLong(Entity::getId).toArray());

        if (extras != null && !extras.isEmpty()) {
            args.putBundle(BKEY_EXTRAS, extras);
        }
        showDialog(context, args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Set<Long> previousSelection = Arrays
                .stream(Objects.requireNonNull(result.getLongArray(BKEY_PREVIOUS_SELECTION),
                                               BKEY_PREVIOUS_SELECTION))
                .boxed()
                .collect(Collectors.toSet());

        final Set<Long> currentSelection = Arrays
                .stream(Objects.requireNonNull(result.getLongArray(BKEY_CURRENT_SELECTION),
                                               BKEY_CURRENT_SELECTION))
                .boxed()
                .collect(Collectors.toSet());

        resultListener.onResult(previousSelection, currentSelection, result.getBundle(BKEY_EXTRAS));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's selection.
         *
         * @param previousSelection the selection as it was before the user (potentially)
         *                          made changes
         * @param currentSelection  the set of <strong>checked</strong> items
         * @param extras            the optional Bundle as provided to
         *                          {@link #launch(Context, String, String, List, List, Bundle)}
         */
        void onResult(@NonNull Set<Long> previousSelection,
                      @NonNull Set<Long> currentSelection,
                      @Nullable Bundle extras);
    }
}
