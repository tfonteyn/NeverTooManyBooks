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
import androidx.annotation.IdRes;
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
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
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
    /** The list of items to display. */
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
    private WeakReference<CheckListResultsListener> mListener;
    /** identifier of the field this dialog is bound to. */
    @IdRes
    private int mFieldId;
    @StringRes
    private int mDialogTitleId;

    /**
     * Constructor.
     *
     * @param dialogTitleId resource id for the dialog title
     * @param fieldId       the field whose content we want to edit
     * @param allItems      list of all possible items
     * @param selectedItems list of item which are currently selected
     *
     * @return the new instance
     */
    public static CheckListDialogFragment newInstance(
            @StringRes final int dialogTitleId,
            @IdRes final int fieldId,
            final ArrayList<Entity> allItems,
            @NonNull final ArrayList<Entity> selectedItems) {

        CheckListDialogFragment frag = new CheckListDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putInt(UniqueId.BKEY_FIELD_ID, fieldId);

        args.putParcelableArrayList(BKEY_ALL, allItems);
        args.putParcelableArrayList(BKEY_SELECTED, selectedItems);

        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mDialogTitleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.action_edit);
        mFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);

        args = savedInstanceState != null ? savedInstanceState : args;

        mAllItems = args.getParcelableArrayList(BKEY_ALL);
        Objects.requireNonNull(mAllItems, ErrorMsg.ARGS_MISSING_CHECKLIST);

        mSelectedItems = args.getParcelableArrayList(BKEY_SELECTED);
        Objects.requireNonNull(mSelectedItems, ErrorMsg.ARGS_MISSING_CHECKLIST);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        View root = createCheckBoxes();

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(root)
                .setTitle(mDialogTitleId)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> sendResults())
                .create();
    }

    private View createCheckBoxes() {
        // Reminder: *always* use the activity inflater here.
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
                } else {
                    mSelectedItems.remove(item);
                }
            });
            body.addView(itemView);
        }
        return root;
    }

    /** this was a try... but it's not very pleasing on the eye. */
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

    private void sendResults() {
        if (mListener.get() != null) {
            mListener.get().onCheckListEditorSave(mFieldId, mSelectedItems);

        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Log.d(TAG, "onCheckListEditorSave|" + ErrorMsg.WEAK_REFERENCE);
            }
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(BKEY_SELECTED, mAllItems);
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
         * @param fieldId the field this dialog is bound to
         * @param value   the CHECKED items
         */
        void onCheckListEditorSave(int fieldId,
                                   @NonNull ArrayList<Entity> value);
    }
}
