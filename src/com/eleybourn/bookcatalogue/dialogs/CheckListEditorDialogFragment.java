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
package com.eleybourn.bookcatalogue.dialogs;

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
import java.util.List;
import java.util.Objects;

/**
 * Fragment wrapper for {@link CheckListEditorDialog}
 *
 * @param <T> type to use for {@link CheckListItem}
 */
public class CheckListEditorDialogFragment<T> extends DialogFragment {

    public static final String BKEY_CHECK_LIST = "list";

    @StringRes
    private int mTitleId;
    @IdRes
    private int mDestinationFieldId;
    /**
     * Object to handle changes
     */
    private final CheckListEditorDialog.OnCheckListEditorResultsListener mEditListener =
            new CheckListEditorDialog.OnCheckListEditorResultsListener() {
                /**
                 * @param <T2>  type to use for {@link CheckListItem}
                 */
                @Override
                public <T2> void onCheckListEditorSave(final @NonNull CheckListEditorDialog dialog,
                                                       final @NonNull List<CheckListItem<T2>> list) {
                    dialog.dismiss();
                    ((OnCheckListEditorResultsListener) requireActivity()).onCheckListEditorSave(CheckListEditorDialogFragment.this,
                            mDestinationFieldId, list);
                }

                @Override
                public void onCheckListEditorCancel(final @NonNull CheckListEditorDialog dialog) {
                    dialog.dismiss();
                    ((OnCheckListEditorResultsListener) requireActivity()).onCheckListEditorCancel(CheckListEditorDialogFragment.this,
                            mDestinationFieldId);
                }
            };
    @Nullable
    private ArrayList<CheckListItem<T>> mList;

    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof CheckListEditorDialogFragment.OnCheckListEditorResultsListener)) {
            throw new RTE.MustImplementException(context, OnCheckListEditorResultsListener.class);
        }
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public CheckListEditorDialog<T> onCreateDialog(final @Nullable Bundle savedInstanceState) {
        // Restore saved state info
        if (savedInstanceState != null) {
            mTitleId = savedInstanceState.getInt(UniqueId.BKEY_DIALOG_TITLE);
            mDestinationFieldId = savedInstanceState.getInt(UniqueId.BKEY_FIELD_ID);
            // data to edit
            if (savedInstanceState.containsKey(BKEY_CHECK_LIST)) {
                mList = ArrayUtils.getListFromBundle(savedInstanceState, BKEY_CHECK_LIST);
            }
        } else {
            Bundle args = getArguments();
            Objects.requireNonNull(args);
            mTitleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
            mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);
            // data to edit
            if (args.containsKey(BKEY_CHECK_LIST)) {
                mList = ArrayUtils.getListFromBundle(args, BKEY_CHECK_LIST);
            }
        }

        CheckListEditorDialog<T> editor = new CheckListEditorDialog<>(requireActivity());
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        if (mList != null) {
            editor.setList(mList);
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
            outState.putSerializable(BKEY_CHECK_LIST, mList);
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
        CheckListEditorDialog<T> dialog = (CheckListEditorDialog<T>) getDialog();
        if (dialog != null) {
            mList = dialog.getList();
        }
        super.onPause();
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OnCheckListEditorResultsListener {
        <T2> void onCheckListEditorSave(final @NonNull CheckListEditorDialogFragment dialog,
                                        final int destinationFieldId,
                                        final @NonNull List<CheckListItem<T2>> list);

        void onCheckListEditorCancel(final @NonNull CheckListEditorDialogFragment dialog,
                                     final int destinationFieldId);
    }
}
