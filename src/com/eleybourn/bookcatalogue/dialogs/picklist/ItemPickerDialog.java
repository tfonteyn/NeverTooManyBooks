package com.eleybourn.bookcatalogue.dialogs.picklist;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

public class ItemPickerDialog extends AlertDialog {

    /** body of the dialog */
    private final ViewGroup mContent;
    /** the list to display in the content view */
    private ArrayList<Item> mList;
    /** Listener for dialog exit/save/cancel */
    private OnItemPickerResultsListener mListener;

    private int mSelectedItem = -1;

    // Create the listener for each item
    private final View.OnClickListener onItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final @NonNull View v) {
            Item item = ViewTagger.getTag(v, R.id.TAG_DIALOG_ITEM);
            // For a consistent UI, make sure the selector is checked as well.
            // NOT mandatory from a functional point of view, just consistent
            if (item != null && !(v instanceof Checkable)) {
                CompoundButton btn = item.getSelector(v);
                if (btn != null) {
                    btn.setChecked(true);
                    btn.invalidate();
                }
            }
            if (item != null) {
                mListener.onItemPickerSave(ItemPickerDialog.this, item);
            }
        }
    };

    /**
     * Constructor
     *
     * @param context Calling context
     */
    ItemPickerDialog(final @NonNull Context context,
                     final @Nullable String message) {
        super(context);

        // Get the layout
        View root = this.getLayoutInflater().inflate(R.layout.dialog_select_one_from_list, null);
        setView(root);

        // and the top message (if any)
        TextView messageView = root.findViewById(R.id.message);
        if (message != null && !message.isEmpty()) {
            messageView.setText(message);
        } else {
            messageView.setVisibility(View.GONE);
            root.findViewById(R.id.messageBottomDivider).setVisibility(View.GONE);
        }
        // get the content view
        mContent = root.findViewById(android.R.id.list);

        // Handle Cancel by any means
        this.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(final DialogInterface dialog) {
                mListener.onItemPickerCancel(ItemPickerDialog.this);
            }
        });
    }

    @NonNull
    public ArrayList<Item> getList() {
        return mList;
    }

    /** Set the current list */
    public void setList(final @NonNull ArrayList<Item> list, final int selectedItem) {
        mList = list;
        mSelectedItem = selectedItem;

        for (int p=0; p<mList.size(); p++) {
            Item item = mList.get(p);
            View view = item.getView(this.getLayoutInflater());
            view.setOnClickListener(onItemClickListener);
            view.setBackgroundResource(android.R.drawable.list_selector_background);

            ViewTagger.setTag(view, R.id.TAG_DIALOG_ITEM, item);

            CompoundButton btn = item.getSelector(view);
            if (btn != null) {
                ViewTagger.setTag(btn, R.id.TAG_DIALOG_ITEM, item);
                btn.setChecked(p == mSelectedItem);
                btn.setOnClickListener(onItemClickListener);
            }
            mContent.addView(view);
        }
    }

    public int getSelectedItem() {
        return mSelectedItem;
    }

    /** Set the listener */
    void setResultsListener(final @NonNull OnItemPickerResultsListener listener) {
        mListener = listener;
    }

    /**
     * Interface for item that displays in a custom dialog list
     */
    public interface Item {
        @NonNull
        View getView(final @NonNull LayoutInflater inflater);

        @Nullable
        CompoundButton getSelector(View v);
    }

    /**
     * Listener to receive notifications when dialog is closed by any means.
     */
    protected interface OnItemPickerResultsListener {
        void onItemPickerSave(final @NonNull ItemPickerDialog dialog,
                              final @NonNull Item item);

        void onItemPickerCancel(final @NonNull ItemPickerDialog dialog);
    }
}
