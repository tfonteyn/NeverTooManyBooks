/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * Replacement for the AlertDialog with checkbox setup.
 */
public class MultiChoiceDialogFragment
        extends DialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "MultiChoiceDialogFragment";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    private static final String BKEY_DIALOG_TITLE = TAG + ":title";
    private static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    /** Argument. */
    private static final String BKEY_ALL_IDS = TAG + ":ids";
    private static final String BKEY_ALL_LABELS = TAG + ":labels";
    /** Argument. */
    private static final String BKEY_SELECTED = TAG + ":selected";
    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    @Nullable
    private String dialogTitle;
    @Nullable
    private String dialogMessage;

    /** The list of items to display. */
    private List<Long> itemIds;
    private List<String> itemLabels;
    /** The selected items. */
    private Set<Long> selectedItems;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        dialogTitle = args.getString(BKEY_DIALOG_TITLE, getString(R.string.action_edit));
        dialogMessage = args.getString(BKEY_DIALOG_MESSAGE, null);

        itemIds = Arrays.stream(Objects.requireNonNull(
                                args.getLongArray(BKEY_ALL_IDS), BKEY_ALL_IDS))
                        .boxed().collect(Collectors.toList());
        itemLabels = Arrays.stream(Objects.requireNonNull(
                                   args.getStringArray(BKEY_ALL_LABELS), BKEY_ALL_LABELS))
                           .collect(Collectors.toList());

        args = savedInstanceState != null ? savedInstanceState : args;

        selectedItems = Arrays.stream(Objects.requireNonNull(
                                      args.getLongArray(BKEY_SELECTED), BKEY_SELECTED))
                              .boxed().collect(Collectors.toSet());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        return new MultiChoiceAlertDialogBuilder<Long>(getLayoutInflater())
                .setTitle(dialogTitle)
                .setMessage(dialogMessage)
                .setItems(itemIds, itemLabels)
                .setSelectedItems(selectedItems)
                .setPositiveButton(android.R.string.ok, this::saveChanges)
                .setOnDismiss(this::dismiss)
                .create();
    }

    private void saveChanges(@NonNull final Set<Long> selection) {
        Launcher.setResult(this, requestKey, selection);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLongArray(BKEY_SELECTED, selectedItems.stream().mapToLong(o -> o).toArray());
    }

    public abstract static class Launcher<T extends Parcelable & Entity>
            implements FragmentResultListener {

        private static final String SELECTED = "selected";
        private String requestKey;
        private FragmentManager fragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final Set<Long> selectedItems) {
            final Bundle result = new Bundle(1);
            result.putLongArray(SELECTED, selectedItems.stream().mapToLong(o -> o).toArray());
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.requestKey = requestKey;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
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
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putString(BKEY_DIALOG_TITLE, dialogTitle);

            args.putLongArray(BKEY_ALL_IDS, allItems
                    .stream().mapToLong(Entity::getId).toArray());
            args.putStringArray(BKEY_ALL_LABELS, allItems
                    .stream().map(item -> item.getLabel(context)).toArray(String[]::new));

            args.putLongArray(BKEY_SELECTED, selectedItems
                    .stream().mapToLong(Entity::getId).toArray());

            final DialogFragment frag = new MultiChoiceDialogFragment();
            frag.setArguments(args);
            frag.show(fragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Arrays.stream(Objects.requireNonNull(result.getLongArray(SELECTED), SELECTED))
                           .boxed().collect(Collectors.toSet()));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param selectedItems the set of <strong>checked</strong> items
         */
        public abstract void onResult(@NonNull Set<Long> selectedItems);
    }
}
