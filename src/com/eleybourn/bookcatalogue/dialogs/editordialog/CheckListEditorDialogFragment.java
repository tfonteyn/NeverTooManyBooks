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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.BookBaseFragment;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.datamanager.Fields;

/**
 * DialogFragment to edit a list of checkbox options.
 *
 * @param <T> type to use for {@link CheckListItem}
 */
public class CheckListEditorDialogFragment<T>
        extends
        EditorDialogFragment<CheckListEditorDialogFragment.OnCheckListEditorResultsListener<T>> {

    /** Fragment manager tag. */
    public static final String TAG = CheckListEditorDialogFragment.class.getSimpleName();

    /** Argument. */
    private static final String BKEY_CHECK_LIST = "list";
    /** The list of items to display. Object + checkbox. */
    @Nullable
    private ArrayList<CheckListItem<T>> mList;

    /**
     * Constructor.
     *
     * @param callerTag     tag of the calling fragment to send results back to.
     * @param field         the field whose content we want to edit
     * @param dialogTitleId titel resource id for the dialog
     * @param listGetter    callback interface for getting the list to use.
     * @param <T>           type of the {@link CheckListItem}
     *
     * @return the new instance
     */
    public static <T> CheckListEditorDialogFragment<T> newInstance(
            @NonNull final String callerTag,
            @NonNull final Fields.Field field,
            @StringRes final int dialogTitleId,
            @NonNull final BookBaseFragment.CheckListEditorListGetter<T> listGetter) {

        CheckListEditorDialogFragment<T> frag = new CheckListEditorDialogFragment<>();
        Bundle args = new Bundle();
        args.putString(UniqueId.BKEY_CALLER_TAG, callerTag);
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putInt(UniqueId.BKEY_FIELD_ID, field.id);
        args.putParcelableArrayList(BKEY_CHECK_LIST, listGetter.getList());
        frag.setArguments(args);
        return frag;
    }

    /**
     * Create the underlying dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        readBaseArgs(savedInstanceState);

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mList = args.getParcelableArrayList(BKEY_CHECK_LIST);

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
        super.onSaveInstanceState(outState);
        if (mList != null) {
            outState.putParcelableArrayList(BKEY_CHECK_LIST, mList);
        }
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
         * @param destinationFieldId the field this dialog is bound to
         * @param list               the list of options
         */
        void onCheckListEditorSave(int destinationFieldId,
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
         * @param context the caller context
         */
        CheckListEditorDialog(@NonNull final Context context) {
            super(context);

            // Get the layout
            View root = getLayoutInflater().inflate(R.layout.dialog_edit_base, null);
            setView(root);

            // get the content view
            mContent = root.findViewById(R.id.content);

            // Handle OK
            root.findViewById(R.id.confirm).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(@NonNull final View v) {
                            dismiss();
                            getFragmentListener().onCheckListEditorSave(mDestinationFieldId, mList);
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
                CompoundButton buttonView = new CheckBox(getContext());
                buttonView.setChecked(item.isSelected());
                buttonView.setText(item.getLabel());
                buttonView.setOnCheckedChangeListener(this);
                buttonView.setTag(R.id.TAG_DIALOG_ITEM, item);
                mContent.addView(buttonView);
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
            CheckListItem item = (CheckListItem) buttonView.getTag(R.id.TAG_DIALOG_ITEM);
            item.setSelected(isChecked);
        }
    }
}
