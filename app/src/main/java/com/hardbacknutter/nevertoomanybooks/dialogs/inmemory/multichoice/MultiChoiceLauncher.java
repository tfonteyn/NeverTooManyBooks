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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.multichoice;

import android.content.Context;
import android.os.Bundle;

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

/**
 * Replacement for an AlertDialog with checkbox setup.
 *
 * @param <T> An {@link Entity}: used in the {@link #launch} method
 *            to prepare the lists of ids/labels for the choices
 *            and the list of preselected ids.
 */
public final class MultiChoiceLauncher<T extends Entity>
        extends DialogLauncher {

    private static final String TAG = "MultiChoiceLauncher";
    static final String BKEY_DIALOG_TITLE = TAG + ":title";
    static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    static final String BKEY_EXTRAS = TAG + ":extras";

    /** The list of strings to display in the dropdown. */
    static final String BKEY_ITEM_LIST_TEXT = TAG + ":items-text";
    /** The ids for the list of strings to display in the dropdown. */
    static final String BKEY_ITEM_LIST_ID = TAG + ":items-id";

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
        result.putLongArray(BKEY_ORIGINAL,
                            previousSelection.stream().mapToLong(o -> o).toArray());
        result.putLongArray(BKEY_EDIT,
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
     * @param context       preferably the {@code Activity}
     *                      but another UI {@code Context} will also do.
     * @param dialogTitle   the dialog title
     * @param dialogMessage (optional) message to display at the top of the dialog
     * @param allItems      list of all possible items
     * @param edit          (optional) list of items which are currently selected
     * @param extras        (optional) Bundle which will be passed back to the result-listener.
     */
    public void launch(@NonNull final Context context,
                       @NonNull final String dialogTitle,
                       @Nullable final String dialogMessage,
                       @NonNull final List<T> allItems,
                       @Nullable final List<T> edit,
                       @Nullable final Bundle extras) {

        Objects.requireNonNull(resultListener, ERROR_NULL_ON_EDIT_LISTENER);

        final Bundle args = new Bundle();
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);
        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            args.putString(BKEY_DIALOG_MESSAGE, dialogMessage);
        }

        // pass in id/labels as two arrays
        args.putLongArray(BKEY_ITEM_LIST_ID, allItems
                .stream().mapToLong(Entity::getId).toArray());
        args.putStringArray(BKEY_ITEM_LIST_TEXT, allItems
                .stream().map(item -> item.getLabel(context)).toArray(String[]::new));

        if (edit != null) {
            args.putLongArray(BKEY_EDIT, edit.stream().mapToLong(Entity::getId).toArray());
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

        final Set<Long> previousSelection =
                Arrays.stream(Objects.requireNonNull(result.getLongArray(BKEY_ORIGINAL),
                                                     BKEY_ORIGINAL))
                      .boxed()
                      .collect(Collectors.toSet());

        final Set<Long> currentSelection =
                Arrays.stream(Objects.requireNonNull(result.getLongArray(BKEY_EDIT),
                                                     BKEY_EDIT))
                      .boxed()
                      .collect(Collectors.toSet());

        resultListener.onResult(previousSelection,
                                currentSelection,
                                result.getBundle(BKEY_EXTRAS));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's selection.
         * <p>
         * Either selection can be empty when nothing was/is selected.
         *
         * @param previousSelection the previous selection/value
         * @param currentSelection  the new selection/value
         * @param extras            (optional) Bundle as provided to one of the
         *                          {@code Launcher#launch} methods
         */
        void onResult(@NonNull Set<Long> previousSelection,
                      @NonNull Set<Long> currentSelection,
                      @Nullable Bundle extras);
    }
}
