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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.multichoice;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;

import java.util.List;
import java.util.Set;

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

    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener to use
     */
    public MultiChoiceLauncher(@NonNull final String requestKey,
                               @NonNull final ResultListener resultListener) {
        super(requestKey,
              MultiChoiceDialogFragment::new,
              MultiChoiceBottomSheet::new);
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
    public void launch(@NonNull @UiContext final Context context,
                       @NonNull final String dialogTitle,
                       @Nullable final String dialogMessage,
                       @NonNull final List<T> allItems,
                       @Nullable final List<T> edit,
                       @Nullable final Bundle extras) {

        final MultiChoiceInput input = new MultiChoiceInput(
                context, getRequestKey(), dialogTitle, dialogMessage, allItems, edit, extras);
        showDialog(context, input.toBundle());
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        final MultiChoiceOutput output = MultiChoiceOutput.fromBundle(result);
        resultListener.onResult(output.getOriginal(), output.getEdited(), output.getExtras());
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
