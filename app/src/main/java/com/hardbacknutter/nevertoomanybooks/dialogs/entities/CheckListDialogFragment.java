/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.CompoundButton;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * DialogFragment to edit a list of {@link Entity}.
 * <p>
 * Replacement for the AlertDialog with multipleChoice setup.
 */
public class CheckListDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "CheckListDialogFragment";

    /** Argument. */
    private static final String BKEY_ALL = TAG + ":all";
    /** Argument. */
    private static final String BKEY_SELECTED = TAG + ":selected";

    /** The list of items to display. */
    private ArrayList<Entity> mAllItems;
    /** The list of selected items. */
    private ArrayList<Entity> mSelectedItems;

    private final View.OnClickListener filterChipListener = view -> {
        Entity current = (Entity) view.getTag();
        if (((Checkable) view).isChecked()) {
            mSelectedItems.add(current);
        } else {
            mSelectedItems.remove(current);
        }
    };

    /** Where to send the result. */
    @Nullable
    private WeakReference<CheckListResultsListener> mListener;

    /**
     * Constructor.
     *
     * @param dialogTitleId resource id for the dialog title
     * @param allItems      list of all possible items
     * @param selectedItems list of item which are currently selected
     *
     * @return instance
     */
    public static DialogFragment newInstance(@StringRes final int dialogTitleId,
                                             @NonNull final ArrayList<Entity> allItems,
                                             @NonNull final ArrayList<Entity> selectedItems) {

        final DialogFragment frag = new CheckListDialogFragment();
        final Bundle args = new Bundle(3);
        args.putInt(StandardDialogs.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putParcelableArrayList(BKEY_ALL, allItems);
        args.putParcelableArrayList(BKEY_SELECTED, selectedItems);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mAllItems = args.getParcelableArrayList(BKEY_ALL);
        Objects.requireNonNull(mAllItems, ErrorMsg.ARGS_MISSING_CHECKLIST);

        args = savedInstanceState != null ? savedInstanceState : requireArguments();
        mSelectedItems = args.getParcelableArrayList(BKEY_SELECTED);
        Objects.requireNonNull(mSelectedItems, ErrorMsg.ARGS_MISSING_CHECKLIST);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        final View root = createCheckBoxes();

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_edit)
                .setView(root)
                .setTitle(getArguments().getInt(StandardDialogs.BKEY_DIALOG_TITLE,
                                                R.string.action_edit))
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> saveChanges())
                .create();
    }

    private View createCheckBoxes() {
        // Reminder: *always* use the activity inflater here.
//        final LayoutInflater inflater = LayoutInflater.from(getContext());
        //noinspection ConstantConditions
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        @SuppressLint("InflateParams")
        final View root = inflater.inflate(R.layout.dialog_edit_checklist, null);
        final ViewGroup body = root.findViewById(R.id.item_list);
        // Takes the list of items and create a list of checkboxes in the display.
        for (Entity item : mAllItems) {
            CompoundButton itemView = new CheckBox(getContext());
            //noinspection ConstantConditions
            itemView.setText(item.getLabel(getContext()));
            itemView.setChecked(mSelectedItems.contains(item));

            itemView.setOnCheckedChangeListener((v, isChecked) -> {
                if (isChecked) {
                    mSelectedItems.add(item);
                    Log.d(TAG, "add: " + item.getLabel(getContext()));
                } else {
                    mSelectedItems.remove(item);
                    Log.d(TAG, "remove: " + item.getLabel(getContext()));
                }
            });
            body.addView(itemView);
        }
        return root;
    }

    // this was a try... but it's not very pleasing on the eye.
    @SuppressWarnings("unused")
    private View createChips() {

        //noinspection ConstantConditions
        ViewGroup root = new com.google.android.material.chip.ChipGroup(getContext());
        for (Entity item : mAllItems) {
            Chip chip = new Chip(getContext(), null, R.style.Widget_MaterialComponents_Chip_Filter);
            // RTL-friendly Chip Layout
            chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

            chip.setTag(item);
            chip.setText(item.getLabel(getContext()));
            // reminder: the Filter style has checkable=true, but unless we explicitly set it
            // here in code, it won't take effect.
            chip.setCheckable(true);
            chip.setChecked(mSelectedItems.contains(item));
            chip.setOnClickListener(filterChipListener);
            root.addView(chip);
        }

        return root;
    }

    private void saveChanges() {
        if (mListener != null && mListener.get() != null) {
            mListener.get().onCheckListEditorSave(mSelectedItems);

        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "onCheckListEditorSave|" +
                           (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                              : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(BKEY_SELECTED, mSelectedItems);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final CheckListResultsListener listener) {
        mListener = new WeakReference<>(listener);
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface CheckListResultsListener {

        /**
         * reports the results after this dialog was confirmed.
         *
         * @param list the CHECKED items
         */
        void onCheckListEditorSave(@NonNull ArrayList<Entity> list);
    }
}
