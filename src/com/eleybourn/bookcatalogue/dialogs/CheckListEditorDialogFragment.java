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

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment wrapper for {@link CheckListEditorDialog}
 *
 * @param <T> type to use for {@link CheckListItem}
 */
public class CheckListEditorDialogFragment<T> extends DialogFragment {
    /** Dialog title */
    private static final String BKEY_TITLE = "title";
    private static final String BKEY_LIST = "list";

    @StringRes
    private int mTitleId;
    @IdRes
    private int mDestinationFieldId;

    @Nullable
    private ArrayList<CheckListItem<T>> mList;

    /**
     * Object to handle changes.
     */
    private final CheckListEditorDialog.OnEditListener mEditListener = new CheckListEditorDialog.OnEditListener() {
        /**
         * @param <T2>  type to use for {@link CheckListItem}
         */
        @Override
        public <T2> void onCheckListSave(@NonNull final CheckListEditorDialog dialog,
                                        @NonNull final List<CheckListItem<T2>> list) {
            dialog.dismiss();
            ((OnCheckListChangedListener) requireActivity()).onCheckListSave(CheckListEditorDialogFragment.this,
                    mDestinationFieldId, list);
        }

        @Override
        public void onCheckListCancel(@NonNull final CheckListEditorDialog dialog) {
            dialog.dismiss();
            ((OnCheckListChangedListener) requireActivity()).onCheckListCancel(CheckListEditorDialogFragment.this,
                    mDestinationFieldId);
        }
    };

    /**
     * Ensure activity supports interface
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof OnCheckListChangedListener))
            throw new RTE.MustImplementException(context, OnCheckListChangedListener.class);
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public CheckListEditorDialog<T> onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Restore saved state info
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BKEY_LIST)) {
                mList = ArrayUtils.getListFromBundle(savedInstanceState, BKEY_LIST);
            }
            mTitleId = savedInstanceState.getInt(BKEY_TITLE);
            mDestinationFieldId = savedInstanceState.getInt(UniqueId.BKEY_FIELD_ID);
        }


        CheckListEditorDialog<T> editor = new CheckListEditorDialog<>(requireActivity());
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        if (mList != null) {
            editor.setList(mList);
        }
        editor.setOnEditListener(mEditListener);
        return editor;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public CheckListEditorDialogFragment setDestinationFieldId(@IdRes final int id) {
        mDestinationFieldId = id;
        return this;
    }

    /**
     * Accessor. Update dialog if available.
     */
    @NonNull
    public CheckListEditorDialogFragment setTitle(@StringRes final int title) {
        mTitleId = title;
        CheckListEditorDialog d = (CheckListEditorDialog) getDialog();
        if (d != null) {
            d.setTitle(mTitleId);
        }
        return this;
    }

    /**
     * Accessor. Update dialog if available.
     */
    @NonNull
    public CheckListEditorDialogFragment setList(@NonNull final ArrayList<CheckListItem<T>> list) {
        mList = list;
        CheckListEditorDialog<T> d = (CheckListEditorDialog<T>) getDialog();
        if (d != null) {
            d.setList(mList);
        }
        return this;
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putInt(BKEY_TITLE, mTitleId);
        outState.putInt(UniqueId.BKEY_FIELD_ID, mDestinationFieldId);
        if (mList != null) {
            outState.putSerializable(BKEY_LIST, mList);
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
    public interface OnCheckListChangedListener {
        <T2> void onCheckListSave(@NonNull final CheckListEditorDialogFragment dialog,
                             final int destinationFieldId,
                             @NonNull final List<CheckListItem<T2>> list);

        void onCheckListCancel(@NonNull final CheckListEditorDialogFragment dialog,
                               final int destinationFieldId);
    }
}
