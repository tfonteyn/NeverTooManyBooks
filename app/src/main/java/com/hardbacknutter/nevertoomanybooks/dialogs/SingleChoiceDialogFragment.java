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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogChooseOneBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RadioGroupRecyclerAdapter;

/**
 * Replacement for the AlertDialog with radio button setup.
 * <p>
 * FIXME: not in use right now, but if we do use it, this needs
 * ENHANCE: to be converted to dialog/bottom-sheet support
 *
 * Search the code for "setSingleChoiceItems" to check where this can/should be used.
 *
 * @see MultiChoiceDelegate
 */
public class SingleChoiceDialogFragment
        extends DialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "SingleChoiceDialogFragment";
    private static final String BKEY_DIALOG_TITLE = TAG + ":title";
    private static final String BKEY_DIALOG_MESSAGE = TAG + ":msg";

    /** Argument. */
    private static final String BKEY_FIELD_ID = TAG + ":fieldId";
    /** Argument. */
    private static final String BKEY_ALL_IDS = TAG + ":ids";
    private static final String BKEY_ALL_LABELS = TAG + ":labels";
    /** Argument. */
    private static final String BKEY_SELECTED = TAG + ":selected";
    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    @IdRes
    private int fieldId;
    @Nullable
    private String dialogTitle;
    @Nullable
    private String dialogMessage;

    /** The list of items to display. */
    private List<Long> itemIds;
    private List<String> itemLabels;
    private long selectedItem;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        dialogTitle = args.getString(BKEY_DIALOG_TITLE, getString(R.string.action_select));
        dialogMessage = args.getString(BKEY_DIALOG_MESSAGE, null);
        fieldId = args.getInt(BKEY_FIELD_ID);

        itemIds = Arrays.stream(Objects.requireNonNull(
                                args.getLongArray(BKEY_ALL_IDS), BKEY_ALL_IDS))
                        .boxed().collect(Collectors.toList());
        itemLabels = Arrays.stream(Objects.requireNonNull(
                                   args.getStringArray(BKEY_ALL_LABELS), BKEY_ALL_LABELS))
                           .collect(Collectors.toList());

        args = savedInstanceState != null ? savedInstanceState : args;

        selectedItem = args.getLong(BKEY_SELECTED);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        @SuppressLint("InflateParams")
        final DialogChooseOneBinding vb = DialogChooseOneBinding
                .inflate(getLayoutInflater(), null, false);

        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            vb.message.setText(dialogMessage);
            vb.message.setVisibility(View.VISIBLE);
        } else {
            vb.message.setVisibility(View.GONE);
        }

        final Context context = getContext();

        //noinspection DataFlowIssue
        final RadioGroupRecyclerAdapter<Long> adapter =
                new RadioGroupRecyclerAdapter<>(context, itemIds,
                                                position -> itemLabels.get(position),
                                                selectedItem,
                                                id -> selectedItem = id);

        vb.itemList.setAdapter(adapter);

        return new MaterialAlertDialogBuilder(context)
                .setView(vb.getRoot())
                .setTitle(dialogTitle)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> saveChanges())
                .create();
    }

    private void saveChanges() {
        Launcher.setResult(this, requestKey, fieldId, selectedItem);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BKEY_SELECTED, selectedItem);
    }

    public static class Launcher<T extends Parcelable & Entity>
            extends DialogLauncher {

        @NonNull
        private final ResultListener resultListener;

        /**
         * Constructor.
         *
         * @param requestKey     FragmentResultListener request key to use for our response.
         * @param resultListener listener
         */
        public Launcher(@NonNull final String requestKey,
                        @NonNull final ResultListener resultListener) {
            super(requestKey, SingleChoiceDialogFragment::new);
            this.resultListener = resultListener;
        }

        /**
         * Encode and forward the results to {@link #onFragmentResult(String, Bundle)}.
         *
         * @param fragment     the calling DialogFragment
         * @param requestKey   to use
         * @param fieldId      this destination field id
         * @param selectedItem the single selected item
         *
         * @see #onFragmentResult(String, Bundle)
         */
        @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @IdRes final int fieldId,
                              final long selectedItem) {
            final Bundle result = new Bundle(2);
            result.putInt(BKEY_FIELD_ID, fieldId);
            result.putLong(BKEY_SELECTED, selectedItem);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param context          preferably the {@code Activity}
         *                         but another UI {@code Context} will also do.
         * @param dialogTitle  the dialog title
         * @param fieldId      this dialog operates on
         *                     (one launcher can serve multiple fields)
         * @param allItems     list of all possible items
         * @param selectedItem item which is currently selected
         */
        public void launch(@NonNull final Context context,
                           @NonNull final String dialogTitle,
                           @IdRes final int fieldId,
                           @NonNull final List<T> allItems,
                           @NonNull final T selectedItem) {

            final Bundle args = new Bundle(6);
            args.putString(BKEY_DIALOG_TITLE, dialogTitle);
            args.putInt(BKEY_FIELD_ID, fieldId);

            args.putLongArray(BKEY_ALL_IDS, allItems
                    .stream().mapToLong(Entity::getId).toArray());
            args.putStringArray(BKEY_ALL_LABELS, allItems
                    .stream().map(item -> item.getLabel(context)).toArray(String[]::new));

            args.putLong(BKEY_SELECTED, selectedItem.getId());

            createDialog(context, args);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            resultListener.onResult(result.getInt(BKEY_FIELD_ID),
                                    result.getLong(BKEY_SELECTED));
        }

        @FunctionalInterface
        public interface ResultListener {
            /**
             * Callback handler with the user's selection.
             *
             * @param fieldId      this destination field id
             * @param selectedItem the single selected item
             */
            void onResult(@IdRes int fieldId,
                          long selectedItem);
        }
    }
}
