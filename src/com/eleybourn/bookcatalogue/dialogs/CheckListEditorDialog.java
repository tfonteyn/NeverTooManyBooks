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
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
    /** body of the dialog */
    private ViewGroup mContent;
    /** the list to display in the content view */
    private ArrayList<CheckListItem<T>> mList;
    /** Listener for dialog exit/save/onCancel */
    private OnEditListener mListener;

    /**
     * Constructor
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
                    public void onClick(final View view) {
                        mListener.onCheckListSave(CheckListEditorDialog.this, mList);
                    }
                }
        );

        // Handle Cancel
        root.findViewById(R.id.cancel).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(final View view) {
                        mListener.onCheckListCancel(CheckListEditorDialog.this);
                    }
                }
        );

        // Handle Cancel by any means
        this.setOnCancelListener(new OnCancelListener() {

            @Override
            public void onCancel(final DialogInterface dialog) {
                mListener.onCheckListCancel(CheckListEditorDialog.this);
            }
        });

        // Make sure the buttons moves if the keyboard appears
        //noinspection ConstantConditions
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /** Set the listener */
    void setOnEditListener(@NonNull final OnEditListener listener) {
        mListener = listener;
    }

    private final CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
            CheckListItem item = ViewTagger.getTag(buttonView, R.id.TAG_DIALOG_ITEM);
            //noinspection ConstantConditions
            item.setSelected(isChecked);
        }
    };

    /** Set the current list */
    public void setList(@NonNull final ArrayList<CheckListItem<T>> list) {
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

    @NonNull
    public ArrayList<CheckListItem<T>> getList() {
        return mList;
    }

    /**
     * Listener to receive notifications when dialog is closed by any means.
     */
    protected interface OnEditListener {
        <T> void onCheckListSave(@NonNull final CheckListEditorDialog dialog,
                                    @NonNull final List<CheckListItem<T>> list);

        void onCheckListCancel(@NonNull final CheckListEditorDialog dialog);
    }

}
