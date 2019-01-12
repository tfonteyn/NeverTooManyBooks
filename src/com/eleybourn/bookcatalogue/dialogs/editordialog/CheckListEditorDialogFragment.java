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
package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TODO: as the Dialog is now an inner class, remove the listener between DialogFragment and Dialog.
 * <p>
 * DialogFragment to edit a list of checkbox options
 *
 * @param <T> type to use for {@link CheckListItem}
 */
public class CheckListEditorDialogFragment<T>
        extends
        EditorDialogFragment<CheckListEditorDialogFragment.OnCheckListEditorResultsListener<T>> {

    public static final String BKEY_CHECK_LIST = "list";
    /**
     * Object to handle changes.
     */
    private final CheckListEditorDialog.OnCheckListEditorResultsListener<T> mEditListener =
            new CheckListEditorDialog.OnCheckListEditorResultsListener<T>() {
                @Override
                public void onCheckListEditorSave(@NonNull final List<CheckListItem<T>> list) {
                    getFragmentListener()
                            .onCheckListEditorSave(CheckListEditorDialogFragment.this,
                                                   mDestinationFieldId, list);
                }
            };

    @Nullable
    private ArrayList<CheckListItem<T>> mList;

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public CheckListEditorDialog<T> onCreateDialog(@Nullable final Bundle savedInstanceState) {
        initStandardArgs(savedInstanceState);

        // Restore saved state info
        if (savedInstanceState != null) {
            mList = savedInstanceState.getParcelableArrayList(BKEY_CHECK_LIST);
        } else {
            Bundle args = getArguments();
            Objects.requireNonNull(args);
            mList = args.getParcelableArrayList(BKEY_CHECK_LIST);
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
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        if (mList != null) {
            outState.putParcelableArrayList(BKEY_CHECK_LIST, mList);
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Make sure data is saved in onPause() because onSaveInstanceState will have lost the views.
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
     *
     * @param <T> type of item in the checklist
     */
    public interface OnCheckListEditorResultsListener<T> {

        void onCheckListEditorSave(@NonNull CheckListEditorDialogFragment dialog,
                                   int destinationFieldId,
                                   @NonNull List<CheckListItem<T>> list);
    }

    public static class CheckListEditorDialog<T>
            extends AlertDialog {

        private final CompoundButton.OnCheckedChangeListener onCheckedChangeListener =
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(@NonNull final CompoundButton buttonView,
                                                 final boolean isChecked) {
                        CheckListItem item =
                                ViewTagger.getTagOrThrow(buttonView, R.id.TAG_DIALOG_ITEM);
                        item.setSelected(isChecked);
                    }
                };

        /** body of the dialog. */
        private final ViewGroup mContent;
        /** the list to display in the content view. */
        private ArrayList<CheckListItem<T>> mList;
        /** Listener for dialog exit/save/cancel. */
        private OnCheckListEditorResultsListener<T> mListener;

        /**
         * Constructor.
         *
         * @param context Calling context
         */
        CheckListEditorDialog(@NonNull final Context context) {
            super(context);

            // Get the layout
            View root = this.getLayoutInflater().inflate(R.layout.dialog_edit_base, null);
            setView(root);

            // get the content view
            mContent = root.findViewById(R.id.content);

            // Handle OK
            root.findViewById(R.id.confirm).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final View v) {
                            mListener.onCheckListEditorSave(mList);
                        }
                    }
            );

            // Handle Cancel
            root.findViewById(R.id.cancel).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final View v) {
                            dismiss();
                        }
                    }
            );
        }

        @NonNull
        public ArrayList<CheckListItem<T>> getList() {
            return mList;
        }

        /** Set the current list. */
        public void setList(@NonNull final ArrayList<CheckListItem<T>> list) {
            mList = list;
            for (CheckListItem item : mList) {
                CompoundButton btn = new CheckBox(getContext());
                btn.setChecked(item.isSelected());
                btn.setText(item.getLabel());
                btn.setOnCheckedChangeListener(onCheckedChangeListener);
                ViewTagger.setTag(btn, R.id.TAG_DIALOG_ITEM, item);
                mContent.addView(btn);
            }
        }

        /** Set the listener. */
        void setResultsListener(@NonNull final OnCheckListEditorResultsListener<T> listener) {
            mListener = listener;
        }

        /**
         * Listener to receive notifications when dialog is closed by any means.
         *
         * @param <T> type of item in the checklist
         */
        interface OnCheckListEditorResultsListener<T> {

            void onCheckListEditorSave(@NonNull List<CheckListItem<T>> list);
        }
    }
}
