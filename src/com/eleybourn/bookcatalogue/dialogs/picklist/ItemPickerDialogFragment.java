/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.dialogs.picklist;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Fragment wrapper for {@link ItemPickerDialog}
 */
public class ItemPickerDialogFragment extends DialogFragment {

    public static final String BKEY_ITEM_LIST = "list";
    public static final String BKEY_MESSAGE = "message";
    public static final String BKEY_SELECTED_ITEM = "item";

    @StringRes
    private int mTitleId;
    @IdRes
    private int mDestinationFieldId;

    private String mMessage = null;
    private int mSelectedItem = -1;

    @Nullable
    private ArrayList<ItemPickerDialog.Item> mList;

    /**
     * Object to handle changes
     */
    private final ItemPickerDialog.OnItemPickerResultsListener mEditListener =
            new ItemPickerDialog.OnItemPickerResultsListener() {
                @Override
                public void onItemPickerSave(final @NonNull ItemPickerDialog dialog,
                                             final @NonNull ItemPickerDialog.Item item) {
                    dialog.dismiss();
                    ((OnItemPickerResultsListener) requireActivity()).onItemPickerSave(ItemPickerDialogFragment.this,
                            mDestinationFieldId, item);
                }

                @Override
                public void onItemPickerCancel(final @NonNull ItemPickerDialog dialog) {
                    dialog.dismiss();
                    ((OnItemPickerResultsListener) requireActivity()).onCheckListEditorCancel(ItemPickerDialogFragment.this,
                            mDestinationFieldId);
                }
            };


    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof ItemPickerDialogFragment.OnItemPickerResultsListener)) {
            throw new RTE.MustImplementException(context, OnItemPickerResultsListener.class);
        }
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public ItemPickerDialog onCreateDialog(final @Nullable Bundle savedInstanceState) {

        // Restore saved state info
        if (savedInstanceState != null) {
            mTitleId = savedInstanceState.getInt(UniqueId.BKEY_DIALOG_TITLE);
            mDestinationFieldId = savedInstanceState.getInt(UniqueId.BKEY_FIELD_ID);
            // data to edit
            if (savedInstanceState.containsKey(BKEY_ITEM_LIST)) {
                mList = ArrayUtils.getListFromBundle(savedInstanceState, BKEY_ITEM_LIST);
            }
            if (savedInstanceState.containsKey(BKEY_MESSAGE)) {
                mMessage = savedInstanceState.getString(BKEY_MESSAGE);
            }
            if (savedInstanceState.containsKey(BKEY_SELECTED_ITEM)) {
                mSelectedItem = savedInstanceState.getInt(BKEY_SELECTED_ITEM, -1);
            }
        } else {
            Bundle args = getArguments();
            Objects.requireNonNull(args);
            mTitleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.select_an_action);
            mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);
            // data to edit
            if (args.containsKey(BKEY_ITEM_LIST)) {
                mList = ArrayUtils.getListFromBundle(args, BKEY_ITEM_LIST);
            }
            if (args.containsKey(BKEY_MESSAGE)) {
                mMessage = args.getString(BKEY_MESSAGE);
            }
            if (args.containsKey(BKEY_SELECTED_ITEM)) {
                mSelectedItem = args.getInt(BKEY_SELECTED_ITEM, -1);
            }
        }

        ItemPickerDialog editor = new ItemPickerDialog(requireActivity(), mMessage);
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        if (mList != null) {
            editor.setList(mList, mSelectedItem);
        }
        editor.setResultsListener(mEditListener);
        return editor;
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        outState.putInt(UniqueId.BKEY_DIALOG_TITLE, mTitleId);
        outState.putInt(UniqueId.BKEY_FIELD_ID, mDestinationFieldId);
        if (mList != null) {
            outState.putSerializable(BKEY_ITEM_LIST, mList);
        }
        if (mMessage != null) {
            outState.putString(BKEY_MESSAGE, mMessage);
        }
        if (mSelectedItem >= 0) {
            outState.putInt(BKEY_SELECTED_ITEM, mSelectedItem);
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Make sure data is saved in onPause() because onSaveInstanceState will have lost the views
     */
    @Override
    @CallSuper
    public void onPause() {
        @SuppressWarnings("unchecked")
        ItemPickerDialog dialog = (ItemPickerDialog) getDialog();
        if (dialog != null) {
            mList = dialog.getList();
        }
        super.onPause();
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OnItemPickerResultsListener {
        void onItemPickerSave(final @NonNull ItemPickerDialogFragment dialog,
                                   final int destinationFieldId,
                                   final @NonNull ItemPickerDialog.Item item);

        void onCheckListEditorCancel(final @NonNull ItemPickerDialogFragment dialog,
                                     final int destinationFieldId);
    }
}
