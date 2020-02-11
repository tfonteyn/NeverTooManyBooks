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
package com.hardbacknutter.nevertoomanybooks.dialogs.checklist;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * DialogFragment to edit a list of {@link CheckListItem} options.
 * <p>
 * Replacement for the AlertDialog with multipleChoice setup.
 * <ul>Pro:
 * <li>Single item list instead of 2 arrays.</li>
 * <li>Item can be anything as long as it implements {@link CheckListItem}</li>
 * <li>Default implementation {@link SelectableItem} encapsulates a long<br>
 * which can be used to represent an id, number, bit in bitmask, ... </li>
 * <li>Layout easy to enhance.</li>
 * </ul>
 * <p>
 * Con: the items <strong>must</strong> be Parcelable.
 */
public class CheckListDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "CheckListDialogFragment";

    /** Argument. */
    private static final String BKEY_CHECK_LIST = TAG + ":list";

    /** The list of items to display. Object + checkbox. */
    private ArrayList<CheckListItem> mList;
    private WeakReference<CheckListResultsListener> mListener;

    /** identifier of the field this dialog is bound to. */
    @IdRes
    private int mDestinationFieldId;

    /**
     * Constructor.
     *
     * @param fieldId       the field whose content we want to edit
     * @param dialogTitleId resource id for the dialog title
     * @param items         list of items
     *
     * @return the new instance
     */
    public static CheckListDialogFragment newInstance(
            @IdRes final int fieldId,
            @StringRes final int dialogTitleId,
            @NonNull final ArrayList<CheckListItem> items) {

        CheckListDialogFragment frag = new CheckListDialogFragment();
        Bundle args = new Bundle(3);
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putInt(UniqueId.BKEY_FIELD_ID, fieldId);
        args.putParcelableArrayList(BKEY_CHECK_LIST, items);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);

        args = savedInstanceState != null ? savedInstanceState : args;
        mList = args.getParcelableArrayList(BKEY_CHECK_LIST);
        Objects.requireNonNull(mList, ErrorMsg.ARGS_MISSING_CHECKLIST);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        @SuppressLint("InflateParams")
        View root = layoutInflater.inflate(R.layout.dialog_edit_checklist, null);

        // Takes the list of items and create a list of checkboxes in the display.
        ViewGroup body = root.findViewById(android.R.id.list);
        for (CheckListItem item : mList) {
            CompoundButton buttonView = new CheckBox(getContext());
            buttonView.setChecked(item.isSelected());
            //noinspection ConstantConditions
            buttonView.setText(item.getLabel(getContext()));
            buttonView.setOnCheckedChangeListener((v, isChecked) -> item.setSelected(isChecked));
            body.addView(buttonView);
        }

        //noinspection ConstantConditions
        AlertDialog dialog =
                new MaterialAlertDialogBuilder(getContext())
                        .setView(root)
                        .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                        .setPositiveButton(android.R.string.ok, (d, which) -> {
                            if (mListener.get() != null) {
                                mListener.get().onCheckListEditorSave(mDestinationFieldId,
                                                                      getSelectedItems(mList));
                            } else {
                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                                    Log.d(TAG, "onCheckListEditorSave|"
                                               + Logger.WEAK_REFERENCE_DEAD);
                                }
                            }
                        })
                        .create();

        Bundle args = getArguments();
        if (args != null) {
            @StringRes
            int titleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
            if (titleId != 0) {
                dialog.setTitle(titleId);
            }
        }
        return dialog;
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(BKEY_CHECK_LIST, mList);
    }

    /**
     * Get the list of selected items.
     *
     * @param list to dissect
     *
     * @return filtered list
     */
    @NonNull
    private ArrayList<CheckListItem>
    getSelectedItems(@NonNull final Collection<CheckListItem> list) {
        final ArrayList<CheckListItem> result = new ArrayList<>(list.size());
        for (CheckListItem entry : list) {
            if (entry.isSelected()) {
                result.add(entry);
            }
        }
        return result;
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
         * @param destinationFieldId the field this dialog is bound to
         * @param list               the list of CHECKED options
         *                           (non-checked options have been removed)
         */
        void onCheckListEditorSave(int destinationFieldId,
                                   @NonNull ArrayList<CheckListItem> list);
    }

    /**
     * Loads the {@link CheckListDialogFragment} with the *current* list,
     * e.g. not the state of the list at definition time, but the one at creation time.
     */
    public interface ListGetter {

        @NonNull
        ArrayList<CheckListItem> getList();
    }
}
