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

package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.entities.Entity;

public class MultiChoiceLauncher<T extends Parcelable & Entity>
        extends DialogLauncher {

    private static final String TAG = "MultiChoiceLauncher";
    static final String BKEY_DIALOG_TITLE = TAG + ":title";
    static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";
    static final String BKEY_SELECTED = TAG + ":selected";
    static final String BKEY_ALL_IDS = TAG + ":ids";
    static final String BKEY_ALL_LABELS = TAG + ":labels";
    @NonNull
    private final ResultListener resultListener;

    /**
     * Constructor.
     *
     * @param uiContext      Current context - this <strong>MUST</strong> be a UI context
     * @param requestKey     FragmentResultListener request key to use for our response.
     * @param resultListener listener
     */
    public MultiChoiceLauncher(@NonNull final Context uiContext,
                               @NonNull final String requestKey,
                               @NonNull final ResultListener resultListener) {
        super(uiContext, requestKey);
        this.resultListener = resultListener;
    }

    /**
     * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
     *
     * @param fragment      the calling DialogFragment
     * @param requestKey    to use
     * @param selectedItems the set of <strong>checked</strong> items
     *
     * @see #onFragmentResult(String, Bundle)
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static void setResult(@NonNull final Fragment fragment,
                          @NonNull final String requestKey,
                          @NonNull final Set<Long> selectedItems) {
        final Bundle result = new Bundle(1);
        result.putLongArray(BKEY_SELECTED, selectedItems.stream().mapToLong(o -> o).toArray());
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    /**
     * Launch the dialog.
     *
     * @param context       Current context
     * @param dialogTitle   the dialog title
     * @param allItems      list of all possible items
     * @param selectedItems list of item which are currently selected
     */
    public void launch(@NonNull final Context context,
                       @NonNull final String dialogTitle,
                       @NonNull final List<T> allItems,
                       @NonNull final List<T> selectedItems) {

        final Bundle args = new Bundle(5);
        args.putString(BKEY_DIALOG_TITLE, dialogTitle);

        args.putLongArray(BKEY_ALL_IDS, allItems
                .stream().mapToLong(Entity::getId).toArray());
        args.putStringArray(BKEY_ALL_LABELS, allItems
                .stream().map(item -> item.getLabel(context)).toArray(String[]::new));

        args.putLongArray(BKEY_SELECTED, selectedItems
                .stream().mapToLong(Entity::getId).toArray());

        createDialog(args);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        resultListener.onResult(
                Arrays.stream(Objects.requireNonNull(result.getLongArray(BKEY_SELECTED),
                                                     BKEY_SELECTED))
                      .boxed().collect(Collectors.toSet()));
    }

    @FunctionalInterface
    public interface ResultListener {
        /**
         * Callback handler with the user's selection.
         *
         * @param selectedItems the set of <strong>checked</strong> items
         */
        void onResult(@NonNull Set<Long> selectedItems);
    }
}
