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

/**
 * DialogFragment to edit a list of checkbox options.
 *
 * @param <T> type to use for {@link CheckListItem}
 */
public class CheckListEditorDialogFragment<T>
        extends
        EditorDialogFragment<CheckListEditorDialogFragment.OnCheckListEditorResultsListener<T>> {

    /** Argument. */
    public static final String BKEY_CHECK_LIST = "list";

    /** The list of items to display. Object + checkbox. */
    @Nullable
    private ArrayList<CheckListItem<T>> mList;

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public CheckListEditorDialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        // Restore saved state info
        if (savedInstanceState != null) {
            mList = savedInstanceState.getParcelableArrayList(BKEY_CHECK_LIST);
        } else {
            Bundle args = getArguments();
            //noinspection ConstantConditions
            mList = args.getParcelableArrayList(BKEY_CHECK_LIST);
        }

        CheckListEditorDialog editor = new CheckListEditorDialog(requireActivity());
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        if (mList != null) {
            editor.setList(mList);
        }
        return editor;
    }

    /**
     * Make sure data is saved in onPause() because onSaveInstanceState will have lost the views.
     */
    @Override
    @CallSuper
    public void onPause() {
        @SuppressWarnings("unchecked")
        CheckListEditorDialog dialog = (CheckListEditorDialog) getDialog();
        if (dialog != null) {
            mList = dialog.getList();
        }
        super.onPause();
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
     * The dialog calls this to report back the user input.
     *
     * @param result - with options
     */
    private void reportChanges(@NonNull final ArrayList<CheckListItem<T>> result) {
        getFragmentListener().onCheckListEditorSave(this, mDestinationFieldId, result);
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @param <T> - type of item in the checklist
     */
    public interface OnCheckListEditorResultsListener<T> {

        /**
         * reports the results after this dialog was confirmed.
         *
         * @param dialog             the dialog
         * @param destinationFieldId the field this dialog is bound to
         * @param list               the list of options
         */
        void onCheckListEditorSave(@NonNull CheckListEditorDialogFragment dialog,
                                   int destinationFieldId,
                                   @NonNull List<CheckListItem<T>> list);
    }

    /**
     * The custom dialog.
     */
    public class CheckListEditorDialog
            extends AlertDialog
            implements CompoundButton.OnCheckedChangeListener {

        /** body of the dialog. */
        private final ViewGroup mContent;
        /** the list to display in the content view. */
        private ArrayList<CheckListItem<T>> mList;

        /**
         * Constructor.
         *
         * @param context - Calling context
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
                            CheckListEditorDialogFragment.this.reportChanges(mList);
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

        /**
         * @return the current list
         */
        @NonNull
        public ArrayList<CheckListItem<T>> getList() {
            return mList;
        }

        /** @param list the current list to use. */
        public void setList(@NonNull final ArrayList<CheckListItem<T>> list) {
            mList = list;
            for (CheckListItem item : mList) {
                CompoundButton btn = new CheckBox(getContext());
                btn.setChecked(item.isSelected());
                btn.setText(item.getLabel());
                btn.setOnCheckedChangeListener(this);
                ViewTagger.setTag(btn, R.id.TAG_DIALOG_ITEM, item);
                mContent.addView(btn);
            }
        }

        /**
         * Called when the user changes a checkbox and updated the list.
         *
         * @param buttonView – The compound button view whose state has changed.
         * @param isChecked  – The new checked state of buttonView.
         */
        @Override
        public void onCheckedChanged(final CompoundButton buttonView,
                                     final boolean isChecked) {
            CheckListItem item = ViewTagger.getTagOrThrow(buttonView, R.id.TAG_DIALOG_ITEM);
            item.setSelected(isChecked);
        }
    }
}
