package com.eleybourn.bookcatalogue;

import android.app.Dialog;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

import java.util.List;

abstract class EditStringDialog {
    protected final CatalogueDBAdapter mDb;
    @SuppressWarnings("WeakerAccess")
    protected final Runnable mOnChanged;
    private final Context mContext;
    private ArrayAdapter<String> mAdapter;
    private Dialog mDialog;

    /**
     * EditText
     */
    EditStringDialog(@NonNull final Context context, @NonNull final CatalogueDBAdapter db, @NonNull final Runnable onChanged) {
        mContext = context;
        mOnChanged = onChanged;
        mDb = db;
    }

    /**
     * AutoCompleteTextView
     */
    EditStringDialog(@NonNull final Context context,
                     @NonNull final CatalogueDBAdapter db,
                     @NonNull final Runnable onChanged,
                     final int adapterResId,
                     @NonNull final List<String> list) {
        mContext = context;
        mOnChanged = onChanged;
        mDb = db;
        mAdapter = new ArrayAdapter<>(context, adapterResId, list);
    }

    protected void edit(@NonNull final String s, final int layout, final int title) {
        mDialog = new StandardDialogs.BasicDialog(mContext);
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
                    Toast.makeText(mContext, R.string.name_can_not_be_blank, Toast.LENGTH_LONG).show();
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
