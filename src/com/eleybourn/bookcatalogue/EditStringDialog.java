package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.app.Dialog;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.util.List;

abstract class EditStringDialog {
    protected final CatalogueDBAdapter mDb;
    protected final Runnable mOnChanged;
    private final Activity mActivity;
    private ArrayAdapter<String> mAdapter;
    private Dialog mDialog;

    /**
     * EditText
     */
    EditStringDialog(@NonNull final Activity activity, @NonNull final CatalogueDBAdapter db, @NonNull final Runnable onChanged) {
        this.mActivity = activity;
        mOnChanged = onChanged;
        mDb = db;
    }

    /**
     * AutoCompleteTextView
     */
    EditStringDialog(@NonNull final Activity activity,
                     @NonNull final CatalogueDBAdapter db,
                     @NonNull final Runnable onChanged,
                     @LayoutRes final int adapterResId,
                     @NonNull final List<String> list) {
        this.mActivity = activity;
        mOnChanged = onChanged;
        mDb = db;
        mAdapter = new ArrayAdapter<>(activity, adapterResId, list);
    }

    protected void edit(@NonNull final String s, @LayoutRes final int layout, final int title) {
        mDialog = new StandardDialogs.BasicDialog(mActivity);
        mDialog.setContentView(layout);
        mDialog.setTitle(title);

        final EditText nameView = mDialog.findViewById(R.id.name);
        nameView.setText(s);
        if (nameView instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) nameView).setAdapter(mAdapter);
        }

        Button cancelButton = mDialog.findViewById(R.id.cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDialog.dismiss();
            }
        });

        Button saveButton = mDialog.findViewById(R.id.confirm);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = nameView.getText().toString().trim();
                if (newName.isEmpty()) {
                    StandardDialogs.showQuickNotice(mActivity, R.string.name_can_not_be_blank);
                    return;
                }
                mDialog.dismiss();
                confirmEdit(s, newName);
            }
        });

        mDialog.show();
    }

    abstract protected void confirmEdit(@NonNull final String from, @NonNull final String to);
}
