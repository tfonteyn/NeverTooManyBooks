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
import java.util.Objects;

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
    private static final String BKEY_CHECK_LIST = TAG + ":list";

    /** The list of items to display. Object + checkbox. */
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
            @NonNull final CheckListEditorListGetter<T> listGetter) {

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
        Objects.requireNonNull(mList);

        CheckListEditorDialog editor = new CheckListEditorDialog(requireActivity());
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        return editor;
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(BKEY_CHECK_LIST, mList);
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
     * Loads the {@link CheckListEditorDialogFragment} with the *current* list,
     * e.g. not the state of the list at init time.
     */
    public interface CheckListEditorListGetter<T> {

        @NonNull
        ArrayList<CheckListItem<T>> getList();
    }

    /**
     * The custom dialog.
     */
    private class CheckListEditorDialog
            extends AlertDialog {

        /**
         * Constructor.
         *
         * @param context caller context
         */
        CheckListEditorDialog(@NonNull final Context context) {
            super(context);

            View root = getLayoutInflater().inflate(R.layout.dialog_edit_base, null);
            setView(root);

            // Takes the list of items and create a list of checkboxes in the display.
            ViewGroup body = root.findViewById(R.id.content);
            for (final CheckListItem item : mList) {
                CompoundButton buttonView = new CheckBox(context);
                buttonView.setChecked(item.isChecked());
                buttonView.setText(item.getLabel(context));
                buttonView.setOnCheckedChangeListener(
                        (v, isChecked) -> item.setChecked(isChecked));
                body.addView(buttonView);
            }

            // Handle OK
            root.findViewById(R.id.confirm).setOnClickListener(
                    v -> {
                        dismiss();
                        getFragmentListener().onCheckListEditorSave(mDestinationFieldId, mList);
                    }
            );

            // Handle Cancel
            root.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());
        }
    }
}
