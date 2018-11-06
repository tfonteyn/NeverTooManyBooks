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
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog to edit a list of checkbox options
 *
 * The constructors and interface are now protected because this really should
 * only be called as part of the fragment version.
 *
 * @param <T> type to use for {@link CheckListItem}
 */
public class CheckListEditorDialog<T> extends AlertDialog {

    private final CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
            CheckListItem item = ViewTagger.getTag(buttonView, R.id.TAG_DIALOG_ITEM);
            //noinspection ConstantConditions
            item.setSelected(isChecked);
        }
    };
    /** body of the dialog */
    private final ViewGroup mContent;
    /** the list to display in the content view */
    private ArrayList<CheckListItem<T>> mList;
    /** Listener for dialog exit/save/cancel */
    private OnCheckListEditorResultsListener mListener;

    /**
     * Constructor
     *
     * @param context Calling context
     */
    CheckListEditorDialog(final @NonNull Context context) {
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
                    public void onClick(final View view) {
                        mListener.onCheckListEditorSave(CheckListEditorDialog.this, mList);
                    }
                }
        );

        // Handle Cancel
        root.findViewById(R.id.cancel).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(final View view) {
                        mListener.onCheckListEditorCancel(CheckListEditorDialog.this);
                    }
                }
        );

        // Handle Cancel by any means
        this.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(final DialogInterface dialog) {
                mListener.onCheckListEditorCancel(CheckListEditorDialog.this);
            }
        });
    }

    @NonNull
    public ArrayList<CheckListItem<T>> getList() {
        return mList;
    }

    /** Set the current list */
    public void setList(final @NonNull ArrayList<CheckListItem<T>> list) {
        mList = list;
        for (CheckListItem item : mList) {
            CompoundButton btn = new CheckBox(getContext());
            btn.setChecked(item.getSelected());
            btn.setText(item.getLabel());
            btn.setOnCheckedChangeListener(onCheckedChangeListener);
            ViewTagger.setTag(btn, R.id.TAG_DIALOG_ITEM, item);
            mContent.addView(btn);
        }
    }

    /** Set the listener */
    void setResultsListener(final @NonNull OnCheckListEditorResultsListener listener) {
        mListener = listener;
    }

    /**
     * Listener to receive notifications when dialog is closed by any means.
     */
    protected interface OnCheckListEditorResultsListener {
        <T> void onCheckListEditorSave(final @NonNull CheckListEditorDialog dialog,
                                       final @NonNull List<CheckListItem<T>> list);

        void onCheckListEditorCancel(final @NonNull CheckListEditorDialog dialog);
    }

}
