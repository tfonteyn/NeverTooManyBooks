/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.FragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * DialogFragment to edit a list of {@link Entity}.
 * <p>
 * Replacement for the AlertDialog with MultipleChoice setup.
 */
public class MultiChoiceDialogFragment
        extends DialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "MultiChoiceDialogFragment";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    private static final String BKEY_DIALOG_TITLE = TAG + ":title";
    /** Argument. */
    private static final String BKEY_FIELD_ID = TAG + ":fieldId";
    /** Argument. */
    private static final String BKEY_ALL = TAG + ":all";
    /** Argument. */
    private static final String BKEY_SELECTED = TAG + ":selected";
    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;
    @IdRes
    private int mFieldId;
    @Nullable
    private String mDialogTitle;

    /** The list of items to display. */
    private ArrayList<Entity> mAllItems;
    /** The list of selected items. */
    private ArrayList<Entity> mSelectedItems;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mDialogTitle = args.getString(BKEY_DIALOG_TITLE, getString(R.string.action_edit));
        mFieldId = args.getInt(BKEY_FIELD_ID);

        mAllItems = Objects.requireNonNull(args.getParcelableArrayList(BKEY_ALL),
                                           "mAllItems");

        args = savedInstanceState != null ? savedInstanceState : args;
        mSelectedItems = Objects.requireNonNull(args.getParcelableArrayList(BKEY_SELECTED),
                                                "mSelectedItems");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        @SuppressLint("InflateParams")
        final View root = getLayoutInflater().inflate(R.layout.dialog_edit_checklist, null);
        final ViewGroup itemListView = root.findViewById(R.id.item_list);
        // Takes the list of items and create a list of checkboxes in the display.
        for (final Entity item : mAllItems) {
            final CompoundButton itemView = new CheckBox(getContext());
            //noinspection ConstantConditions
            itemView.setText(item.getLabel(getContext()));
            itemView.setChecked(mSelectedItems.contains(item));

            itemView.setOnCheckedChangeListener((v, isChecked) -> {
                if (isChecked) {
                    mSelectedItems.add(item);
                } else {
                    mSelectedItems.remove(item);
                }
            });
            itemListView.addView(itemView);
        }

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(root)
                .setTitle(mDialogTitle)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> saveChanges())
                .create();
    }

    private void saveChanges() {
        Launcher.setResult(this, mRequestKey, mFieldId, mSelectedItems);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(BKEY_SELECTED, mSelectedItems);
    }

    public abstract static class Launcher
            extends FragmentLauncherBase {

        private static final String FIELD_ID = "fieldId";
        private static final String SELECTED_ITEMS = "selectedItems";

        public Launcher(@NonNull final String requestKey) {
            super(requestKey);
        }

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @IdRes final int fieldId,
                              @NonNull final ArrayList<Entity> selectedItems) {
            final Bundle result = new Bundle(2);
            result.putInt(FIELD_ID, fieldId);
            result.putParcelableArrayList(SELECTED_ITEMS, selectedItems);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param dialogTitle   the dialog title
         * @param fieldId       this dialog operates on
         *                      (one launcher can serve multiple fields)
         * @param allItems      list of all possible items
         * @param selectedItems list of item which are currently selected
         */
        public void launch(@NonNull final String dialogTitle,
                           @IdRes final int fieldId,
                           @NonNull final ArrayList<Entity> allItems,
                           @NonNull final ArrayList<Entity> selectedItems) {

            final Bundle args = new Bundle(5);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putString(BKEY_DIALOG_TITLE, dialogTitle);
            args.putInt(BKEY_FIELD_ID, fieldId);
            args.putParcelableArrayList(BKEY_ALL, allItems);
            args.putParcelableArrayList(BKEY_SELECTED, selectedItems);

            final DialogFragment frag = new MultiChoiceDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(result.getInt(FIELD_ID),
                     Objects.requireNonNull(result.getParcelableArrayList(SELECTED_ITEMS)));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param fieldId       this destination field id
         * @param selectedItems the list of <strong>checked</strong> items
         */
        public abstract void onResult(@IdRes int fieldId,
                                      @NonNull ArrayList<Entity> selectedItems);
    }
}
